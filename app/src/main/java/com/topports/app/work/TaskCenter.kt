package com.topports.app.work

import android.content.Context
import com.topports.app.archive.ArchiveEntry
import com.topports.app.archive.ArchiveExtractor
import com.topports.app.archive.ExtractCancelledException
import com.topports.app.archive.ExtractResult
import com.topports.app.archive.PasswordRequiredException
import com.topports.app.archive.VolumeSet
import com.topports.app.install.ApkInstaller
import com.topports.app.storage.Storage
import com.topports.app.telegram.TelegramClient
import com.topports.core.AppRelease
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.GamePackage
import com.topports.core.InstallPlanner
import com.topports.core.InstallRecord
import com.topports.app.updates.InstallRegistry
import com.topports.core.PackageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Tarea larga visible en la pestaña TAREAS. */
data class TaskState(
    val id: Int,
    val title: String,
    val message: String,
    /** 0..1, o null si no se sabe. */
    val progress: Float? = null,
    val running: Boolean = true,
    val failed: Boolean = false,
    val needsPassword: Boolean = false,
    /** Carpeta con el resultado, para abrirla en el explorador. */
    val resultDir: String? = null,
)

/** Estado de descarga de un paquete (suma de todas sus partes). */
data class DownloadInfo(
    val downloaded: Long,
    val total: Long,
    val active: Boolean,
    val complete: Boolean,
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val started: Boolean get() = downloaded > 0 || active
}

class NotEnoughSpaceException(needed: Long, free: Long) : IOException(
    "No hay espacio suficiente: hacen falta ${FileNames.formatSize(needed)} y quedan ${FileNames.formatSize(free)}",
)

/**
 * Descargas de Telegram y tareas de extracción / instalación / archivos.
 * Mientras haya trabajo, [WorkService] mantiene viva la app en segundo plano.
 */
class TaskCenter(
    private val context: Context,
    private val tg: TelegramClient,
    private val scope: CoroutineScope,
    private val installs: InstallRegistry,
) {
    private val extractor = ArchiveExtractor()
    private val ids = AtomicInteger(1)
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val retries = ConcurrentHashMap<Int, (String?) -> Unit>()

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    /** Nombres de los archivos que se han mandado descargar, para la pestaña TAREAS. */
    private val _downloadNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadNames: StateFlow<Map<Int, String>> = _downloadNames.asStateFlow()

    val hasRunningTasks: Boolean get() = _tasks.value.any { it.running }

    /** Paquetes que se están moviendo a su carpeta tras descargarse (clave [GamePackage.key]). */
    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    /** Resultado reciente de [savedFiles] por paquete (se consulta en cada redibujado de la ficha). */
    private val savedCache = ConcurrentHashMap<String, Pair<Long, List<Pair<String, File>>?>>()

    /** Última vez que se publicó el progreso de cada tarea. */
    private val lastProgressAt = ConcurrentHashMap<Int, Long>()

    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Extraer solo al terminar la descarga (Ajustes > Almacenamiento). */
    var autoExtract: Boolean = prefs.getBoolean("autoExtract", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("autoExtract", value).apply()
        }

    private fun movePendingDownloads(files: Map<Int, TdApi.File>) {
        for (p in installs.pendingSaves()) {
            if (p.pkgKey in _saving.value) continue
            if (!p.parts.all { (id, _) -> files[id]?.local?.isDownloadingCompleted == true }) continue
            // Carpeta del juego en una tarjeta que no está: espera a que vuelva.
            if (!File(p.dir).let { it.isDirectory || it.mkdirs() }) continue
            _saving.update { it + p.pkgKey }
            val unpack = autoExtract && shouldAutoExtract(p.parts.map { it.second })
            launchTask((if (unpack) "Extrayendo " else "Guardando ") + p.parts.first().second) { taskId, password ->
                try {
                    val parts = p.parts.map { (id, name) ->
                        val f = tg.refreshFile(id)
                        Triple(name, File(f.local.path), f.size)
                    }.also { checkParts(it) }.map { (name, file, _) -> name to file }
                    if (unpack) {
                        extractDownloaded(taskId, p, parts, password)
                    } else {
                        saveParts(taskId, p.gameId, p.pkgKey, p.parts.map { it.first }, parts, File(p.dir))
                    }
                } finally {
                    // Si falla, el archivo sigue en la caché y se puede guardar o extraer a mano.
                    installs.removePendingSave(p.pkgKey)
                    _saving.update { it - p.pkgKey }
                }
            }
        }
    }

    /**
     * Solo se extraen solos los comprimidos en partes (.7z.001, .part1.rar, .z01…). Cualquier
     * archivo suelto (.7z, .rar, .zip) se guarda tal cual en /TopPorts/<juego>: hay lanzadores de
     * ports que lo cargan directamente, y si hace falta se extrae a mano desde la ficha.
     */
    private fun shouldAutoExtract(names: List<String>): Boolean {
        val v = FileNames.volumeOf(names.first())
        if (FileNames.kindOf(v.baseName, v.style) != PackageKind.ARCHIVE) return false
        return names.size > 1
    }

    /**
     * Extrae en /TopPorts/<juego> lo recién descargado y borra el comprimido de la caché de
     * Telegram. Si falla, el comprimido se queda y se puede extraer o guardar a mano.
     */
    private suspend fun extractDownloaded(taskId: Int, p: InstallRegistry.PendingSave, parts: List<Pair<String, File>>, password: String?) {
        // Donde se eligió al descargar (raíz u otra carpeta, tal cual) o en la carpeta del juego.
        val chosen = p.extractTo?.let(::File)
        val dest = extractAndInstall(
            taskId, VolumeSet.ofParts(parts), chosen ?: File(p.dir), password, gameId = p.gameId, wrapper = chosen == null,
        )
        installs.recordUnpacked(p.pkgKey, dest.path, FileNames.volumeOf(p.parts.first().second).baseName)
        p.parts.forEach { (id, _) -> runCatching { tg.deleteFile(id) } }
    }

    /**
     * Partes ya guardadas en la carpeta del juego (null si falta alguna o no se guardaron). Mira el
     * disco como mucho cada 2 s por paquete: durante una descarga se pregunta muchas veces por segundo.
     */
    fun savedFiles(pkg: GamePackage): List<Pair<String, File>>? {
        val now = android.os.SystemClock.uptimeMillis()
        savedCache[pkg.key]?.let { (at, value) -> if (now - at < 2000) return value }
        return checkSavedFiles(pkg).also { setSaved(pkg.key, now, it) }
    }

    /** Sube cuando cambia lo guardado de algún paquete, para que la ficha se redibuje. */
    private val _savedVersion = MutableStateFlow(0)
    val savedVersion: StateFlow<Int> = _savedVersion.asStateFlow()
    private val savedChecks = ConcurrentHashMap.newKeySet<String>()

    private fun setSaved(key: String, at: Long, value: List<Pair<String, File>>?) {
        val old = savedCache.put(key, at to value)
        if (old == null || old.second != value) _savedVersion.update { it + 1 }
    }

    /**
     * Como [savedFiles], para la pantalla: devuelve lo último que se sabe sin tocar el disco y,
     * si es viejo, lo vuelve a mirar en segundo plano (avisa con [savedVersion] si cambia).
     */
    fun savedFilesCached(pkg: GamePackage): List<Pair<String, File>>? {
        val now = android.os.SystemClock.uptimeMillis()
        val cached = savedCache[pkg.key]
        if ((cached == null || now - cached.first >= 2000) && savedChecks.add(pkg.key)) {
            scope.launch(Dispatchers.IO) {
                try {
                    setSaved(pkg.key, android.os.SystemClock.uptimeMillis(), checkSavedFiles(pkg))
                } finally {
                    savedChecks.remove(pkg.key)
                }
            }
        }
        return cached?.second
    }

    private fun checkSavedFiles(pkg: GamePackage): List<Pair<String, File>>? {
        val dir = installs.savedDir(pkg.key) ?: return null
        val files = pkg.parts.map { it.name to File(dir, it.name) }
        val ok = files.zip(pkg.parts).all { (pair, part) -> pair.second.isFile && pair.second.length() == part.size }
        return files.takeIf { ok }
    }

    /** Archivos de un paquete en el móvil: en su carpeta si se guardaron, si no en la caché de Telegram. */
    private suspend fun localParts(pkg: GamePackage): List<Pair<String, File>> =
        savedFiles(pkg) ?: pkg.parts.map { part ->
            val f = tg.refreshFile(part.id)
            if (!f.local.isDownloadingCompleted) throw IOException("Falta descargar ${part.name}")
            Triple(part.name, File(f.local.path), if (f.size > 0) f.size else part.size)
        }.also { checkParts(it) }.map { (name, file, _) -> name to file }

    /**
     * Antes de extraer o guardar: cada parte está en el disco y con su tamaño. Si a una le faltan
     * bytes, 7-Zip da un error confuso de "archivo dañado"; así se dice cuál y qué hacer.
     */
    private fun checkParts(parts: List<Triple<String, File, Long>>) {
        for ((name, file, expected) in parts) {
            if (!file.isFile) throw IOException("No se encuentra $name. Bórralo y vuelve a descargarlo.")
            if (expected > 0 && file.length() != expected) {
                throw IOException(
                    "$name está incompleto (${FileNames.formatSize(file.length())} de " +
                        "${FileNames.formatSize(expected)}). Bórralo y vuelve a descargarlo.",
                )
            }
        }
    }

    // --- Descargas ---

    fun downloadInfo(pkg: GamePackage, files: Map<Int, TdApi.File>): DownloadInfo {
        if (savedFilesCached(pkg) != null) return DownloadInfo(pkg.size, pkg.size, active = false, complete = true)
        var downloaded = 0L
        var total = 0L
        var active = false
        var complete = true
        for (part in pkg.parts) {
            val f = files[part.id]
            val size = f?.let { if (it.size > 0) it.size else it.expectedSize }?.takeIf { it > 0 } ?: part.size
            total += size
            downloaded += f?.local?.downloadedSize ?: 0
            active = active || f?.local?.isDownloadingActive == true
            complete = complete && f?.local?.isDownloadingCompleted == true
        }
        return DownloadInfo(downloaded, total, active, complete)
    }

    /** Pide a TDLib el estado actual de las partes (p. ej. al abrir una ficha). */
    fun refresh(pkg: GamePackage) {
        scope.launch {
            pkg.parts.forEach { runCatching { tg.refreshFile(it.id) } }
        }
    }

    /**
     * Espacio libre que hace falta para descargar [pkg] (sin lo ya bajado, [downloaded]) y, si es
     * un comprimido, extraerlo después; si TDLib descarga en la memoria privada, también para
     * copiar la parte más grande a /TopPorts. Para avisar en la ficha antes de descargar.
     */
    fun spaceNeeded(pkg: GamePackage, downloaded: Long = 0L): Long {
        val copyNeeded = tg.filesDir.startsWith(context.filesDir)
        // Solo los comprimidos en partes se extraen al descargar y necesitan sitio para ello.
        val extracts = autoExtract && shouldAutoExtract(pkg.parts.map { it.name })
        val extra = (if (extracts) pkg.size else 0L) + (if (copyNeeded) pkg.parts.maxOf { it.size } else 0L)
        return (pkg.size - downloaded).coerceAtLeast(0) + extra + queuedRemaining(except = pkg.key)
    }

    /** Espacio libre en la carpeta de juegos (donde se descargan y se extraen). */
    fun freeSpace(): Long = Storage.freeBytes(Storage.topPortsDir(context))

    /**
     * Dónde se extraerá sola una descarga pendiente al terminar (null si no se extrae sola). Si es
     * la carpeta del juego, puede acabar en la que traiga el comprimido ([unwrapDest]).
     */
    fun extractTarget(p: InstallRegistry.PendingSave): File? =
        if (autoExtract && shouldAutoExtract(p.parts.map { it.second })) File(p.extractTo ?: p.dir) else null

    /** ¿Se extraerá sola al terminar la descarga? (Comprimido en partes y "Extraer al descargar".) */
    fun willAutoExtract(pkg: GamePackage): Boolean = autoExtract && shouldAutoExtract(pkg.parts.map { it.name })

    /**
     * Pone [pkg] en la cola. [extractTo]: dónde extraerlo al terminar si se eligió al descargar
     * (null: en la carpeta del juego, o donde se eligió la primera vez si ya estaba empezada).
     */
    fun download(game: Game, pkg: GamePackage, extractTo: File? = null, onError: (String) -> Unit) {
        scope.launch {
            try {
                Storage.gamesDirProblem(context)?.let { throw java.io.IOException(it) }
                val files = pkg.parts.map { tg.refreshFile(it.id) }
                val remaining = files.sumOf { f ->
                    val size = if (f.size > 0) f.size else f.expectedSize
                    (size - f.local.downloadedSize).coerceAtLeast(0)
                }
                // Los archivos comprimidos necesitan además sitio para extraerse.
                // Si TDLib descarga en la memoria privada, al pasarlo a /TopPorts se copia parte a
                // parte: hace falta sitio para la mayor. En /TopPorts/.telegram solo se renombra.
                val copyNeeded = tg.filesDir.startsWith(context.filesDir)
                val extracts = autoExtract && shouldAutoExtract(pkg.parts.map { it.name })
                val extra = (if (extracts) pkg.size else 0L) + (if (copyNeeded) pkg.parts.maxOf { it.size } else 0L)
                val free = freeSpace() - queuedRemaining(except = pkg.key)
                val previous = installs.pendingSaves().firstOrNull { it.pkgKey == pkg.key }
                val target = extractTo ?: previous?.extractTo?.let(::File)
                if (extracts && target != null && !Storage.sameVolume(context, target, Storage.topPortsDir(context))) {
                    // Se descarga en la carpeta de juegos (p. ej. la SD) y se extrae en otra memoria
                    // (p. ej. la raíz de la interna): cada sitio necesita lo suyo.
                    if (remaining + extra - pkg.size > free) throw NotEnoughSpaceException(remaining + extra - pkg.size, free.coerceAtLeast(0))
                    val freeThere = Storage.freeBytes(target)
                    if (pkg.size > freeThere) throw NotEnoughSpaceException(pkg.size, freeThere)
                } else if (remaining + extra > free) {
                    throw NotEnoughSpaceException(remaining + extra, free.coerceAtLeast(0))
                }

                val dir = when {
                    // Los APK, todos juntos en la carpeta APK (se borran al instalarse).
                    pkg.kind == PackageKind.APK -> Storage.apkDir(context).apply { mkdirs() }
                    // Si se extrae en otro sitio, la carpeta del juego no se crea: se quedaría vacía.
                    extracts && target != null -> File(Storage.topPortsDir(context), installs.gameFolderName(game.id, game.title))
                    else -> installs.gameDir(context, game.id, game.title)
                }
                _downloadErrors.update { it - pkg.key }
                // A la cola: la descarga empieza cuando le toque (ver [schedule]).
                installs.addPendingSave(
                    InstallRegistry.PendingSave(
                        pkg.key, game.id, dir.path, pkg.parts.map { it.id to it.name },
                        addedAt = previous?.addedAt ?: System.currentTimeMillis(),
                        paused = false,
                        extractTo = target?.path,
                    ),
                )
                _downloadNames.update { names -> names + pkg.parts.associate { it.id to it.name } }
                requestSchedule()
                WorkService.start(context)
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
            }
        }
    }

    /**
     * Lo que aún falta por bajar de las demás descargas de la cola: ese sitio ya está "pedido".
     * Sin esto, varias descargas grandes seguidas pasaban la comprobación una a una y la memoria
     * se llenaba a mitad.
     */
    private fun queuedRemaining(except: String): Long {
        val files = tg.files.value
        return installs.pendingSaves().filter { it.pkgKey != except && it.pkgKey !in _saving.value }
            .flatMap { it.parts }
            .sumOf { (id, _) ->
                val f = files[id] ?: return@sumOf 0L
                val size = if (f.size > 0) f.size else f.expectedSize
                if (f.local.isDownloadingCompleted) 0L else (size - f.local.downloadedSize).coerceAtLeast(0)
            }
    }

    /** Descargas de la tienda aún sin terminar (y sin mover ni extraer), para TAREAS. */
    fun pendingDownloads(): List<InstallRegistry.PendingSave> =
        installs.pendingSaves().filter { it.pkgKey !in _saving.value }

    /**
     * Descargas que Telegram no deja hacer (p. ej. el post se borró), con el motivo. Se quedan en
     * pausa con el error a la vista en vez de reintentarlo sin fin y sin avisar.
     */
    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    private suspend fun failDownload(p: InstallRegistry.PendingSave, error: Throwable) {
        installs.setPendingPaused(p.pkgKey, true)
        startedAt.remove(p.pkgKey)
        p.parts.forEach { (id, _) -> runCatching { tg.pauseDownload(id) } }
        val raw = error.message ?: error.toString()
        val reason = when {
            "FILE_REFERENCE" in raw || "not found" in raw.lowercase() || "MESSAGE_ID_INVALID" in raw ->
                "Telegram ya no tiene este archivo (¿se borró el post?)"
            "FLOOD_WAIT" in raw || "Too Many Requests" in raw -> "Telegram pide esperar un rato antes de seguir"
            else -> "Telegram no deja descargarlo: $raw"
        }
        _downloadErrors.update { it + (p.pkgKey to reason) }
    }

    /** Pausa a mano: la cola no la vuelve a arrancar hasta que se pulse Continuar. */
    fun pauseDownload(p: InstallRegistry.PendingSave) {
        installs.setPendingPaused(p.pkgKey, true)
        scope.launch { p.parts.forEach { (id, _) -> runCatching { tg.pauseDownload(id) } } }
        requestSchedule()
    }

    /** Vuelve a la cola (en su sitio de antes); empieza en cuanto haya hueco. */
    fun resumeDownload(p: InstallRegistry.PendingSave) {
        _downloadErrors.update { it - p.pkgKey }
        installs.setPendingPaused(p.pkgKey, false)
        requestSchedule()
        WorkService.start(context)
    }

    /** Para la descarga y borra lo ya bajado (no toca lo instalado ni extraído antes). */
    fun cancelDownload(p: InstallRegistry.PendingSave) {
        _downloadErrors.update { it - p.pkgKey }
        installs.removePendingSave(p.pkgKey)
        scope.launch {
            p.parts.forEach { (id, _) ->
                runCatching { tg.pauseDownload(id) }
                runCatching { tg.deleteFile(id) }
            }
        }
    }

    fun pause(pkg: GamePackage) {
        val p = installs.pendingSaves().firstOrNull { it.pkgKey == pkg.key }
        if (p != null) pauseDownload(p) else scope.launch { pkg.parts.forEach { runCatching { tg.pauseDownload(it.id) } } }
    }

    // --- Cola de descargas ---

    /** Descargas a la vez (0 = sin límite). Ajustes → Almacenamiento. */
    var maxParallel: Int = prefs.getInt("maxParallel", 2)
        set(value) {
            field = value
            prefs.edit().putInt("maxParallel", value).apply()
            requestSchedule()
        }

    /** Reanudar las descargas pendientes al encender la consola ([BootReceiver]). */
    var resumeOnBoot: Boolean = prefs.getBoolean("resumeOnBoot", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("resumeOnBoot", value).apply()
        }

    /**
     * Que no se apague la pantalla mientras se extrae, se copia o se instala con la app abierta
     * ([MainActivity] lo aplica). Ajustes → Almacenamiento.
     */
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keepScreenOn", true))
    val keepScreenOnFlow: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    var keepScreenOn: Boolean
        get() = _keepScreenOn.value
        set(value) {
            _keepScreenOn.value = value
            prefs.edit().putBoolean("keepScreenOn", value).apply()
        }

    /** Descargar solo con WiFi (o cable). Ajustes → Almacenamiento. */
    var wifiOnly: Boolean = prefs.getBoolean("wifiOnly", false)
        set(value) {
            field = value
            prefs.edit().putBoolean("wifiOnly", value).apply()
            requestSchedule()
        }

    /** Paquetes esperando su turno en la cola (clave [GamePackage.key]). */
    private val _queued = MutableStateFlow<Set<String>>(emptySet())
    val queued: StateFlow<Set<String>> = _queued.asStateFlow()

    /** Hay descargas paradas porque no hay WiFi y está activado "Solo con WiFi". */
    private val _waitingWifi = MutableStateFlow(false)
    val waitingWifi: StateFlow<Boolean> = _waitingWifi.asStateFlow()

    /**
     * Por qué no puede arrancar ninguna descarga aunque haya cola: la carpeta de juegos elegida no
     * está (tarjeta SD fuera, sin permiso) o TDLib aún no se ha pasado a ella. null si todo bien.
     */
    private val _storageProblem = MutableStateFlow<String?>(null)
    val storageProblem: StateFlow<String?> = _storageProblem.asStateFlow()

    private val onWifi = MutableStateFlow(true)
    private val scheduleRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    /** Arrancadas hace poco: TDLib tarda un momento en avisar de que ya están activas. */
    private val startedAt = ConcurrentHashMap<String, Long>()

    // Va después de las propiedades de la cola: Kotlin inicializa en orden y el init las usa.
    init {
        // Al terminar una descarga, el archivo pasa solo a /TopPorts/<juego>.
        scope.launch {
            tg.files.collect { files ->
                movePendingDownloads(files)
                requestSchedule()
            }
        }
        // La cola: se revisa al cambiar las descargas, la red o los ajustes (como mucho 2 veces por segundo).
        watchNetwork()
        // Esperando a la tarjeta SD (p. ej. al encender, que se monta tarde): nadie avisa de que
        // ha vuelto, así que se mira cada pocos segundos.
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (_storageProblem.value != null) requestSchedule()
            }
        }
        scope.launch {
            for (request in scheduleRequests) {
                runCatching { schedule() }
                kotlinx.coroutines.delay(500)
            }
        }
        // Si la app se cerró justo al terminar una descarga (o a mitad de extraerla), TDLib no
        // vuelve a avisar de esas partes y se quedarían sin mover. Al conectar se pregunta por
        // ellas: las ya completas se mueven o extraen solas.
        // Los ids de archivo de TDLib solo valen mientras ese TDLib sigue abierto. Al cerrar
        // sesión las descargas pendientes se olvidan (lo bajado se borra con la sesión); si TDLib
        // solo se reinicia (p. ej. al dar el permiso de archivos), esperan a que el catálogo se
        // lea otra vez y les ponga los ids nuevos ([pendingResolved]).
        scope.launch {
            tg.authState.collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateLoggingOut -> {
                        installs.invalidatePending(forget = true)
                        _downloadErrors.value = emptyMap()
                        _queued.value = emptySet()
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        installs.invalidatePending(forget = false)
                        startedAt.clear()
                        _queued.value = emptySet()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * El catálogo ha puesto a las descargas pendientes sus ids de archivo de ahora (al arrancar la
     * app o tras reiniciarse TDLib): se pregunta por sus partes —las ya completas se mueven o
     * extraen solas, por si la app se cerró justo al terminar— y la cola sigue.
     */
    fun pendingResolved() {
        scope.launch {
            installs.pendingSaves().flatMap { it.parts }.forEach { (id, _) -> runCatching { tg.refreshFile(id) } }
            requestSchedule()
            if (installs.pendingSaves().any { !it.paused }) WorkService.start(context)
        }
    }

    private fun requestSchedule() {
        scheduleRequests.trySend(Unit)
    }

    private fun watchNetwork() {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        fun update(caps: android.net.NetworkCapabilities?) {
            onWifi.value = caps != null && (
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            requestSchedule()
        }
        update(cm.getNetworkCapabilities(cm.activeNetwork))
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) = update(caps)
                override fun onLost(network: android.net.Network) = update(null)
            })
        }
    }

    /**
     * Reparte las descargas de la tienda: como mucho [maxParallel] a la vez, por orden de
     * petición; las demás esperan "en cola". Con [wifiOnly] y sin WiFi, se paran todas (sin
     * marcarlas como pausadas: siguen solas al volver la WiFi). Las pausadas a mano no se tocan.
     */
    private suspend fun schedule() {
        val files = tg.files.value
        val now = android.os.SystemClock.uptimeMillis()
        fun complete(p: InstallRegistry.PendingSave) = p.parts.all { (id, _) -> files[id]?.local?.isDownloadingCompleted == true }
        fun active(p: InstallRegistry.PendingSave) =
            p.parts.any { (id, _) -> files[id]?.local?.isDownloadingActive == true } ||
                now - (startedAt[p.pkgKey] ?: 0L) < 5_000
        // Las que se están moviendo o extrayendo ([saving]) no se tocan: al terminar se borran de la
        // caché de Telegram y, por un momento, parecen "sin descargar" antes de salir de la lista;
        // la cola las volvía a descargar y se quedaban colgadas al 100 % sin nadie que las moviera.
        val busy = _saving.value
        val waiting = installs.pendingSaves()
            .filter { !it.paused && it.pkgKey !in busy && !complete(it) }
            .sortedBy { it.addedAt }

        // Sin la carpeta de juegos (o con TDLib aún descargando en otra) no se descarga nada:
        // bajaría a otra memoria y no se podría pasar a la carpeta del juego.
        val problem = Storage.gamesDirProblem(context)
            ?: if (tg.filesDir != tg.chooseFilesDir()) "Cambiando la carpeta de descargas…" else null
        _storageProblem.value = problem
        if (problem != null || (wifiOnly && !onWifi.value)) {
            waiting.filter { active(it) }.forEach { p ->
                startedAt.remove(p.pkgKey)
                p.parts.forEach { (id, _) -> runCatching { tg.pauseDownload(id) } }
            }
            _waitingWifi.value = problem == null && waiting.isNotEmpty()
            _queued.value = waiting.map { it.pkgKey }.toSet()
            // Ha vuelto la tarjeta o se ha cambiado de carpeta: TDLib se reinicia en la buena
            // en cuanto no haya nada descargándose.
            if (problem != null && Storage.gamesDirProblem(context) == null) tg.applyStorageLocation()
            return
        }
        _waitingWifi.value = false
        val limit = if (maxParallel <= 0) Int.MAX_VALUE else maxParallel
        val running = waiting.filter { active(it) }.toMutableList()
        // Si se ha bajado el límite, las últimas pedidas vuelven a esperar.
        while (running.size > limit) {
            val p = running.removeAt(running.lastIndex)
            startedAt.remove(p.pkgKey)
            p.parts.forEach { (id, _) -> runCatching { tg.pauseDownload(id) } }
        }
        val failed = mutableSetOf<InstallRegistry.PendingSave>()
        for (p in waiting) {
            if (running.size >= limit) break
            if (p in running) continue
            startedAt[p.pkgKey] = now
            val error = p.parts.firstNotNullOfOrNull { (id, _) -> runCatching { tg.startDownload(id) }.exceptionOrNull() }
            if (error != null) {
                // Solo un rechazo de Telegram cuenta como fallo; si TDLib se está reiniciando
                // (código -1) se vuelve a intentar en la siguiente vuelta de la cola.
                if (error is com.topports.app.telegram.TdException && error.code != -1) {
                    failDownload(p, error)
                    failed += p
                } else {
                    startedAt.remove(p.pkgKey)
                }
                continue
            }
            running += p
        }
        _queued.value = (waiting - running.toSet() - failed).map { it.pkgKey }.toSet()
    }

    /**
     * Android ha confirmado la instalación [sessionId]: se borran los APK descargados que se
     * instalaron (solo los de la carpeta APK; los datos no se tocan). Si falló o se canceló, se
     * quedan para volver a intentarlo ([installed] = false).
     */
    fun apkInstallFinished(sessionId: Int, installed: Boolean) {
        val (pkgKey, files) = installs.takeApkCleanup(sessionId) ?: return
        if (!installed) return
        files.filter { it.isFile && FileNames.isApk(FileNames.volumeOf(it.name).baseName) }.forEach { it.delete() }
        installs.forgetSaved(pkgKey)
        savedCache.remove(pkgKey)
        _savedVersion.update { it + 1 }
    }

    /**
     * Cambia la carpeta de juegos (null = /TopPorts de siempre). Devuelve el motivo si no se puede:
     * con descargas a medias o tareas en marcha no se cambia, porque sus archivos están en la
     * carpeta de ahora. Los juegos ya bajados se quedan donde están.
     */
    fun setGamesDir(dir: File?): String? {
        if (installs.pendingSaves().isNotEmpty()) return "Antes termina o cancela las descargas de TAREAS"
        if (_tasks.value.any { it.running }) return "Espera a que terminen las tareas en marcha"
        if (tg.files.value.values.any { it.local.isDownloadingActive }) return "Espera a que termine la descarga en curso"
        if (dir != null) Storage.checkGamesDir(context, dir)?.let { return it }
        // Volver a /TopPorts es como no haber elegido nada.
        val chosen = dir?.takeIf { it.absolutePath != File(Storage.externalRoot, "TopPorts").absolutePath }
        Storage.setCustomGamesDir(context, chosen)
        tg.applyStorageLocation()
        requestSchedule()
        return null
    }

    fun deleteDownload(pkg: GamePackage) {
        _downloadErrors.update { it - pkg.key }
        installs.removePendingSave(pkg.key)
        scope.launch {
            pkg.parts.forEach {
                runCatching { tg.pauseDownload(it.id) }
                runCatching { tg.deleteFile(it.id) }
            }
            withContext(Dispatchers.IO) { savedFiles(pkg)?.forEach { it.second.delete() } }
            installs.forgetSaved(pkg.key)
            installs.forgetUnpacked(pkg.key)
            savedCache.remove(pkg.key)
            _savedVersion.update { it + 1 }
        }
    }

    // --- Instalar desde la tienda ---

    /**
     * [dest]: carpeta donde extraer o guardar (raíz de la memoria, la carpeta del juego u otra).
     * [wrapper]: [dest] es la carpeta del juego, que se salta si el comprimido ya trae la suya.
     */
    fun installPackage(game: Game, pkg: GamePackage, dest: File, wrapper: Boolean = false) {
        launchTask(game.title) { taskId, password ->
            val parts = localParts(pkg)
            when (pkg.kind) {
                PackageKind.APK -> {
                    val source = if (parts.size == 1) ApkInstaller.fromFile(parts[0].second)
                    else ApkInstaller.fromParts(pkg.name, FileNames.sortVolumes(parts) { it.first }.map { it.second })
                    if (parts.size == 1) remember(parts[0].second, game, pkg)
                    installApks(taskId, listOf(source)) { sessionId ->
                        // Partido en .001/.002…: el paquete se sabe cuando Android lo instala.
                        if (parts.size > 1) {
                            installs.rememberSession(sessionId, InstallRecord("", pkg.name, pkg.parts.first().id, game.id, pkg.size))
                        }
                        // El APK descargado (el que se guardó al bajarlo, en la carpeta APK o, en
                        // descargas de antes, en la del juego) se borra cuando Android confirme la
                        // instalación. Los APK sacados de un comprimido u otros archivos, no.
                        val savedDir = installs.savedDir(pkg.key)?.let { File(it).absolutePath }
                        val downloaded = parts.map { it.second }.filter { it.parentFile?.absolutePath == savedDir }
                        if (downloaded.isNotEmpty()) installs.rememberApkCleanup(sessionId, pkg.key, downloaded)
                    }
                    finish(taskId, "Instalación lanzada. Acepta el aviso de Android.", notify = false)
                }
                PackageKind.OBB -> {
                    val dest = copyObb(taskId, parts[0].second, parts[0].first)
                    finish(taskId, "OBB copiado en $dest", dest)
                }
                PackageKind.ARCHIVE, PackageKind.APK_BUNDLE -> {
                    extractAndInstall(taskId, VolumeSet.ofParts(parts), dest, password, game, pkg, wrapper = wrapper)
                }
                PackageKind.OTHER -> saveParts(taskId, game.id, pkg.key, pkg.parts.map { it.id }, parts, dest)
            }
        }
    }

    /**
     * Guarda el archivo tal cual, sin extraerlo (p. ej. un .zip que carga el lanzador del port).
     * Se copia de la caché de Telegram a [dest] y después se borra de la caché para no ocupar el doble.
     */
    fun savePackage(game: Game, pkg: GamePackage, dest: File) {
        launchTask(game.title) { taskId, _ ->
            saveParts(taskId, game.id, pkg.key, pkg.parts.map { it.id }, localParts(pkg), dest)
        }
    }

    /**
     * Pone las partes en [dest] (moviéndolas si se puede, si no copiándolas) y borra la copia de la
     * caché de Telegram para no ocupar el doble.
     */
    private suspend fun saveParts(
        taskId: Int,
        gameId: Long,
        pkgKey: String,
        fileIds: List<Int>,
        parts: List<Pair<String, File>>,
        dest: File,
    ) {
        dest.mkdirs()
        val saved = parts.map { (name, file) ->
            val target = File(dest, name)
            if (file.canonicalPath != target.canonicalPath && !file.renameTo(target)) {
                val free = Storage.freeBytes(dest)
                if (file.length() > free) throw NotEnoughSpaceException(file.length(), free)
                try {
                    copyWithProgress(taskId, file, target)
                } catch (e: Exception) {
                    // No dejar un archivo a medias si se cancela o falla la copia.
                    target.delete()
                    throw e
                }
                file.delete()
            }
            target
        }
        installs.recordExtracted(gameId, saved.filter { Storage.isSafeToDelete(it) }.map { it.path })
        installs.recordSaved(pkgKey, dest.path)
        savedCache.remove(pkgKey)
        _savedVersion.update { it + 1 }
        fileIds.forEach { runCatching { tg.deleteFile(it) } }
        val names = saved.joinToString("\n") { it.name }
        finish(taskId, "Guardado en ${dest.path}:\n$names", dest.path)
    }

    /** Descarga el APK nuevo de Top Ports del canal y lo instala encima (misma firma: no se pierde nada). */
    fun updateApp(release: AppRelease) {
        launchTask("Top Ports ${release.version}") { taskId, _ ->
            update(taskId, "Descargando ${release.file.name}", null)
            val path = tg.downloadNow(release.file.id, priority = 32)
                ?: throw IOException("No se pudo descargar ${release.file.name}")
            installApks(taskId, listOf(ApkInstaller.fromFile(File(path))))
            // El instalador ya tiene su copia: no hace falta guardar el APK.
            runCatching { tg.deleteFile(release.file.id) }
            finish(taskId, "Instalación lanzada. Acepta el aviso de Android; la app se reiniciará.", notify = false)
        }
    }

    // --- Explorador de archivos ---

    /** [wrapper]: [target] es una carpeta nueva con el nombre del comprimido (ver [unwrapDest]). */
    fun extractLocal(archive: File, target: File, installAfter: Boolean, wrapper: Boolean = false) {
        launchTask(archive.name) { taskId, password ->
            val volumes = VolumeSet.local(archive)
            if (installAfter) {
                extractAndInstall(taskId, volumes, target, password, wrapper = wrapper)
            } else {
                val dest = if (wrapper) unwrapDest(volumes, target, password) else target
                val result = extract(taskId, volumes, dest, password)
                val folder = resultFolder(target, dest, result.files)
                finish(
                    taskId,
                    "Extraídos ${result.files.size} archivos (${FileNames.formatSize(result.writtenBytes)}) en ${folder.path}",
                    folder.path,
                )
            }
        }
    }

    suspend fun listArchive(archive: File, password: String? = null): List<ArchiveEntry> =
        withContext(Dispatchers.IO) { extractor.list(VolumeSet.local(archive), password) }

    fun installLocalApk(apk: File) {
        launchTask(apk.name) { taskId, _ ->
            val v = FileNames.volumeOf(apk.name)
            val source = if (v.style == FileNames.SplitStyle.NUMBERED) {
                val dir = apk.parentFile ?: throw IOException("Ruta no válida")
                val parts = generateSequence(1) { it + 1 }
                    .map { File(dir, v.baseName + "." + it.toString().padStart(3, '0')) }
                    .takeWhile { it.isFile }
                    .toList()
                ApkInstaller.fromParts(v.baseName, parts)
            } else {
                ApkInstaller.fromFile(apk)
            }
            installApks(taskId, listOf(source))
            finish(taskId, "Instalación lanzada. Acepta el aviso de Android.", notify = false)
        }
    }

    fun copyFiles(sources: List<File>, destDir: File, move: Boolean) {
        val title = if (move) "Mover ${sources.size} elemento(s)" else "Copiar ${sources.size} elemento(s)"
        launchTask(title) { taskId, _ ->
            val total = sources.sumOf { sizeOf(it) }.coerceAtLeast(1)
            var done = 0L
            val destPath = destDir.absoluteFile.toPath().normalize()
            for (src in sources) {
                // Una carpeta no se puede copiar ni mover dentro de sí misma: se copiaría sin fin.
                if (src.isDirectory && destPath.startsWith(src.absoluteFile.toPath().normalize())) {
                    throw IOException("No se puede copiar \"${src.name}\" dentro de sí misma")
                }
                // Mover a la misma carpeta donde ya está: no hay nada que hacer (si no, saldría
                // una copia "(2)" y se borraría el original).
                if (move && samePath(src.parentFile ?: src, destDir)) continue
                val target = uniqueTarget(File(destDir, src.name))
                if (move && src.renameTo(target)) {
                    done += sizeOf(target)
                    continue
                }
                src.walkTopDown().forEach { file ->
                    val rel = file.relativeTo(src).path
                    val out = if (rel.isEmpty()) target else File(target, rel)
                    if (file.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        file.inputStream().use { input ->
                            out.outputStream().use { output ->
                                val buffer = ByteArray(1 shl 20)
                                while (true) {
                                    checkActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    done += n
                                    update(taskId, "${file.name}", done.toFloat() / total)
                                }
                            }
                        }
                    }
                }
                if (move) src.deleteRecursively()
            }
            finish(taskId, if (move) "Movido" else "Copiado", destDir.path)
        }
    }

    fun deleteFiles(files: List<File>) {
        launchTask("Borrar ${files.size} elemento(s)") { taskId, _ ->
            files.forEach {
                update(taskId, it.name, null)
                it.deleteRecursively()
            }
            finish(taskId, "Borrado")
        }
    }

    // --- Gestión de tareas ---

    fun cancel(taskId: Int) {
        jobs[taskId]?.cancel()
        _tasks.update { list ->
            list.map { if (it.id == taskId) it.copy(running = false, failed = true, message = "Cancelado") else it }
        }
    }

    fun retryWithPassword(taskId: Int, password: String) {
        val retry = retries.remove(taskId) ?: return
        dismiss(taskId)
        retry(password)
    }

    fun dismiss(taskId: Int) {
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        forget(taskId)
    }

    fun clearFinished() {
        val gone = _tasks.value.filterNot { it.running }
        _tasks.update { list -> list.filter { it.running } }
        gone.forEach { forget(it.id) }
    }

    /** Lo que se guarda por tarea, para que no se acumule tras quitarla de la lista. */
    private fun forget(taskId: Int) {
        retries.remove(taskId)
        lastProgressAt.remove(taskId)
    }

    private fun launchTask(title: String, password: String? = null, block: suspend (Int, String?) -> Unit) {
        val taskId = ids.getAndIncrement()
        _tasks.update { it + TaskState(taskId, title, "Preparando…") }
        WorkService.start(context)
        val job = scope.launch(Dispatchers.IO) {
            try {
                block(taskId, password)
            } catch (e: PasswordRequiredException) {
                retries[taskId] = { pw -> launchTask(title, pw, block) }
                fail(taskId, if (password == null) "Necesita contraseña" else "Contraseña incorrecta", needsPassword = true)
            } catch (e: ExtractCancelledException) {
                fail(taskId, "Cancelado", notify = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                fail(taskId, "Cancelado", notify = false)
            } catch (e: Exception) {
                fail(taskId, e.message ?: e.toString())
            } finally {
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
    }

    private suspend fun extract(taskId: Int, volumes: VolumeSet, dest: File, password: String?): ExtractResult {
        val coroutineContext = kotlin.coroutines.coroutineContext
        update(taskId, "Extrayendo en ${dest.path}", 0f)
        val result = extractor.extract(
            volumes = volumes,
            dest = dest,
            password = password,
            checkSpace = { needed ->
                // Sin tamaños en la cabecera, el comprimido como estimación mínima.
                val bytes = needed ?: volumes.allFiles().sumOf { it.length() }
                val free = Storage.freeBytes(dest)
                if (bytes > free) throw NotEnoughSpaceException(bytes, free)
            },
            onProgress = { done, total ->
                if (total > 0) update(taskId, "Extrayendo… ${FileNames.formatSize(done)} / ${FileNames.formatSize(total)}", done.toFloat() / total)
            },
            isCancelled = { !coroutineContext.isActive },
        )
        // Partes .001/.002 de un archivo sin extensión conocida: lo que sale es el .7z/.zip/.rar
        // unido. Se extrae también y se borra, para no tener que hacerlo a mano.
        val inner = result.files.singleOrNull()?.let { File(dest, it) }
        if (FileNames.volumeOf(volumes.firstName).style == FileNames.SplitStyle.NUMBERED &&
            inner != null && inner.isFile && FileNames.kindOf(inner.name) == PackageKind.ARCHIVE
        ) {
            val nested = extract(taskId, VolumeSet.local(inner), dest, password)
            inner.delete()
            return nested
        }
        return result
    }

    /** Anota de qué archivo del canal sale un APK, para avisar luego de sus actualizaciones. */
    private fun remember(apk: File, game: Game, pkg: GamePackage) {
        val packageName = installs.packageNameOf(context, apk) ?: return
        installs.record(InstallRecord(packageName, pkg.name, pkg.parts.first().id, game.id, pkg.size))
    }

    /**
     * Si todo el comprimido cuelga de una sola carpeta (lo normal: "DKC/…"), se extrae al lado de
     * [dest] y no dentro, para que no queden dos niveles (/TopPorts/<juego>/DKC): queda
     * /TopPorts/DKC, con el nombre que traiga, que es el que suelen pedir las instrucciones. Con
     * nombres genéricos (data, files…) se deja dentro de [dest]: dos juegos podrían compartirlos.
     */
    private suspend fun unwrapDest(volumes: VolumeSet, dest: File, password: String?): File {
        val parent = dest.parentFile ?: return dest
        // Con contraseña puede no dejar ver el contenido: se extrae como siempre.
        val entries = withContext(Dispatchers.IO) { runCatching { extractor.list(volumes, password) }.getOrNull() }
            ?: return dest
        fun clean(path: String) = path.replace('\\', '/').removePrefix("./").trim('/')
        val paths = entries.map { clean(it.path) }.filter { it.isNotEmpty() }
        val top = paths.map { it.substringBefore('/') }.distinct().singleOrNull() ?: return dest
        val isFolder = paths.any { it.startsWith("$top/") } || entries.any { it.isDirectory && clean(it.path) == top }
        if (!isFolder || top == ".." || top.startsWith(".") || top.lowercase() in genericFolders) return dest
        // La carpeta del juego, recién creada y vacía, ya no hace falta.
        if (dest.isDirectory && dest.list().isNullOrEmpty()) dest.delete()
        return parent
    }

    /** Carpetas que traen muchos comprimidos: sacarlas fuera mezclaría juegos distintos. */
    private val genericFolders = setOf(
        "data", "datos", "files", "archivos", "assets", "game", "games", "juego", "juegos", "bin", "content",
        "contents", "resources", "res", "src", "output", "extracted", "new folder", "nueva carpeta",
        "android", "obb", "install", "port", "ports", "topports",
    )

    /**
     * Extrae en [target] (o al lado, ver [unwrapDest], si [wrapper]), mueve OBB y datos e instala
     * los APK. Devuelve la carpeta que ha salido (la que traía el comprimido, si se sacó fuera).
     */
    private suspend fun extractAndInstall(
        taskId: Int,
        volumes: VolumeSet,
        target: File,
        password: String?,
        game: Game? = null,
        pkg: GamePackage? = null,
        gameId: Long? = game?.id,
        wrapper: Boolean = false,
    ): File {
        val dest = if (wrapper) unwrapDest(volumes, target, password) else target
        dest.mkdirs()
        // Solo lo recién extraído: el destino puede ser la raíz de la memoria, con otras cosas.
        val result = extract(taskId, volumes, dest, password)
        val extracted = result.files
        val folder = resultFolder(target, dest, extracted)
        // Recordar qué se extrajo de este juego (primer nivel) para poder borrarlo en Instalados.
        if (gameId != null) {
            installs.recordExtracted(
                gameId,
                extracted.map { it.substringBefore('/') }.distinct()
                    .map { File(dest, it) }
                    .filter { Storage.isSafeToDelete(it) }
                    .map { it.path },
            )
        }
        val plan = InstallPlanner.plan(extracted)
        val notes = mutableListOf(
            "Extraídos ${extracted.size} archivos (${FileNames.formatSize(result.writtenBytes)}) en ${folder.path}",
        )

        for (obb in plan.obbs) {
            val src = File(dest, obb.path)
            val target = File(Storage.obbDir(obb.packageName), src.name)
            update(taskId, "Copiando OBB ${src.name}", null)
            // Extraído en la raíz de la memoria: ya está en Android/obb y no hay que moverlo.
            if (samePath(src, target)) {
                notes += "OBB en Android/obb/${obb.packageName}"
                continue
            }
            // El OBB se mueve: ya no estará en la carpeta de extracción, sino en Android/obb.
            if (moveOrCopy(src, target)) notes += "OBB movido a Android/obb/${obb.packageName}"
            else notes += "No se pudo copiar ${src.name} a Android/obb/${obb.packageName}: muévelo a mano"
        }

        for (data in plan.dataDirs) {
            val src = File(dest, data.path)
            val target = Storage.dataDir(data.packageName)
            update(taskId, "Copiando datos de ${data.packageName}", null)
            // Extraído en la raíz: ya está en su sitio. Copiarlo encima de sí mismo lo borraría
            // (copyTo borra el destino, que es el mismo archivo, antes de leerlo).
            if (samePath(src, target)) {
                notes += "Datos en Android/data/${data.packageName}"
                continue
            }
            val ok = runCatching { src.copyRecursively(target, overwrite = true) }.getOrDefault(false)
            notes += if (ok) "Datos copiados en Android/data/${data.packageName}"
            else "Android no deja copiar a Android/data/${data.packageName}: cópialo con el explorador o un PC desde ${src.path}"
        }

        if (plan.apks.isNotEmpty()) {
            if (game != null && pkg != null) plan.apks.forEach { remember(File(dest, it), game, pkg) }
            if (plan.isSplitApk) {
                installApks(taskId, plan.apks.map { ApkInstaller.fromFile(File(dest, it)) })
            } else {
                plan.apks.forEach { installApks(taskId, listOf(ApkInstaller.fromFile(File(dest, it)))) }
            }
            notes += "Instalación lanzada (${plan.apks.size} APK)"
        }
        if (plan.isEmpty) notes += "No hay APK ni OBB: los archivos están en la carpeta"
        finish(taskId, notes.joinToString("\n"), folder.path, notify = plan.apks.isEmpty())
        return folder
    }

    /** La carpeta que ha salido: la que traía el comprimido si se sacó fuera ([unwrapDest]), si no [dest]. */
    private fun resultFolder(target: File, dest: File, files: List<String>): File =
        if (dest == target) dest
        else files.firstOrNull()?.let { File(dest, it.replace('\\', '/').trimStart('/').substringBefore('/')) } ?: dest

    private suspend fun installApks(
        taskId: Int,
        sources: List<ApkInstaller.Source>,
        beforeCommit: (sessionId: Int) -> Unit = {},
    ) {
        if (!ApkInstaller.canInstall(context)) {
            withContext(Dispatchers.Main) { context.startActivity(ApkInstaller.unknownSourcesIntent(context)) }
            throw IOException("Permite \"Instalar apps desconocidas\" para Top Ports y vuelve a intentarlo")
        }
        update(taskId, "Preparando instalación…", 0f)
        ApkInstaller.install(
            context,
            sources,
            onProgress = { update(taskId, "Preparando instalación…", it) },
            beforeCommit = beforeCommit,
        )
    }

    private suspend fun copyObb(taskId: Int, file: File, originalName: String): String {
        val pkg = FileNames.obbPackage(originalName)
            ?: throw IOException("No se sabe a qué juego pertenece $originalName")
        val target = File(Storage.obbDir(pkg), originalName)
        update(taskId, "Copiando OBB", null)
        if (!moveOrCopy(file, target, keepSource = true)) {
            throw IOException("Android no deja escribir en Android/obb/$pkg. Da permiso de archivos o cópialo a mano.")
        }
        return target.parent ?: target.path
    }

    private fun samePath(a: File, b: File): Boolean =
        runCatching { a.canonicalPath == b.canonicalPath }.getOrDefault(a.absolutePath == b.absolutePath)

    private fun moveOrCopy(src: File, target: File, keepSource: Boolean = false): Boolean = runCatching {
        // Copiar un archivo encima de sí mismo lo borraría: ya está donde tiene que estar.
        if (samePath(src, target)) return true
        target.parentFile?.mkdirs()
        if (!keepSource && src.renameTo(target)) return true
        src.copyTo(target, overwrite = true)
        if (!keepSource) src.delete()
        true
    }.getOrDefault(false)

    private suspend fun copyWithProgress(taskId: Int, src: File, target: File) {
        target.parentFile?.mkdirs()
        val total = src.length().coerceAtLeast(1)
        var done = 0L
        src.inputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    checkActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    update(taskId, "Copiando ${src.name}", done.toFloat() / total)
                }
            }
        }
    }

    private suspend fun checkActive() {
        if (!kotlin.coroutines.coroutineContext.isActive) throw ExtractCancelledException()
    }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    private fun uniqueTarget(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (true) {
            val candidate = File(file.parentFile, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    /**
     * Progreso de una tarea. Extraer o copiar avisa cientos de veces por segundo: se publica como
     * mucho ~6 veces por segundo para que la interfaz y la notificación no vayan a tirones.
     */
    private fun update(taskId: Int, message: String, progress: Float?) {
        val now = android.os.SystemClock.uptimeMillis()
        if (progress != null && progress < 1f && now - (lastProgressAt[taskId] ?: 0L) < 160) return
        lastProgressAt[taskId] = now
        _tasks.update { list ->
            list.map { if (it.id == taskId) it.copy(message = message, progress = progress) else it }
        }
    }

    /**
     * [notify]: avisar con una notificación si la app no está en pantalla. Las instalaciones no
     * avisan aquí: Android ya muestra la suya para confirmar.
     */
    private fun finish(taskId: Int, message: String, resultDir: String? = null, notify: Boolean = true) {
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) it.copy(message = message, progress = 1f, running = false, resultDir = resultDir) else it
            }
        }
        if (notify) titleOf(taskId)?.let { DoneNotifier.notify(context, taskId, it, message, failed = false) }
    }

    private fun fail(taskId: Int, message: String, needsPassword: Boolean = false, notify: Boolean = true) {
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(message = message, running = false, failed = true, needsPassword = needsPassword)
                } else it
            }
        }
        if (notify) titleOf(taskId)?.let { DoneNotifier.notify(context, taskId, it, message, failed = true) }
    }

    private fun titleOf(taskId: Int): String? = _tasks.value.firstOrNull { it.id == taskId }?.title
}

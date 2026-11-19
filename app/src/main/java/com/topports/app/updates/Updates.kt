package com.topports.app.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import com.topports.app.storage.Storage
import com.topports.app.telegram.ChannelRepository
import com.topports.core.FileNames
import com.topports.core.InstallRecord
import com.topports.core.InstalledFinder
import com.topports.core.GamePackage
import com.topports.core.InstalledGame
import com.topports.core.InstalledApp
import com.topports.core.UpdateFinder
import com.topports.core.UpdateItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Recuerda qué archivo del canal se usó para instalar cada paquete de Android. */
class InstallRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("installs", Context.MODE_PRIVATE)

    fun record(record: InstallRecord) {
        val json = JSONObject()
            .put("fileName", record.fileName)
            .put("fileId", record.fileId)
            .put("gameId", record.gameId)
            .put("fileSize", record.fileSize)
        prefs.edit().putString(record.packageName, json.toString()).apply()
    }

    fun all(): Map<String, InstallRecord> = prefs.all.filterKeys { key -> internalPrefixes.none { key.startsWith(it) } }.mapNotNull { (pkg, value) ->
        runCatching {
            val json = JSONObject(value as String)
            pkg to InstallRecord(
                pkg, json.getString("fileName"), json.getInt("fileId"), json.getLong("gameId"),
                json.optLong("fileSize", 0),
            )
        }.getOrNull()
    }.toMap()

    /** Carpetas o archivos que dejó una extracción de este juego (para "Borrar datos"). */
    fun recordExtracted(gameId: Long, paths: Collection<String>) {
        val key = "extracted_$gameId"
        val all = (extracted(gameId) + paths).toSet()
        prefs.edit().putString(key, org.json.JSONArray(all.toList()).toString()).apply()
    }

    fun extracted(gameId: Long): List<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("extracted_$gameId", "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun forgetExtracted(gameId: Long) {
        prefs.edit().remove("extracted_$gameId").apply()
    }

    /** Carpeta donde se guardó un archivo del canal sin extraerlo (clave: [GamePackage.key]). */
    fun recordSaved(pkgKey: String, dir: String) {
        prefs.edit().putString("saved_$pkgKey", dir).apply()
    }

    fun savedDir(pkgKey: String): String? = prefs.getString("saved_$pkgKey", null)

    fun forgetSaved(pkgKey: String) {
        prefs.edit().remove("saved_$pkgKey").apply()
    }

    /**
     * Carpeta donde se extrajo solo un comprimido al terminar de descargarse. Se guarda también el
     * nombre del archivo, y solo vale si coincide.
     */
    fun recordUnpacked(pkgKey: String, dir: String, name: String) {
        prefs.edit().putString("unpacked_$pkgKey", JSONObject().put("dir", dir).put("name", name).toString()).apply()
    }

    /** Solo si el apunte es de este mismo archivo ([name]); los de versiones viejas no valen. */
    fun unpackedDir(pkgKey: String, name: String): String? {
        val raw = prefs.getString("unpacked_$pkgKey", null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return json.optString("dir").takeIf { it.isNotEmpty() && json.optString("name").equals(name, ignoreCase = true) }
    }

    fun forgetUnpacked(pkgKey: String) {
        prefs.edit().remove("unpacked_$pkgKey").apply()
    }

    /** Descarga que al terminar se mueve sola a la carpeta del juego. */
    data class PendingSave(
        val pkgKey: String,
        val gameId: Long,
        val dir: String,
        val parts: List<Pair<Int, String>>,
        /** Cuándo se pidió: la cola de descargas va por este orden. */
        val addedAt: Long = 0,
        /** Pausada a mano: la cola no la vuelve a arrancar sola. */
        val paused: Boolean = false,
    )

    /**
     * Todas las descargas pendientes, también las que aún no se pueden usar: al arrancar la app
     * los ids de archivo de TDLib son otros, y hasta que el catálogo vuelve a leerse no se sabe
     * cuáles son los de ahora ([resolvePending]). En disco solo van los nombres; los ids, no.
     */
    private var allPending: List<PendingSave> = loadPending()

    /** Claves de las pendientes que esperan a que el catálogo diga sus ids de archivo de ahora. */
    private var unresolved: Set<String> = allPending.map { it.pkgKey }.toSet()

    // Las ya utilizables, en memoria: se consulta con cada aviso de progreso de TDLib.
    @Volatile
    private var pending: List<PendingSave> = emptyList()

    /** Descargas pendientes con sus ids de archivo al día (las únicas que se pueden pedir a TDLib). */
    fun pendingSaves(): List<PendingSave> = pending

    /** ¿Queda alguna descarga sin pausar, aunque aún no se sepan sus ids? (Al encender la consola.) */
    @Synchronized
    fun hasPendingToResume(): Boolean = allPending.any { !it.paused }

    private fun publishPending() {
        pending = allPending.filter { it.pkgKey !in unresolved }
    }

    @Synchronized
    fun addPendingSave(save: PendingSave) {
        val parts = org.json.JSONArray(save.parts.map { (_, name) -> JSONObject().put("name", name) })
        val json = JSONObject().put("gameId", save.gameId).put("dir", save.dir).put("parts", parts)
            .put("addedAt", save.addedAt).put("paused", save.paused)
        prefs.edit().putString("pending_${save.pkgKey}", json.toString()).apply()
        allPending = allPending.filter { it.pkgKey != save.pkgKey } + save
        unresolved = unresolved - save.pkgKey
        publishPending()
    }

    /** Marca una descarga pendiente como pausada a mano (o no). */
    @Synchronized
    fun setPendingPaused(pkgKey: String, paused: Boolean) {
        val current = allPending.firstOrNull { it.pkgKey == pkgKey } ?: return
        val waiting = pkgKey in unresolved
        addPendingSave(current.copy(paused = paused))
        if (waiting) {
            unresolved = unresolved + pkgKey
            publishPending()
        }
    }

    @Synchronized
    fun removePendingSave(pkgKey: String) {
        prefs.edit().remove("pending_$pkgKey").apply()
        allPending = allPending.filter { it.pkgKey != pkgKey }
        unresolved = unresolved - pkgKey
        publishPending()
    }

    /**
     * Pone a las descargas pendientes los ids de archivo que tienen ahora en el catálogo. Se llama
     * cada vez que cambia el catálogo. Con [complete] (canal leído entero), las que siguen sin
     * aparecer se olvidan: su post se borró o cambió de archivos. Devuelve true si algo cambió.
     */
    @Synchronized
    fun resolvePending(games: List<com.topports.core.Game>, complete: Boolean): Boolean {
        if (allPending.isEmpty()) return false
        val byKey = HashMap<String, GamePackage>()
        games.forEach { g -> g.packages.forEach { byKey[it.key] = it } }
        var changed = false
        val edit = prefs.edit()
        allPending = allPending.mapNotNull { p ->
            val pkg = byKey[p.pkgKey]
            when {
                pkg != null -> {
                    val parts = pkg.parts.map { it.id to it.name }
                    if (parts != p.parts || p.pkgKey in unresolved) {
                        changed = true
                        unresolved = unresolved - p.pkgKey
                        p.copy(parts = parts)
                    } else p
                }
                complete && p.pkgKey in unresolved -> {
                    changed = true
                    unresolved = unresolved - p.pkgKey
                    edit.remove("pending_${p.pkgKey}")
                    null
                }
                else -> p
            }
        }
        if (changed) {
            edit.apply()
            publishPending()
        }
        return changed
    }

    /**
     * TDLib se ha reiniciado (o se ha cerrado sesión): los ids de archivo que había ya no valen.
     * Las pendientes esperan a que el catálogo se lea otra vez; con [forget], se olvidan.
     */
    @Synchronized
    fun invalidatePending(forget: Boolean) {
        if (forget) {
            val edit = prefs.edit()
            allPending.forEach { edit.remove("pending_${it.pkgKey}") }
            edit.apply()
            allPending = emptyList()
        }
        unresolved = allPending.map { it.pkgKey }.toSet()
        publishPending()
    }

    private fun loadPending(): List<PendingSave> {
        // Versiones anteriores apuntaban con ids de archivo de TDLib, que cambian al reiniciar la
        // app: esos apuntes pueden señalar a otro juego. Los de ahora empiezan por "m" (mensaje).
        val stale = prefs.all.keys.filter { key ->
            listOf("pending_", "saved_", "unpacked_").any { key.startsWith(it) && !key.removePrefix(it).startsWith("m") }
        }
        if (stale.isNotEmpty() && !com.topports.app.BuildConfig.DEMO) {
            prefs.edit().apply { stale.forEach { remove(it) } }.apply()
        }
        return prefs.all.filterKeys { it.startsWith("pending_") && it !in stale }.mapNotNull { (key, value) ->
            runCatching {
                val json = JSONObject(value as String)
                val arr = json.getJSONArray("parts")
                PendingSave(
                    key.removePrefix("pending_"),
                    json.getLong("gameId"),
                    json.getString("dir"),
                    // Sin ids: los pone [resolvePending] cuando el catálogo esté leído.
                    List(arr.length()) { 0 to arr.getJSONObject(it).getString("name") },
                    addedAt = json.optLong("addedAt", 0),
                    paused = json.optBoolean("paused", false),
                )
            }.getOrNull()
        }
    }

    /** "No avisar de esta actualización": se recuerda el archivo concreto (nombre y tamaño). */
    fun ignoreUpdate(packageName: String, pkg: GamePackage) {
        prefs.edit().putString("ignored_$packageName", "${pkg.name}|${pkg.size}").apply()
    }

    fun isIgnored(packageName: String, pkg: GamePackage): Boolean =
        prefs.getString("ignored_$packageName", null) == "${pkg.name}|${pkg.size}"

    /**
     * Carpeta /TopPorts/<juego> de un juego. Dos posts con el mismo título no comparten carpeta:
     * el segundo va a "<juego> (2)". Se recuerda el nombre (no la ruta) porque /TopPorts cambia
     * de sitio al dar o quitar el permiso de archivos.
     */
    fun gameDir(context: Context, gameId: Long, title: String): File {
        val name = gameFolderName(gameId, title)
        if (prefs.getString("gamedir_$gameId", null) != name) prefs.edit().putString("gamedir_$gameId", name).apply()
        val dir = File(Storage.topPortsDir(context), name).apply { mkdirs() }
        // Y la ruta entera: al cambiar la carpeta de juegos (p. ej. a la SD), lo ya bajado se queda
        // en la de antes y hay que poder encontrarlo para verlo o borrarlo desde Instalados.
        val paths = prefs.getStringSet("gamepaths_$gameId", emptySet()).orEmpty()
        if (dir.path !in paths) prefs.edit().putStringSet("gamepaths_$gameId", paths + dir.path).apply()
        return dir
    }

    /** Todas las carpetas que ha tenido un juego (la de la carpeta de juegos de ahora incluida). */
    fun gameDirs(context: Context, gameId: Long, title: String): List<File> {
        val name = gameFolderName(gameId, title)
        return (
            prefs.getStringSet("gamepaths_$gameId", emptySet()).orEmpty().map(::File) +
                File(Storage.topPortsDir(context), name) +
                // Lo bajado antes de que se apuntaran las rutas estaba en /TopPorts.
                File(Storage.defaultTopPortsDir(context), name)
            ).distinctBy { it.path }
    }

    /** Nombre de la carpeta de [gameDir] sin crearla (para enseñarla en la interfaz). */
    @Synchronized
    fun gameFolderName(gameId: Long, title: String): String {
        prefs.getString("gamedir_$gameId", null)?.let { return it }
        val taken = prefs.all.filterKeys { it.startsWith("gamedir_") }.values.map { (it as String).lowercase() }.toSet()
        val base = FileNames.safeFolderName(title)
        var name = base
        var i = 2
        while (name.lowercase() in taken) name = "$base (${i++})"
        return name
    }

    /**
     * Instalación de un APK partido (.apk.001…) a la espera de que Android la confirme: hasta
     * entonces no se sabe su nombre de paquete. [completeSession] lo apunta con el que da Android.
     */
    fun rememberSession(sessionId: Int, record: InstallRecord) {
        val json = JSONObject()
            .put("fileName", record.fileName)
            .put("fileId", record.fileId)
            .put("gameId", record.gameId)
            .put("fileSize", record.fileSize)
        prefs.edit().putString("session_$sessionId", json.toString()).apply()
    }

    fun completeSession(sessionId: Int, packageName: String?) {
        val value = prefs.getString("session_$sessionId", null) ?: return
        prefs.edit().remove("session_$sessionId").apply()
        if (packageName.isNullOrBlank()) return
        runCatching {
            val json = JSONObject(value)
            record(
                InstallRecord(
                    packageName, json.getString("fileName"), json.getInt("fileId"), json.getLong("gameId"),
                    json.optLong("fileSize", 0),
                ),
            )
        }
    }

    fun forgetSession(sessionId: Int) {
        prefs.edit().remove("session_$sessionId").apply()
    }

    private val internalPrefixes = listOf("extracted_", "saved_", "pending_", "ignored_", "unpacked_", "gamedir_", "gamepaths_", "session_")

    /** Nombre del paquete de un APK aún sin instalar (null si no se puede leer). */
    fun packageNameOf(context: Context, apk: File): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
    }.getOrNull()
}

/** Busca en el canal versiones nuevas de las apps instaladas. */
@OptIn(FlowPreview::class)
class UpdateService(
    private val context: Context,
    private val registry: InstallRegistry,
    private val channel: ChannelRepository,
    private val scope: CoroutineScope,
) {
    private val _updates = MutableStateFlow<List<UpdateItem>>(emptyList())
    val updates: StateFlow<List<UpdateItem>> = _updates.asStateFlow()

    /** Juegos del canal que están instalados (pestaña Instalados y marca en la tienda). */
    private val _installed = MutableStateFlow<List<InstalledGame>>(emptyList())
    val installed: StateFlow<List<InstalledGame>> = _installed.asStateFlow()

    init {
        // Recalcular cuando cambia el catálogo (posts nuevos en el canal).
        scope.launch {
            channel.state.map { it.games }.distinctUntilChanged().debounce(1500).collect { refresh() }
        }
    }

    fun refresh() {
        scope.launch {
            val games = channel.state.value.games
            if (games.isEmpty()) return@launch
            val apps = withContext(Dispatchers.IO) { installedApps() }
            _updates.value = UpdateFinder.find(apps, games).filterNot { registry.isIgnored(it.packageName, it.pkg) }
            _installed.value = InstalledFinder.find(apps, games)
        }
    }

    private fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val records = registry.all()
        @Suppress("DEPRECATION")
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        return packages
            .filter { info ->
                val app = info.applicationInfo ?: return@filter false
                info.packageName != context.packageName &&
                    (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.packageName in records)
            }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName,
                    versionName = info.versionName,
                    record = records[info.packageName],
                )
            }
    }
}

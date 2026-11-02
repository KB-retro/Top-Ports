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

    /** Carpeta donde se extrajo solo un comprimido al terminar de descargarse. */
    fun recordUnpacked(pkgKey: String, dir: String) {
        prefs.edit().putString("unpacked_$pkgKey", dir).apply()
    }

    fun unpackedDir(pkgKey: String): String? = prefs.getString("unpacked_$pkgKey", null)

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

    // En memoria: se consulta con cada aviso de progreso de TDLib.
    @Volatile
    private var pending: List<PendingSave> = loadPending()

    fun pendingSaves(): List<PendingSave> = pending

    fun addPendingSave(save: PendingSave) {
        val parts = org.json.JSONArray(save.parts.map { (id, name) -> JSONObject().put("id", id).put("name", name) })
        val json = JSONObject().put("gameId", save.gameId).put("dir", save.dir).put("parts", parts)
            .put("addedAt", save.addedAt).put("paused", save.paused)
        prefs.edit().putString("pending_${save.pkgKey}", json.toString()).apply()
        pending = pending.filter { it.pkgKey != save.pkgKey } + save
    }

    /** Marca una descarga pendiente como pausada a mano (o no). */
    fun setPendingPaused(pkgKey: String, paused: Boolean) {
        pending.firstOrNull { it.pkgKey == pkgKey }?.let { addPendingSave(it.copy(paused = paused)) }
    }

    fun removePendingSave(pkgKey: String) {
        prefs.edit().remove("pending_$pkgKey").apply()
        pending = pending.filter { it.pkgKey != pkgKey }
    }

    private fun loadPending(): List<PendingSave> = prefs.all.filterKeys { it.startsWith("pending_") }.mapNotNull { (key, value) ->
        runCatching {
            val json = JSONObject(value as String)
            val arr = json.getJSONArray("parts")
            PendingSave(
                key.removePrefix("pending_"),
                json.getLong("gameId"),
                json.getString("dir"),
                List(arr.length()) { arr.getJSONObject(it).let { p -> p.getInt("id") to p.getString("name") } },
                addedAt = json.optLong("addedAt", 0),
                paused = json.optBoolean("paused", false),
            )
        }.getOrNull()
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
        return File(Storage.topPortsDir(context), name).apply { mkdirs() }
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

    private val internalPrefixes = listOf("extracted_", "saved_", "pending_", "ignored_", "unpacked_", "gamedir_", "session_")

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

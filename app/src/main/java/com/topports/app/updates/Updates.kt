package com.topports.app.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import com.topports.app.telegram.ChannelRepository
import com.topports.core.InstallRecord
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
        prefs.edit().putString(record.packageName, json.toString()).apply()
    }

    fun all(): Map<String, InstallRecord> = prefs.all.mapNotNull { (pkg, value) ->
        runCatching {
            val json = JSONObject(value as String)
            pkg to InstallRecord(pkg, json.getString("fileName"), json.getInt("fileId"), json.getLong("gameId"))
        }.getOrNull()
    }.toMap()

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
            _updates.value = withContext(Dispatchers.IO) { UpdateFinder.find(installedApps(), games) }
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

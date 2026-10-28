package com.topports.app.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.BuildConfig
import com.topports.app.install.ApkInstaller
import com.topports.app.settings.StorageTools
import com.topports.app.storage.Storage
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.SettingsSection
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.pixelCard
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTag
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val storage = vm.graph.storage

    // Se recalcula al volver de los ajustes de Android y tras cada limpieza.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val usage by produceState<StorageTools.Usage?>(null, refresh) { value = storage.usage() }
    var orphans by remember { mutableStateOf<StorageTools.Orphans?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                toast(e.message ?: "Error")
            } finally {
                busy = false
                refresh++
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Secciones: solo se muestra la elegida para que Ajustes no sea una lista larguísima.
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection.entries.forEach { section ->
                    val selected = vm.settingsSection == section
                    PixelButton(
                        section.label,
                        onClick = { vm.settingsSection = section },
                        color = if (selected) Px.orange else Px.navyDark,
                        textColor = if (selected) Px.black else Px.grey,
                        small = true,
                    )
                }
            }
        }
        // --- Permisos ---
        if (vm.settingsSection == SettingsSection.PERMISSIONS) {
            item { SectionTitle("Permisos", Px.yellow) }
            item {
                key(refresh) {
                    PermissionRow(
                        "Instalar apps",
                        "Para instalar los APK de los ports.",
                        ApkInstaller.canInstall(context),
                    ) { context.startActivity(ApkInstaller.unknownSourcesIntent(context)) }
                }
            }
            item {
                key(refresh) {
                    PermissionRow(
                        "Archivos",
                        "Explorador, extraer en la raíz o /TopPorts y copiar OBB.",
                        Storage.hasAllFilesAccess(context),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching { context.startActivity(Storage.allFilesAccessIntent(context)) }
                        } else {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
            }
            item {
                key(refresh) {
                    PermissionRow(
                        "Notificaciones",
                        "Ver el progreso de descargas y extracciones.",
                        NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            item {
                key(refresh) {
                    PermissionRow(
                        "Sin límite de batería",
                        "Evita que Android corte las descargas largas en segundo plano.",
                        isIgnoringBattery(context),
                    ) { requestIgnoreBattery(context) }
                }
            }

        }

        // --- Almacenamiento ---
        if (vm.settingsSection == SettingsSection.STORAGE) {
            item { SectionTitle("Almacenamiento", Px.blue) }
            item {
                PixelPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val u = usage
                        if (u == null) {
                            PixelProgress(null, Modifier.fillMaxWidth(), color = Px.blue)
                        } else {
                            Text("Juegos descargados: ${FileNames.formatSize(u.downloads)}", style = PxText.body)
                            Text("Caché de imágenes y vídeos: ${FileNames.formatSize(u.cache)}", style = PxText.body)
                            Text("Descargas a medias: ${FileNames.formatSize(u.partial)}", style = PxText.body)
                        }
                        if (busy) PixelProgress(null, Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Borrar caché", onClick = {
                                work { toast("Liberado ${FileNames.formatSize(storage.clearCache())}") }
                            }, color = Px.blue, small = true, enabled = !busy)
                            PixelButton("Buscar huérfanos", onClick = {
                                work { orphans = storage.findOrphans() }
                            }, color = Px.orange, small = true, enabled = !busy)
                            PixelButton("Borrar todas las descargas", onClick = { confirmDeleteAll = true },
                                color = Px.red, textColor = Px.white, small = true, enabled = !busy)
                        }
                        orphans?.let { o ->
                            if (o.isEmpty) {
                                Text("No hay restos de descargas: todo limpio.", style = PxText.small.copy(color = Px.green))
                            } else {
                                Text(
                                    "Restos de descargas fallidas o en pausa: ${o.fileIds.size} archivo(s), " +
                                        FileNames.formatSize(o.total),
                                    style = PxText.body.copy(color = Px.yellow),
                                )
                                PixelButton("Limpiar ${FileNames.formatSize(o.total)}", onClick = {
                                    work {
                                        val freed = storage.cleanOrphans(o)
                                        orphans = null
                                        toast("Liberado ${FileNames.formatSize(freed)}")
                                    }
                                }, color = Px.green, small = true, enabled = !busy)
                            }
                        }
                        Text(
                            "Borrar descargas no desinstala nada ni toca lo ya extraído; solo libera el espacio " +
                                "de los archivos bajados de Telegram.",
                            style = PxText.small,
                        )
                    }
                }
            }

        }

        // --- Apariencia ---
        if (vm.settingsSection == SettingsSection.THEME) {
            item { SectionTitle("Tema de colores", Px.pink) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTheme.entries.forEach { theme -> ThemeChip(theme, selected = Px.theme == theme) { vm.setTheme(theme) } }
                }
            }

        }

        // --- Mando ---
        if (vm.settingsSection == SettingsSection.GAMEPAD) {
            item { SectionTitle("Mando", Px.green) }
            item {
                PixelPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Intercambiar A y B", style = PxText.body)
                                Text("Para mandos con distribución Nintendo.", style = PxText.small)
                            }
                            PixelButton(
                                if (vm.swapAB) "Sí" else "No",
                                onClick = { vm.toggleSwapAB() },
                                color = if (vm.swapAB) Px.green else Px.darkGrey,
                                textColor = if (vm.swapAB) Px.black else Px.white,
                                small = true,
                            )
                        }
                        Text(
                            "${if (vm.swapAB) "B" else "A"} aceptar · ${if (vm.swapAB) "A" else "B"} inicio · " +
                                "L1/R1 fila o texto · stick derecho texto · L2/R2 pestaña · Y buscar · X ordenar · SELECT sistema",
                            style = PxText.small,
                        )
                    }
                }
            }

        }

        // --- Privacidad ---
        if (vm.settingsSection == SettingsSection.PRIVACY) {
            item { SectionTitle("Privacidad", Px.green) }
            item {
                PixelPanel(Modifier.fillMaxWidth(), border = Px.green) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PRIVACY_POINTS.forEach { Text("• $it", style = PxText.small) }
                    }
                }
            }

        }

        // --- Aviso legal ---
        if (vm.settingsSection == SettingsSection.LEGAL) {
            item { SectionTitle("Aviso legal", Px.orange) }
            item {
                PixelPanel(Modifier.fillMaxWidth(), border = Px.orange) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEGAL_POINTS.forEach { Text("• $it", style = PxText.small) }
                        PixelButton("Contactar / pedir retirada", onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(legalContactLink())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { toast("No hay ninguna app para abrir el contacto") }
                        }, color = Px.orange, small = true)
                    }
                }
            }

        }

        // --- Licencias ---
        if (vm.settingsSection == SettingsSection.LEGAL) {
            item { SectionTitle("Licencias", Px.blue) }
            item {
                PixelPanel(Modifier.fillMaxWidth(), border = Px.blue) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Ports usa este software de terceros. Pulsa uno para ver su licencia.", style = PxText.small)
                        LICENSES.forEach { lib ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .pixelCard(onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    })
                                    .padding(8.dp),
                            ) {
                                Text(lib.name, style = PxText.body)
                                Text(lib.license, style = PxText.small.copy(color = Px.blue))
                                if (lib.note.isNotEmpty()) Text(lib.note, style = PxText.small)
                            }
                        }
                    }
                }
            }

        }

        // --- Cuenta ---
        if (vm.settingsSection == SettingsSection.ACCOUNT) {
            item { SectionTitle("Cuenta", Px.lavender) }
            item {
                PixelPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Ports ${BuildConfig.VERSION_NAME}", style = PxText.body)
                        if (catalog.channelTitle.isNotBlank()) {
                            Text("Canal: ${catalog.channelTitle} · ${catalog.games.size} juegos", style = PxText.small)
                        }
                        PixelButton("Cerrar sesión", onClick = { confirmLogout = true },
                            color = Px.red, textColor = Px.white, small = true)
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    if (confirmDeleteAll) {
        PixelDialog("¿Borrar todas las descargas?", onDismiss = { confirmDeleteAll = false }) {
            Text("Se borrarán los juegos bajados de Telegram. Lo instalado y lo extraído se queda.", style = PxText.body)
            DialogButtons {
                PixelButton("No", onClick = { confirmDeleteAll = false }, color = Px.grey, small = true)
                PixelButton("Borrar", onClick = {
                    confirmDeleteAll = false
                    work { toast("Liberado ${FileNames.formatSize(storage.deleteAllDownloads())}") }
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
    if (confirmLogout) {
        PixelDialog("¿Cerrar sesión?", onDismiss = { confirmLogout = false }) {
            Text("Se cerrará la sesión de Telegram en esta app.", style = PxText.body)
            DialogButtons {
                PixelButton("No", onClick = { confirmLogout = false }, color = Px.grey, small = true)
                PixelButton("Salir", onClick = {
                    confirmLogout = false
                    vm.logOut()
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onRequest: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth(), border = if (granted) Px.green else Px.yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelTag(if (granted) "OK" else "NO", if (granted) Px.green else Px.red,
                textColor = if (granted) Px.black else Px.white)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = PxText.body)
                Text(description, style = PxText.small)
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                PixelButton("Dar permiso", onClick = onRequest, color = Px.yellow, small = true)
            }
        }
    }
}

/** Muestra del tema: sus colores principales y el nombre. */
@Composable
private fun ThemeChip(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val p = theme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.background(p.night).padding(4.dp)) {
            listOf(p.navy, p.orange, p.blue, p.green, p.pink, p.white).forEach {
                Box(Modifier.size(12.dp).background(it))
            }
        }
        PixelButton(
            if (selected) "✓ ${theme.label}" else theme.label,
            onClick = onClick,
            color = if (selected) Px.orange else Px.navyDark,
            textColor = if (selected) Px.black else Px.white,
            small = true,
        )
    }
}

private fun isIgnoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

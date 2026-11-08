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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.topports.app.ui.theme.UiStyle
import com.topports.app.ui.components.pixelFrame
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import com.topports.app.ui.theme.UiAnim
import com.topports.app.ui.theme.pxCaps
import com.topports.app.ui.components.PxTransition

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
    var confirmClearCache by remember { mutableStateOf(false) }
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

    // Stick derecho: desplaza Ajustes (Privacidad, Legal…) de forma continua, como la
    // descripción de un juego. MainActivity deja el valor en vm.descriptionStick.
    val settingsList = androidx.compose.foundation.lazy.rememberLazyListState()
    val stickSpeed = with(androidx.compose.ui.platform.LocalDensity.current) { 900.dp.toPx() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val v = vm.descriptionStick
                if (v != 0f) settingsList.dispatchRawDelta(v * kotlin.math.abs(v) * stickSpeed * dt)
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.descriptionStick = 0f } }

    // Permisos plegados dentro de Versión (se abren con su botón).
    var permissionsOpen by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = settingsList,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Secciones: solo se muestra la elegida para que Ajustes no sea una lista larguísima.
        item {
            // Hueco de 6 dp: con 8 las 9 secciones no caben en una línea en pantallas 4:3.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection.entries.forEach { section ->
                    val selected = vm.settingsSection == section
                    // Con una versión nueva de la app, la sección Versión se marca en verde.
                    val highlight = section == SettingsSection.VERSION && catalog.appUpdate != null
                    PixelButton(
                        if (highlight) "${section.label} nueva" else section.label,
                        onClick = { vm.settingsSection = section },
                        color = when {
                            selected -> Px.orange
                            highlight -> Px.green
                            else -> Px.navyDark
                        },
                        textColor = if (selected || highlight) Px.black else Px.grey,
                        small = true,
                    )
                }
            }
        }
        // --- Versión ---
        if (vm.settingsSection == SettingsSection.VERSION) {
            item { SectionTitle("Versión de Top Ports", Px.green) }
            item {
                val update = catalog.appUpdate
                PixelPanel(Modifier.fillMaxWidth(), border = if (update != null) Px.green else Px.white) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Instalada: ${BuildConfig.VERSION_NAME}", style = PxText.body)
                        if (update != null) {
                            Text(
                                "Nueva versión en el canal: ${update.version} (${FileNames.formatSize(update.file.size)})",
                                style = PxText.body.copy(color = Px.green),
                            )
                            Text("Se instala encima sin perder la sesión, las descargas ni los ajustes.", style = PxText.small)
                            PixelButton("Actualizar a ${update.version}", onClick = {
                                vm.graph.tasks.updateApp(update)
                                toast("Mira el progreso en TAREAS")
                            }, color = Px.green, small = true)
                        } else {
                            Text(
                                if (catalog.loading) "Comprobando el canal…" else "Tienes la última versión.",
                                style = PxText.small,
                            )
                            PixelButton("Comprobar", onClick = { vm.refreshCatalog() }, color = Px.blue, small = true,
                                enabled = !catalog.loading)
                        }
                        if (catalog.announcement != null) {
                            PixelButton("Ver últimas novedades del canal", onClick = { vm.announcementOpen = true },
                                color = Px.yellow, small = true)
                        }
                    }
                }
            }
        }

        // --- Sonido ---
        if (vm.settingsSection == SettingsSection.SOUND) {
            item { SectionTitle("Sonidos", Px.yellow) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sonidos de menú", style = PxText.body)
                        Text(
                            "Bips al mover el cursor, aceptar y volver.",
                            style = PxText.small,
                        )
                    }
                    PixelButton(
                        if (vm.sounds) "Sí" else "No",
                        onClick = { vm.changeSounds(!vm.sounds) },
                        color = if (vm.sounds) Px.green else Px.darkGrey,
                        textColor = if (vm.sounds) Px.black else Px.white,
                        small = true,
                    )
                }
            }
            item { SectionTitle("Música", Px.lavender) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Banda sonora chiptune generada al momento: cada canción es nueva y no ocupa nada. " +
                            "Solo con la app en pantalla.",
                        style = PxText.small,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Alta (la de por defecto) primero.
                        listOf(3 to "Alta", 2 to "Media", 1 to "Baja", 0 to "No").forEach { (level, label) ->
                            PixelButton(
                                if (vm.music == level) "✓ $label" else label,
                                onClick = { vm.changeMusic(level) },
                                color = if (vm.music == level) Px.orange else Px.navyDark,
                                textColor = if (vm.music == level) Px.black else Px.white,
                                small = true,
                            )
                        }
                        if (vm.music > 0) {
                            PixelButton("Otra canción", onClick = { com.topports.app.ui.ChipMusic.next() }, color = Px.lavender, small = true)
                        }
                    }
                }
            }
            item { SectionTitle("Tipo de sonido", Px.lavender) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Para los sonidos de menú y la música, con cualquier tema.", style = PxText.small)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.topports.app.ui.RetroSound.SoundType.entries.forEach { type ->
                            val selected = vm.soundType == type
                            PixelButton(
                                if (selected) "✓ ${type.label}" else type.label,
                                onClick = { vm.changeSoundType(type) },
                                color = if (selected) Px.orange else Px.navyDark,
                                textColor = if (selected) Px.black else Px.white,
                                small = true,
                            )
                        }
                    }
                }
            }
        }

        // --- Permisos (dentro de Versión, plegados) ---
        if (vm.settingsSection == SettingsSection.VERSION) {
            item {
                val missing = listOf(
                    ApkInstaller.canInstall(context),
                    Storage.hasAllFilesAccess(context),
                    NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    isIgnoringBattery(context),
                ).count { !it }
                key(refresh) {
                    PixelButton(
                        (if (permissionsOpen) "▲ Permisos" else "▼ Permisos") +
                            if (missing > 0) " (faltan $missing)" else " (todos dados)",
                        onClick = { permissionsOpen = !permissionsOpen },
                        color = if (missing > 0) Px.yellow else Px.navyDark,
                        textColor = if (missing > 0) Px.black else Px.white,
                        small = true,
                    )
                }
            }
        }
        if (vm.settingsSection == SettingsSection.VERSION && permissionsOpen) {
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

        // Controles del mando (dentro de Versión, plegados), con su dibujo.
        if (vm.settingsSection == SettingsSection.VERSION) {
            item {
                var open by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(
                        if (open) "▲ Controles" else "▼ Controles",
                        onClick = { open = !open },
                        color = Px.navyDark,
                        textColor = Px.white,
                        small = true,
                    )
                    if (open) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val wide = maxWidth >= 560.dp
                            PixelPanel(Modifier.fillMaxWidth()) { ControlsHelp(vm.swapAB, wide) }
                        }
                    }
                }
            }
        }

        // Restablecer ajustes, al final del todo de Versión.
        if (vm.settingsSection == SettingsSection.VERSION) {
            item {
                var confirmReset by remember { mutableStateOf(false) }
                PixelButton("Restablecer ajustes", onClick = { confirmReset = true }, color = Px.red, textColor = Px.white, small = true)
                if (confirmReset) {
                    PixelDialog("¿Restablecer ajustes?", onDismiss = { confirmReset = false }) {
                        Text(
                            "Vuelven a como vienen de serie el tema, la paleta, las animaciones, el sonido, la música, " +
                                "el mando, las descargas y el orden de la tienda. Tus favoritos, tus descargas y tu " +
                                "sesión de Telegram no se tocan.",
                            style = PxText.body,
                        )
                        DialogButtons {
                            PixelButton("No", onClick = { confirmReset = false }, color = Px.grey, small = true)
                            PixelButton("Restablecer", onClick = {
                                confirmReset = false
                                vm.resetSettings()
                                toast("Ajustes restablecidos")
                            }, color = Px.red, textColor = Px.white, small = true)
                        }
                    }
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
                            Text("Descargas terminadas sin pasar a /TopPorts: ${FileNames.formatSize(u.downloads)}", style = PxText.body)
                            Text("Caché de imágenes y vídeos: ${FileNames.formatSize(u.cache)}", style = PxText.body)
                            Text("Descargas a medias (sin terminar): ${FileNames.formatSize(u.partial)}", style = PxText.body)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Extraer al descargar", style = PxText.body)
                                Text(
                                    "Los comprimidos en partes (.7z.001, .part1.rar…) se extraen solos en /TopPorts/<juego> " +
                                        "y se borra el comprimido. Los archivos sueltos se guardan tal cual.",
                                    style = PxText.small,
                                )
                            }
                            var auto by remember(vm.settingsResets) { mutableStateOf(vm.graph.tasks.autoExtract) }
                            PixelButton(
                                if (auto) "Sí" else "No",
                                onClick = { auto = !auto; vm.graph.tasks.autoExtract = auto },
                                color = if (auto) Px.green else Px.darkGrey,
                                textColor = if (auto) Px.black else Px.white,
                                small = true,
                            )
                        }
                        // Cola de descargas: cuántas a la vez y si solo con WiFi.
                        Text("Descargas a la vez", style = PxText.body)
                        Text("Las demás esperan en cola, por orden de petición.", style = PxText.small)
                        var parallel by remember(vm.settingsResets) { mutableIntStateOf(vm.graph.tasks.maxParallel) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 2 (el de por defecto) primero.
                            listOf(2, 1, 3, 0).forEach { n ->
                                PixelButton(
                                    if (n == 0) "Sin límite" else n.toString(),
                                    onClick = { parallel = n; vm.graph.tasks.maxParallel = n },
                                    color = if (parallel == n) Px.orange else Px.navyDark,
                                    textColor = if (parallel == n) Px.black else Px.white,
                                    small = true,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Solo con WiFi", style = PxText.body)
                                Text("Con datos móviles las descargas esperan y siguen solas al volver la WiFi.", style = PxText.small)
                            }
                            var wifi by remember(vm.settingsResets) { mutableStateOf(vm.graph.tasks.wifiOnly) }
                            PixelButton(
                                if (wifi) "Sí" else "No",
                                onClick = { wifi = !wifi; vm.graph.tasks.wifiOnly = wifi },
                                color = if (wifi) Px.green else Px.darkGrey,
                                textColor = if (wifi) Px.black else Px.white,
                                small = true,
                            )
                        }
                        if (busy) PixelProgress(null, Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Pide confirmar: hay que reiniciar la app para que TDLib suelte la caché.
                            PixelButton("Borrar caché", onClick = { confirmClearCache = true }, color = Px.blue, small = true, enabled = !busy)
                            PixelButton("Buscar huérfanos", onClick = {
                                work { orphans = storage.findOrphans() }
                            }, color = Px.orange, small = true, enabled = !busy)
                            PixelButton("Borrar descargas de la caché", onClick = { confirmDeleteAll = true },
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
            item { SectionTitle("Temas", Px.blue) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Cambia las letras, los recuadros, las barras y el fondo. Se combina con cualquier paleta de colores.",
                        style = PxText.small,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiStyle.entries.forEach { style -> StyleChip(style, selected = Px.style == style) { vm.setStyle(style) } }
                    }
                    Text(Px.style.description, style = PxText.small.copy(color = Px.white))
                }
            }
            item { SectionTitle("Animaciones", Px.green) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Al cambiar de pestaña, abrir un juego y abrir la app.",
                        style = PxText.small,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimPreview()
                        Text(Px.anim.description, style = PxText.small.copy(color = Px.white), modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiAnim.entries.forEach { anim ->
                            val selected = Px.anim == anim
                            PixelButton(
                                if (selected) "✓ ${anim.label}" else anim.label,
                                onClick = { vm.setAnim(anim) },
                                color = if (selected) Px.orange else Px.navyDark,
                                textColor = if (selected) Px.black else Px.white,
                                small = true,
                            )
                        }
                    }
                }
            }
            item { SectionTitle("Paleta de colores", Px.pink) }
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

        // --- Aviso legal y privacidad (en Legal, plegados como las licencias) ---
        if (vm.settingsSection == SettingsSection.LEGAL) {
            item { SectionTitle("Aviso legal", Px.orange) }
            item {
                var open by remember { mutableStateOf(false) }
                PixelPanel(Modifier.fillMaxWidth(), border = Px.orange) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton(
                            if (open) "▲ Ocultar aviso legal" else "▼ Ver aviso legal",
                            onClick = { open = !open },
                            color = Px.orange,
                            small = true,
                        )
                        if (open) {
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
            item { SectionTitle("Privacidad", Px.green) }
            item {
                var open by remember { mutableStateOf(false) }
                PixelPanel(Modifier.fillMaxWidth(), border = Px.green) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton(
                            if (open) "▲ Ocultar privacidad" else "▼ Ver privacidad",
                            onClick = { open = !open },
                            color = Px.green,
                            small = true,
                        )
                        if (open) PRIVACY_POINTS.forEach { Text("• $it", style = PxText.small) }
                    }
                }
            }
        }

        // --- Licencias ---
        if (vm.settingsSection == SettingsSection.LEGAL) {
            item { SectionTitle("Licencias", Px.blue) }
            item {
                // Plegadas por defecto: son muchas y casi nadie las mira.
                var open by remember { mutableStateOf(false) }
                PixelPanel(Modifier.fillMaxWidth(), border = Px.blue) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Ports usa software de terceros (${LICENSES.size}).", style = PxText.small)
                        PixelButton(
                            if (open) "▲ Ocultar licencias" else "▼ Ver licencias",
                            onClick = { open = !open },
                            color = Px.blue,
                            small = true,
                        )
                        if (open) Text("Pulsa una para ver su licencia.", style = PxText.small)
                        if (open) LICENSES.forEach { lib ->
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
                        // Diagnóstico: si dos dispositivos muestran distinto número de juegos, aquí se ve
                        // si es por la cuenta o porque uno no ha leído el canal entero.
                        val me by produceState<String?>(null) {
                            value = runCatching {
                                val user = vm.graph.telegram.send(org.drinkless.tdlib.TdApi.GetMe())
                                val phone = user.phoneNumber.takeLast(3)
                                "${user.firstName} ${user.lastName}".trim() + if (phone.isNotEmpty()) " (···$phone)" else ""
                            }.getOrNull()
                        }
                        me?.let { Text("Cuenta de Telegram: $it", style = PxText.small) }
                        if (catalog.scannedMessages > 0) {
                            val since = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                .format(java.util.Date(catalog.oldestDate * 1000L))
                            Text(
                                "Mensajes leídos del canal: ${catalog.scannedMessages} (desde el $since)" +
                                    if (catalog.loading) " · leyendo…" else "",
                                style = PxText.small,
                            )
                        }
                        PixelButton("Cerrar sesión", onClick = { confirmLogout = true },
                            color = Px.red, textColor = Px.white, small = true)
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    if (confirmClearCache) {
        PixelDialog("¿Borrar la caché?", onDismiss = { confirmClearCache = false }) {
            Text(
                "Se borrarán las carátulas, capturas y vídeos guardados (se vuelven a bajar al verlos). " +
                    "Para que surta efecto, la app se reiniciará al terminar. Las descargas en curso " +
                    "seguirán solas al volver a abrirse.",
                style = PxText.body,
            )
            DialogButtons {
                PixelButton("Cancelar", onClick = { confirmClearCache = false }, color = Px.grey, small = true)
                PixelButton("Borrar y reiniciar", onClick = {
                    confirmClearCache = false
                    work {
                        val freed = storage.clearCache()
                        toast("Liberado ${FileNames.formatSize(freed)}. Reiniciando…")
                        kotlinx.coroutines.delay(800)
                        restartApp(context)
                    }
                }, color = Px.blue, small = true)
            }
        }
    }

    if (confirmDeleteAll) {
        PixelDialog("¿Borrar descargas de la caché?", onDismiss = { confirmDeleteAll = false }) {
            Text(
                "Se borrarán las descargas que siguen en la caché de Telegram (a medias o sin pasar a " +
                    "/TopPorts). Los juegos ya guardados en /TopPorts, lo instalado y lo extraído no se " +
                    "tocan: esos se borran desde la ficha del juego o desde Instalados.",
                style = PxText.body,
            )
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

/** Muestra de un tema (estilo de interfaz): un recuadro dibujado con él y "Aa" en su letra. */
@Composable
private fun StyleChip(style: UiStyle, selected: Boolean, onClick: () -> Unit) {
    val spec = style.spec
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(width = 112.dp, height = 56.dp)
                // Tocar la muestra también elige el tema (no solo el botón de debajo).
                .pointerInput(style) { detectTapGestures { onClick() } }
                .pixelFrame(fill = Px.navy, border = if (selected) Px.yellow else Px.white, style = style),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Aa",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = spec.headFont, fontWeight = spec.headWeight,
                    fontSize = spec.title.size, color = if (selected) Px.yellow else Px.white,
                ),
            )
        }
        PixelButton(
            if (selected) "✓ ${style.label}" else style.label,
            onClick = onClick,
            color = if (selected) Px.orange else Px.navyDark,
            textColor = if (selected) Px.black else Px.white,
            small = true,
        )
    }
}

/** Vista previa de la animación elegida: dos pantallas de muestra que se alternan solas. */
@Composable
private fun AnimPreview() {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(Px.anim) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            page = (page + 1) % 2
        }
    }
    Box(Modifier.size(width = 150.dp, height = 84.dp)) {
        PxTransition(page, Modifier.fillMaxSize(), order = { it }) { shown ->
            Box(
                Modifier.fillMaxSize().pixelFrame(fill = if (shown == 0) Px.navy else Px.purple, border = Px.white),
                contentAlignment = Alignment.Center,
            ) {
                Text((if (shown == 0) "Tienda" else "Juego").pxCaps(), style = PxText.heading.copy(color = if (shown == 0) Px.blue else Px.yellow))
            }
        }
    }
}

/** Muestra de una paleta: sus colores principales y el nombre. */
@Composable
private fun ThemeChip(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val p = theme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Tocar los colores también elige la paleta (no solo el botón de debajo).
        Row(Modifier.background(p.night).pointerInput(theme) { detectTapGestures { onClick() } }.padding(4.dp)) {
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

/**
 * Reinicia la app: abre de nuevo la pantalla principal en una tarea nueva y cierra el proceso
 * actual, para que TDLib arranque de cero (si no, sigue usando la caché que se acaba de borrar).
 */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}

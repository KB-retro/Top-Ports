package com.topports.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.pixelClickable
import com.topports.app.ui.files.FileBrowserScreen
import com.topports.app.ui.installed.InstalledScreen
import com.topports.app.ui.login.LoginFlow
import com.topports.app.ui.login.MessageScreen
import com.topports.app.ui.login.SplashScreen
import com.topports.app.ui.login.TopPortsLogo
import com.topports.app.ui.store.AnnouncementDialog
import com.topports.app.ui.store.CatalogScreen
import com.topports.app.ui.store.GameScreen
import com.topports.app.ui.tasks.TasksScreen
import com.topports.app.ui.settings.SettingsScreen
import com.topports.app.ui.updates.UpdatesScreen
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import org.drinkless.tdlib.TdApi

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Px.night)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (!vm.graph.telegram.isConfigured) {
            MessageScreen(
                title = "Falta configurar",
                message = "Esta compilación no tiene TELEGRAM_API_ID ni TELEGRAM_API_HASH. " +
                    "Créalos en my.telegram.org y añádelos como secretos del repositorio (mira el README).",
            )
            return@Box
        }
        when (val state = auth) {
            null,
            is TdApi.AuthorizationStateWaitTdlibParameters,
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed,
            -> SplashScreen()
            is TdApi.AuthorizationStateReady -> MainScreen(vm)
            else -> LoginFlow(vm, state)
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val updates by vm.updates.collectAsStateWithLifecycle()
    val catalogState by vm.catalog.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadCatalogIfNeeded() }
    // Atrás (o B): cerrar la ficha, volver a la pestaña anterior y, en la tienda, pedir
    // una segunda pulsación antes de salir en lugar de minimizar la app de golpe.
    val context = LocalContext.current
    var lastExitPress by remember { mutableLongStateOf(0L) }
    BackHandler {
        when {
            vm.tab == Tab.STORE && vm.selectedGameId != null -> vm.selectedGameId = null
            vm.goBackTab() -> Unit
            vm.tab != Tab.STORE -> vm.tab = Tab.STORE
            else -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastExitPress < 2000) {
                    context.findActivity()?.moveTaskToBack(true)
                } else {
                    lastExitPress = now
                    Toast.makeText(context, "Pulsa atrás otra vez para salir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            when (vm.tab) {
                Tab.STORE -> {
                    val id = vm.selectedGameId
                    if (id == null) CatalogScreen(vm) else GameScreen(vm, id)
                }
                Tab.INSTALLED -> InstalledScreen(vm)
                Tab.UPDATES -> UpdatesScreen(vm)
                Tab.FILES -> FileBrowserScreen(vm)
                Tab.TASKS -> TasksScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
    val tabButton: @Composable (Tab, Modifier) -> Unit = { tab, modifier ->
        val running = when (tab) {
            Tab.TASKS -> tasks.count { it.running }
            Tab.UPDATES -> updates.size
            // Versión nueva de la propia app: se avisa en Ajustes.
            Tab.SETTINGS -> if (catalogState.appUpdate != null) 1 else 0
            else -> 0
        }
        PixelButton(
            text = if (running > 0) "${tab.label} $running" else tab.label,
            onClick = {
                if (vm.tab == tab && tab == Tab.STORE) vm.selectedGameId = null
                vm.tab = tab
            },
            // Con actualizaciones pendientes, la pestaña se marca en verde.
            color = when {
                vm.tab == tab -> Px.orange
                (tab == Tab.UPDATES || tab == Tab.SETTINGS) && running > 0 -> Px.green
                else -> Px.navyDark
            },
            textColor = if (vm.tab == tab || ((tab == Tab.UPDATES || tab == Tab.SETTINGS) && running > 0)) Px.black else Px.grey,
            small = true,
            modifier = modifier,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Consolas (Odin 2 Portal, RG477M…) y móviles en horizontal: menú lateral.
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxHeight().width(150.dp).background(Px.navy).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopPortsLogo(size = 96.dp)
                    Tab.entries.forEach { tab -> tabButton(tab, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.weight(1f))
                    Text("L1/R1  FILA\nL2/R2  PESTAÑA\nSELECT SISTEMA\nA  ACEPTAR\nB  INICIO", style = PxText.tiny.copy(color = Px.darkGrey))
                }
                Box(Modifier.fillMaxHeight().width(3.dp).background(Px.black))
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Px.navy).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Px.orange)) { append("TOP") }
                            withStyle(SpanStyle(color = Px.blue)) { append(" PORTS") }
                            withStyle(SpanStyle(color = Px.green)) { append(" ANDROID") }
                        },
                        style = PxText.title,
                        modifier = Modifier.weight(1f).pixelClickable {
                            vm.tab = Tab.STORE
                            vm.selectedGameId = null
                        },
                    )
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(Px.black))
                content(Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.fillMaxWidth().height(3.dp).background(Px.black))
                // Seis pestañas no caben en un móvil en vertical: la barra se desplaza.
                Row(
                    Modifier.fillMaxWidth().background(Px.navy).horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tab.entries.forEach { tab -> tabButton(tab, Modifier) }
                }
            }
        }
    }

    // Aviso de novedades del canal: una vez por aviso nuevo (o a mano desde Ajustes → Versión).
    catalogState.announcement?.let { announcement ->
        if (announcement.id > vm.seenAnnouncementId || vm.announcementOpen) {
            AnnouncementDialog(vm, announcement)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

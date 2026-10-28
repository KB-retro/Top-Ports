package com.topports.app.ui.installed

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.updates.GameData
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Tab
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.store.CoverImage
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.InstalledGame
import kotlinx.coroutines.launch

/** Juegos del canal que están instalados: abrir, desinstalar y borrar sus datos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstalledScreen(vm: AppViewModel) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Al volver de desinstalar (o de cualquier otra app), comprobar otra vez.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.graph.updates.refresh() }

    var cleaning by remember { mutableStateOf<Pair<InstalledGame, List<GameData.Entry>>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(
                        if (installed.isEmpty()) "Nada instalado" else "${installed.size} instalados",
                        Px.green,
                    )
                }
                PixelButton("Comprobar", onClick = { vm.graph.updates.refresh() }, color = Px.blue, small = true)
            }
        }
        if (installed.isEmpty()) {
            item {
                Text(
                    "Aquí aparecen los juegos del canal que tienes instalados: los instalados desde Top Ports " +
                        "y los que tienen el mismo nombre que un juego del canal.",
                    style = PxText.small,
                )
            }
        }
        items(installed, key = { it.game.id }) { item ->
            PixelPanel(Modifier.fillMaxWidth(), border = Px.green) { Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 96.dp, height = 54.dp).background(Px.black)) {
                        item.game.images.firstOrNull()?.let { CoverImage(it.thumbFileId, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.game.title.uppercase(), style = PxText.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.apps.forEach { app ->
                            Text(
                                "${app.label} ${app.versionName ?: ""}".trim(),
                                style = PxText.small,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelButton("Jugar", onClick = {
                        val launch = context.packageManager.getLaunchIntentForPackage(item.apps.first().packageName)
                        if (launch != null) context.startActivity(launch) else toast("Esta app no se puede abrir")
                    }, color = Px.green, small = true)
                    PixelButton("Ficha", onClick = {
                        vm.tab = Tab.STORE
                        vm.openGame(item.game.id)
                    }, color = Px.blue, small = true)
                    PixelButton("Desinstalar", onClick = {
                        // Android pide confirmación por cada app del juego.
                        item.apps.forEach { app ->
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }, color = Px.orange, small = true)
                    PixelButton("Borrar datos", enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                cleaning = item to vm.graph.gameData.entries(item)
                            } finally {
                                busy = false
                            }
                        }
                    }, color = Px.red, textColor = Px.white, small = true)
                }
            } }
        }
    }

    cleaning?.let { (item, entries) ->
        val downloaded = item.game.packages.sumOf { it.size }
        PixelDialog("Borrar datos", onDismiss = { if (!busy) cleaning = null }, wide = true, widthFraction = 0.7f) {
            Text(item.game.title.uppercase(), style = PxText.label.copy(color = Px.yellow))
            Spacer(Modifier.size(8.dp))
            if (entries.isEmpty()) {
                Text("No hay OBB, datos ni archivos extraídos de este juego.", style = PxText.body)
            } else {
                entries.forEach { e ->
                    Text("• ${e.label} (${FileNames.formatSize(e.size)})", style = PxText.body)
                    Text(e.file.path, style = PxText.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "También se borran las descargas de Telegram de este juego (hasta ${FileNames.formatSize(downloaded)}). " +
                    "La partida guardada dentro de la app solo se borra al desinstalarla.",
                style = PxText.small,
            )
            DialogButtons {
                PixelButton("Cancelar", onClick = { cleaning = null }, color = Px.grey, small = true, enabled = !busy)
                PixelButton(if (busy) "Borrando…" else "Borrar", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val freed = vm.graph.gameData.clean(item, entries)
                            toast("Liberado ${FileNames.formatSize(freed)}")
                        } catch (e: Exception) {
                            toast(e.message ?: "Error")
                        } finally {
                            busy = false
                            cleaning = null
                        }
                    }
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
}

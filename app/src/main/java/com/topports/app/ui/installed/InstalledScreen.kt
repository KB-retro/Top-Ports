package com.topports.app.ui.installed

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.topports.app.ui.store.TdImage
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.InstalledGame
import com.topports.core.UpdateItem
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch
import com.topports.app.ui.theme.pxCaps

/** Juegos del canal que están instalados: abrir, desinstalar y borrar sus datos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstalledScreen(vm: AppViewModel) {
    val installedRaw by vm.installed.collectAsStateWithLifecycle()
    // Actualizaciones (antes en su propia pestaña): van con su juego instalado, y los juegos con
    // actualización salen primero, en amarillo y con la carátula iluminada.
    val updates by vm.updates.collectAsStateWithLifecycle()
    fun updateOf(item: InstalledGame): UpdateItem? =
        updates.firstOrNull { u -> item.apps.any { it.packageName == u.packageName } }
    val installed = remember(installedRaw, updates) {
        installedRaw.sortedBy { if (updateOf(it) != null) 0 else 1 }
    }
    // Actualizaciones de apps que no se han podido unir a un juego instalado: se ven igual, sueltas.
    val looseUpdates = remember(installedRaw, updates) {
        updates.filter { u -> installedRaw.none { item -> item.apps.any { it.packageName == u.packageName } } }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Al volver de desinstalar (o de cualquier otra app), comprobar otra vez.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.graph.updates.refresh() }

    var cleaning by remember { mutableStateOf<Pair<InstalledGame, List<GameData.Entry>>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Sube al borrar datos: se vuelve a medir lo que ocupa cada juego.
    var measured by remember { mutableIntStateOf(0) }

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
                        when {
                            installed.isEmpty() && updates.isEmpty() -> "Nada instalado"
                            updates.isEmpty() -> "${installed.size} instalados · todo al día"
                            else -> "${installed.size} instalados · ${updates.size} con actualización"
                        },
                        if (updates.isEmpty()) Px.green else Px.yellow,
                    )
                }
                PixelButton("Comprobar", onClick = { vm.graph.updates.refresh() }, color = Px.blue, small = true)
            }
        }
        items(looseUpdates, key = { "u" + it.packageName }) { update -> LooseUpdateCard(vm, update) }
        if (installed.isEmpty() && looseUpdates.isEmpty()) {
            item {
                Text(
                    "Aquí aparecen los juegos del canal que tienes instalados: los instalados desde Top Ports " +
                        "y los que tienen el mismo nombre que un juego del canal.",
                    style = PxText.small,
                )
            }
        }
        items(installed, key = { it.game.id }) { item ->
            val update = updateOf(item)
            PixelPanel(Modifier.fillMaxWidth(), border = if (update != null) Px.yellow else Px.green) { Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 54.dp)
                            .then(if (update != null) Modifier.updateGlow() else Modifier)
                            .background(Px.black),
                    ) {
                        item.game.images.firstOrNull()?.let { TdImage(it.thumbFileId, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.game.title.pxCaps(), style = PxText.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.apps.forEach { app ->
                            Text(
                                "${app.label} ${app.versionName ?: ""}".trim(),
                                style = PxText.small,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        update?.let { u ->
                            Text(
                                "ACTUALIZACIÓN: ${u.installedVersion ?: "instalada"} → ${u.newVersion ?: "nueva versión"}",
                                style = PxText.small.copy(color = Px.yellow),
                            )
                        }
                        // Lo que ocupa (app, OBB, datos y lo extraído), medido en segundo plano.
                        val size by produceState<Long?>(null, item.game.id, item.apps, measured) {
                            value = runCatching { vm.graph.gameData.footprint(item) }.getOrNull()
                        }
                        size?.let { Text("Ocupa ${FileNames.formatSize(it)}", style = PxText.small.copy(color = Px.yellow)) }
                    }
                }
                Spacer(Modifier.size(8.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (update != null) {
                        // Abre la ficha del post nuevo, donde está el archivo para descargar.
                        PixelButton("Actualizar", onClick = {
                            vm.tab = Tab.STORE
                            vm.openGame(update.game.id)
                        }, color = Px.yellow, small = true)
                        // Por si el aviso no es correcto: no volver a avisar de este archivo.
                        PixelButton("Ignorar", onClick = {
                            vm.graph.installs.ignoreUpdate(update.packageName, update.pkg)
                            vm.graph.updates.refresh()
                        }, color = Px.darkGrey, textColor = Px.white, small = true)
                    }
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
            Text(item.game.title.pxCaps(), style = PxText.label.copy(color = Px.yellow))
            Spacer(Modifier.size(8.dp))
            // Con muchas carpetas la lista se desplaza y los botones siguen visibles.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
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
            }
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
                            measured++
                        }
                    }
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
}

/** Carátula de un juego con actualización: un halo amarillo que late. */
@Composable
private fun Modifier.updateGlow(): Modifier {
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "halo",
    )
    return this
        .drawBehind {
            val grow = 5.dp.toPx()
            drawRect(
                Px.yellow.copy(alpha = 0.45f * pulse),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
            )
        }
        .border(2.dp, Px.yellow.copy(alpha = 0.5f + 0.5f * pulse))
}

/** Actualización de una app que no se ha podido unir a ningún juego instalado. */
@Composable
private fun LooseUpdateCard(vm: AppViewModel, update: UpdateItem) {
    PixelPanel(Modifier.fillMaxWidth(), border = Px.yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 54.dp).updateGlow().background(Px.black)) {
                update.game.images.firstOrNull()?.let { TdImage(it.thumbFileId, Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(update.game.title.pxCaps(), style = PxText.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${update.appLabel}: ${update.installedVersion ?: "instalada"} → ${update.newVersion ?: "nueva versión"}",
                    style = PxText.small.copy(color = Px.yellow),
                )
                Text("${update.pkg.name} · ${FileNames.formatSize(update.pkg.size)}", style = PxText.small)
            }
            Spacer(Modifier.width(8.dp))
            // Tan ancha como el botón más largo: si no, el texto de la izquierda se queda sin sitio.
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelButton("Actualizar", onClick = {
                    vm.tab = Tab.STORE
                    vm.openGame(update.game.id)
                }, color = Px.yellow, small = true, modifier = Modifier.fillMaxWidth())
                PixelButton("Ignorar", onClick = {
                    vm.graph.installs.ignoreUpdate(update.packageName, update.pkg)
                    vm.graph.updates.refresh()
                }, color = Px.darkGrey, textColor = Px.white, small = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

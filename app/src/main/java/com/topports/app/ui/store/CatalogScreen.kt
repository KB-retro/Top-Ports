package com.topports.app.ui.store

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.topports.app.TopPortsApp
import com.topports.app.telegram.ChannelRepository
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTag
import com.topports.app.ui.components.PixelTextField
import com.topports.app.ui.components.pixelCard
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.PackageKind
import androidx.compose.runtime.produceState
import java.io.File

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val state by vm.catalog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val query = vm.search.trim()
    val games = remember(state.games, query) {
        if (query.isEmpty()) state.games
        else state.games.filter { it.title.contains(query, true) || it.description.contains(query, true) }
    }

    // Con mando: al entrar (o al volver de una ficha) se selecciona la última carátula abierta.
    val focus = remember { FocusRequester() }
    val targetId = vm.lastGameId?.takeIf { id -> games.any { it.id == id } } ?: games.firstOrNull()?.id
    LaunchedEffect(targetId != null) {
        if (targetId == null) return@LaunchedEffect
        val index = games.indexOfFirst { it.id == targetId }
        val visible = vm.catalogGrid.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && index !in visible) vm.catalogGrid.scrollToItem(index)
        delay(80)
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                placeholder = "Buscar juego…",
                modifier = Modifier.weight(1f),
            )
            if (!state.loading) {
                PixelButton("Recargar", onClick = { vm.refreshCatalog() }, color = Px.blue, small = true)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                when {
                    state.loading -> "ESCANEANDO… ${state.scannedMessages} MENSAJES"
                    else -> "${games.size} JUEGOS" + if (state.channelTitle.isNotBlank()) " · ${state.channelTitle.uppercase()}" else ""
                },
                style = PxText.tiny.copy(color = Px.grey),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.loading) {
                Spacer(Modifier.height(8.dp))
                PixelProgress(null, Modifier.fillMaxWidth(), color = Px.blue)
            }
        }

        state.error?.let { error ->
            PixelPanel(Modifier.padding(12.dp).fillMaxWidth(), border = Px.red) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ERROR", style = PxText.label.copy(color = Px.red))
                    Text(error, style = PxText.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Reintentar", onClick = { vm.refreshCatalog() }, small = true)
                        PixelButton("Abrir canal", onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ChannelRepository.telegramLink()))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }, color = Px.blue, small = true)
                    }
                }
            }
        }

        // Cuadrícula de carátulas: 2 columnas en un móvil en vertical, 3–5 en consolas en horizontal.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            state = vm.catalogGrid,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(games, key = { it.id }) { game ->
                GameCard(
                    game,
                    onClick = { vm.openGame(game.id) },
                    modifier = if (game.id == targetId) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .pixelCard(onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Px.black)) {
            val image = game.images.firstOrNull()
            if (image != null) {
                TdImage(image.thumbFileId, Modifier.fillMaxSize())
            } else {
                Text("?", style = PxText.title.copy(color = Px.darkGrey), modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            game.title.uppercase(),
            style = PxText.label,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            game.packages.map { kindTag(it.name, it.kind) }.distinct().take(2).forEach { (label, color) ->
                PixelTag(label, color)
            }
            Text(FileNames.formatSize(game.size), style = PxText.small, maxLines = 1)
        }
    }
}

/** Etiqueta y color según el tipo de archivo. */
fun kindTag(name: String, kind: PackageKind): Pair<String, Color> = when (kind) {
    PackageKind.APK -> "APK" to Px.green
    PackageKind.APK_BUNDLE -> "XAPK" to Px.green
    PackageKind.OBB -> "OBB" to Px.pink
    PackageKind.OTHER -> "FILE" to Px.grey
    PackageKind.ARCHIVE -> when (FileNames.extension(FileNames.volumeOf(name).baseName)) {
        "7z" -> "7Z" to Px.orange
        "rar" -> "RAR" to Px.purple
        "zip" -> "ZIP" to Px.yellow
        else -> "ARC" to Px.orange
    }
}

/** Imagen de Telegram: se descarga con TDLib y se muestra con Coil. */
@Composable
fun TdImage(fileId: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val path by produceState<String?>(null, fileId) {
        val graph = (context.applicationContext as TopPortsApp).graph
        value = runCatching { graph.telegram.downloadNow(fileId) }.getOrNull()
    }
    val current = path
    if (current != null) {
        AsyncImage(model = File(current), contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier.background(Px.navyDark))
    }
}

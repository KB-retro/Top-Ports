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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.topports.app.ui.components.pixelClickable
import com.topports.app.ui.components.pixelFrame
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            PixelTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                placeholder = "Buscar juego…",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        state.loading -> "ESCANEANDO… ${state.scannedMessages} MENSAJES"
                        else -> "${games.size} JUEGOS" + if (state.channelTitle.isNotBlank()) " · ${state.channelTitle.uppercase()}" else ""
                    },
                    style = PxText.tiny.copy(color = Px.grey),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!state.loading) {
                    PixelButton("Recargar", onClick = { vm.refreshCatalog() }, color = Px.blue, small = true)
                }
            }
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

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(games, key = { it.id }) { game ->
                GameCard(game, onClick = { vm.selectedGameId = game.id })
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pixelFrame(Px.navy, Px.white)
            .pixelClickable(onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).background(Px.black)) {
            val image = game.images.firstOrNull()
            if (image != null) {
                TdImage(image.thumbFileId, Modifier.fillMaxSize())
            } else {
                Text("?", style = PxText.title.copy(color = Px.darkGrey), modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                game.title.uppercase(),
                style = PxText.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                game.packages.map { kindTag(it.name, it.kind) }.distinct().take(3).forEach { (label, color) ->
                    PixelTag(label, color)
                }
                Text(FileNames.formatSize(game.size), style = PxText.small)
            }
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

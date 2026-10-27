package com.topports.app.ui.store

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.telegram.ChannelRepository
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Tab
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTag
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.components.pixelClickable
import com.topports.app.ui.components.pixelFrame
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.GamePackage
import com.topports.core.PackageKind
import java.text.DateFormat
import java.util.Date

@Composable
fun GameScreen(vm: AppViewModel, gameId: Long) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val game = catalog.games.firstOrNull { it.id == gameId }
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PixelButton("Volver", onClick = { vm.selectedGameId = null })
        }
        return
    }
    val context = LocalContext.current
    var zoomed by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(game.id) { game.packages.forEach { vm.graph.tasks.refresh(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton("< Volver", onClick = { vm.selectedGameId = null }, color = Px.grey, small = true)
            Spacer(Modifier.weight(1f))
            PixelButton("Ver en Telegram", onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(ChannelRepository.telegramLink(game.id)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }, color = Px.blue, small = true)
        }
        Spacer(Modifier.height(12.dp))

        if (game.images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(game.images) { image ->
                    Box(
                        Modifier
                            .size(width = 260.dp, height = 150.dp)
                            .pixelFrame(Px.black, Px.white)
                            .pixelClickable { zoomed = image.fullFileId },
                    ) {
                        TdImage(image.thumbFileId, Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(game.title.uppercase(), style = PxText.title.copy(color = Px.yellow))
        Spacer(Modifier.height(6.dp))
        Text(
            "${FileNames.formatSize(game.size)} · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.date * 1000L)),
            style = PxText.small,
        )
        if (game.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            PixelPanel(Modifier.fillMaxWidth(), fill = Px.navyDark) {
                Text(game.description, style = PxText.body)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Archivos")
        game.packages.forEach { pkg ->
            PackageRow(vm, game, pkg)
            Spacer(Modifier.height(10.dp))
        }
        PixelPanel(Modifier.fillMaxWidth(), fill = Px.navyDark, border = Px.darkGrey) {
            Text(
                "Al instalar, los archivos comprimidos se extraen en /TopPorts/${FileNames.safeFolderName(game.title)}, " +
                    "los APK se instalan y los OBB se copian a Android/obb.",
                style = PxText.small,
            )
        }
    }

    zoomed?.let { fileId ->
        PixelDialog("Captura", onDismiss = { zoomed = null }) {
            TdImage(fileId, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
            DialogButtons { PixelButton("Cerrar", onClick = { zoomed = null }, small = true) }
        }
    }
}

@Composable
private fun PackageRow(vm: AppViewModel, game: Game, pkg: GamePackage) {
    val files by vm.files.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val info = vm.graph.tasks.downloadInfo(pkg, files)
    val (tag, color) = kindTag(pkg.name, pkg.kind)
    var confirmDelete by remember { mutableStateOf(false) }

    PixelPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelTag(tag, color)
                Spacer(Modifier.width(8.dp))
                Text(pkg.name, style = PxText.body, modifier = Modifier.weight(1f))
            }
            Text(
                buildString {
                    append(FileNames.formatSize(pkg.size))
                    if (pkg.isMultiPart) append(" · ${pkg.parts.size} partes")
                },
                style = PxText.small,
            )

            if (info.started && !info.complete) {
                PixelProgress(info.fraction, Modifier.fillMaxWidth())
                Text(
                    "${FileNames.formatSize(info.downloaded)} / ${FileNames.formatSize(info.total)} " +
                        if (info.active) "· descargando" else "· en pausa",
                    style = PxText.small,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    info.complete -> {
                        val action = when (pkg.kind) {
                            PackageKind.APK -> "Instalar"
                            PackageKind.ARCHIVE, PackageKind.APK_BUNDLE -> "Extraer e instalar"
                            PackageKind.OBB -> "Copiar OBB"
                            PackageKind.OTHER -> "Guardar"
                        }
                        PixelButton(action, onClick = {
                            vm.graph.tasks.installPackage(game, pkg)
                            Toast.makeText(context, "Mira el progreso en TAREAS", Toast.LENGTH_SHORT).show()
                            vm.tab = Tab.TASKS
                        }, color = Px.green, small = true)
                    }
                    info.active -> PixelButton("Pausar", onClick = { vm.graph.tasks.pause(pkg) }, color = Px.yellow, small = true)
                    info.started -> PixelButton("Continuar", onClick = { download(vm, context, pkg) }, small = true)
                    else -> PixelButton("Descargar", onClick = { download(vm, context, pkg) }, small = true)
                }
                if (info.started || info.complete) {
                    PixelButton("Borrar", onClick = { confirmDelete = true }, color = Px.red, textColor = Px.white, small = true)
                }
            }
        }
    }

    if (confirmDelete) {
        PixelDialog("¿Borrar descarga?", onDismiss = { confirmDelete = false }) {
            Text("Se borrará ${pkg.name} del móvil para liberar espacio. Lo ya instalado o extraído no se toca.", style = PxText.body)
            DialogButtons {
                PixelButton("No", onClick = { confirmDelete = false }, color = Px.grey, small = true)
                PixelButton("Borrar", onClick = {
                    confirmDelete = false
                    vm.graph.tasks.deleteDownload(pkg)
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
}

private fun download(vm: AppViewModel, context: android.content.Context, pkg: GamePackage) {
    vm.graph.tasks.download(pkg) { error ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
}

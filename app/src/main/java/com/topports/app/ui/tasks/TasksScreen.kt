package com.topports.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.ui.AppViewModel
import com.topports.app.updates.InstallRegistry
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTextField
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.app.work.TaskState
import com.topports.core.FileNames
import com.topports.app.ui.theme.pxCaps

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val names by vm.graph.tasks.downloadNames.collectAsStateWithLifecycle()
    var passwordFor by remember { mutableStateOf<TaskState?>(null) }

    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf<InstallRegistry.PendingSave?>(null) }

    // Descargas de la tienda, una por paquete (todas sus partes juntas) con sus botones.
    val pending = vm.graph.tasks.pendingDownloads()
    val pendingIds = pending.flatMap { p -> p.parts.map { it.first } }.toSet()
    // Otras descargas sueltas (vídeos, actualización de la app…): solo el progreso.
    val downloads = files.values
        .filter { it.id !in pendingIds }
        .filter { it.local.isDownloadingActive || (it.id in names && !it.local.isDownloadingCompleted && it.local.downloadedSize > 0) }
        .sortedBy { names[it.id] ?: "" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Descargas", Px.blue) }
        if (downloads.isEmpty() && pending.isEmpty()) {
            item { Text("No hay descargas en curso.", style = PxText.small) }
        }
        items(pending, key = { "p" + it.pkgKey }) { p ->
            val parts = p.parts.map { (id, _) -> files[id] }
            val total = parts.sumOf { f -> f?.let { if (it.size > 0) it.size else it.expectedSize } ?: 0L }
            val done = parts.sumOf { it?.local?.downloadedSize ?: 0L }
            val active = parts.any { it?.local?.isDownloadingActive == true }
            val game = catalog.games.firstOrNull { it.id == p.gameId }
            PixelPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    game?.let { Text(it.title.pxCaps(), style = PxText.label) }
                    Text(
                        p.parts.first().second + if (p.parts.size > 1) " · ${p.parts.size} partes" else "",
                        style = PxText.body,
                    )
                    PixelProgress(if (total > 0) done.toFloat() / total else null, Modifier.fillMaxWidth(), color = Px.blue)
                    Text(
                        "${FileNames.formatSize(done)} / ${FileNames.formatSize(total)}" + if (active) "" else " · en pausa",
                        style = PxText.small,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (active) {
                            PixelButton("Pausar", onClick = { vm.graph.tasks.pauseDownload(p) }, color = Px.yellow, small = true)
                        } else {
                            PixelButton("Continuar", onClick = { vm.graph.tasks.resumeDownload(p) }, color = Px.orange, small = true)
                        }
                        PixelButton("Cancelar", onClick = { confirmCancel = p }, color = Px.red, textColor = Px.white, small = true)
                        if (game != null) {
                            PixelButton("Ver juego", onClick = { vm.openGameFromTasks(game.id) }, color = Px.blue, small = true)
                        }
                    }
                }
            }
        }
        items(downloads, key = { "d" + it.id }) { file ->
            val total = if (file.size > 0) file.size else file.expectedSize
            PixelPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(names[file.id] ?: "Archivo ${file.id}", style = PxText.body)
                    PixelProgress(if (total > 0) file.local.downloadedSize.toFloat() / total else null, Modifier.fillMaxWidth(), color = Px.blue)
                    Text(
                        "${FileNames.formatSize(file.local.downloadedSize)} / ${FileNames.formatSize(total)}" +
                            if (file.local.isDownloadingActive) "" else " · en pausa",
                        style = PxText.small,
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Tareas", Px.yellow) }
                if (tasks.any { !it.running }) {
                    PixelButton("Limpiar", onClick = { vm.graph.tasks.clearFinished() }, color = Px.grey, small = true)
                }
            }
        }
        if (tasks.isEmpty()) {
            item { Text("Aquí verás las extracciones, instalaciones y copias.", style = PxText.small) }
        }
        items(tasks.reversed(), key = { "t" + it.id }) { task ->
            val border = when {
                task.failed -> Px.red
                !task.running -> Px.green
                else -> Px.white
            }
            PixelPanel(Modifier.fillMaxWidth(), border = border) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(task.title.pxCaps(), style = PxText.label)
                    Text(task.message, style = PxText.body.copy(color = if (task.failed) Px.red else Px.white))
                    if (task.running) PixelProgress(task.progress, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.running) {
                            PixelButton("Cancelar", onClick = { vm.graph.tasks.cancel(task.id) }, color = Px.red, textColor = Px.white, small = true)
                        }
                        if (task.needsPassword) {
                            PixelButton("Contraseña", onClick = { passwordFor = task }, color = Px.yellow, small = true)
                        }
                        task.resultDir?.let { dir ->
                            PixelButton("Abrir carpeta", onClick = { vm.openFolder(dir) }, color = Px.blue, small = true)
                        }
                        if (!task.running) {
                            PixelButton("X", onClick = { vm.graph.tasks.dismiss(task.id) }, color = Px.grey, small = true)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    confirmCancel?.let { p ->
        PixelDialog("¿Cancelar descarga?", onDismiss = { confirmCancel = null }) {
            Text("Se para la descarga de ${p.parts.first().second} y se borra lo ya descargado.", style = PxText.body)
            DialogButtons {
                PixelButton("No", onClick = { confirmCancel = null }, color = Px.grey, small = true)
                PixelButton("Cancelar descarga", onClick = {
                    vm.graph.tasks.cancelDownload(p)
                    confirmCancel = null
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }

    passwordFor?.let { task ->
        var password by remember(task.id) { mutableStateOf("") }
        PixelDialog("Contraseña", onDismiss = { passwordFor = null }) {
            Text("El archivo está protegido. Suele venir en el post del canal.", style = PxText.body)
            Spacer(Modifier.padding(4.dp))
            PixelTextField(password, { password = it }, Modifier.fillMaxWidth(), placeholder = "contraseña")
            DialogButtons {
                PixelButton("Cancelar", onClick = { passwordFor = null }, color = Px.grey, small = true)
                PixelButton("Extraer", onClick = {
                    vm.graph.tasks.retryWithPassword(task.id, password)
                    passwordFor = null
                }, color = Px.green, small = true, enabled = password.isNotEmpty())
            }
        }
    }
}

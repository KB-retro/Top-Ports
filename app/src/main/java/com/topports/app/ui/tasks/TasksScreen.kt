package com.topports.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
fun TasksScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val names by vm.graph.tasks.downloadNames.collectAsStateWithLifecycle()
    var passwordFor by remember { mutableStateOf<TaskState?>(null) }

    val downloads = files.values
        .filter { it.local.isDownloadingActive || (it.id in names && !it.local.isDownloadingCompleted && it.local.downloadedSize > 0) }
        .sortedBy { names[it.id] ?: "" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Descargas", Px.blue) }
        if (downloads.isEmpty()) {
            item { Text("No hay descargas en curso.", style = PxText.small) }
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
                    Text(task.title.uppercase(), style = PxText.label)
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

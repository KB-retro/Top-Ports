package com.topports.app.ui.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.archive.ArchiveEntry
import com.topports.app.archive.PasswordRequiredException
import com.topports.app.storage.Storage
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Tab
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTag
import com.topports.app.ui.components.PixelTextField
import com.topports.app.ui.components.pixelCard
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.PackageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class Clip { COPY, MOVE }

private sealed interface FileDialog {
    data class Apk(val file: File) : FileDialog
    data class Archive(val file: File) : FileDialog
    data class Contents(val file: File) : FileDialog
    data class Rename(val file: File) : FileDialog
    data object NewFolder : FileDialog
    data class Delete(val files: List<File>) : FileDialog
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Storage.hasAllFilesAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = Storage.hasAllFilesAccess(context) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAccess = Storage.hasAllFilesAccess(context)
    }

    val root = remember(hasAccess) {
        if (hasAccess) Storage.externalRoot else Storage.topPortsDir(context).parentFile ?: context.filesDir
    }
    val dir = vm.browserDir?.let(::File)?.takeIf { it.isDirectory } ?: root
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    val finishedTasks = tasks.count { !it.running }

    val entries by produceState(emptyList<File>(), dir.path, reload, finishedTasks, hasAccess) {
        value = withContext(Dispatchers.IO) {
            (dir.listFiles()?.toList() ?: emptyList())
                .filterNot { it.name.startsWith(".") }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    var selection by remember { mutableStateOf(setOf<File>()) }
    var clipboard by remember { mutableStateOf<Pair<Clip, List<File>>?>(null) }
    var dialog by remember { mutableStateOf<FileDialog?>(null) }

    BackHandler(enabled = selection.isNotEmpty() || dir.path != root.path) {
        if (selection.isNotEmpty()) selection = emptySet()
        else vm.browserDir = dir.parentFile?.path
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasAccess) {
            PixelPanel(Modifier.padding(12.dp).fillMaxWidth(), border = Px.yellow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PERMISO DE ARCHIVOS", style = PxText.label.copy(color = Px.yellow))
                    Text(
                        "Para navegar por todo el móvil, extraer en /TopPorts y copiar OBB, " +
                            "Top Ports necesita acceso a todos los archivos.",
                        style = PxText.body,
                    )
                    PixelButton("Dar permiso", onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching { context.startActivity(Storage.allFilesAccessIntent(context)) }
                        } else {
                            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }, small = true)
                }
            }
        }

        // Accesos directos
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PixelButton("Inicio", onClick = { vm.browserDir = root.path }, color = Px.blue, small = true)
            PixelButton("TopPorts", onClick = { vm.browserDir = Storage.topPortsDir(context).path }, color = Px.orange, small = true)
            if (hasAccess) {
                PixelButton("Descargas", onClick = {
                    vm.browserDir = File(Storage.externalRoot, "Download").path
                }, color = Px.green, small = true)
                PixelButton("OBB", onClick = {
                    vm.browserDir = File(Storage.externalRoot, "Android/obb").path
                }, color = Px.pink, small = true)
            }
        }

        // Ruta y espacio libre
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(
                dir.path.removePrefix(root.parent ?: "").ifEmpty { "/" },
                style = PxText.small.copy(color = Px.yellow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val total = Storage.totalBytes(dir)
            val free = Storage.freeBytes(dir)
            if (total > 0) {
                Spacer(Modifier.height(4.dp))
                PixelProgress((total - free).toFloat() / total, Modifier.fillMaxWidth(), color = Px.blue)
                Text("Libre ${FileNames.formatSize(free)} de ${FileNames.formatSize(total)}", style = PxText.small)
            }
        }

        // Barra de acciones
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val clip = clipboard
            when {
                selection.isNotEmpty() -> {
                    PixelButton("Copiar", onClick = {
                        clipboard = Clip.COPY to selection.toList(); selection = emptySet()
                    }, small = true)
                    PixelButton("Cortar", onClick = {
                        clipboard = Clip.MOVE to selection.toList(); selection = emptySet()
                    }, small = true)
                    if (selection.size == 1) {
                        PixelButton("Renombrar", onClick = { dialog = FileDialog.Rename(selection.first()) }, color = Px.blue, small = true)
                    }
                    PixelButton("Borrar", onClick = { dialog = FileDialog.Delete(selection.toList()) }, color = Px.red, textColor = Px.white, small = true)
                    PixelButton("X ${selection.size}", onClick = { selection = emptySet() }, color = Px.grey, small = true)
                }
                clip != null -> {
                    PixelButton("Pegar aquí (${clip.second.size})", onClick = {
                        vm.graph.tasks.copyFiles(clip.second, dir, move = clip.first == Clip.MOVE)
                        clipboard = null
                    }, color = Px.green, small = true)
                    PixelButton("Cancelar", onClick = { clipboard = null }, color = Px.grey, small = true)
                }
                else -> {
                    PixelButton("Nueva carpeta", onClick = { dialog = FileDialog.NewFolder }, color = Px.blue, small = true)
                    PixelButton("Subir", onClick = {
                        if (dir.path != root.path) vm.browserDir = dir.parentFile?.path
                    }, color = Px.grey, small = true, enabled = dir.path != root.path)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("CARPETA VACÍA", style = PxText.tiny.copy(color = Px.darkGrey))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entries, key = { it.path }) { file ->
                val selected = file in selection
                Row(
                    Modifier
                        .fillMaxWidth()
                        // Mando: X o Y marcan/desmarcan el archivo (como mantener pulsado).
                        .onPreviewKeyEvent { event ->
                            val toggle = event.type == KeyEventType.KeyDown &&
                                (event.key == Key.ButtonX || event.key == Key.ButtonY)
                            if (toggle) selection = if (selected) selection - file else selection + file
                            toggle
                        }
                        .pixelCard(
                            fill = if (selected) Px.purple else Px.navy,
                            border = if (selected) Px.pink else Px.navyDark,
                            shadow = null,
                            onClick = {
                                when {
                                    selection.isNotEmpty() ->
                                        selection = if (selected) selection - file else selection + file
                                    file.isDirectory -> vm.browserDir = file.path
                                    FileNames.isApk(file.name) -> dialog = FileDialog.Apk(file)
                                    FileNames.isArchive(file.name) -> dialog = FileDialog.Archive(file)
                                    else -> openExternally(context, file)
                                }
                            },
                            onLongClick = { selection = if (selected) selection - file else selection + file },
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (tag, color) = fileTag(file)
                    PixelTag(tag, color, Modifier.width(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = PxText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (file.isDirectory) "Carpeta" else FileNames.formatSize(file.length())) + " · " +
                                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.lastModified())),
                            style = PxText.small,
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is FileDialog.Apk -> PixelDialog("Instalar APK", onDismiss = { dialog = null }) {
            Text(d.file.name, style = PxText.body)
            DialogButtons {
                PixelButton("Cancelar", onClick = { dialog = null }, color = Px.grey, small = true)
                PixelButton("Instalar", onClick = {
                    dialog = null
                    vm.graph.tasks.installLocalApk(d.file)
                    vm.tab = Tab.TASKS
                }, color = Px.green, small = true)
            }
        }
        is FileDialog.Archive -> PixelDialog("Archivo comprimido", onDismiss = { dialog = null }) {
            val target = File(dir, FileNames.safeFolderName(archiveFolderName(d.file.name)))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.file.name, style = PxText.body)
                Text("Se extraerá en: ${target.name}/", style = PxText.small)
                PixelButton("Extraer aquí", onClick = {
                    dialog = null
                    vm.graph.tasks.extractLocal(d.file, target, installAfter = false)
                    Toast.makeText(context, "Extrayendo… mira TAREAS", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), small = true)
                PixelButton("Extraer e instalar", onClick = {
                    dialog = null
                    vm.graph.tasks.extractLocal(d.file, target, installAfter = true)
                    vm.tab = Tab.TASKS
                }, color = Px.green, modifier = Modifier.fillMaxWidth(), small = true)
                PixelButton("Ver contenido", onClick = { dialog = FileDialog.Contents(d.file) },
                    color = Px.blue, modifier = Modifier.fillMaxWidth(), small = true)
                PixelButton("Cancelar", onClick = { dialog = null }, color = Px.grey, modifier = Modifier.fillMaxWidth(), small = true)
            }
        }
        is FileDialog.Contents -> PixelDialog("Contenido", onDismiss = { dialog = null }) {
            val result by produceState<Result<List<ArchiveEntry>>?>(null, d.file) {
                value = runCatching { vm.graph.tasks.listArchive(d.file) }
            }
            val r = result
            when {
                r == null -> PixelProgress(null, Modifier.fillMaxWidth())
                r.isFailure -> Text(
                    if (r.exceptionOrNull() is PasswordRequiredException) "Tiene contraseña: usa Extraer y te la pedirá."
                    else "No se pudo abrir: ${r.exceptionOrNull()?.message}",
                    style = PxText.body.copy(color = Px.red),
                )
                else -> {
                    val list = r.getOrDefault(emptyList())
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list.take(1000)) { entry ->
                            Row {
                                Text(
                                    entry.path,
                                    style = PxText.small.copy(color = if (entry.isDirectory) Px.yellow else Px.white),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!entry.isDirectory) Text(FileNames.formatSize(entry.size), style = PxText.small)
                            }
                        }
                    }
                    Text("${list.size} elementos", style = PxText.small.copy(color = Px.grey))
                }
            }
            DialogButtons { PixelButton("Cerrar", onClick = { dialog = null }, small = true) }
        }
        is FileDialog.Rename -> TextInputDialog("Renombrar", d.file.name, onDismiss = { dialog = null }) { name ->
            // Sin "/": un nombre con barras movería el archivo a otra carpeta.
            val ok = name.isNotBlank() && '/' !in name && d.file.renameTo(File(d.file.parentFile, name.trim()))
            if (!ok) Toast.makeText(context, "No se pudo renombrar", Toast.LENGTH_SHORT).show()
            dialog = null
            selection = emptySet()
            reload++
        }
        FileDialog.NewFolder -> TextInputDialog("Nueva carpeta", "", onDismiss = { dialog = null }) { name ->
            val ok = name.isNotBlank() && '/' !in name && File(dir, name.trim()).mkdirs()
            if (!ok) Toast.makeText(context, "No se pudo crear la carpeta", Toast.LENGTH_SHORT).show()
            dialog = null
            reload++
        }
        is FileDialog.Delete -> PixelDialog("¿Borrar?", onDismiss = { dialog = null }) {
            Text(
                if (d.files.size == 1) "Se borrará ${d.files[0].name}" else "Se borrarán ${d.files.size} elementos",
                style = PxText.body,
            )
            DialogButtons {
                PixelButton("No", onClick = { dialog = null }, color = Px.grey, small = true)
                PixelButton("Borrar", onClick = {
                    vm.graph.tasks.deleteFiles(d.files)
                    dialog = null
                    selection = emptySet()
                }, color = Px.red, textColor = Px.white, small = true)
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PixelDialog(title, onDismiss = onDismiss) {
        PixelTextField(text, { text = it }, Modifier.fillMaxWidth())
        DialogButtons {
            PixelButton("Cancelar", onClick = onDismiss, color = Px.grey, small = true)
            PixelButton("OK", onClick = { onConfirm(text.trim()) }, color = Px.green, small = true)
        }
    }
}

/** `Juego.part1.rar` → `Juego`, `datos.7z.001` → `datos`. */
private fun archiveFolderName(name: String): String =
    FileNames.volumeOf(name).baseName.substringBeforeLast('.').ifBlank { name }

private fun fileTag(file: File): Pair<String, Color> {
    if (file.isDirectory) return "DIR" to Px.yellow
    return when (FileNames.kindOfFile(file.name)) {
        PackageKind.APK -> "APK" to Px.green
        PackageKind.APK_BUNDLE -> "XAPK" to Px.green
        PackageKind.OBB -> "OBB" to Px.pink
        PackageKind.ARCHIVE -> when (FileNames.extension(FileNames.volumeOf(file.name).baseName)) {
            "7z" -> "7Z" to Px.orange
            "rar" -> "RAR" to Px.lavender
            "zip" -> "ZIP" to Px.orange
            else -> "ARC" to Px.orange
        }
        PackageKind.OTHER -> when (FileNames.extension(file.name)) {
            "png", "jpg", "jpeg", "gif", "webp" -> "IMG" to Px.blue
            "mp4", "mkv", "webm" -> "VID" to Px.blue
            "txt", "md", "ini", "cfg", "json", "xml", "log" -> "TXT" to Px.grey
            else -> "FILE" to Px.darkGrey
        }
    }
}

private fun openExternally(context: Context, file: File) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.extension(file.name)) ?: "*/*"
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "No hay ninguna app para abrir ${file.name}", Toast.LENGTH_SHORT).show()
    }
}

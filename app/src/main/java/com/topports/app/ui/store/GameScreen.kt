package com.topports.app.ui.store

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.topports.app.ui.components.pixelFrame
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
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
import android.content.Context
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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
import com.topports.app.ui.components.pixelCard
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.app.storage.Storage
import com.topports.core.CatalogBuilder
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.GamePackage
import com.topports.core.PackageKind
import com.topports.core.VideoRef
import com.topports.app.ui.video.VideoPlayerDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private val urlRegex = Regex("""https?://[^\s)]+""")

/** Convierte las URLs del texto en enlaces pulsables. */
private fun linkify(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in urlRegex.findAll(text)) {
        append(text.substring(last, match.range.first))
        withLink(LinkAnnotation.Url(match.value, TextLinkStyles(SpanStyle(color = Px.blue, textDecoration = TextDecoration.Underline)))) {
            append(match.value)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

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
    // Con mando, al abrir la ficha queda seleccionado el botón del primer archivo.
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(game.id) {
        game.packages.forEach { vm.graph.tasks.refresh(it) }
        delay(120)
        runCatching { primaryFocus.requestFocus() }
    }

    val topBar: @Composable () -> Unit = {
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
    }
    val gallery: @Composable () -> Unit = {
        if (game.images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(game.images) { image ->
                    Box(
                        Modifier
                            .size(width = 260.dp, height = 150.dp)
                            .pixelCard(onClick = { zoomed = image.fullFileId }, fill = Px.black),
                    ) {
                        TdImage(image.thumbFileId, Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    // Scroll de la columna con la descripción y su zona visible, para desplazarla con el mando.
    val infoScroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val trackViewport = Modifier.onGloballyPositioned { viewport = it.boundsInWindow() }

    val info: @Composable () -> Unit = {
        Text(game.title.uppercase(), style = PxText.title.copy(color = Px.yellow))
        Spacer(Modifier.height(6.dp))
        Text(
            "${FileNames.formatSize(game.size)} · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.date * 1000L)),
            style = PxText.small,
        )
        if (game.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            DescriptionPanel(game.description, infoScroll, viewport)
        }
    }
    val downloads: @Composable () -> Unit = {
        SectionTitle("Archivos")
        game.packages.forEachIndexed { index, pkg ->
            PackageRow(vm, game, pkg, if (index == 0) primaryFocus else null)
            Spacer(Modifier.height(10.dp))
        }
        if (game.videos.isNotEmpty()) {
            SectionTitle("Vídeos", Px.pink)
            game.videos.forEach { video ->
                VideoRow(vm, video)
                Spacer(Modifier.height(10.dp))
            }
        }
        PixelPanel(Modifier.fillMaxWidth(), fill = Px.navyDark, border = Px.darkGrey) {
            Text(
                "Los APK se instalan directamente. Los comprimidos se extraen donde elijas " +
                    "(raíz de la memoria o /TopPorts) y los OBB se copian a Android/obb.",
                style = PxText.small,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 560.dp) {
            // Horizontal (consolas): ficha a la izquierda, archivos y acciones a la derecha.
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(0.48f).fillMaxHeight().then(trackViewport).verticalScroll(infoScroll)) {
                    topBar()
                    Spacer(Modifier.height(12.dp))
                    gallery()
                    info()
                }
                Column(Modifier.weight(0.52f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    downloads()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().then(trackViewport).verticalScroll(infoScroll).padding(12.dp)) {
                topBar()
                Spacer(Modifier.height(12.dp))
                gallery()
                info()
                Spacer(Modifier.height(12.dp))
                downloads()
            }
        }
    }

    zoomed?.let { fileId ->
        PixelDialog("Captura", onDismiss = { zoomed = null }) {
            TdImage(fileId, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
            DialogButtons { PixelButton("Cerrar", onClick = { zoomed = null }, small = true) }
        }
    }
}

/**
 * Descripción seleccionable con el mando: con ella marcada, arriba/abajo (o L1/R1)
 * desplazan el texto poco a poco en vez de saltar al siguiente botón. Cuando el texto
 * ya se ve entero por ese lado, la cruceta pasa al siguiente elemento como siempre.
 */
@Composable
private fun DescriptionPanel(text: String, scroll: ScrollState, viewport: Rect) {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val step = with(LocalDensity.current) { 110.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val down = when (event.key) {
                    Key.DirectionDown -> true
                    Key.DirectionUp -> false
                    else -> return@onPreviewKeyEvent false
                }
                // ¿Queda texto por ver en esa dirección?
                val hidden = if (down) bounds.bottom - viewport.bottom else viewport.top - bounds.top
                if (hidden <= 1f) return@onPreviewKeyEvent false
                val delta = minOf(step, hidden)
                scope.launch {
                    scroll.animateScrollBy(if (down) delta else -delta, tween(160, easing = LinearEasing))
                }
                true
            }
            .focusable(interactionSource = interaction)
            .pixelFrame(
                fill = Px.navyDark,
                border = if (focused) Px.yellow else Px.white,
                shadow = if (focused) Px.yellow else Px.black,
            )
            .padding(10.dp),
    ) {
        Text(linkify(text), style = PxText.body)
    }
}

@Composable
private fun PackageRow(vm: AppViewModel, game: Game, pkg: GamePackage, focus: FocusRequester? = null) {
    val files by vm.files.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val info = vm.graph.tasks.downloadInfo(pkg, files)
    val (tag, color) = kindTag(pkg.name, pkg.kind)
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseDest by remember { mutableStateOf(false) }
    val primary = if (focus != null) Modifier.focusRequester(focus) else Modifier

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
                            if (pkg.kind == PackageKind.APK || pkg.kind == PackageKind.OBB) {
                                startInstall(vm, context, game, pkg, Storage.gameDir(context, game.title))
                            } else {
                                chooseDest = true
                            }
                        }, color = Px.green, small = true, modifier = primary)
                    }
                    info.active -> PixelButton("Pausar", onClick = { vm.graph.tasks.pause(pkg) }, color = Px.yellow, small = true, modifier = primary)
                    info.started -> PixelButton("Continuar", onClick = { download(vm, context, pkg) }, small = true, modifier = primary)
                    else -> PixelButton("Descargar", onClick = { download(vm, context, pkg) }, small = true, modifier = primary)
                }
                if (info.started || info.complete) {
                    PixelButton("Borrar", onClick = { confirmDelete = true }, color = Px.red, textColor = Px.white, small = true)
                }
            }
        }
    }

    if (chooseDest) {
        DestinationDialog(vm, game, pkg, onDismiss = { chooseDest = false })
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

/** Vídeo del post (tutorial): se descarga de Telegram y se abre con el reproductor del móvil. */
@Composable
private fun VideoRow(vm: AppViewModel, video: VideoRef) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files by vm.files.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    val file = files[video.fileId]
    PixelPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 56.dp).background(Px.black)) {
                video.thumbFileId?.let { TdImage(it, Modifier.fillMaxSize()) }
                Text("▶", style = PxText.title.copy(color = Px.white), modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(video.caption, style = PxText.body)
                Text("%d:%02d".format(video.durationSeconds / 60, video.durationSeconds % 60), style = PxText.small)
                if (loading && file != null) {
                    val total = if (file.size > 0) file.size else file.expectedSize
                    PixelProgress(if (total > 0) file.local.downloadedSize.toFloat() / total else null, Modifier.fillMaxWidth(), color = Px.pink)
                }
                PixelButton(if (loading) "Cargando…" else "Ver vídeo", onClick = {
                    if (loading) return@PixelButton
                    loading = true
                    scope.launch {
                        val path = runCatching { vm.graph.telegram.downloadNow(video.fileId, priority = 32) }.getOrNull()
                        loading = false
                        if (path == null) {
                            Toast.makeText(context, "No se pudo descargar el vídeo", Toast.LENGTH_SHORT).show()
                        } else {
                            playingPath = path
                        }
                    }
                }, color = Px.pink, small = true, enabled = !loading)
            }
        }
    }
    playingPath?.let { path ->
        VideoPlayerDialog(File(path), video.caption, onClose = { playingPath = null })
    }
}

private fun startInstall(vm: AppViewModel, context: Context, game: Game, pkg: GamePackage, dest: File) {
    vm.graph.tasks.installPackage(game, pkg, dest)
    Toast.makeText(context, "Mira el progreso en TAREAS", Toast.LENGTH_SHORT).show()
    vm.tab = Tab.TASKS
}

/** Dónde extraer/guardar: raíz de la memoria interna o /TopPorts/<juego>. */
@Composable
private fun DestinationDialog(vm: AppViewModel, game: Game, pkg: GamePackage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val wantsRoot = pkg.kind != PackageKind.OTHER && CatalogBuilder.wantsStorageRoot(game.description)
    val hasAccess = Storage.hasAllFilesAccess(context)
    val gameDir = "/TopPorts/${FileNames.safeFolderName(game.title)}"
    val verb = if (pkg.kind == PackageKind.OTHER) "guardar" else "extraer"
    PixelDialog("¿Dónde $verb?", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wantsRoot) {
                Text("Las instrucciones del post piden la raíz de la memoria interna.", style = PxText.body.copy(color = Px.yellow))
            }
            if (!hasAccess) {
                Text("Para usar la raíz o /TopPorts, da permiso de acceso a todos los archivos.", style = PxText.small)
                PixelButton("Dar permiso", onClick = {
                    runCatching { context.startActivity(Storage.allFilesAccessIntent(context)) }
                }, color = Px.yellow, small = true, modifier = Modifier.fillMaxWidth())
            }
            val rootButton: @Composable () -> Unit = {
                PixelButton("Raíz de la memoria", onClick = {
                    onDismiss()
                    startInstall(vm, context, game, pkg, Storage.externalRoot)
                }, color = if (wantsRoot) Px.green else Px.blue, enabled = hasAccess, small = true, modifier = Modifier.fillMaxWidth())
            }
            val folderButton: @Composable () -> Unit = {
                PixelButton(gameDir, onClick = {
                    onDismiss()
                    startInstall(vm, context, game, pkg, Storage.gameDir(context, game.title))
                }, color = if (wantsRoot) Px.blue else Px.green, small = true, modifier = Modifier.fillMaxWidth())
            }
            // La opción recomendada va primero.
            if (wantsRoot) { rootButton(); folderButton() } else { folderButton(); rootButton() }
            PixelButton("Cancelar", onClick = onDismiss, color = Px.grey, small = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun download(vm: AppViewModel, context: Context, pkg: GamePackage) {
    vm.graph.tasks.download(pkg) { error ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
}

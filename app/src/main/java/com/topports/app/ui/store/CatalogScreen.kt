package com.topports.app.ui.store

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.topports.app.ui.components.PixelDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import com.topports.app.ui.SortMode
import java.text.Collator
import java.util.Locale
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTag
import com.topports.app.ui.components.PixelKeyboardDialog
import com.topports.app.ui.components.pixelCard
import com.topports.app.ui.components.keepVerticalFocus
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.PackageKind
import androidx.compose.runtime.produceState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(vm: AppViewModel) {
    val state by vm.catalog.collectAsStateWithLifecycle()
    val edgeScope = rememberCoroutineScope()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val installedIds = remember(installed) { installed.map { it.game.id }.toSet() }
    val context = LocalContext.current
    val query = vm.search.trim()
    val games = remember(state.games, query, vm.sortMode, vm.systemFilter) {
        val filter = vm.systemFilter
        val found = state.games.filter { game ->
            (filter == null || filter in game.tags) &&
                (
                    query.isEmpty() || game.title.contains(query, true) ||
                        game.description.contains(query, true) || game.tags.any { it.contains(query, true) }
                    )
        }
        // Orden alfabético en español: ignora tildes y mayúsculas, la Ñ va tras la N.
        val collator = Collator.getInstance(Locale("es", "ES")).apply { strength = Collator.PRIMARY }
        when (vm.sortMode) {
            SortMode.NEWEST -> found.sortedByDescending { it.id }
            // Última actividad: archivos añadidos después, avisos y ediciones del post.
            SortMode.UPDATED -> found.sortedWith(compareByDescending<Game> { it.updated }.thenByDescending { it.id })
            SortMode.ALPHABETICAL -> found.sortedWith { a, b -> collator.compare(a.title, b.title) }
            // Agrupados por su primera etiqueta; los que no tienen ninguna, al final.
            SortMode.SYSTEM -> found.sortedWith(
                compareBy<Game> { it.tags.isEmpty() }
                    .thenComparator { a, b -> collator.compare(a.tags.firstOrNull().orEmpty(), b.tags.firstOrNull().orEmpty()) }
                    .thenComparator { a, b -> collator.compare(a.title, b.title) },
            )
        }
    }
    // Sistemas del canal con cuántos juegos tiene cada uno, para el selector.
    val systems = remember(state.games) {
        state.games.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedBy { it.first }
    }

    // Al cambiar el orden, volver al principio de la lista.
    var shownSort by remember { mutableStateOf(vm.sortMode) }
    var shownFilter by remember { mutableStateOf(vm.systemFilter) }
    LaunchedEffect(vm.sortMode, vm.systemFilter) {
        if (vm.sortMode != shownSort || vm.systemFilter != shownFilter) {
            shownSort = vm.sortMode
            shownFilter = vm.systemFilter
            vm.catalogGrid.scrollToItem(0)
        }
    }

    // Con mando: al entrar (o al volver de una ficha) se selecciona la última carátula abierta.
    val requesters = remember { HashMap<Long, FocusRequester>() }
    fun requesterFor(id: Long) = requesters.getOrPut(id) { FocusRequester() }
    val targetId = vm.lastGameId?.takeIf { id -> games.any { it.id == id } } ?: games.firstOrNull()?.id

    suspend fun focusGame(index: Int) {
        val game = games.getOrNull(index) ?: return
        val visible = vm.catalogGrid.layoutInfo.visibleItemsInfo.map { it.index }
        if (index !in visible) vm.catalogGrid.scrollToItem(index)
        delay(40)
        runCatching { requesterFor(game.id).requestFocus() }
    }

    // También al cerrar el teclado de búsqueda, para seguir con la cruceta en los resultados.
    LaunchedEffect(targetId != null, vm.searchOpen) {
        if (targetId == null || vm.searchOpen) return@LaunchedEffect
        delay(40)
        focusGame(games.indexOfFirst { it.id == targetId })
    }

    // L1 / R1: una fila de juegos arriba o abajo, esté donde esté el foco.
    LaunchedEffect(vm.catalogJump) {
        val (rows, _) = vm.catalogJump ?: return@LaunchedEffect
        if (games.isEmpty()) return@LaunchedEffect
        val columns = (vm.catalogGrid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
        val current = vm.catalogFocusIndex
        val target = if (current in games.indices) (current + rows * columns).coerceIn(0, games.lastIndex)
        else vm.catalogGrid.firstVisibleItemIndex
        focusGame(target)
    }

    Column(Modifier.fillMaxSize()) {
        // Botón en lugar de campo de texto: así mover el mando no abre el teclado de Android.
        val searchButtons: @Composable RowScope.() -> Unit = {
            PixelButton(
                if (vm.search.isBlank()) "Buscar (Y)" else "Buscar: ${vm.search}",
                onClick = { vm.searchOpen = true },
                color = Px.navyDark,
                textColor = if (vm.search.isBlank()) Px.grey else Px.yellow,
                small = true,
                modifier = Modifier.weight(1f),
            )
            if (vm.search.isNotBlank()) {
                PixelButton("X", onClick = { vm.search = "" }, color = Px.red, textColor = Px.white, small = true)
            }
        }
        val filterButtons: @Composable () -> Unit = {
            PixelButton(
                "Orden: ${vm.sortMode.label} (X)",
                onClick = { vm.toggleSort() },
                color = Px.purple,
                textColor = Px.white,
                small = true,
            )
            if (systems.isNotEmpty()) {
                PixelButton(
                    "Sistema: ${vm.systemFilter ?: "Todos"} (Sel)",
                    onClick = { vm.systemPickerOpen = true },
                    color = if (vm.systemFilter == null) Px.navyDark else Px.yellow,
                    textColor = if (vm.systemFilter == null) Px.grey else Px.black,
                    small = true,
                )
            }
            if (!state.loading) {
                PixelButton("Recargar", onClick = { vm.refreshCatalog() }, color = Px.blue, small = true)
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                // Con el mando en los botones de arriba, la lista vuelve arriba del todo.
                .onFocusChanged { focus ->
                    if (focus.hasFocus) edgeScope.launch { vm.catalogGrid.animateScrollToItem(0) }
                },
        ) {
            if (maxWidth >= 600.dp) {
                // Horizontal: todo en una fila.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    searchButtons()
                    filterButtons()
                }
            } else {
                // Vertical: la búsqueda ocupa su propia fila y los demás botones van debajo.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        searchButtons()
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filterButtons()
                    }
                }
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

        if (vm.systemPickerOpen) {
            SystemPickerDialog(vm, systems, state.games.size)
        }

        if (vm.searchOpen) {
            PixelKeyboardDialog(
                title = "Buscar juego",
                value = vm.search,
                onValueChange = { vm.search = it },
                onDone = { vm.searchOpen = false },
            )
        }

        // Cuadrícula de carátulas: 2 columnas en un móvil en vertical, 3–5 en consolas en horizontal.
        // Con el mando, en la primera y la última fila se sube o baja del todo, igual que con el dedo
        // (si no, la lista se queda a unos píxeles del borde y no se ve que se ha llegado al final).
        fun scrollToEdge(index: Int) {
            val columns = (vm.catalogGrid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
            val lastRowStart = games.lastIndex - games.lastIndex % columns
            val target = when {
                index < columns -> 0
                index >= lastRowStart -> games.lastIndex
                else -> return
            }
            edgeScope.launch {
                delay(30) // después del desplazamiento automático del foco
                vm.catalogGrid.animateScrollToItem(target)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            state = vm.catalogGrid,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Abajo en la última fila no salta al menú lateral; arriba sí sube a Buscar/Orden.
            modifier = Modifier.fillMaxSize().keepVerticalFocus(blockUp = false),
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                GameCard(
                    game,
                    installed = game.id in installedIds,
                    onClick = { vm.openGame(game.id) },
                    modifier = Modifier
                        .focusRequester(requesterFor(game.id))
                        .onFocusChanged {
                            if (it.isFocused) {
                                vm.catalogFocusIndex = index
                                scrollToEdge(index)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, installed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            if (installed) PixelTag("INSTALADO", Px.green, Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (recentlyUpdated(game)) PixelTag("ACTUALIZADO", Px.yellow, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        // Etiquetas de sistema junto al título; la fila ocupa siempre lo mismo para que las
        // carátulas queden alineadas.
        Row(Modifier.heightIn(min = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            game.tags.take(3).forEach { SystemTag(it) }
        }
        Spacer(Modifier.height(4.dp))
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

/** Actualizado (no solo publicado) en las dos últimas semanas. */
fun recentlyUpdated(game: Game): Boolean {
    val now = System.currentTimeMillis() / 1000
    return game.updated - game.date > 86_400 && now - game.updated < 14 * 86_400
}

/** Etiqueta de sistema ("#N64"), con un color fijo para cada sistema. */
@Composable
fun SystemTag(tag: String, modifier: Modifier = Modifier) {
    PixelTag("#$tag", systemColor(tag), modifier)
}

fun systemColor(tag: String): Color {
    val colors = listOf(Px.blue, Px.pink, Px.orange, Px.lavender, Px.green, Px.yellow)
    return colors[Math.floorMod(tag.hashCode(), colors.size)]
}

/** Elegir sistema: todos o solo los juegos con una etiqueta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemPickerDialog(vm: AppViewModel, systems: List<Pair<String, Int>>, total: Int) {
    val close = { vm.systemPickerOpen = false }
    PixelDialog("Sistema", onDismiss = close, wide = true, widthFraction = 0.8f) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    "Todos ($total)",
                    onClick = { vm.systemFilter = null; close() },
                    color = if (vm.systemFilter == null) Px.orange else Px.navyDark,
                    textColor = if (vm.systemFilter == null) Px.black else Px.white,
                    small = true,
                )
                systems.forEach { (tag, count) ->
                    PixelButton(
                        "#$tag ($count)",
                        onClick = { vm.systemFilter = tag; close() },
                        color = if (vm.systemFilter == tag) Px.orange else systemColor(tag),
                        textColor = Px.black,
                        small = true,
                    )
                }
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

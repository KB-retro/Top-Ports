package com.topports.app.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Tab
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.store.CoverImage
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.FileNames

/** Apps instaladas con una versión más nueva publicada en el canal. */
@Composable
fun UpdatesScreen(vm: AppViewModel) {
    val updates by vm.updates.collectAsStateWithLifecycle()
    // Al volver a la app (p. ej. tras instalar), comprobar otra vez.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.graph.updates.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(if (updates.isEmpty()) "Todo al día" else "${updates.size} actualizaciones", Px.green)
                }
                PixelButton("Comprobar", onClick = { vm.graph.updates.refresh() }, color = Px.blue, small = true)
            }
        }
        if (updates.isEmpty()) {
            item {
                Text(
                    "Aquí aparecerán las apps que tienes instaladas cuando el canal publique una versión nueva. " +
                        "Se detectan las instaladas desde Top Ports y las que tienen el mismo nombre y versión " +
                        "en el canal.",
                    style = PxText.small,
                )
            }
        }
        items(updates, key = { it.packageName }) { update ->
            PixelPanel(Modifier.fillMaxWidth(), border = Px.green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 96.dp, height = 54.dp).background(Px.black)) {
                        update.game.images.firstOrNull()?.let { CoverImage(it.thumbFileId, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(update.game.title.uppercase(), style = PxText.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${update.appLabel}: ${update.installedVersion ?: "instalada"} → ${update.newVersion ?: "nueva versión"}",
                            style = PxText.body.copy(color = Px.green),
                        )
                        Text("${update.pkg.name} · ${FileNames.formatSize(update.pkg.size)}", style = PxText.small)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton("Ver", onClick = {
                            vm.tab = Tab.STORE
                            vm.openGame(update.game.id)
                        }, color = Px.green, small = true, modifier = Modifier.fillMaxWidth())
                        // Por si el aviso no es correcto: no volver a avisar de este archivo.
                        PixelButton("Ignorar", onClick = {
                            vm.graph.installs.ignoreUpdate(update.packageName, update.pkg)
                            vm.graph.updates.refresh()
                        }, color = Px.darkGrey, textColor = Px.white, small = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

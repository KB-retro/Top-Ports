package com.topports.app.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Tab
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.SectionTitle
import com.topports.app.ui.components.pixelScrollbar
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.Announcement
import com.topports.core.Announcements
import com.topports.core.Game

/**
 * Aviso de novedades del canal ("ACTUALIZACIÓN DE PORTS"). Sale una vez al abrir la app; al
 * cerrarlo no vuelve hasta que se publique otro. Los ports de la lista que están en la tienda
 * tienen un botón para abrir su ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnnouncementDialog(vm: AppViewModel, announcement: Announcement, games: List<Game>) {
    val listed = remember(announcement, games) { Announcements.gamesIn(announcement, games) }
    val close = { vm.dismissAnnouncement(announcement.id) }
    val scroll = rememberScrollState()
    PixelDialog(announcement.title.ifBlank { "Novedades" }, onDismiss = close, wide = true, widthFraction = 0.8f) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .pixelScrollbar(scroll)
                .verticalScroll(scroll),
        ) {
            Text(announcement.body, style = PxText.body)
            if (listed.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionTitle("Ver en la tienda", Px.green)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listed.forEach { game ->
                        PixelButton(game.title, onClick = {
                            close()
                            vm.tab = Tab.STORE
                            vm.openGame(game.id)
                        }, color = Px.navyDark, textColor = Px.white, small = true)
                    }
                }
            }
        }
        DialogButtons {
            PixelButton("Entendido", onClick = close, color = Px.green, small = true)
        }
    }
}

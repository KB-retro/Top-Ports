package com.topports.app.ui.store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.pixelScrollbar
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.Announcement

/**
 * Aviso de novedades del canal ("ACTUALIZACIÓN DE PORTS"). Sale una vez al abrir la app; al
 * cerrarlo no vuelve hasta que se publique otro.
 */
@Composable
fun AnnouncementDialog(vm: AppViewModel, announcement: Announcement) {
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
        }
        DialogButtons {
            PixelButton("Entendido", onClick = close, color = Px.green, small = true)
        }
    }
}

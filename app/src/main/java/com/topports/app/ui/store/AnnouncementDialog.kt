package com.topports.app.ui.store

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.Gamepad
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.components.pixelScrollbar
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.core.Announcement
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aviso de novedades del canal ("ACTUALIZACIÓN DE PORTS"). Sale una vez al abrir la app; al
 * cerrarlo no vuelve hasta que se publique otro. Con el mando, el texto se desplaza como la
 * descripción de los ports: L1/R1 o la cruceta a saltos y el joystick derecho de forma continua.
 */
@Composable
fun AnnouncementDialog(vm: AppViewModel, announcement: Announcement) {
    val close = { vm.dismissAnnouncement(announcement.id) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val accept = remember { FocusRequester() }
    val density = LocalDensity.current
    val step = with(density) { 120.dp.toPx() }
    val stickSpeed = with(density) { 900.dp.toPx() }

    // Con el botón seleccionado desde el principio, el mando funciona sin tocar la pantalla.
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { accept.requestFocus() }
    }
    // Joystick derecho: desplazamiento continuo, más rápido cuanto más se empuja.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val v = Gamepad.dialogStick
                if (v != 0f) scroll.dispatchRawDelta(v * abs(v) * stickSpeed * dt)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { Gamepad.dialogStick = 0f } }

    PixelDialog(announcement.title.ifBlank { "Novedades" }, onDismiss = close, wide = true, widthFraction = 0.8f) {
        Column(
            Modifier
                .weight(1f, fill = false)
                // L1/R1 llegan como arriba/abajo: desplazan el texto en vez de mover la selección.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val dir = when (event.key) {
                        Key.DirectionDown -> 1
                        Key.DirectionUp -> -1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.nativeKeyEvent.repeatCount > 0) {
                        // Manteniendo pulsado: sigue bajando sin saltos.
                        scroll.dispatchRawDelta(dir * step / 3)
                    } else {
                        scope.launch { scroll.animateScrollBy(dir * step, tween(160, easing = LinearEasing)) }
                    }
                    true
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Lo que sobre tras el título y el botón: el botón siempre se ve entero.
                    .weight(1f, fill = false)
                    .pixelScrollbar(scroll)
                    .verticalScroll(scroll),
            ) {
                Text(announcement.body, style = PxText.body)
            }
            DialogButtons {
                Text("L1/R1 · STICK: DESPLAZAR", style = PxText.tiny.copy(color = Px.darkGrey))
                PixelButton("Entendido", onClick = close, color = Px.green, small = true, modifier = Modifier.focusRequester(accept))
            }
        }
    }
}

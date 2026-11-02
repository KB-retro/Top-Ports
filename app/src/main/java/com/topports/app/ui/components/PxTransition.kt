package com.topports.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.UiAnim

/**
 * Cambia de pantalla con la animación elegida en Ajustes → Apariencia ([Px.anim]). [order] da
 * la dirección: si la pantalla nueva va "después" (pestaña de más abajo, abrir una ficha), la
 * animación avanza hacia la derecha; si va antes, hacia la izquierda.
 *
 * Fundido y Deslizar usan las transiciones de Compose. Cortinilla, Persiana y Píxeles dejan la
 * pantalla anterior quieta debajo y van descubriendo la nueva encima con una máscara; la nueva
 * lleva su propio fondo ([pxBackdrop]) para tapar la anterior.
 */
@Composable
fun <T> PxTransition(
    target: T,
    modifier: Modifier = Modifier,
    order: (T) -> Int = { 0 },
    content: @Composable (T) -> Unit,
) {
    val anim = Px.anim
    // Dirección del último cambio (se calcula al cambiar de pantalla, no en cada redibujado).
    val memo = remember { object { var previous = target; var dir = 1 } }
    if (target != memo.previous) {
        memo.dir = if (order(target) >= order(memo.previous)) 1 else -1
        memo.previous = target
    }
    AnimatedContent(
        targetState = target,
        // Nada de la animación sale de su zona: el menú lateral nunca queda tapado.
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val dir = memo.dir
            when (anim) {
                UiAnim.NONE -> EnterTransition.None togetherWith ExitTransition.None
                UiAnim.FADE -> fadeIn(tween(240, delayMillis = 40)) togetherWith fadeOut(tween(180))
                UiAnim.SLIDE ->
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * dir / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * dir / 3 } + fadeOut(tween(220)))
                // La anterior se queda hasta que la nueva termina de aparecer encima.
                else -> (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
                    .apply { targetContentZIndex = 1f }
            }.using(SizeTransform(clip = false))
        },
        label = "pxTransition",
    ) { state ->
        if (anim == UiAnim.NONE || anim == UiAnim.FADE || anim == UiAnim.SLIDE) {
            content(state)
            return@AnimatedContent
        }
        val progress by transition.animateFloat(
            transitionSpec = {
                when (anim) {
                    UiAnim.WIPE -> tween(380, easing = FastOutSlowInEasing)
                    else -> tween(440, easing = LinearEasing)
                }
            },
            label = "reveal",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val entering = transition.targetState == EnterExitState.Visible
        val dir = memo.dir
        Box(
            Modifier
                .fillMaxSize()
                .pxBackdrop()
                .drawWithContent {
                    if (!entering || progress >= 1f) {
                        drawContent()
                    } else {
                        when (anim) {
                            UiAnim.WIPE -> drawWipe(progress, dir)
                            UiAnim.BLINDS -> drawBlinds(progress)
                            else -> drawPixels(progress)
                        }
                    }
                },
        ) { content(state) }
    }
}

/** Cortinilla: lo nuevo aparece detrás de una barra de color que cruza la pantalla. */
private fun ContentDrawScope.drawWipe(p: Float, dir: Int) {
    val edge = size.width * p
    val left = if (dir > 0) 0f else size.width - edge
    clipRect(left, 0f, left + edge, size.height) { this@drawWipe.drawContent() }
    val bar = 8.dp.toPx()
    val x = if (dir > 0) edge else size.width - edge
    // Barra con estela: el borde de la cortinilla.
    drawRect(Px.orange, Offset(x - bar / 2, 0f), Size(bar, size.height))
    drawRect(Px.yellow.copy(alpha = 0.5f), Offset(x - bar / 2 - dir * bar, 0f), Size(bar, size.height))
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(x + bar / 2 * dir - if (dir > 0) 0f else 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
}

/** Persiana: franjas horizontales que se abren de arriba abajo, cada una un poco después. */
private fun ContentDrawScope.drawBlinds(p: Float) {
    val stripes = 10
    val h = size.height / stripes
    val path = Path()
    for (i in 0 until stripes) {
        val local = ((p * 1.3f) - i * 0.03f).coerceIn(0f, 1f)
        if (local > 0f) path.addRect(Rect(0f, i * h, size.width, i * h + h * local))
    }
    clipPath(path) { this@drawBlinds.drawContent() }
}

/** Píxeles: bloques que aparecen en orden aleatorio (fijo), como un fundido de consola de 8 bits. */
private fun ContentDrawScope.drawPixels(p: Float) {
    val cell = 28.dp.toPx()
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    val path = Path()
    for (y in 0 until rows) for (x in 0 until cols) {
        if (blockOrder(x, y) < p) path.addRect(Rect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell))
    }
    clipPath(path) { this@drawPixels.drawContent() }
}

/** Número fijo entre 0 y 1 para cada bloque: el orden en que aparece. */
private fun blockOrder(x: Int, y: Int): Float {
    var h = x * 73856093 xor y * 19349663
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65536f
}

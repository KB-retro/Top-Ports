package com.topports.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import com.topports.app.ui.GamepadWindowCallback
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText

/** Marco de píxel: borde de 1 "píxel gordo" sin esquinas y sombra dura desplazada. */
fun Modifier.pixelFrame(
    fill: Color = Px.navy,
    border: Color = Px.white,
    shadow: Color? = Px.black,
    px: Dp = 3.dp,
): Modifier = this
    .padding(end = if (shadow != null) px else 0.dp, bottom = if (shadow != null) px else 0.dp)
    .drawBehind {
        val p = px.toPx()
        if (shadow != null) drawPixelBox(shadow, shadow, p, Offset(p, p))
        drawPixelBox(border, fill, p, Offset.Zero)
    }
    .padding(px)

private fun DrawScope.drawPixelBox(border: Color, fill: Color, p: Float, at: Offset) {
    val w = size.width
    val h = size.height
    // Relleno interior
    drawRect(fill, at + Offset(p, p), Size(w - 2 * p, h - 2 * p))
    // Bordes sin las 4 esquinas: da el aspecto redondeado de los juegos de 8 bits.
    drawRect(border, at + Offset(p, 0f), Size(w - 2 * p, p))
    drawRect(border, at + Offset(p, h - p), Size(w - 2 * p, p))
    drawRect(border, at + Offset(0f, p), Size(p, h - 2 * p))
    drawRect(border, at + Offset(w - p, p), Size(p, h - 2 * p))
}

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    fill: Color = Px.navy,
    border: Color = Px.white,
    content: @Composable () -> Unit,
) {
    Box(modifier.pixelFrame(fill, border)) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Px.orange,
    textColor: Color = Px.black,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Con mando o cruceta, el botón seleccionado se marca en amarillo.
    val focused by interaction.collectIsFocusedAsState()
    val px = 3.dp
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            // Al pulsar, el botón "baja" y tapa su sombra.
            .offset(x = if (pressed) px else 0.dp, y = if (pressed) px else 0.dp)
            .pixelFrame(
                fill = color,
                border = if (focused) Px.yellow else Px.black,
                shadow = if (pressed) null else if (focused) Px.yellow else Px.black,
                px = px,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = (if (small) PxText.tiny else PxText.label).copy(color = textColor),
            textAlign = TextAlign.Center,
        )
    }
}

/** Barra de progreso por bloques. */
@Composable
fun PixelProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = Px.green,
    blocks: Int = 20,
) {
    val transition = rememberInfiniteTransition(label = "progress")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = blocks.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = StepsEasing(blocks)), RepeatMode.Restart),
        label = "scan",
    )
    Box(modifier.height(18.dp).pixelFrame(Px.night, Px.white, shadow = null, px = 2.dp)) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (blocks - 1)) / blocks
            for (i in 0 until blocks) {
                val on = if (progress == null) {
                    // Indeterminado: tres bloques que recorren la barra.
                    val d = (i - scan.toInt() + blocks) % blocks
                    d < 3
                } else {
                    i < (progress.coerceIn(0f, 1f) * blocks + 0.001f).toInt()
                }
                drawRect(
                    if (on) color else Px.navyDark,
                    Offset(i * (bw + gap), 0f),
                    Size(bw, size.height),
                )
            }
        }
    }
}

private class StepsEasing(private val steps: Int) : androidx.compose.animation.core.Easing {
    override fun transform(fraction: Float): Float = (fraction * steps).toInt() / steps.toFloat()
}

@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Box(modifier.pixelFrame(Px.night, Px.white, shadow = Px.black)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PxText.body,
            cursorBrush = SolidColor(Px.orange),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = PxText.body.copy(color = Px.darkGrey))
                inner()
            },
        )
    }
}

/** Etiqueta de color tipo cartucho: "APK", "7Z", "RAR"… */
@Composable
fun PixelTag(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Px.black) {
    Box(
        modifier
            .background(color)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PxText.tiny.copy(color = textColor))
    }
}

/** Texto que parpadea como "PRESS START". */
@Composable
fun BlinkingText(text: String, style: TextStyle = PxText.label, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blink")
    val visible by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = StepsEasing(2)), RepeatMode.Restart),
        label = "blinkAlpha",
    )
    Text(text, style = style, modifier = modifier.alpha(if (visible > 0.5f) 1f else 0f))
}

@Composable
fun PixelDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        // Los diálogos son otra ventana: también ahí A acepta y B cierra.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                val callback = window.callback
                if (callback != null && callback !is GamepadWindowCallback) {
                    window.callback = GamepadWindowCallback(callback)
                }
            }
        }
        PixelPanel(fill = Px.navy, border = Px.orange) {
            Column {
                Text(title.uppercase(), style = PxText.heading.copy(color = Px.orange))
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Fila de botones de diálogo alineados a la derecha. */
@Composable
fun DialogButtons(content: @Composable () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.End)) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, color: Color = Px.yellow, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = PxText.label.copy(color = color))
    }
}

/** Clic sin efecto de onda (la onda no pega con el estilo pixel). */
fun Modifier.pixelClickable(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = onClick)

/**
 * Tarjeta o fila pulsable con marco de píxel. Al recorrerla con la cruceta del mando
 * el marco y la sombra se vuelven amarillos, para saber siempre qué está seleccionado.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pixelCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    fill: Color = Px.navy,
    border: Color = Px.white,
    shadow: Color? = Px.black,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    this
        .pixelFrame(
            fill = fill,
            border = if (focused) Px.yellow else border,
            shadow = if (focused) Px.yellow else shadow,
        )
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

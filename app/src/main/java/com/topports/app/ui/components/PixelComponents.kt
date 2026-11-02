package com.topports.app.ui.components

import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import com.topports.app.ui.GamepadWindowCallback
import com.topports.app.ui.RetroSound
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.topports.app.ui.theme.Backdrop
import com.topports.app.ui.theme.FocusPulse
import com.topports.app.ui.theme.FrameKind
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.app.ui.theme.UiStyle
import com.topports.app.ui.theme.pxCaps

/**
 * Recuadro de panel, botón o tarjeta, dibujado según el estilo de interfaz ([Px.style]): en
 * Pixel, borde de 1 "píxel gordo" sin esquinas y sombra dura desplazada; en los demás, doble
 * línea, cristal, bloque plano o ventana de RPG. [focused] es el elemento seleccionado con el
 * mando: además del borde, lleva un tinte y un anillo exterior que late (ver [drawFocusRing]).
 */
fun Modifier.pixelFrame(
    fill: Color = Px.navy,
    border: Color = Px.white,
    shadow: Color? = Px.black,
    px: Dp = 3.dp,
    focused: Boolean = false,
    style: UiStyle = Px.style,
): Modifier {
    val spec = style.spec
    val focus = Px.focus
    // Un poco del color de selección en el relleno: se nota aunque la paleta sea de un solo tono.
    val fill = if (focused) lerp(fill, focus, 0.22f) else fill
    if (spec.frame == FrameKind.PIXEL) {
        return this
            .padding(end = if (shadow != null) px else 0.dp, bottom = if (shadow != null) px else 0.dp)
            .drawBehind {
                val p = px.toPx()
                if (shadow != null) drawPixelBox(shadow, shadow, p, Offset(p, p))
                drawPixelBox(border, fill, p, Offset.Zero)
                if (focused) drawFocusRing(focus, size, 0f)
            }
            .padding(px)
    }
    // Sombra solo en los estilos que la tienen (RPG); un botón pulsado no la lleva.
    val drop = if (spec.shadow && shadow != null) 3.dp else 0.dp
    val inset = frameInset(style)
    return this
        .drawBehind {
            val d = drop.toPx()
            val box = Size(size.width - d, size.height - d)
            if (d > 0f) drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(d, d), box, CornerRadius(spec.radius.toPx()))
            when (spec.frame) {
                FrameKind.DOUBLE_LINE -> drawDoubleLine(fill, border, spec.stroke.toPx(), box)
                FrameKind.GLASS -> drawGlass(fill, border, focused, spec.stroke.toPx(), spec.radius.toPx(), box)
                FrameKind.FLAT -> drawFlat(fill, border, focused, spec.stroke.toPx(), spec.radius.toPx(), box)
                FrameKind.WINDOW -> drawWindow(fill, border, spec.stroke.toPx(), spec.radius.toPx(), box)
                FrameKind.NEON -> drawNeon(fill, border, spec.stroke.toPx(), spec.radius.toPx(), box)
                FrameKind.BEVEL -> drawBevel(fill, spec.stroke.toPx(), box)
                FrameKind.KEYCAP -> drawKeycap(fill, border, spec.stroke.toPx(), spec.radius.toPx(), box)
                FrameKind.PIXEL -> Unit
            }
            if (focused) drawFocusRing(focus, box, spec.radius.toPx())
        }
        .padding(
            start = inset,
            top = inset,
            end = inset + drop,
            bottom = inset + drop + if (spec.frame == FrameKind.KEYCAP) KEYCAP_LIP else 0.dp,
        )
}

/**
 * Anillo de lo seleccionado, por fuera del recuadro: una línea oscura que lo separa de lo que
 * haya debajo y otra gruesa del color de selección que late. Así el cursor se ve con cualquier
 * paleta, también en las de un solo tono (Game Boy, GB DMG, Virtual Boy), y con cualquier tema.
 */
private fun DrawScope.drawFocusRing(color: Color, box: Size, radius: Float) {
    val gap = 2.dp.toPx()
    val ring = 3.dp.toPx()
    val pulse = FocusPulse.value
    // Separación oscura pegada al recuadro.
    val g = gap / 2
    drawRoundRect(
        Color.Black.copy(alpha = 0.85f), Offset(-g, -g), Size(box.width + gap, box.height + gap),
        CornerRadius(if (radius > 0f) radius + g else 0f), style = Stroke(gap),
    )
    // Anillo de color: siempre bien visible, y más intenso y algo más ancho al latir.
    val w = ring * (0.8f + 0.4f * pulse)
    val o = gap + w / 2
    drawRoundRect(
        color.copy(alpha = 0.6f + 0.4f * pulse), Offset(-o, -o), Size(box.width + 2 * o, box.height + 2 * o),
        CornerRadius(if (radius > 0f) radius + o else 0f), style = Stroke(w),
    )
}

/** Grosor del borde de cada estilo: lo que el contenido se separa del filo del recuadro. */
private fun frameInset(style: UiStyle): Dp {
    val spec = style.spec
    return when (spec.frame) {
        FrameKind.PIXEL -> 3.dp
        FrameKind.DOUBLE_LINE -> spec.stroke * 2 + 2.dp
        FrameKind.GLASS, FrameKind.FLAT -> spec.stroke
        FrameKind.WINDOW -> spec.stroke + 2.dp
        FrameKind.NEON -> spec.stroke + 1.dp
        FrameKind.BEVEL -> spec.stroke + 1.dp
        // El canto de abajo de la tecla ocupa más que el resto del borde.
        FrameKind.KEYCAP -> spec.stroke
    }
}

/** Lo que el canto inferior de una tecla (Cartucho) añade por abajo. */
private val KEYCAP_LIP = 4.dp

/**
 * Neón: los fondos oscuros se vuelven translúcidos y el borde brilla (tres trazos: halo ancho,
 * medio y el tubo). Los botones de color conservan su relleno y brillan de su color.
 */
private fun DrawScope.drawNeon(fill: Color, border: Color, t: Float, r: Float, box: Size) {
    val dark = fill.luminance() < 0.3f
    val tube = when {
        !dark -> fill
        border == Px.white -> Px.blue
        border == Px.black -> Px.lavender
        else -> border
    }
    drawRoundRect(if (dark) fill.copy(alpha = 0.45f) else fill, Offset.Zero, box, CornerRadius(r))
    val inset = Offset(t / 2, t / 2)
    val inner = Size(box.width - t, box.height - t)
    drawRoundRect(tube.copy(alpha = 0.12f), inset, inner, CornerRadius(r), style = Stroke(t * 6))
    drawRoundRect(tube.copy(alpha = 0.28f), inset, inner, CornerRadius(r), style = Stroke(t * 3))
    drawRoundRect(lerp(tube, Color.White, 0.35f), inset, inner, CornerRadius(r), style = Stroke(t))
}

/** Relieve: línea negra, luz arriba a la izquierda, sombra abajo a la derecha. */
private fun DrawScope.drawBevel(fill: Color, t: Float, box: Size) {
    val line = 1.dp.toPx()
    drawRect(Color.Black, Offset.Zero, box)
    val w = box.width - 2 * line
    val h = box.height - 2 * line
    val o = Offset(line, line)
    drawRect(lerp(fill, Color.Black, 0.5f), o, Size(w, h))
    drawRect(lerp(fill, Color.White, 0.45f), o, Size(w - t, h - t))
    drawRect(fill, o + Offset(t, t), Size(w - 2 * t, h - 2 * t))
}

/** Tecla: canto inferior oscuro, cara con brillo arriba y, si el borde es de color, un filo de ese color. */
private fun DrawScope.drawKeycap(fill: Color, border: Color, t: Float, r: Float, box: Size) {
    val lip = KEYCAP_LIP.toPx()
    val face = Size(box.width, box.height - lip)
    drawRoundRect(lerp(fill, Color.Black, 0.45f), Offset(0f, lip), face, CornerRadius(r))
    drawRoundRect(fill, Offset.Zero, face, CornerRadius(r))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = face.height * 0.5f),
        Offset.Zero, face, CornerRadius(r),
    )
    if (border != Px.white && border != Px.black) {
        drawRoundRect(border, Offset(t / 2, t / 2), Size(face.width - t, face.height - t), CornerRadius(r), style = Stroke(t))
    }
}

/** Doble línea fina y esquinas rectas (menú de texto). */
private fun DrawScope.drawDoubleLine(fill: Color, border: Color, t: Float, box: Size) {
    drawRect(fill, Offset.Zero, box)
    val gap = 2.dp.toPx()
    drawRect(border, Offset(t / 2, t / 2), Size(box.width - t, box.height - t), style = Stroke(t))
    val i = t + gap + t / 2
    drawRect(border, Offset(i, i), Size(box.width - 2 * i, box.height - 2 * i), style = Stroke(t))
}

/** Cristal: los fondos oscuros se vuelven translúcidos; al seleccionarlo, borde y halo. */
private fun DrawScope.drawGlass(fill: Color, border: Color, focused: Boolean, t: Float, r: Float, box: Size) {
    val glass = if (fill.luminance() < 0.3f) fill.copy(alpha = 0.55f) else fill
    drawRoundRect(glass, Offset.Zero, box, CornerRadius(r))
    // Brillo arriba, como el cristal de XMB.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = box.height * 0.6f),
        Offset.Zero, box, CornerRadius(r),
    )
    val w = if (focused) t * 2 else t
    drawRoundRect(
        if (focused) border else border.copy(alpha = 0.3f),
        Offset(w / 2, w / 2), Size(box.width - w, box.height - w), CornerRadius(r), style = Stroke(w),
    )
}

/** Bloque plano: sin borde; seleccionado, borde de color; con borde de color, barra de acento. */
private fun DrawScope.drawFlat(fill: Color, border: Color, focused: Boolean, t: Float, r: Float, box: Size) {
    drawRoundRect(fill, Offset.Zero, box, CornerRadius(r))
    when {
        focused -> drawRoundRect(border, Offset(t / 2, t / 2), Size(box.width - t, box.height - t), CornerRadius(r), style = Stroke(t))
        border != Px.white && border != Px.black ->
            drawRoundRect(border, Offset.Zero, Size(t * 2, box.height), CornerRadius(r))
    }
}

/** Ventana de RPG: línea oscura, borde de color, línea oscura y relleno en degradado. */
private fun DrawScope.drawWindow(fill: Color, border: Color, t: Float, r: Float, box: Size) {
    val line = 1.dp.toPx()
    val dark = Color.Black.copy(alpha = 0.8f)
    drawRoundRect(dark, Offset.Zero, box, CornerRadius(r))
    drawRoundRect(border, Offset(line, line), Size(box.width - 2 * line, box.height - 2 * line), CornerRadius(r - line))
    val i = line + t
    drawRoundRect(dark, Offset(i, i), Size(box.width - 2 * i, box.height - 2 * i), CornerRadius((r - i).coerceAtLeast(0f)))
    val j = i + line
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.16f), lerp(fill, Color.Black, 0.3f)), startY = j, endY = box.height - j),
        Offset(j, j), Size(box.width - 2 * j, box.height - 2 * j), CornerRadius((r - j).coerceAtLeast(0f)),
    )
}

/**
 * Fondo de la app según el estilo: liso, líneas de barrido (Terminal), ondas animadas (Ondas)
 * o degradado (RPG).
 */
fun Modifier.pxBackdrop(): Modifier = composed {
    val night = Px.night
    when (Px.style.spec.backdrop) {
        Backdrop.NONE -> background(night)
        Backdrop.SCANLINES -> {
            val brush = tileBrush(3.dp) { cell -> drawRect(Color.Black.copy(alpha = 0.35f), Offset.Zero, Size(cell, 1.dp.toPx())) }
            background(night).background(brush)
        }
        Backdrop.GRADIENT -> background(Brush.verticalGradient(listOf(Px.navyDark, night, Px.black)))
        // Rejilla de recreativa: horizonte y líneas que se pierden hacia el fondo.
        Backdrop.GRID -> {
            val line = Px.pink
            background(Brush.verticalGradient(listOf(Px.black, night, Px.navyDark))).drawBehind {
                val horizon = size.height * 0.55f
                val c = line.copy(alpha = 0.22f)
                val w = 1.dp.toPx()
                var gap = 6.dp.toPx()
                var y = horizon
                while (y < size.height) {
                    drawRect(c, Offset(0f, y), Size(size.width, w))
                    y += gap
                    gap *= 1.35f
                }
                val cx = size.width / 2
                for (i in -12..12) {
                    drawLine(c, Offset(cx + i * size.width / 24f, horizon), Offset(cx + i * size.width / 4f, size.height), w)
                }
                drawRect(line.copy(alpha = 0.45f), Offset(0f, horizon), Size(size.width, 2.dp.toPx()))
            }
        }
        // Trama de puntos alternos, como los fondos de los escritorios antiguos.
        Backdrop.DITHER -> {
            val c = Px.navy.copy(alpha = 0.55f)
            val brush = tileBrush(4.dp, c) { cell ->
                val half = cell / 2
                drawRect(c, Offset.Zero, Size(half, half))
                drawRect(c, Offset(half, half), Size(half, half))
            }
            background(night).background(brush)
        }
        Backdrop.DOTS -> {
            val c = Px.lavender.copy(alpha = 0.18f)
            val brush = tileBrush(18.dp, c) { cell -> drawCircle(c, 1.5.dp.toPx(), Offset(cell / 2, cell / 2)) }
            background(night).background(brush)
        }
        Backdrop.WAVES -> {
            val transition = rememberInfiniteTransition(label = "waves")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
                label = "wavePhase",
            )
            val top = Px.navyDark
            val colors = listOf(Px.lavender, Px.blue, Px.pink)
            background(Brush.verticalGradient(listOf(top, night))).drawBehind {
                colors.forEachIndexed { k, c -> drawWave(c.copy(alpha = 0.10f + 0.04f * k), phase * (1 + k * 0.35f) + k * 1.7f, 0.55f + k * 0.08f, k) }
            }
        }
    }
}

/**
 * Patrón pequeño que se repite por todo el fondo. Se dibuja una vez en una imagen de [tile] y
 * se pinta con un sombreador: el fondo entero cuesta un solo rectángulo aunque se redibuje
 * muchas veces (el latido de la selección, el progreso…).
 */
@Composable
private fun tileBrush(tile: Dp, vararg keys: Any, draw: DrawScope.(cell: Float) -> Unit): Brush {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density, tile, *keys) {
        val px = with(density) { tile.toPx() }.let { kotlin.math.round(it).toInt() }.coerceAtLeast(2)
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(px, px)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density,
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bitmap),
            Size(px.toFloat(), px.toFloat()),
        ) { draw(px.toFloat()) }
        androidx.compose.ui.graphics.ShaderBrush(
            androidx.compose.ui.graphics.ImageShader(
                bitmap,
                androidx.compose.ui.graphics.TileMode.Repeated,
                androidx.compose.ui.graphics.TileMode.Repeated,
            ),
        )
    }
}

/** Una onda suave que cruza la pantalla, como el fondo de XMB. */
private fun DrawScope.drawWave(color: Color, phase: Float, baseline: Float, k: Int) {
    val amp = size.height * (0.06f + 0.02f * k)
    val y0 = size.height * baseline
    val path = Path()
    val step = 12.dp.toPx()
    var x = 0f
    path.moveTo(0f, y0)
    while (x <= size.width + step) {
        val t = x / size.width * (2 * Math.PI).toFloat() * (1.2f + 0.3f * k)
        path.lineTo(x, y0 + amp * kotlin.math.sin(t + phase))
        x += step
    }
    // Banda: la misma curva un poco más abajo, de vuelta.
    while (x >= -step) {
        val t = x / size.width * (2 * Math.PI).toFloat() * (1.2f + 0.3f * k)
        path.lineTo(x, y0 + amp * 0.6f + amp * kotlin.math.sin(t + phase * 1.1f + 0.8f))
        x -= step
    }
    path.close()
    drawPath(path, color)
}

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
    textStyle: TextStyle? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Con mando o cruceta, el botón seleccionado se marca en amarillo.
    val focused by interaction.collectIsFocusedAsState()
    val px = 3.dp
    val sinks = Px.style.spec.shadow
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            // Al pulsar, el botón "baja" y tapa su sombra (en los estilos con sombra).
            .offset(x = if (pressed && sinks) px else 0.dp, y = if (pressed && sinks) px else 0.dp)
            .alpha(if (pressed && !sinks) 0.75f else 1f)
            .pixelFrame(
                fill = color,
                border = if (focused) Px.focus else Px.black,
                shadow = if (pressed) null else if (focused) Px.focus else Px.black,
                px = px,
                focused = focused,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                RetroSound.play(RetroSound.Kind.ACCEPT)
                onClick()
            }
            .padding(horizontal = Px.style.spec.buttonPad + if (small) 0.dp else 4.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.pxCaps(),
            style = (textStyle ?: if (small) PxText.tiny else PxText.label).copy(color = textColor),
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
    if (!Px.style.spec.blocks) {
        SmoothProgress(progress, modifier, color)
        return
    }
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

/** Barra continua (Ondas, Plano, RPG). Indeterminada: un tramo que la recorre. */
@Composable
private fun SmoothProgress(progress: Float?, modifier: Modifier, color: Color) {
    val spec = Px.style.spec
    val transition = rememberInfiniteTransition(label = "smoothProgress")
    val sweep by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val window = spec.frame == FrameKind.WINDOW
    val track = Px.navyDark
    val edge = Px.white
    Canvas(modifier.height(if (window) 14.dp else 8.dp)) {
        val r = CornerRadius(if (window) 3.dp.toPx() else size.height / 2)
        val inset = if (window) 2.dp.toPx() else 0f
        if (window) {
            drawRoundRect(Color.Black.copy(alpha = 0.8f), cornerRadius = r)
            drawRoundRect(edge, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), r, style = Stroke(1.dp.toPx()))
        }
        val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
        drawRoundRect(track.copy(alpha = if (spec.frame == FrameKind.GLASS) 0.6f else 1f), Offset(inset, inset), inner, r)
        val (from, to) = if (progress == null) sweep.coerceAtLeast(0f) to (sweep + 0.3f).coerceAtMost(1f)
        else 0f to progress.coerceIn(0f, 1f)
        if (to > from) {
            val brush = if (window) Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.3f)))
            else Brush.horizontalGradient(listOf(color.copy(alpha = 0.85f), color))
            drawRoundRect(brush, Offset(inset + inner.width * from, inset), Size(inner.width * (to - from), inner.height), r)
        }
    }
}

private class StepsEasing(private val steps: Int) : androidx.compose.animation.core.Easing {
    override fun transform(fraction: Float): Float = (fraction * steps).toInt() / steps.toFloat()
}

/**
 * Campo de texto. Usa un EditText de Android con el teclado "sin pantalla completa":
 * en horizontal el teclado del sistema se abría a pantalla completa con su propio cuadro,
 * que no mostraba lo que se escribía. Así el teclado ocupa solo la parte de abajo.
 */
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
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentActions by rememberUpdatedState(keyboardActions)
    val actionScope = remember {
        object : KeyboardActionScope {
            override fun defaultKeyboardAction(imeAction: ImeAction) = Unit
        }
    }
    Box(modifier.pixelFrame(Px.night, Px.white, shadow = Px.black)) {
        AndroidView(
            factory = { ctx ->
                EditText(ctx).apply {
                    background = null
                    val pad = (8 * ctx.resources.displayMetrics.density).toInt()
                    setPadding(pad + pad / 4, pad / 2, pad + pad / 4, pad / 2)
                    setTextColor(Px.white.toArgb())
                    setHintTextColor(Px.darkGrey.toArgb())
                    hint = placeholder
                    isSingleLine = true
                    inputType = inputTypeFor(keyboardOptions.keyboardType, password)
                    imeOptions = imeActionFor(keyboardOptions.imeAction) or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                    setText(value)
                    setSelection(text.length)
                    doAfterTextChanged { currentOnChange(it?.toString() ?: "") }
                    setOnEditorActionListener { _, _, _ ->
                        val actions = currentActions
                        val action = actions.onDone ?: actions.onGo ?: actions.onSearch ?: actions.onSend
                        action?.invoke(actionScope)
                        action != null
                    }
                }
            },
            update = { field ->
                // La letra del estilo de interfaz (o la del sistema en Ondas y Plano).
                val spec = Px.style.spec
                field.typeface = spec.bodyFontRes?.let { ResourcesCompat.getFont(field.context, it) }
                    ?: android.graphics.Typeface.DEFAULT
                // En dp con la escala ya limitada (MAX_FONT_SCALE), igual que el resto de la app.
                field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, spec.body.size.value * fontScale)
                if (field.text.toString() != value) {
                    field.setText(value)
                    field.setSelection(value.length)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun inputTypeFor(type: KeyboardType, password: Boolean): Int = when (type) {
    KeyboardType.Phone -> InputType.TYPE_CLASS_PHONE
    KeyboardType.Number -> InputType.TYPE_CLASS_NUMBER
    KeyboardType.NumberPassword -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    KeyboardType.Password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    else -> if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
}

private fun imeActionFor(action: ImeAction): Int = when (action) {
    ImeAction.Go -> EditorInfo.IME_ACTION_GO
    ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
    ImeAction.Send -> EditorInfo.IME_ACTION_SEND
    ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
    else -> EditorInfo.IME_ACTION_DONE
}

/** Etiqueta de color tipo cartucho: "APK", "7Z", "RAR"… */
@Composable
fun PixelTag(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Px.black) {
    val spec = Px.style.spec
    // En Terminal, como en un menú de texto: [APK] en su color, sin fondo.
    if (spec.frame == FrameKind.DOUBLE_LINE) {
        Text("[$text]", style = PxText.tiny.copy(color = color), modifier = modifier)
        return
    }
    Box(
        modifier
            .background(color, RoundedCornerShape(spec.radius / 2))
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

/** Los diálogos son otra ventana: hay que enseñarles también que A acepta y B cierra. */
@Composable
fun DialogGamepadSupport() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            val callback = window.callback
            if (callback != null && callback !is GamepadWindowCallback) {
                window.callback = GamepadWindowCallback(callback)
            }
        }
    }
}

@Composable
fun PixelDialog(
    title: String,
    onDismiss: () -> Unit,
    wide: Boolean = false,
    /** Ancho de la ventana cuando [wide] (fracción de la pantalla). */
    widthFraction: Float = 0.94f,
    /**
     * Contenido en columna. Un texto largo debe ir en `Modifier.weight(1f, fill = false)` con scroll:
     * así ocupa lo que sobra y los botones de abajo siempre caben (consolas en horizontal).
     */
    content: @Composable ColumnScope.() -> Unit,
) {
    // Nunca más alta que la pantalla: si no, los botones del final se aplastan o desaparecen.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !wide)) {
        DialogGamepadSupport()
        com.topports.app.ui.theme.LimitFontScale { PixelPanel(
            // En 21:9 y pantallas grandes no se estira de lado a lado: líneas de texto legibles.
            modifier = (if (wide) Modifier.widthIn(max = 820.dp).fillMaxWidth(widthFraction) else Modifier)
                .heightIn(max = maxHeight),
            fill = Px.navy,
            border = Px.orange,
        ) {
            Column {
                Text(title.pxCaps(), style = PxText.heading.copy(color = Px.orange))
                Spacer(Modifier.height(12.dp))
                content()
            }
        } }
    }
}

/** Fila de botones de diálogo alineados a la derecha. */
@Composable
fun DialogButtons(content: @Composable () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, color: Color = Px.yellow, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val marker = Px.style.spec.marker
        if (marker == null) Box(Modifier.width(8.dp).height(8.dp).background(color))
        else Text(marker, style = PxText.label.copy(color = color))
        Spacer(Modifier.width(8.dp))
        Text(text.pxCaps(), style = PxText.label.copy(color = color))
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
            border = if (focused) Px.focus else border,
            shadow = if (focused) Px.focus else shadow,
            focused = focused,
        )
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = {
                RetroSound.play(RetroSound.Kind.ACCEPT)
                onClick()
            },
        )
}

/**
 * Barra de desplazamiento pixel a la derecha de una zona con scroll. Va antes de
 * `verticalScroll(scroll)` para dibujarse sobre el área visible.
 */
fun Modifier.pixelScrollbar(scroll: androidx.compose.foundation.ScrollState, color: Color = Px.blue): Modifier =
    drawWithContent {
        drawContent()
        val max = scroll.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val w = 4.dp.toPx()
        val x = size.width - w
        val visible = size.height / (size.height + max)
        val thumb = (size.height * visible).coerceAtLeast(16.dp.toPx())
        val y = (size.height - thumb) * scroll.value / max
        drawRect(Px.navyDark, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
        drawRect(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(w, thumb))
    }

/**
 * Con el mando, arriba/abajo no sacan la selección de esta zona (p. ej. al llegar al último
 * archivo no salta al menú lateral); izquierda/derecha sí cambian de columna.
 * [blockUp] = false deja subir (p. ej. de la cuadrícula a los botones de búsqueda).
 */
fun Modifier.keepVerticalFocus(blockUp: Boolean = true): Modifier =
    focusProperties {
        onExit = {
            if (requestedFocusDirection == FocusDirection.Down ||
                (blockUp && requestedFocusDirection == FocusDirection.Up)
            ) {
                cancelFocusChange()
            }
        }
    }.focusGroup()

private val HEART_PIXELS = listOf(
    ".XX.XX.",
    "XXXXXXX",
    "XXXXXXX",
    ".XXXXX.",
    "..XXX..",
    "...X...",
)

/** Corazón pixel art de 7×6 (la fuente pixel no tiene ♥). Vacío: solo el contorno. */
@Composable
fun PixelHeart(filled: Boolean, modifier: Modifier = Modifier, color: Color = Px.red) {
    val outline = Px.grey
    androidx.compose.foundation.Canvas(modifier) {
        val cell = minOf(size.width / 7f, size.height / 6f)
        val ox = (size.width - cell * 7f) / 2f
        val oy = (size.height - cell * 6f) / 2f
        fun on(x: Int, y: Int) = y in HEART_PIXELS.indices && x in 0..6 && HEART_PIXELS[y][x] == 'X'
        for (y in HEART_PIXELS.indices) for (x in 0..6) {
            if (!on(x, y)) continue
            val edge = !on(x - 1, y) || !on(x + 1, y) || !on(x, y - 1) || !on(x, y + 1)
            val c = when {
                filled -> color
                edge -> outline
                else -> null
            } ?: continue
            drawRect(
                c,
                androidx.compose.ui.geometry.Offset(ox + x * cell, oy + y * cell),
                androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f),
            )
        }
    }
}

/** Botón de corazón con el número, como las reacciones del canal. */
@Composable
fun HeartButton(count: Int, mine: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .pixelCard(onClick, fill = if (mine) Px.navyDark else Px.navy, border = if (mine) Px.red else Px.white)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelHeart(filled = mine, modifier = Modifier.size(width = 21.dp, height = 18.dp))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = PxText.label.copy(color = if (mine) Px.red else Px.white))
    }
}

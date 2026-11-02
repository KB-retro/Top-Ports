package com.topports.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText

/**
 * Logo del menú lateral dibujado por la app: el robot de Android en pixel art y "TOP PORTS"
 * con la letra y los colores del tema y la paleta, para que encaje con cualquiera. El logo
 * ilustrado del canal se queda en la pantalla de carga y en el inicio de sesión.
 * [height] es el alto disponible (el que antes ocupaba el logo ilustrado).
 */
@Composable
fun TopPortsWordmark(height: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // El texto ocupa unos 45 dp; el robot, lo que quede (como mucho algo más de la mitad).
        PixelRobot(Modifier.size((height - 45.dp).coerceIn(18.dp, height * 0.6f)), Px.green)
        val title = PxText.title
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Px.orange)) { append("TOP") }
                withStyle(SpanStyle(color = Px.blue)) { append(" PORTS") }
            },
            style = title.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            // Del tamaño del título del tema, pero encogiendo si no cabe en el ancho del menú.
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = title.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        BasicText(
            "ANDROID",
            style = PxText.tiny.copy(color = Px.green, textAlign = TextAlign.Center, letterSpacing = 0.2.sp),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val ROBOT = listOf(
    "...X.....X...",
    "....X...X....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    "..XX.XXX.XX..",
    "..XXXXXXXXX..",
    ".............",
    "X.XXXXXXXXX.X",
    "X.XXXXXXXXX.X",
    "X.XXXXXXXXX.X",
    "..XXXXXXXXX..",
    "...XX...XX...",
    "...XX...XX...",
)

/** Robot de Android de 13×13 píxeles, en un color de la paleta y con sombra dura. */
@Composable
fun PixelRobot(modifier: Modifier = Modifier, color: Color = Px.green) {
    val shadow = Px.black
    Canvas(modifier) {
        val cells = ROBOT.size
        val cell = minOf(size.width, size.height) / (cells + 1)
        val ox = (size.width - cell * (cells + 1)) / 2
        val oy = (size.height - cell * (cells + 1)) / 2
        for (pass in 0..1) {
            val c = if (pass == 0) shadow else color
            val shift = if (pass == 0) cell else 0f
            ROBOT.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == 'X') {
                        drawRect(c, Offset(ox + x * cell + shift, oy + y * cell + shift), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

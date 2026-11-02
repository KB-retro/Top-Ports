package com.topports.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText

/**
 * Controles del mando (Ajustes → Versión → Controles): un mando dibujado con cada botón y, al
 * lado, qué hace cada uno, como en el menú de opciones de un videojuego. Con [swapAB] (Ajustes →
 * Mando) A y B aparecen intercambiados.
 */
@Composable
fun ControlsHelp(swapAB: Boolean, wide: Boolean) {
    val accept = if (swapAB) "B" else "A"
    val back = if (swapAB) "A" else "B"
    val legend = listOf(
        "Cruceta / stick izq." to "Mover el cursor",
        accept to "Aceptar",
        back to "Volver al inicio de la tienda",
        "Y" to "Buscar (en la tienda)",
        "X" to "Cambiar el orden (en la tienda)",
        "L1 / R1" to "Fila arriba / abajo · sección en Ajustes · desplazar la descripción",
        "L2 / R2" to "Pestaña anterior / siguiente",
        "SELECT" to "Elegir sistema · en Favoritos, quitar el juego",
        "Stick dcho." to "Desplazar la descripción de un juego y Ajustes",
    )
    val pad: @Composable () -> Unit = {
        GamepadDrawing(swapAB, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1.9f))
    }
    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            legend.forEach { (button, action) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(button, style = PxText.label.copy(color = Px.yellow), modifier = Modifier.widthIn(min = 92.dp))
                    Text(action, style = PxText.small.copy(color = Px.white))
                }
            }
        }
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { pad() }
            list(Modifier.weight(1.2f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            pad()
            list(Modifier.fillMaxWidth())
        }
    }
}

/** Mando dibujado: gatillos, cruceta, sticks, SELECT/START y los botones A/B/X/Y con su letra. */
@Composable
private fun GamepadDrawing(swapAB: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val body = Px.navyDark
    val edge = Px.grey
    val key = Px.navy
    val text = Px.white
    // Colores de los botones como en las consolas: A rojo, B amarillo, X azul, Y verde.
    val colors = mapOf("A" to Px.red, "B" to Px.yellow, "X" to Px.blue, "Y" to Px.green)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        // Gatillos L1/L2 y R1/R2.
        val sw = w * 0.2f
        val sh = h * 0.1f
        listOf(0.08f to "L2", 0.72f to "R2").forEach { (x, name) ->
            drawRoundRect(key, Offset(w * x, h * 0.02f), Size(sw, sh), CornerRadius(sh / 2))
            label(measurer, name, Offset(w * x + sw / 2, h * 0.02f + sh / 2), text, 10f)
        }
        listOf(0.08f to "L1", 0.72f to "R1").forEach { (x, name) ->
            drawRoundRect(edge.copy(alpha = 0.5f), Offset(w * x, h * 0.13f), Size(sw, sh), CornerRadius(sh / 2))
            label(measurer, name, Offset(w * x + sw / 2, h * 0.13f + sh / 2), text, 10f)
        }
        // Cuerpo con asas.
        val top = h * 0.24f
        drawRoundRect(body, Offset(0f, top), Size(w, h - top), CornerRadius(h * 0.3f))
        drawRoundRect(edge, Offset(stroke / 2, top + stroke / 2), Size(w - stroke, h - top - stroke), CornerRadius(h * 0.3f), style = Stroke(stroke))
        // Cruceta.
        val dc = Offset(w * 0.2f, top + (h - top) * 0.35f)
        val arm = h * 0.09f
        drawRect(key, Offset(dc.x - arm * 3, dc.y - arm), Size(arm * 6, arm * 2))
        drawRect(key, Offset(dc.x - arm, dc.y - arm * 3), Size(arm * 2, arm * 6))
        // Sticks (izquierdo bajo la cruceta, derecho bajo los botones).
        listOf(Offset(w * 0.36f, top + (h - top) * 0.72f), Offset(w * 0.64f, top + (h - top) * 0.72f)).forEach { c ->
            drawCircle(key, h * 0.11f, c)
            drawCircle(edge.copy(alpha = 0.6f), h * 0.11f, c, style = Stroke(stroke))
        }
        // SELECT y START.
        listOf(0.43f to "SELECT", 0.57f to "START").forEach { (x, name) ->
            drawRoundRect(key, Offset(w * x - w * 0.05f, top + (h - top) * 0.22f), Size(w * 0.1f, h * 0.06f), CornerRadius(h * 0.03f))
            label(measurer, name, Offset(w * x, top + (h - top) * 0.22f + h * 0.11f), edge, 7f)
        }
        // A/B/X/Y en rombo (disposición de las consolas con mando integrado: X arriba, A derecha,
        // B abajo, Y izquierda); con A/B intercambiados se ven cambiados.
        val bc = Offset(w * 0.8f, top + (h - top) * 0.35f)
        val r = h * 0.075f
        val d = r * 2.1f
        val right = if (swapAB) "B" else "A"
        val down = if (swapAB) "A" else "B"
        listOf(Offset(0f, -d) to "X", Offset(d, 0f) to right, Offset(0f, d) to down, Offset(-d, 0f) to "Y").forEach { (o, letter) ->
            val c = bc + o
            drawCircle(colors.getValue(letter), r, c)
            label(measurer, letter, c, Color.Black, 11f)
        }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, center: Offset, color: Color, sizeSp: Float) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

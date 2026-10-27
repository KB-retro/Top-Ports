package com.topports.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import kotlinx.coroutines.delay

private val keyRows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKLÑ", "ZXCVBNM-:.")

/** Teclas grandes, cómodas en pantallas pequeñas como la de la RG477M. */
private val KEY_HEIGHT = 42.dp
private val keyText = PxText.heading.copy(fontSize = 12.sp)

/**
 * Teclado en pantalla para el mando: se recorre con la cruceta y se escribe con A.
 * Evita el teclado de Android, que en horizontal tapa toda la pantalla.
 */
@Composable
fun PixelKeyboardDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstKey.requestFocus() }
    }
    PixelDialog(title, onDismiss = onDone, wide = true, widthFraction = 0.75f) {
        // Con scroll por si la pantalla es baja (consolas en horizontal).
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().pixelFrame(Px.night, Px.white, shadow = null).padding(8.dp)) {
                Text(
                    if (value.isEmpty()) "escribe con la cruceta y A…" else "$value▌",
                    style = PxText.body.copy(color = if (value.isEmpty()) Px.darkGrey else Px.white, fontSize = 21.sp),
                    maxLines = 1,
                )
            }
            keyRows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEachIndexed { i, c ->
                        val focus = if (r == 1 && i == 0) Modifier.focusRequester(firstKey) else Modifier
                        PixelButton(
                            c.toString(),
                            onClick = { onValueChange(value + c.lowercaseChar()) },
                            color = Px.navyDark,
                            textColor = Px.white,
                            textStyle = keyText,
                            modifier = Modifier.weight(1f).height(KEY_HEIGHT).then(focus),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val m = Modifier.height(KEY_HEIGHT)
                PixelButton("Espacio", onClick = { onValueChange("$value ") }, color = Px.grey, modifier = m.weight(3f))
                PixelButton("Borrar", onClick = { onValueChange(value.dropLast(1)) }, color = Px.yellow, modifier = m.weight(2f))
                PixelButton("Limpiar", onClick = { onValueChange("") }, color = Px.red, textColor = Px.white, modifier = m.weight(2f))
                PixelButton("OK", onClick = onDone, color = Px.green, modifier = m.weight(2f))
            }
        }
    }
}

package com.topports.app.ui.settings

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
import androidx.compose.ui.unit.dp
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText

/**
 * Controles del mando (Ajustes → Versión → Controles): la imagen del mando y, al lado, qué hace
 * cada botón, como en el menú de opciones de un videojuego. Con [swapAB] (Ajustes → Mando) la
 * lista dice A y B intercambiados.
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
        GamepadImage(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(960f / 447f))
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

/** Mando de los controles: la imagen del canal (estilo SNES en pixel art, sin fondo). */
@Composable
private fun GamepadImage(modifier: Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.topports.app.R.drawable.mando_controles),
        contentDescription = "Mando",
        modifier = modifier,
    )
}

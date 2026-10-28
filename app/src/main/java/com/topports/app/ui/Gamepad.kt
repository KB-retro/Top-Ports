package com.topports.app.ui

import android.view.KeyEvent
import android.view.Window

/**
 * Botones de los mandos de consolas Android (Odin, Anbernic, Retroid…):
 * A acepta (como el centro de la cruceta), B vuelve atrás y L1/R1 suben o bajan una fila.
 * En la ventana principal, B lleva a la tienda (ver MainActivity); el botón atrás del sistema
 * sigue volviendo a la pantalla anterior.
 */
object Gamepad {
    /** Mandos con distribución Nintendo: A y B intercambiados (opción en Ajustes). */
    @Volatile
    var swapAB = false

    /** ¿Es el botón B del mando (o A si están intercambiados)? */
    fun isBackButton(event: KeyEvent): Boolean =
        event.keyCode == if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B

    fun remap(event: KeyEvent): KeyEvent {
        val accept = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        val back = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        val mapped = when (event.keyCode) {
            accept -> KeyEvent.KEYCODE_DPAD_CENTER
            back -> KeyEvent.KEYCODE_BACK
            // L1 / R1: subir y bajar una fila (en la tienda, una fila de juegos).
            KeyEvent.KEYCODE_BUTTON_L1 -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_BUTTON_R1 -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> return event
        }
        return KeyEvent(
            event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
        )
    }
}

/** Aplica [Gamepad.remap] a una ventana aparte, como la de los diálogos. */
class GamepadWindowCallback(private val base: Window.Callback) : Window.Callback by base {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = base.dispatchKeyEvent(Gamepad.remap(event))
}

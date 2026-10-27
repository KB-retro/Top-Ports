package com.topports.app.ui

import android.view.KeyEvent
import android.view.Window

/**
 * Botones de los mandos de consolas Android (Odin, Anbernic, Retroid…):
 * A acepta (como el centro de la cruceta) y B vuelve atrás.
 */
object Gamepad {
    fun remap(event: KeyEvent): KeyEvent {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
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

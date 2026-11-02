package com.topports.app.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

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

    const val STICK_DEAD_ZONE = 0.2f

    /** Solo para las capturas automáticas: simular una consola con mando en el emulador. */
    @Volatile
    var forceGamepad = false

    /** ¿Hay un mando (integrado en consolas como la Odin o la RG477M, o conectado)? */
    fun hasGamepad(): Boolean = forceGamepad || InputDevice.getDeviceIds().any { id ->
        val sources = InputDevice.getDevice(id)?.sources ?: return@any false
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    }

    /**
     * Joystick derecho en vertical (-1 arriba … 1 abajo, 0 en reposo) o null si el evento no es de
     * un joystick. Ejes Z/RZ en casi todos los mandos; algunos usan RY.
     */
    fun rightStickY(event: MotionEvent): Float? {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return null
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
        val y = if (abs(rz) > 0.01f) rz else event.getAxisValue(MotionEvent.AXIS_RY)
        return if (abs(y) < STICK_DEAD_ZONE) 0f else y
    }

    /** Joystick derecho mientras hay un diálogo abierto (su ventana no pasa por MainActivity). */
    var dialogStick by mutableFloatStateOf(0f)

    /**
     * Sonido de menú de una tecla: mover (cruceta, L1/R1, L2/R2) o volver (B). El de aceptar lo
     * ponen los propios botones al pulsarse, así suena también con la pantalla táctil.
     */
    fun playSound(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when {
            isBackButton(event) -> if (event.repeatCount == 0) RetroSound.play(RetroSound.Kind.BACK)
            event.keyCode in moveKeys -> RetroSound.play(RetroSound.Kind.MOVE)
        }
    }

    private val moveKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
    )

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
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Gamepad.playSound(event)
        return base.dispatchKeyEvent(Gamepad.remap(event))
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val y = Gamepad.rightStickY(event)
        if (y != null) {
            Gamepad.dialogStick = y
            if (y != 0f) return true
        }
        return base.dispatchGenericMotionEvent(event)
    }
}

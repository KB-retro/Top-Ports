package com.topports.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.TopPortsTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preferencias guardadas en Ajustes: tema de colores y botones A/B.
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        Px.theme = AppTheme.fromName(prefs.getString("theme", null))
        Gamepad.swapAB = prefs.getBoolean("swapAB", false)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            TopPortsTheme {
                AppRoot(vm)
            }
        }
    }

    /**
     * Joystick derecho (ejes Z/RZ; algunos mandos usan RY): con un juego abierto desplaza la
     * descripción de forma continua. El izquierdo sigue moviendo el foco como la cruceta.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
            val y = if (abs(rz) > 0.01f) rz else event.getAxisValue(MotionEvent.AXIS_RY)
            val value = if (abs(y) < STICK_DEAD_ZONE) 0f else y
            if (vm.tab == Tab.STORE && vm.selectedGameId != null) {
                vm.descriptionStick = value
                if (value != 0f) return true
            } else {
                vm.descriptionStick = 0f
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Al volver de dar el permiso de archivos, las descargas pasan a /TopPorts/.telegram.
        vm.graph.telegram.applyStorageLocation()
    }

    /**
     * Mandos de consolas Android (Odin, Anbernic, Retroid…):
     * A = aceptar, B = atrás, L1/R1 = fila arriba/abajo, L2/R2 = cambiar de pestaña, Y = buscar,
     * X = ordenar. La cruceta mueve el foco.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        // B: a la pantalla principal de la tienda. (Los diálogos tienen su propia ventana y ahí B
        // los sigue cerrando; el botón atrás del sistema vuelve a la pantalla anterior.)
        if (Gamepad.isBackButton(event)) {
            if (down) vm.goHome()
            return true
        }
        when (event.keyCode) {
            // L2 / R2: pestaña anterior / siguiente (L1/R1 se usan para moverse entre filas).
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (down) vm.cycleTab(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L2) -1 else 1)
                return true
            }
            // L1 / R1 en la tienda: una fila de juegos arriba / abajo aunque el foco esté en el menú.
            // Con un juego abierto: L1 / R1 desplazan la descripción, esté donde esté el foco.
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 ->
                if (vm.tab == Tab.STORE && !vm.searchOpen) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val dir = if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1
                        if (vm.selectedGameId == null) vm.jumpCatalogRows(dir) else vm.scrollDescription(dir)
                    }
                    return true
                }
            // Y en la tienda: abrir la búsqueda con el teclado del mando.
            KeyEvent.KEYCODE_BUTTON_Y -> if (vm.tab == Tab.STORE && vm.selectedGameId == null) {
                if (down) vm.searchOpen = true
                return true
            }
            // X en la tienda: cambiar el orden (más nuevos / A-Z / sistema).
            KeyEvent.KEYCODE_BUTTON_X -> if (vm.tab == Tab.STORE && vm.selectedGameId == null) {
                if (down) vm.toggleSort()
                return true
            }
            // SELECT en la tienda: elegir sistema (#N64, #X360…).
            KeyEvent.KEYCODE_BUTTON_SELECT -> if (vm.tab == Tab.STORE && vm.selectedGameId == null) {
                if (down) vm.systemPickerOpen = true
                return true
            }
        }
        return super.dispatchKeyEvent(Gamepad.remap(event))
    }

    private companion object {
        const val STICK_DEAD_ZONE = 0.2f
    }
}

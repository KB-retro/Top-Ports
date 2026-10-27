package com.topports.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.topports.app.ui.theme.TopPortsTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
     * Mandos de consolas Android (Odin, Anbernic, Retroid…):
     * A = aceptar, B = atrás, L1/R1 = cambiar de pestaña, Y = buscar, X = ordenar. La cruceta mueve el foco.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (down) vm.cycleTab(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                return true
            }
            // Y en la tienda: abrir la búsqueda con el teclado del mando.
            KeyEvent.KEYCODE_BUTTON_Y -> if (vm.tab == Tab.STORE && vm.selectedGameId == null) {
                if (down) vm.searchOpen = true
                return true
            }
            // X en la tienda: cambiar el orden (más nuevos / A-Z).
            KeyEvent.KEYCODE_BUTTON_X -> if (vm.tab == Tab.STORE && vm.selectedGameId == null) {
                if (down) vm.toggleSort()
                return true
            }
        }
        return super.dispatchKeyEvent(Gamepad.remap(event))
    }
}

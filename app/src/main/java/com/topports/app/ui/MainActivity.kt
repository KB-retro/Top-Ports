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
     * A = aceptar, B = atrás, L1/R1 = cambiar de pestaña. La cruceta mueve el foco sola.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> null
        }
        if (mapped != null) {
            return super.dispatchKeyEvent(
                KeyEvent(
                    event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
                ),
            )
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                vm.cycleTab(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

package com.topports.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.topports.app.ui.components.DialogButtons
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelDialog
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import com.topports.app.work.WorkService

/** "Salir" del menú principal: confirma y cierra la app del todo (no solo minimizarla). */
@Composable
fun ExitDialog(vm: AppViewModel, context: Context, onDismiss: () -> Unit) {
    val working = vm.graph.tasks.hasRunningTasks ||
        vm.files.value.values.any { it.local.isDownloadingActive }
    PixelDialog("¿Cerrar Top Ports?", onDismiss = onDismiss) {
        Text(
            if (working) "Hay descargas o tareas en marcha: se pararán. Las descargas siguen donde se " +
                "quedaron al volver a abrir la app; una extracción a medias habrá que repetirla."
            else "La app se cierra del todo, música incluida.",
            style = PxText.body,
        )
        DialogButtons {
            PixelButton("No", onClick = onDismiss, color = Px.grey, small = true)
            PixelButton("Cerrar", onClick = {
                onDismiss()
                closeApp(context)
            }, color = Px.red, textColor = Px.white, small = true)
        }
    }
}

/** Cierra la app del todo: para el servicio de descargas, quita la ventana y termina el proceso. */
fun closeApp(context: Context) {
    context.stopService(Intent(context, WorkService::class.java))
    var c: Context? = context
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    (c as? Activity)?.finishAndRemoveTask()
    // Un momento para que la ventana se cierre antes de terminar el proceso.
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 300)
}

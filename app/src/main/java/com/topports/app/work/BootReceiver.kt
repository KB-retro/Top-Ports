package com.topports.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.topports.app.TopPortsApp

/**
 * Al encender la consola: si quedaron descargas de la tienda sin terminar (y no en pausa a mano),
 * se reanudan solas sin abrir la app (Ajustes → Almacenamiento → "Seguir al encender").
 * Arrancar la app ya inicia Telegram y la cola; aquí solo se pide el servicio que la mantiene
 * viva. En Android 15+ Android no deja ese servicio tras el arranque: entonces las descargas
 * siguen mientras el sistema no cierre la app, y del todo al abrirla.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val graph = (context.applicationContext as? TopPortsApp)?.graph ?: return
        if (!graph.tasks.resumeOnBoot) return
        if (graph.installs.pendingSaves().none { !it.paused }) return
        WorkService.start(context, fromBoot = true)
    }
}

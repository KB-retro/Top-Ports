package com.topports.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.topports.app.ui.MainActivity

/** ¿Está la app en pantalla? Lo apunta MainActivity al entrar y salir. */
object AppForeground {
    @Volatile
    var visible = false
}

/**
 * Aviso de "terminado" (descarga pasada a /TopPorts, extracción, copia…) cuando la app no está
 * en pantalla. Con la app abierta no hace falta: se ve en TAREAS.
 */
object DoneNotifier {
    private const val CHANNEL_ID = "done"
    private const val BASE_ID = 20_000

    fun notify(context: Context, taskId: Int, title: String, message: String, failed: Boolean) {
        if (AppForeground.visible) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.let { nm ->
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Tareas terminadas", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (failed) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (failed) "Error: $title" else "Listo: $title")
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(BASE_ID + taskId, notification) }
    }
}

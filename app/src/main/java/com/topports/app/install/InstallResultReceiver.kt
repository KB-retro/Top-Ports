package com.topports.app.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import com.topports.app.TopPortsApp

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                runCatching { context.startActivity(confirm) }
                // Con la app en segundo plano (p. ej. al acabar una extracción larga) Android no deja
                // abrir la confirmación y la instalación se quedaría sin hacer: se avisa también con
                // una notificación que la abre al tocarla.
                notifyConfirm(context, sessionId, confirm, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                cancelConfirm(context, sessionId)
                Toast.makeText(context, "¡Instalado!", Toast.LENGTH_SHORT).show()
                (context.applicationContext as? TopPortsApp)?.graph?.updates?.refresh()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                cancelConfirm(context, sessionId)
                Toast.makeText(context, "Instalación cancelada", Toast.LENGTH_SHORT).show()
            }
            else -> {
                cancelConfirm(context, sessionId)
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error desconocido"
                Toast.makeText(context, "No se pudo instalar: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun notifyConfirm(context: Context, sessionId: Int, confirm: Intent, packageName: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.let { nm ->
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Instalaciones", NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }
        val pending = PendingIntent.getActivity(
            context, sessionId, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Listo para instalar")
            .setContentText("Toca para instalar ${packageName ?: "el juego"}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { manager.notify(NOTIFICATION_BASE + sessionId, notification) }
    }

    private fun cancelConfirm(context: Context, sessionId: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + sessionId)
    }

    companion object {
        const val ACTION = "com.topports.app.INSTALL_RESULT"
        private const val CHANNEL_ID = "install"
        private const val NOTIFICATION_BASE = 10_000
    }
}

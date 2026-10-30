package com.topports.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.topports.app.TopPortsApp
import com.topports.app.ui.MainActivity
import com.topports.core.FileNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: mantiene vivas las descargas de 2–4 GB y las extracciones
 * aunque el usuario salga de la app.
 */
class WorkService : LifecycleService() {

    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Preparando…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15+: un servicio "dataSync" solo puede estar 6 horas al día en primer plano. Al
     * agotarlas hay que pararlo; si no, Android cierra la app con un error. Las descargas siguen
     * mientras la app esté abierta.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observe() {
        val graph = (application as TopPortsApp).graph
        lifecycleScope.launch {
            combine(graph.telegram.files, graph.tasks.tasks) { files, tasks ->
                val downloading = files.values.filter { it.local.isDownloadingActive }
                val running = tasks.filter { it.running }
                downloading to running
            }.conflate().collect { (downloading, running) ->
                if (downloading.isEmpty() && running.isEmpty()) {
                    // Pequeña espera: entre parte y parte puede no haber nada activo un instante.
                    delay(3000)
                    if (graph.telegram.files.value.values.none { it.local.isDownloadingActive } &&
                        !graph.tasks.hasRunningTasks
                    ) {
                        ServiceCompat.stopForeground(this@WorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@collect
                }
                val text: String
                val progress: Float?
                if (downloading.isNotEmpty()) {
                    val done = downloading.sumOf { it.local.downloadedSize }
                    val total = downloading.sumOf { if (it.size > 0) it.size else it.expectedSize }
                    text = "Descargando ${downloading.size} archivo(s): " +
                        "${FileNames.formatSize(done)} / ${FileNames.formatSize(total)}"
                    progress = if (total > 0) done.toFloat() / total else null
                } else {
                    val task = running.first()
                    text = "${task.title}: ${task.message}"
                    progress = task.progress
                }
                val manager = NotificationManagerCompat.from(this@WorkService)
                if (manager.areNotificationsEnabled()) {
                    runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text, progress)) }
                }
                // Android ignora las notificaciones que se actualizan demasiado rápido.
                delay(1000)
            }
        }
    }

    private fun buildNotification(text: String, progress: Float?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Top Ports")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .apply {
                if (progress == null) setProgress(0, 0, true)
                else setProgress(1000, (progress * 1000).toInt(), false)
            }
            .build()

    companion object {
        private const val CHANNEL_ID = "work"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java))
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Descargas y tareas", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}

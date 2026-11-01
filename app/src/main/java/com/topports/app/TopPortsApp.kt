package com.topports.app

import android.app.Application
import android.util.Log
import com.topports.app.telegram.ChannelRepository
import com.topports.app.telegram.TelegramClient
import com.topports.app.settings.StorageTools
import com.topports.app.updates.GameData
import com.topports.app.updates.InstallRegistry
import com.topports.app.updates.UpdateService
import com.topports.app.work.TaskCenter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Objetos compartidos por toda la app. */
class AppGraph(app: Application) {
    /**
     * Un error que se escape de una corrutina (p. ej. TDLib reiniciándose mientras se borra una
     * descarga) se apunta en el log en vez de cerrar la app entera.
     */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e("TopPorts", "Error en segundo plano", e) },
    )
    val telegram = TelegramClient(app)
    val channel = ChannelRepository(telegram, scope)
    val installs = InstallRegistry(app)
    val tasks = TaskCenter(app, telegram, scope, installs)
    val updates = UpdateService(app, installs, channel, scope)
    val storage = StorageTools(app, telegram)
    val gameData = GameData(app, telegram, installs)
}

class TopPortsApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.telegram.start()
        // Modo demo: el catálogo de ejemplo está listo antes de la primera pantalla.
        if (BuildConfig.DEMO) graph.channel.refresh()
    }
}

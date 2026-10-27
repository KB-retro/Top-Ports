package com.topports.app

import android.app.Application
import com.topports.app.telegram.ChannelRepository
import com.topports.app.telegram.TelegramClient
import com.topports.app.work.TaskCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Objetos compartidos por toda la app. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val telegram = TelegramClient(app)
    val channel = ChannelRepository(telegram, scope)
    val tasks = TaskCenter(app, telegram, scope)
}

class TopPortsApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.telegram.start()
    }
}

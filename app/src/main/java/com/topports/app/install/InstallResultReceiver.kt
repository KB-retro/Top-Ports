package com.topports.app.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "¡Instalado!", Toast.LENGTH_SHORT).show()
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Toast.makeText(context, "Instalación cancelada", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error desconocido"
                Toast.makeText(context, "No se pudo instalar: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION = "com.topports.app.INSTALL_RESULT"
    }
}

package com.topports.app.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

/** Instala APKs (sueltos, partidos o split APKs) con PackageInstaller. */
object ApkInstaller {

    class Source(val name: String, val size: Long, val open: () -> InputStream)

    fun fromFile(file: File) = Source(file.name, file.length()) { file.inputStream() }

    /** APK partido en `.apk.001`, `.apk.002`…: se une al vuelo mientras se instala. */
    fun fromParts(name: String, parts: List<File>) = Source(name, parts.sumOf { it.length() }) {
        SequenceInputStream(Collections.enumeration(parts.map { it.inputStream() }))
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Instala [sources] en una sola sesión (una app, con sus splits si los hay).
     * Android muestra después el diálogo de confirmación.
     */
    suspend fun install(
        context: Context,
        sources: List<Source>,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "No hay APK que instalar" }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(sources.sumOf { it.size })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val total = sources.sumOf { it.size }.coerceAtLeast(1)
        var written = 0L
        installer.openSession(sessionId).use { session ->
            try {
                sources.forEachIndexed { index, source ->
                    session.openWrite("${index}_${source.name}", 0, source.size).use { out ->
                        source.open().use { input ->
                            val buffer = ByteArray(1 shl 20)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                written += n
                                onProgress(written.toFloat() / total)
                            }
                        }
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }
    }
}

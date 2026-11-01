package com.topports.app.settings

import android.content.Context
import com.topports.app.telegram.TelegramClient
import com.topports.app.updates.InstallRegistry
import kotlinx.coroutines.Dispatchers
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.withContext
import java.io.File

/** Espacio que ocupa la app y limpieza de caché y restos de descargas. */
class StorageTools(
    private val context: Context,
    private val tg: TelegramClient,
    private val installs: InstallRegistry,
) {

    private val tdFiles get() = tg.filesDir

    /** Carpeta privada que usaba TDLib antes de tener permiso de archivos (puede quedar caché). */
    private val tdFilesDirs get() = listOf(File(context.filesDir, "tdlib-files"), tg.filesDir).distinct()

    /** Carpetas de TDLib con imágenes y vídeos que se pueden volver a descargar. */
    private val cacheDirs get() = tdFilesDirs.flatMap { dir ->
        listOf("photos", "thumbnails", "videos", "profile_photos", "animations").map { File(dir, it) }
    } + context.cacheDir

    data class Usage(val downloads: Long, val cache: Long, val partial: Long)

    data class Orphans(val fileIds: List<Int>, val bytes: Long, val tempBytes: Long) {
        val total: Long get() = bytes + tempBytes
        val isEmpty: Boolean get() = fileIds.isEmpty() && tempBytes == 0L
    }

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        Usage(
            downloads = tdFilesDirs.sumOf { sizeOf(File(it, "documents")) },
            cache = cacheDirs.sumOf { sizeOf(it) },
            partial = sizeOf(File(tdFiles, "temp")),
        )
    }

    /** Borra miniaturas, capturas y vídeos guardados; se vuelven a bajar si hacen falta. */
    suspend fun clearCache(): Long {
        val before = withContext(Dispatchers.IO) { cacheDirs.sumOf { sizeOf(it) } }
        // Primero con TDLib, que así sabe qué ha borrado y vuelve a bajar las imágenes al verlas
        // (borrarlas a mano le deja apuntando a archivos que ya no existen).
        runCatching {
            tg.send(
                TdApi.OptimizeStorage(
                    0, 0, 0, 0,
                    arrayOf(
                        TdApi.FileTypePhoto(), TdApi.FileTypeThumbnail(), TdApi.FileTypeVideo(),
                        TdApi.FileTypeProfilePhoto(), TdApi.FileTypeAnimation(),
                    ),
                    null, null, false, 0,
                ),
            )
        }
        // Lo que quede (p. ej. de la carpeta antigua) y la caché propia de la app.
        withContext(Dispatchers.IO) { cacheDirs.forEach { clearContents(it) } }
        val after = withContext(Dispatchers.IO) { cacheDirs.sumOf { sizeOf(it) } }
        return (before - after).coerceAtLeast(0)
    }

    private fun downloading(): Boolean = tg.files.value.values.any { it.local.isDownloadingActive }

    /** Partes de las descargas de la tienda que siguen en TAREAS (en pausa incluidas). */
    private fun pendingIds(): Set<Int> = installs.pendingSaves().flatMap { p -> p.parts.map { it.first } }.toSet()

    /**
     * Restos de descargas: archivos a medias que no se están descargando ni siguen en TAREAS
     * (una descarga en pausa no es un resto) y, si no hay ninguna descarga activa, lo que quede
     * en la carpeta temporal.
     */
    suspend fun findOrphans(): Orphans = withContext(Dispatchers.IO) {
        val keep = pendingIds()
        val partial = tg.files.value.values.filter {
            it.id !in keep &&
                !it.local.isDownloadingActive && !it.local.isDownloadingCompleted && it.local.downloadedSize > 0
        }
        val tracked = partial.sumOf { it.local.downloadedSize }
        // Con descargas en pausa, la carpeta temporal guarda lo ya bajado: no se toca.
        val temp = if (downloading() || keep.isNotEmpty()) 0L
        else (sizeOf(File(tdFiles, "temp")) - tracked).coerceAtLeast(0)
        Orphans(partial.map { it.id }, tracked, temp)
    }

    suspend fun cleanOrphans(orphans: Orphans): Long {
        val keep = pendingIds()
        orphans.fileIds.filter { it !in keep }.forEach { runCatching { tg.deleteFile(it) } }
        val temp = withContext(Dispatchers.IO) {
            if (downloading() || keep.isNotEmpty()) 0L else clearContents(File(tdFiles, "temp"))
        }
        return orphans.bytes + temp
    }

    /** Borra todos los juegos descargados (no lo instalado ni lo extraído). */
    suspend fun deleteAllDownloads(): Long {
        if (downloading()) throw IllegalStateException("Hay descargas en curso")
        tg.files.value.values.filter { it.local.isDownloadingCompleted || it.local.downloadedSize > 0 }
            .forEach { runCatching { tg.deleteFile(it.id) } }
        // Las descargas en pausa también se han borrado: que no sigan saliendo en TAREAS.
        installs.pendingSaves().forEach { installs.removePendingSave(it.pkgKey) }
        return withContext(Dispatchers.IO) {
            tdFilesDirs.sumOf { clearContents(File(it, "documents")) + clearContents(File(it, "temp")) }
        }
    }

    private fun clearContents(dir: File): Long {
        var freed = 0L
        dir.listFiles()?.forEach { f ->
            val size = sizeOf(f)
            if (f.deleteRecursively()) freed += size
        }
        return freed
    }

    private fun sizeOf(f: File): Long =
        if (!f.exists()) 0L
        else if (f.isFile) f.length()
        else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

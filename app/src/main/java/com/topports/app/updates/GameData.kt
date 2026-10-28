package com.topports.app.updates

import android.content.Context
import com.topports.app.storage.Storage
import com.topports.app.telegram.TelegramClient
import com.topports.core.InstalledGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Datos que un juego deja en el móvil, para revisarlos y borrarlos desde Instalados. */
class GameData(
    private val context: Context,
    private val tg: TelegramClient,
    private val registry: InstallRegistry,
) {
    data class Entry(val label: String, val file: File, val size: Long)

    /** OBB, Android/data, lo extraído y la carpeta /TopPorts del juego que existan. */
    suspend fun entries(item: InstalledGame): List<Entry> = withContext(Dispatchers.IO) {
        val files = mutableListOf<Pair<String, File>>()
        item.apps.forEach { app ->
            files += "OBB de ${app.label}" to Storage.obbDir(app.packageName)
            files += "Datos de ${app.label}" to Storage.dataDir(app.packageName)
        }
        registry.extracted(item.game.id).forEach { path ->
            // Nunca borrar la raíz de la memoria ni carpetas del sistema.
            val f = File(path)
            if (Storage.isSafeToDelete(f)) files += "Extraído: ${f.name}" to f
        }
        files += "Carpeta TopPorts" to File(Storage.topPortsDir(context), com.topports.core.FileNames.safeFolderName(item.game.title))
        files.distinctBy { it.second.path }
            .filter { it.second.exists() && Storage.isSafeToDelete(it.second) }
            .map { (label, f) -> Entry(label, f, sizeOf(f)) }
    }

    /** Borra esos datos y los archivos del juego bajados de Telegram. Devuelve lo liberado. */
    suspend fun clean(item: InstalledGame, entries: List<Entry>): Long {
        val freed = withContext(Dispatchers.IO) {
            entries.sumOf { e -> if (e.file.deleteRecursively()) e.size else 0L }
        }
        item.game.packages.flatMap { it.parts }.forEach { tg.deleteFile(it.id) }
        registry.forgetExtracted(item.game.id)
        item.game.packages.forEach { registry.forgetSaved(it.key) }
        return freed
    }

    private fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

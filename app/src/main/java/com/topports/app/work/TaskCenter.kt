package com.topports.app.work

import android.content.Context
import com.topports.app.archive.ArchiveEntry
import com.topports.app.archive.ArchiveExtractor
import com.topports.app.archive.ExtractCancelledException
import com.topports.app.archive.PasswordRequiredException
import com.topports.app.archive.VolumeSet
import com.topports.app.install.ApkInstaller
import com.topports.app.storage.Storage
import com.topports.app.telegram.TelegramClient
import com.topports.core.FileNames
import com.topports.core.Game
import com.topports.core.GamePackage
import com.topports.core.InstallPlanner
import com.topports.core.InstallRecord
import com.topports.app.updates.InstallRegistry
import com.topports.core.PackageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Tarea larga visible en la pestaña TAREAS. */
data class TaskState(
    val id: Int,
    val title: String,
    val message: String,
    /** 0..1, o null si no se sabe. */
    val progress: Float? = null,
    val running: Boolean = true,
    val failed: Boolean = false,
    val needsPassword: Boolean = false,
    /** Carpeta con el resultado, para abrirla en el explorador. */
    val resultDir: String? = null,
)

/** Estado de descarga de un paquete (suma de todas sus partes). */
data class DownloadInfo(
    val downloaded: Long,
    val total: Long,
    val active: Boolean,
    val complete: Boolean,
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val started: Boolean get() = downloaded > 0 || active
}

class NotEnoughSpaceException(needed: Long, free: Long) : IOException(
    "No hay espacio suficiente: hacen falta ${FileNames.formatSize(needed)} y quedan ${FileNames.formatSize(free)}",
)

/**
 * Descargas de Telegram y tareas de extracción / instalación / archivos.
 * Mientras haya trabajo, [WorkService] mantiene viva la app en segundo plano.
 */
class TaskCenter(
    private val context: Context,
    private val tg: TelegramClient,
    private val scope: CoroutineScope,
    private val installs: InstallRegistry,
) {
    private val extractor = ArchiveExtractor()
    private val ids = AtomicInteger(1)
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val retries = ConcurrentHashMap<Int, (String?) -> Unit>()

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    /** Nombres de los archivos que se han mandado descargar, para la pestaña TAREAS. */
    private val _downloadNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadNames: StateFlow<Map<Int, String>> = _downloadNames.asStateFlow()

    val hasRunningTasks: Boolean get() = _tasks.value.any { it.running }

    // --- Descargas ---

    fun downloadInfo(pkg: GamePackage, files: Map<Int, TdApi.File>): DownloadInfo {
        var downloaded = 0L
        var total = 0L
        var active = false
        var complete = true
        for (part in pkg.parts) {
            val f = files[part.id]
            val size = f?.let { if (it.size > 0) it.size else it.expectedSize }?.takeIf { it > 0 } ?: part.size
            total += size
            downloaded += f?.local?.downloadedSize ?: 0
            active = active || f?.local?.isDownloadingActive == true
            complete = complete && f?.local?.isDownloadingCompleted == true
        }
        return DownloadInfo(downloaded, total, active, complete)
    }

    /** Pide a TDLib el estado actual de las partes (p. ej. al abrir una ficha). */
    fun refresh(pkg: GamePackage) {
        scope.launch {
            pkg.parts.forEach { runCatching { tg.refreshFile(it.id) } }
        }
    }

    fun download(pkg: GamePackage, onError: (String) -> Unit) {
        scope.launch {
            try {
                val files = pkg.parts.map { tg.refreshFile(it.id) }
                val remaining = files.sumOf { f ->
                    val size = if (f.size > 0) f.size else f.expectedSize
                    (size - f.local.downloadedSize).coerceAtLeast(0)
                }
                // Los archivos comprimidos necesitan además sitio para extraerse.
                val extra = if (pkg.kind == PackageKind.ARCHIVE || pkg.kind == PackageKind.APK_BUNDLE) pkg.size else 0L
                val free = Storage.freeBytes(context.filesDir)
                if (remaining + extra > free) throw NotEnoughSpaceException(remaining + extra, free)

                _downloadNames.update { names -> names + pkg.parts.associate { it.id to it.name } }
                pkg.parts.forEach { tg.startDownload(it.id) }
                WorkService.start(context)
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
            }
        }
    }

    fun pause(pkg: GamePackage) {
        scope.launch { pkg.parts.forEach { runCatching { tg.pauseDownload(it.id) } } }
    }

    fun deleteDownload(pkg: GamePackage) {
        scope.launch { pkg.parts.forEach { tg.deleteFile(it.id) } }
    }

    // --- Instalar desde la tienda ---

    /** [dest]: carpeta donde extraer o guardar (raíz de la memoria o /TopPorts/<juego>). */
    fun installPackage(game: Game, pkg: GamePackage, dest: File) {
        launchTask(game.title) { taskId, password ->
            val parts = pkg.parts.map { part ->
                val f = tg.refreshFile(part.id)
                if (!f.local.isDownloadingCompleted) throw IOException("Falta descargar ${part.name}")
                part.name to File(f.local.path)
            }
            when (pkg.kind) {
                PackageKind.APK -> {
                    val source = if (parts.size == 1) ApkInstaller.fromFile(parts[0].second)
                    else ApkInstaller.fromParts(pkg.name, FileNames.sortVolumes(parts) { it.first }.map { it.second })
                    if (parts.size == 1) remember(parts[0].second, game, pkg)
                    installApks(taskId, listOf(source))
                    finish(taskId, "Instalación lanzada. Acepta el aviso de Android.")
                }
                PackageKind.OBB -> {
                    val dest = copyObb(taskId, parts[0].second, parts[0].first)
                    finish(taskId, "OBB copiado en $dest", dest)
                }
                PackageKind.ARCHIVE, PackageKind.APK_BUNDLE -> {
                    extractAndInstall(taskId, VolumeSet.ofParts(parts), dest, password, game, pkg)
                }
                PackageKind.OTHER -> {
                    dest.mkdirs()
                    parts.forEach { (name, file) -> copyWithProgress(taskId, file, File(dest, name)) }
                    finish(taskId, "Copiado en ${dest.path}", dest.path)
                }
            }
        }
    }

    // --- Explorador de archivos ---

    fun extractLocal(archive: File, dest: File, installAfter: Boolean) {
        launchTask(archive.name) { taskId, password ->
            val volumes = VolumeSet.local(archive)
            if (installAfter) {
                extractAndInstall(taskId, volumes, dest, password)
            } else {
                extract(taskId, volumes, dest, password)
                finish(taskId, "Extraído en ${dest.path}", dest.path)
            }
        }
    }

    suspend fun listArchive(archive: File, password: String? = null): List<ArchiveEntry> =
        withContext(Dispatchers.IO) { extractor.list(VolumeSet.local(archive), password) }

    fun installLocalApk(apk: File) {
        launchTask(apk.name) { taskId, _ ->
            val v = FileNames.volumeOf(apk.name)
            val source = if (v.style == FileNames.SplitStyle.NUMBERED) {
                val dir = apk.parentFile ?: throw IOException("Ruta no válida")
                val parts = generateSequence(1) { it + 1 }
                    .map { File(dir, v.baseName + "." + it.toString().padStart(3, '0')) }
                    .takeWhile { it.isFile }
                    .toList()
                ApkInstaller.fromParts(v.baseName, parts)
            } else {
                ApkInstaller.fromFile(apk)
            }
            installApks(taskId, listOf(source))
            finish(taskId, "Instalación lanzada. Acepta el aviso de Android.")
        }
    }

    fun copyFiles(sources: List<File>, destDir: File, move: Boolean) {
        val title = if (move) "Mover ${sources.size} elemento(s)" else "Copiar ${sources.size} elemento(s)"
        launchTask(title) { taskId, _ ->
            val total = sources.sumOf { sizeOf(it) }.coerceAtLeast(1)
            var done = 0L
            for (src in sources) {
                val target = uniqueTarget(File(destDir, src.name))
                if (move && src.renameTo(target)) {
                    done += sizeOf(target)
                    continue
                }
                src.walkTopDown().forEach { file ->
                    val rel = file.relativeTo(src).path
                    val out = if (rel.isEmpty()) target else File(target, rel)
                    if (file.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        file.inputStream().use { input ->
                            out.outputStream().use { output ->
                                val buffer = ByteArray(1 shl 20)
                                while (true) {
                                    checkActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    done += n
                                    update(taskId, "${file.name}", done.toFloat() / total)
                                }
                            }
                        }
                    }
                }
                if (move) src.deleteRecursively()
            }
            finish(taskId, if (move) "Movido" else "Copiado", destDir.path)
        }
    }

    fun deleteFiles(files: List<File>) {
        launchTask("Borrar ${files.size} elemento(s)") { taskId, _ ->
            files.forEach {
                update(taskId, it.name, null)
                it.deleteRecursively()
            }
            finish(taskId, "Borrado")
        }
    }

    // --- Gestión de tareas ---

    fun cancel(taskId: Int) {
        jobs[taskId]?.cancel()
        _tasks.update { list ->
            list.map { if (it.id == taskId) it.copy(running = false, failed = true, message = "Cancelado") else it }
        }
    }

    fun retryWithPassword(taskId: Int, password: String) {
        val retry = retries.remove(taskId) ?: return
        dismiss(taskId)
        retry(password)
    }

    fun dismiss(taskId: Int) {
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.running } }
    }

    private fun launchTask(title: String, password: String? = null, block: suspend (Int, String?) -> Unit) {
        val taskId = ids.getAndIncrement()
        _tasks.update { it + TaskState(taskId, title, "Preparando…") }
        WorkService.start(context)
        val job = scope.launch(Dispatchers.IO) {
            try {
                block(taskId, password)
            } catch (e: PasswordRequiredException) {
                retries[taskId] = { pw -> launchTask(title, pw, block) }
                fail(taskId, if (password == null) "Necesita contraseña" else "Contraseña incorrecta", needsPassword = true)
            } catch (e: ExtractCancelledException) {
                fail(taskId, "Cancelado")
            } catch (e: kotlinx.coroutines.CancellationException) {
                fail(taskId, "Cancelado")
            } catch (e: Exception) {
                fail(taskId, e.message ?: e.toString())
            } finally {
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
    }

    private suspend fun extract(taskId: Int, volumes: VolumeSet, dest: File, password: String?): List<String> {
        val coroutineContext = kotlin.coroutines.coroutineContext
        // Tamaño comprimido como estimación mínima de lo que ocupará lo extraído.
        val estimate = volumes.allFiles().sumOf { it.length() }
        val free = Storage.freeBytes(dest)
        if (estimate > free) throw NotEnoughSpaceException(estimate, free)
        update(taskId, "Extrayendo en ${dest.path}", 0f)
        return extractor.extract(
            volumes = volumes,
            dest = dest,
            password = password,
            onProgress = { done, total ->
                if (total > 0) update(taskId, "Extrayendo… ${FileNames.formatSize(done)} / ${FileNames.formatSize(total)}", done.toFloat() / total)
            },
            isCancelled = { !coroutineContext.isActive },
        )
    }

    /** Anota de qué archivo del canal sale un APK, para avisar luego de sus actualizaciones. */
    private fun remember(apk: File, game: Game, pkg: GamePackage) {
        val packageName = installs.packageNameOf(context, apk) ?: return
        installs.record(InstallRecord(packageName, pkg.name, pkg.parts.first().id, game.id))
    }

    private suspend fun extractAndInstall(
        taskId: Int,
        volumes: VolumeSet,
        dest: File,
        password: String?,
        game: Game? = null,
        pkg: GamePackage? = null,
    ) {
        // Solo lo recién extraído: el destino puede ser la raíz de la memoria, con otras cosas.
        val extracted = extract(taskId, volumes, dest, password)
        val plan = InstallPlanner.plan(extracted)
        val notes = mutableListOf<String>()

        for (obb in plan.obbs) {
            val src = File(dest, obb.path)
            val target = File(Storage.obbDir(obb.packageName), src.name)
            update(taskId, "Copiando OBB ${src.name}", null)
            if (moveOrCopy(src, target)) notes += "OBB en Android/obb/${obb.packageName}"
            else notes += "No se pudo copiar ${src.name} a Android/obb/${obb.packageName}: muévelo a mano"
        }

        for (data in plan.dataDirs) {
            val src = File(dest, data.path)
            val target = Storage.dataDir(data.packageName)
            update(taskId, "Copiando datos de ${data.packageName}", null)
            val ok = runCatching { src.copyRecursively(target, overwrite = true) }.getOrDefault(false)
            notes += if (ok) "Datos copiados en Android/data/${data.packageName}"
            else "Android no deja copiar a Android/data/${data.packageName}: cópialo con el explorador o un PC desde ${src.path}"
        }

        if (plan.apks.isNotEmpty()) {
            if (game != null && pkg != null) plan.apks.forEach { remember(File(dest, it), game, pkg) }
            if (plan.isSplitApk) {
                installApks(taskId, plan.apks.map { ApkInstaller.fromFile(File(dest, it)) })
            } else {
                plan.apks.forEach { installApks(taskId, listOf(ApkInstaller.fromFile(File(dest, it)))) }
            }
            notes += "Instalación lanzada (${plan.apks.size} APK)"
        }
        if (plan.isEmpty) notes += "No hay APK ni OBB: los archivos están en la carpeta"
        finish(taskId, notes.joinToString("\n"), dest.path)
    }

    private suspend fun installApks(taskId: Int, sources: List<ApkInstaller.Source>) {
        if (!ApkInstaller.canInstall(context)) {
            withContext(Dispatchers.Main) { context.startActivity(ApkInstaller.unknownSourcesIntent(context)) }
            throw IOException("Permite \"Instalar apps desconocidas\" para Top Ports y vuelve a intentarlo")
        }
        update(taskId, "Preparando instalación…", 0f)
        ApkInstaller.install(context, sources) { update(taskId, "Preparando instalación…", it) }
    }

    private suspend fun copyObb(taskId: Int, file: File, originalName: String): String {
        val pkg = FileNames.obbPackage(originalName)
            ?: throw IOException("No se sabe a qué juego pertenece $originalName")
        val target = File(Storage.obbDir(pkg), originalName)
        update(taskId, "Copiando OBB", null)
        if (!moveOrCopy(file, target, keepSource = true)) {
            throw IOException("Android no deja escribir en Android/obb/$pkg. Da permiso de archivos o cópialo a mano.")
        }
        return target.parent ?: target.path
    }

    private fun moveOrCopy(src: File, target: File, keepSource: Boolean = false): Boolean = runCatching {
        target.parentFile?.mkdirs()
        if (!keepSource && src.renameTo(target)) return true
        src.copyTo(target, overwrite = true)
        if (!keepSource) src.delete()
        true
    }.getOrDefault(false)

    private suspend fun copyWithProgress(taskId: Int, src: File, target: File) {
        target.parentFile?.mkdirs()
        val total = src.length().coerceAtLeast(1)
        var done = 0L
        src.inputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    checkActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    update(taskId, "Copiando ${src.name}", done.toFloat() / total)
                }
            }
        }
    }

    private suspend fun checkActive() {
        if (!kotlin.coroutines.coroutineContext.isActive) throw ExtractCancelledException()
    }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    private fun uniqueTarget(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (true) {
            val candidate = File(file.parentFile, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun update(taskId: Int, message: String, progress: Float?) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) it.copy(message = message, progress = progress) else it }
        }
    }

    private fun finish(taskId: Int, message: String, resultDir: String? = null) {
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) it.copy(message = message, progress = 1f, running = false, resultDir = resultDir) else it
            }
        }
    }

    private fun fail(taskId: Int, message: String, needsPassword: Boolean = false) {
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(message = message, running = false, failed = true, needsPassword = needsPassword)
                } else it
            }
        }
    }
}

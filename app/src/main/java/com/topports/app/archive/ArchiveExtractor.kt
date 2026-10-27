package com.topports.app.archive

import com.topports.core.FileNames
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

class PasswordRequiredException : IOException("El archivo tiene contraseña")
class ExtractCancelledException : IOException("Cancelado")

/**
 * Volúmenes de un archivo. Los nombres originales pueden no coincidir con los del disco
 * (TDLib renombra si hay duplicados), así que se resuelven por nombre lógico.
 */
class VolumeSet private constructor(
    /** Archivo real del primer volumen. */
    val first: File,
    /** Nombre lógico del primer volumen (`juego.part1.rar`, `juego.7z.001`). */
    val firstName: String,
    private val byName: Map<String, File>,
) {
    private val dir: File? = first.parentFile

    fun resolve(name: String): File? {
        val simple = name.substringAfterLast('/')
        byName[simple.lowercase()]?.let { return it }
        return dir?.let { File(it, simple) }?.takeIf { it.isFile }
    }

    /** Todos los volúmenes conocidos (para calcular el espacio necesario). */
    fun allFiles(): List<File> {
        if (byName.isNotEmpty()) return byName.values.toList()
        val base = FileNames.volumeOf(firstName).baseName
        return dir?.listFiles { f -> f.isFile && FileNames.volumeOf(f.name).baseName.equals(base, ignoreCase = true) }
            ?.toList()?.ifEmpty { null } ?: listOf(first)
    }

    companion object {
        /** Archivo del disco: si es una parte, busca el primer volumen en la misma carpeta. */
        fun local(file: File): VolumeSet {
            val v = FileNames.volumeOf(file.name)
            val firstName = when (v.style) {
                FileNames.SplitStyle.NUMBERED -> v.baseName + ".001"
                FileNames.SplitStyle.RAR_PART -> {
                    val digits = Regex("""\.part(\d+)\.rar$""", RegexOption.IGNORE_CASE)
                        .find(file.name)?.groupValues?.get(1)?.length ?: 1
                    v.baseName.removeSuffix(".rar").removeSuffix(".RAR") + ".part" + "1".padStart(digits, '0') + ".rar"
                }
                FileNames.SplitStyle.NONE -> file.name
            }
            val first = File(file.parentFile, firstName).takeIf { it.isFile } ?: file
            return VolumeSet(first, first.name, emptyMap())
        }

        /** Partes descargadas de Telegram: (nombre original, archivo en disco). */
        fun ofParts(parts: List<Pair<String, File>>): VolumeSet {
            val sorted = FileNames.sortVolumes(parts) { it.first }
            val map = sorted.associate { it.first.lowercase() to it.second }
            return VolumeSet(sorted.first().second, sorted.first().first, map)
        }
    }
}

data class ArchiveEntry(val path: String, val size: Long, val isDirectory: Boolean)

/** Extractor basado en 7-Zip: ZIP, 7z, RAR4/RAR5, TAR… incluidos multivolumen. */
class ArchiveExtractor {

    fun list(volumes: VolumeSet, password: String? = null): List<ArchiveEntry> =
        open(volumes, password) { archive ->
            (0 until archive.numberOfItems).map { i ->
                ArchiveEntry(
                    path = archive.getStringProperty(i, PropID.PATH) ?: "?",
                    size = (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L,
                    isDirectory = (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean) ?: false,
                )
            }
        }

    /**
     * Extrae todo en [dest]. Devuelve las rutas relativas de los archivos creados.
     * [onProgress] recibe bytes procesados y total; [isCancelled] permite abortar.
     */
    fun extract(
        volumes: VolumeSet,
        dest: File,
        password: String? = null,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): List<String> = open(volumes, password) { archive ->
        dest.mkdirs()
        val callback = ExtractCallback(archive, dest, password, onProgress, isCancelled)
        val indices = IntArray(archive.numberOfItems) { it }
        try {
            archive.extract(indices, false, callback)
        } catch (e: SevenZipException) {
            if (isCancelled()) throw ExtractCancelledException()
            if (callback.encrypted && password == null) throw PasswordRequiredException()
            throw IOException(e.message ?: "Error al extraer", e)
        } finally {
            callback.closeCurrent()
        }
        if (isCancelled()) throw ExtractCancelledException()
        callback.failure?.let { result ->
            if (result == ExtractOperationResult.WRONG_PASSWORD ||
                (callback.encrypted && result in setOf(ExtractOperationResult.DATAERROR, ExtractOperationResult.CRCERROR))
            ) {
                throw PasswordRequiredException()
            }
            throw IOException("Error al extraer: $result")
        }
        callback.extracted
    }

    private fun <R> open(volumes: VolumeSet, password: String?, block: (IInArchive) -> R): R {
        val opened = mutableListOf<Closeable>()
        val callback = OpenCallback(volumes, password, opened)
        try {
            val v = FileNames.volumeOf(volumes.firstName)
            val archive = if (v.style == FileNames.SplitStyle.NUMBERED) {
                // Partido a nivel de bytes (.001, .002…): se leen como un único flujo.
                val parts = generateSequence(1) { it + 1 }
                    .map { volumes.resolve(v.baseName + "." + it.toString().padStart(3, '0')) }
                    .takeWhile { it != null }
                    .filterNotNull()
                    .toList()
                val stream = MultiFileInStream(parts).also { opened += it }
                SevenZip.openInArchive(null, stream, callback)
            } else {
                // RAR multivolumen: 7-Zip pide cada volumen al callback por su nombre.
                val stream = callback.getStream(volumes.firstName)
                    ?: throw IOException("No se puede abrir ${volumes.first.name}")
                SevenZip.openInArchive(null, stream, callback)
            }
            try {
                return block(archive)
            } finally {
                runCatching { archive.close() }
            }
        } catch (e: SevenZipException) {
            if (password == null && callback.passwordAsked) throw PasswordRequiredException()
            throw IOException(e.message ?: "No se pudo abrir el archivo", e)
        } finally {
            opened.forEach { runCatching { it.close() } }
        }
    }

    private class OpenCallback(
        private val volumes: VolumeSet,
        private val password: String?,
        private val opened: MutableList<Closeable>,
    ) : IArchiveOpenCallback, IArchiveOpenVolumeCallback, ICryptoGetTextPassword {
        private var currentName: String = volumes.firstName
        private val files = HashMap<String, RandomAccessFile>()
        var passwordAsked = false

        override fun setTotal(files: Long?, bytes: Long?) = Unit
        override fun setCompleted(files: Long?, bytes: Long?) = Unit

        override fun getProperty(propID: PropID): Any? =
            if (propID == PropID.NAME) currentName else null

        override fun getStream(filename: String): IInStream? {
            val key = filename.substringAfterLast('/')
            val raf = files[key] ?: run {
                val file = volumes.resolve(key) ?: return null
                RandomAccessFile(file, "r").also {
                    files[key] = it
                    opened += it
                }
            }
            raf.seek(0)
            currentName = key
            return RandomAccessFileInStream(raf)
        }

        override fun cryptoGetTextPassword(): String {
            passwordAsked = true
            return password ?: throw SevenZipException("password required")
        }
    }

    private class ExtractCallback(
        private val archive: IInArchive,
        private val dest: File,
        private val password: String?,
        private val onProgress: (Long, Long) -> Unit,
        private val isCancelled: () -> Boolean,
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {
        val extracted = mutableListOf<String>()
        var failure: ExtractOperationResult? = null
        var encrypted = false
        private var total = 0L
        private var out: OutputStream? = null
        private val destPath = dest.canonicalPath + File.separator

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            closeCurrent()
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            val path = (archive.getStringProperty(index, PropID.PATH) ?: "archivo_$index").replace('\\', '/')
            if ((archive.getProperty(index, PropID.ENCRYPTED) as? Boolean) == true) encrypted = true
            val target = File(dest, path)
            // Evita "zip slip": nada puede escribirse fuera de la carpeta destino.
            if (!target.canonicalPath.startsWith(destPath)) return null
            if ((archive.getProperty(index, PropID.IS_FOLDER) as? Boolean) == true) {
                target.mkdirs()
                return null
            }
            target.parentFile?.mkdirs()
            val stream = BufferedOutputStream(target.outputStream(), 1 shl 20)
            out = stream
            extracted += path.trimStart('/')
            return ISequentialOutStream { data ->
                try {
                    stream.write(data)
                } catch (e: IOException) {
                    throw SevenZipException("No se pudo escribir ${target.name}: ${e.message}", e)
                }
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            closeCurrent()
            if (extractOperationResult != ExtractOperationResult.OK && failure == null) {
                failure = extractOperationResult
            }
        }

        override fun setTotal(total: Long) {
            this.total = total
        }

        override fun setCompleted(complete: Long) {
            if (isCancelled()) throw SevenZipException("Cancelado")
            onProgress(complete, total)
        }

        override fun cryptoGetTextPassword(): String {
            encrypted = true
            return password ?: throw SevenZipException("password required")
        }

        fun closeCurrent() {
            out?.let { runCatching { it.close() } }
            out = null
        }
    }
}

/** Une varios archivos (.001, .002…) en un solo flujo con acceso aleatorio. */
private class MultiFileInStream(files: List<File>) : IInStream {
    private val rafs = files.map { RandomAccessFile(it, "r") }
    private val lengths = rafs.map { it.length() }
    private val starts = LongArray(lengths.size).also { s ->
        for (i in 1 until s.size) s[i] = s[i - 1] + lengths[i - 1]
    }
    private val total = lengths.sum()
    private var position = 0L

    override fun seek(offset: Long, seekOrigin: Int): Long {
        position = when (seekOrigin) {
            ISeekableStream.SEEK_SET -> offset
            ISeekableStream.SEEK_CUR -> position + offset
            ISeekableStream.SEEK_END -> total + offset
            else -> throw SevenZipException("Origen de seek no válido: $seekOrigin")
        }
        return position
    }

    override fun read(data: ByteArray): Int {
        if (data.isEmpty() || position >= total) return 0
        var i = starts.indexOfLast { it <= position }
        if (i < 0) i = 0
        val offsetInPart = position - starts[i]
        val available = lengths[i] - offsetInPart
        if (available <= 0) return 0
        val raf = rafs[i]
        return try {
            raf.seek(offsetInPart)
            val n = raf.read(data, 0, minOf(data.size.toLong(), available).toInt())
            if (n <= 0) 0 else n.also { position += it }
        } catch (e: IOException) {
            throw SevenZipException(e)
        }
    }

    override fun close() {
        rafs.forEach { runCatching { it.close() } }
    }
}

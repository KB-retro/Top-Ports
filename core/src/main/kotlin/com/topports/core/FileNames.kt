package com.topports.core

import java.util.Locale

object FileNames {

    enum class SplitStyle {
        NONE,

        /** Partido a nivel de bytes: `juego.7z.001`, `juego.zip.002`, `juego.apk.001`. */
        NUMBERED,

        /** Volúmenes RAR: `juego.part1.rar`, `juego.part02.rar`. */
        RAR_PART,

        /**
         * ZIP partido al estilo WinZip: `juego.z01`, `juego.z02`… y el último `juego.zip`, que es el
         * que se abre (7-Zip pide los demás). El `.zip` va como volumen 1 para abrirse primero.
         */
        ZIP_SPAN,
    }

    data class Volume(val baseName: String, val index: Int, val style: SplitStyle)

    private val numbered = Regex("""^(.+)\.(\d{3})$""")
    private val rarPart = Regex("""^(.+)\.part(\d+)\.rar$""", RegexOption.IGNORE_CASE)
    private val zipSpan = Regex("""^(.+)\.z(\d{2,3})$""", RegexOption.IGNORE_CASE)
    private val obbName = Regex("""^(?:main|patch)\.\d+\.(.+)\.obb$""", RegexOption.IGNORE_CASE)

    private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")
    private val bundleExtensions = setOf("xapk", "apks", "apkm")

    fun volumeOf(name: String): Volume {
        rarPart.matchEntire(name)?.let {
            return Volume(it.groupValues[1] + ".rar", it.groupValues[2].toInt(), SplitStyle.RAR_PART)
        }
        // ".z64" es una ROM de N64, no la parte 64 de un ZIP.
        zipSpan.matchEntire(name)?.takeUnless { name.endsWith(".z64", ignoreCase = true) }?.let {
            return Volume(it.groupValues[1] + ".zip", it.groupValues[2].toInt() + 1, SplitStyle.ZIP_SPAN)
        }
        numbered.matchEntire(name)?.let {
            return Volume(it.groupValues[1], it.groupValues[2].toInt(), SplitStyle.NUMBERED)
        }
        return Volume(name, 1, SplitStyle.NONE)
    }

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** Tipo de un archivo a partir de su nombre (ya sin el sufijo de volumen). */
    fun kindOf(baseName: String, split: SplitStyle = SplitStyle.NONE): PackageKind {
        val ext = extension(baseName)
        return when {
            split == SplitStyle.RAR_PART || split == SplitStyle.ZIP_SPAN -> PackageKind.ARCHIVE
            // Un APK partido en .001/.002 se une al instalar: sigue siendo un APK.
            ext == "apk" -> PackageKind.APK
            ext in bundleExtensions -> PackageKind.APK_BUNDLE
            ext in archiveExtensions -> PackageKind.ARCHIVE
            ext == "obb" -> PackageKind.OBB
            // `juego.001` sin extensión interna: casi siempre un 7z/zip partido.
            split == SplitStyle.NUMBERED && ext.isEmpty() -> PackageKind.ARCHIVE
            else -> PackageKind.OTHER
        }
    }

    fun kindOfFile(name: String): PackageKind {
        val v = volumeOf(name)
        return kindOf(v.baseName, v.style)
    }

    /** ¿Se puede abrir con el extractor integrado? */
    fun isArchive(name: String): Boolean =
        kindOfFile(name).let { it == PackageKind.ARCHIVE || it == PackageKind.APK_BUNDLE }

    fun isApk(name: String): Boolean = extension(name) == "apk"

    /** `main.123.com.empresa.juego.obb` → `com.empresa.juego`. */
    fun obbPackage(fileName: String): String? = obbName.matchEntire(fileName)?.groupValues?.get(1)

    /** Ordena los volúmenes de un archivo partido; el primero es el que se abre. */
    fun <T> sortVolumes(items: List<T>, nameOf: (T) -> String): List<T> =
        items.sortedBy { volumeOf(nameOf(it)).index }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100) String.format(Locale.ROOT, "%.0f %s", value, units[unit])
        else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }

    /** Nombre seguro para usar como carpeta. */
    fun safeFolderName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").trim().trim('.').take(80).ifBlank { "juego" }
}

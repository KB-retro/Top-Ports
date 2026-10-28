package com.topports.core

import java.util.Locale

/** Un APK instalado desde Top Ports: qué archivo del canal era. */
data class InstallRecord(
    val packageName: String,
    val fileName: String,
    val fileId: Int,
    val gameId: Long,
    /** Tamaño del archivo instalado (0 en registros de versiones antiguas de la app). */
    val fileSize: Long = 0,
)

/** App instalada en el dispositivo. [record] es null si no se instaló desde Top Ports. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val record: InstallRecord? = null,
)

data class UpdateItem(
    val packageName: String,
    val appLabel: String,
    val installedVersion: String?,
    val newVersion: String?,
    val game: Game,
    val pkg: GamePackage,
)

object UpdateFinder {

    private val versionRegex = Regex("""(?i)v?(\d+(?:[._]\d+)+)""")
    private val looseWords = Regex("""(?i)android|port|apk|release|final|nativo|native""")

    /** "DW5-Android-v1.5.apk" → "1.5"; null si el nombre no lleva versión. */
    fun version(fileName: String): String? =
        versionRegex.findAll(FileNames.volumeOf(fileName).baseName.substringBeforeLast('.'))
            .lastOrNull()?.groupValues?.get(1)?.replace('_', '.')

    /** Nombre sin versión ni extensión: identifica el mismo APK entre versiones. */
    fun normalize(fileName: String): String =
        FileNames.volumeOf(fileName).baseName.substringBeforeLast('.')
            .replace(versionRegex, "")
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    /** Más permisivo, para comparar con el nombre visible de la app. */
    fun normalizeLoose(text: String): String =
        text.substringBeforeLast(".apk").replace(versionRegex, "").replace(looseWords, "")
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Compara versiones numéricas ("1.10" > "1.9"). */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun find(installed: List<InstalledApp>, games: List<Game>): List<UpdateItem> {
        val apks = games.flatMap { g -> g.packages.filter { it.kind == PackageKind.APK }.map { g to it } }
        val result = LinkedHashMap<String, UpdateItem>()
        for (app in installed) {
            val item = app.record?.let { tracked(app, it, games) } ?: untracked(app, apks)
            if (item != null) result[app.packageName] = item
        }
        return result.values.sortedByDescending { it.game.id }
    }

    /**
     * Instalada desde Top Ports: el mismo archivo publicado de nuevo con otra versión.
     * Se comparan las versiones de los nombres de archivo (la del canal con la del archivo que se
     * instaló), no la versión interna de la app, que en los ports suele seguir otra numeración.
     */
    private fun tracked(app: InstalledApp, record: InstallRecord, games: List<Game>): UpdateItem? {
        val key = normalize(record.fileName)
        val newest = games
            .flatMap { g -> g.packages.filter { normalize(it.name) == key }.map { g to it } }
            .maxByOrNull { it.first.id } ?: return null
        val (game, pkg) = newest
        if (pkg.parts.first().id == record.fileId) return null
        val newV = version(pkg.name)
        val oldV = version(record.fileName)
        val isNewer = if (newV != null && oldV != null) {
            compareVersions(newV, oldV) > 0
        } else {
            // Sin versión en el nombre: solo si es otro archivo (otro tamaño) en un post más nuevo.
            // Un repost del mismo APK no es una actualización.
            game.id > record.gameId && record.fileSize > 0 && pkg.size != record.fileSize
        }
        if (!isNewer) return null
        return UpdateItem(app.packageName, app.label, oldV ?: app.versionName, newV, game, pkg)
    }

    /**
     * Instalada por otro medio: solo si el canal tiene ese mismo APK con la versión instalada
     * (así se sabe que las versiones se pueden comparar) y además otro más nuevo.
     */
    private fun untracked(app: InstalledApp, apks: List<Pair<Game, GamePackage>>): UpdateItem? {
        val label = normalizeLoose(app.label)
        val installedV = app.versionName ?: return null
        if (label.length < 3) return null
        val candidates = apks
            .filter { (g, p) -> normalizeLoose(p.name) == label || normalizeLoose(g.title) == label }
            .mapNotNull { (g, p) -> version(p.name)?.let { Triple(g, p, it) } }
        if (candidates.none { compareVersions(it.third, installedV) == 0 }) return null
        val match = candidates.maxWithOrNull { a, b -> compareVersions(a.third, b.third) } ?: return null
        if (compareVersions(match.third, installedV) <= 0) return null
        return UpdateItem(app.packageName, app.label, installedV, match.third, match.first, match.second)
    }
}

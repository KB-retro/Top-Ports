package com.topports.core

/** Qué hacer con el contenido de un archivo ya extraído. */
data class InstallPlan(
    /** Rutas relativas de los APK a instalar. */
    val apks: List<String>,
    /** Si los APK son partes de una misma app (split APKs) y van en una sola sesión. */
    val isSplitApk: Boolean,
    val obbs: List<Target>,
    /** Carpetas `Android/data/<paquete>` que habría que copiar. */
    val dataDirs: List<Target>,
) {
    data class Target(val path: String, val packageName: String)

    val isEmpty: Boolean get() = apks.isEmpty() && obbs.isEmpty() && dataDirs.isEmpty()
}

object InstallPlanner {

    private val obbDir = Regex("""(?:^|/)Android/obb/([^/]+)/[^/]+\.obb$""", RegexOption.IGNORE_CASE)
    private val dataDir = Regex("""^(.*?Android/data/([^/]+))/""", RegexOption.IGNORE_CASE)

    /** [paths] son rutas relativas con `/` como separador. */
    fun plan(paths: List<String>): InstallPlan {
        val apks = paths.filter { FileNames.isApk(it) }.sorted()
        val names = apks.map { it.substringAfterLast('/') }
        val isSplit = apks.size > 1 && names.any {
            it.equals("base.apk", ignoreCase = true) ||
                it.startsWith("split_", ignoreCase = true) ||
                it.startsWith("config.", ignoreCase = true)
        }

        val obbs = paths.filter { FileNames.extension(it) == "obb" }.mapNotNull { path ->
            val pkg = obbDir.find(path)?.groupValues?.get(1)
                ?: FileNames.obbPackage(path.substringAfterLast('/'))
            pkg?.let { InstallPlan.Target(path, it) }
        }

        val dataDirs = paths.mapNotNull { path ->
            dataDir.find(path)?.let { InstallPlan.Target(it.groupValues[1], it.groupValues[2]) }
        }.distinct()

        return InstallPlan(apks, isSplit, obbs, dataDirs)
    }
}

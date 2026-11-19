package com.topports.app.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object Storage {

    val externalRoot: File get() = Environment.getExternalStorageDirectory()

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Pantalla de ajustes para "Acceso a todos los archivos" (Android 11+). */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Carpeta /TopPorts de siempre: en la memoria interna si hay permiso de archivos; si no, en la
     * carpeta privada de la app. Ahí va también la copia de ajustes, para encontrarla siempre.
     */
    fun defaultTopPortsDir(context: Context): File {
        val dir = if (hasAllFilesAccess(context)) File(externalRoot, "TopPorts")
        else File(context.getExternalFilesDir(null) ?: context.filesDir, "TopPorts")
        dir.mkdirs()
        return dir
    }

    /**
     * Carpeta de los juegos: la elegida en Ajustes → Almacenamiento (p. ej. en la tarjeta SD) o, si
     * no se ha elegido ninguna, /TopPorts. Dentro van las carpetas de cada juego y la caché de
     * descargas de Telegram (.telegram), para que todo quede en la misma memoria.
     */
    fun topPortsDir(context: Context): File {
        val custom = customGamesDir(context) ?: return defaultTopPortsDir(context)
        // Sin la tarjeta o sin permiso no se crea nada: [gamesDirProblem] lo avisa.
        if (gamesDirProblem(context) == null) custom.mkdirs()
        return custom
    }

    private fun prefs(context: Context) = context.getSharedPreferences("storage", Context.MODE_PRIVATE)

    /** Carpeta de juegos elegida a mano (null = /TopPorts). */
    fun customGamesDir(context: Context): File? =
        prefs(context).getString("gamesDir", null)?.let(::File).also { chosenDir = it }

    /** La carpeta de juegos elegida, para no borrarla nunca entera (ver [isSafeToDelete]). */
    @Volatile
    private var chosenDir: File? = null

    fun setCustomGamesDir(context: Context, dir: File?) {
        prefs(context).edit().apply { if (dir == null) remove("gamesDir") else putString("gamesDir", dir.path) }.apply()
    }

    /**
     * Por qué no se puede usar ahora la carpeta de juegos elegida (falta la tarjeta, el permiso…),
     * o null si está lista. Sin carpeta elegida, siempre lista.
     */
    fun gamesDirProblem(context: Context): String? {
        val dir = customGamesDir(context) ?: return null
        val where = displayPath(context, dir)
        if (!hasAllFilesAccess(context)) return "Falta el permiso de todos los archivos para usar la carpeta $where"
        if (volumeOf(context, dir) == null) return "La carpeta $where no está disponible: ¿falta la tarjeta SD?"
        if (!dir.isDirectory && !dir.mkdirs()) return "No se puede crear la carpeta $where"
        if (!dir.canWrite()) return "No se puede escribir en $where"
        return null
    }

    /**
     * ¿Se puede usar [dir] como carpeta de juegos? Devuelve el motivo si no. No vale la raíz de una
     * memoria ni las carpetas del sistema (al borrar un juego se borra su carpeta, y ahí se podría
     * borrar lo que no es del juego), ni nada dentro de Android/. Prueba a escribir un archivo.
     */
    fun checkGamesDir(context: Context, dir: File): String? {
        if (!hasAllFilesAccess(context)) return "Primero da el permiso de todos los archivos"
        val volume = volumeOf(context, dir) ?: return "Elige una carpeta de la memoria interna o de una tarjeta SD"
        val rel = relativePath(volume.root, dir) ?: return "Elige una carpeta de la memoria interna o de una tarjeta SD"
        if (rel.isEmpty()) return "No se puede usar la raíz de la memoria: entra en una carpeta o crea una"
        val first = rel.substringBefore('/').lowercase()
        if (first == "android") return "No se pueden usar las carpetas de Android/"
        if ('/' !in rel && first in protectedRootNames && first != "topports") {
            return "${dir.name} es una carpeta del sistema: crea una carpeta dentro y elige esa"
        }
        if (!dir.isDirectory) return "La carpeta no existe"
        val probe = File(dir, ".topports-prueba")
        val writable = runCatching { probe.writeText("ok"); probe.readText() == "ok" }.getOrDefault(false)
        probe.delete()
        if (!writable) return "Android no deja escribir en esta carpeta"
        return null
    }

    /** Una memoria montada: la interna o una tarjeta SD / USB. */
    data class Volume(val root: File, val label: String, val removable: Boolean)

    /** Memorias montadas ahora mismo; la interna, primero. */
    fun volumes(context: Context): List<Volume> {
        val list = mutableListOf(Volume(externalRoot, "Memoria interna", false))
        val sm = context.getSystemService(StorageManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sm != null) {
            runCatching {
                sm.storageVolumes.forEach { v ->
                    val dir = v.directory ?: return@forEach
                    if (v.isPrimary || v.state != Environment.MEDIA_MOUNTED) return@forEach
                    val label = runCatching { v.getDescription(context) }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Tarjeta SD"
                    list += Volume(dir, label, true)
                }
            }
        } else {
            // Android 8-10: la raíz de cada memoria sale de su carpeta privada (…/Android/data/<app>).
            context.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val i = d.path.indexOf("/Android/data/")
                if (i <= 0) return@forEach
                val root = File(d.path.substring(0, i))
                if (Environment.getExternalStorageState(d) == Environment.MEDIA_MOUNTED) list += Volume(root, "Tarjeta SD", true)
            }
        }
        return list.distinctBy { it.root.path }
    }

    /** La memoria (montada) en la que está [file], o null. */
    fun volumeOf(context: Context, file: File): Volume? =
        volumes(context).firstOrNull { relativePath(it.root, file) != null }

    /** Ruta de [file] dentro de [root] ("" si es la propia raíz), o null si está fuera. */
    private fun relativePath(root: File, file: File): String? {
        val r = root.absolutePath.trimEnd('/')
        val f = file.absolutePath.trimEnd('/')
        return when {
            f == r -> ""
            f.startsWith("$r/") -> f.removePrefix("$r/")
            else -> null
        }
    }

    /** Ruta corta para la interfaz: "/TopPorts/Juego" o "SD:/Ports/Juego". */
    fun displayPath(context: Context, file: File): String {
        val all = volumes(context)
        val volume = all.firstOrNull { relativePath(it.root, file) != null }
        // Sin montar (p. ej. la tarjeta fuera): se reconoce por /storage/<id>/.
        if (volume == null) {
            val m = Regex("^/storage/([^/]+)(/.*)?$").find(file.absolutePath)
            if (m != null && m.groupValues[1] != "emulated") return "SD:" + m.groupValues[2].ifEmpty { "/" }
            return file.path
        }
        val rel = "/" + relativePath(volume.root, file)!!
        if (!volume.removable) return rel
        val removables = all.filter { it.removable }
        val name = if (removables.size > 1) "SD${removables.indexOf(volume) + 1}" else "SD"
        return "$name:$rel"
    }

    /** Carpetas de caché de Telegram usadas alguna vez (para limpiarlas aunque ya no se usen). */
    fun telegramDirs(context: Context): List<File> =
        prefs(context).getStringSet("telegramDirs", emptySet()).orEmpty().map(::File)

    fun rememberTelegramDir(context: Context, dir: File) {
        val all = prefs(context).getStringSet("telegramDirs", emptySet()).orEmpty()
        if (dir.path !in all) prefs(context).edit().putStringSet("telegramDirs", all + dir.path).apply()
    }

    /** Carpetas del sistema en la raíz que nunca se registran ni se borran como datos de un juego. */
    val protectedRootNames = setOf(
        "android", "download", "downloads", "dcim", "pictures", "music", "movies", "documents",
        "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings", "topports",
    )

    /** ¿Se puede borrar [file] como dato de un juego sin riesgo para el sistema? */
    fun isSafeToDelete(file: File): Boolean =
        isSafeToDelete(file, listOfNotNull(externalRoot, sdRootOf(file.absoluteFile)))

    private fun isSafeToDelete(file: File, roots: List<File>): Boolean {
        val path = file.canonicalFile
        if (chosenDir?.let { runCatching { it.canonicalFile }.getOrNull() } == path) return false
        for (r in roots) {
            val root = runCatching { r.canonicalFile }.getOrNull() ?: continue
            if (path == root) return false
            if (!path.path.startsWith(root.path + File.separator)) continue
            // En la raíz solo se permiten carpetas propias del juego, nunca las del sistema.
            val isTopLevel = path.parentFile == root
            return !(isTopLevel && path.name.lowercase() in protectedRootNames)
        }
        // Fuera del almacenamiento compartido (carpetas propias de la app): sin riesgo.
        return true
    }

    /** Raíz de la tarjeta SD o memoria USB (/storage/<id>) en la que está [file], o null. */
    private fun sdRootOf(file: File): File? {
        val id = Regex("^/storage/([^/]+)").find(file.path)?.groupValues?.get(1) ?: return null
        return if (id == "emulated" || id == "self") null else File("/storage/$id")
    }

    fun obbDir(packageName: String): File = File(externalRoot, "Android/obb/$packageName")

    fun dataDir(packageName: String): File = File(externalRoot, "Android/data/$packageName")

    fun freeBytes(dir: File): Long {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        return runCatching { StatFs((d ?: dir).path).availableBytes }.getOrDefault(Long.MAX_VALUE)
    }

    fun totalBytes(dir: File): Long = runCatching { StatFs(dir.path).totalBytes }.getOrDefault(0)
}

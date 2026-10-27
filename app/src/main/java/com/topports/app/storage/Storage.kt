package com.topports.app.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.topports.core.FileNames
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

    /** Carpeta donde se extraen los juegos: /sdcard/TopPorts si hay permiso. */
    fun topPortsDir(context: Context): File {
        val dir = if (hasAllFilesAccess(context)) File(externalRoot, "TopPorts")
        else File(context.getExternalFilesDir(null) ?: context.filesDir, "TopPorts")
        dir.mkdirs()
        return dir
    }

    fun gameDir(context: Context, title: String): File =
        File(topPortsDir(context), FileNames.safeFolderName(title)).apply { mkdirs() }

    fun obbDir(packageName: String): File = File(externalRoot, "Android/obb/$packageName")

    fun dataDir(packageName: String): File = File(externalRoot, "Android/data/$packageName")

    fun freeBytes(dir: File): Long {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        return runCatching { StatFs((d ?: dir).path).availableBytes }.getOrDefault(Long.MAX_VALUE)
    }

    fun totalBytes(dir: File): Long = runCatching { StatFs(dir.path).totalBytes }.getOrDefault(0)
}

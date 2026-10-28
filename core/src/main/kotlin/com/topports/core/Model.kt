package com.topports.core

/** Archivo alojado en Telegram (id de archivo de TDLib). */
data class RemoteFile(
    val id: Int,
    val name: String,
    val size: Long,
)

/** Foto de un post: miniatura para listas y versión grande para la ficha. */
data class PhotoRef(
    val thumbFileId: Int,
    val fullFileId: Int,
)

/** Vídeo de un post (normalmente un tutorial de instalación o configuración). */
data class VideoRef(
    val fileId: Int,
    val thumbFileId: Int?,
    val durationSeconds: Int,
    val caption: String,
)

/** Mensaje del canal reducido a lo que necesita el catálogo. */
data class ChannelMessage(
    val id: Long,
    val date: Int,
    val albumId: Long = 0,
    val text: String = "",
    val photo: PhotoRef? = null,
    val document: RemoteFile? = null,
    val video: VideoRef? = null,
    /** Última edición del mensaje en Telegram (0 si no se ha editado). */
    val editDate: Int = 0,
)

enum class PackageKind {
    /** APK suelto: se instala directamente. */
    APK,

    /** XAPK / APKS / APKM: zip con split APKs y a veces OBB. */
    APK_BUNDLE,

    /** ZIP, 7z, RAR o un archivo partido en volúmenes (.001, .partN.rar). */
    ARCHIVE,

    /** Datos OBB sueltos. */
    OBB,

    OTHER,
}

/** Un archivo lógico de un juego; puede venir en varias partes. */
data class GamePackage(
    val name: String,
    val kind: PackageKind,
    val parts: List<RemoteFile>,
) {
    val size: Long get() = parts.sumOf { it.size }
    val isMultiPart: Boolean get() = parts.size > 1

    /** Clave estable para guardar el estado de descarga / instalación. */
    val key: String get() = parts.joinToString("-") { it.id.toString() }
}

data class Game(
    val id: Long,
    val title: String,
    val description: String,
    val date: Int,
    val images: List<PhotoRef>,
    val packages: List<GamePackage>,
    val messageIds: List<Long>,
    val videos: List<VideoRef> = emptyList(),
    /** Etiquetas del post sin la almohadilla y en mayúsculas ("N64", "X360"). */
    val tags: List<String> = emptyList(),
    /**
     * Última actividad del juego: archivos o avisos añadidos después y ediciones del post
     * (p. ej. al cambiar el APK por una versión nueva). Igual a [date] si no ha cambiado.
     */
    val updated: Int = date,
) {
    val size: Long get() = packages.sumOf { it.size }
}

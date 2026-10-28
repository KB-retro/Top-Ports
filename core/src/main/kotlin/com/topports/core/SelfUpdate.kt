package com.topports.core

/** APK de la propia tienda publicado en el canal ("TopPorts-0.1.47.apk"). */
data class AppRelease(val file: RemoteFile, val version: String, val messageId: Long, val date: Int)

object SelfUpdate {

    private val namePattern = Regex("""(?i)^top[ _-]?ports[ _-]*v?(\d+(?:\.\d+)+)\.apk$""")

    /** Versión si [fileName] es un APK de Top Ports; null si es cualquier otro archivo. */
    fun versionOf(fileName: String): String? = namePattern.matchEntire(fileName.trim())?.groupValues?.get(1)

    /** Mensajes que son APK de la tienda: no forman parte del catálogo de juegos. */
    fun isAppRelease(message: ChannelMessage): Boolean =
        message.document?.let { versionOf(it.name) } != null

    /** La versión más nueva publicada, solo si es mayor que [currentVersion]. */
    fun newest(messages: List<ChannelMessage>, currentVersion: String): AppRelease? =
        messages.mapNotNull { m ->
            val doc = m.document ?: return@mapNotNull null
            versionOf(doc.name)?.let { AppRelease(doc, it, m.id, m.date) }
        }
            .maxWithOrNull { a, b -> UpdateFinder.compareVersions(a.version, b.version) }
            ?.takeIf { UpdateFinder.compareVersions(it.version, currentVersion) > 0 }
}

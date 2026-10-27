package com.topports.core

/**
 * Agrupa los mensajes del canal en fichas de juego.
 *
 * Un post típico es una foto (o álbum) con la descripción seguido de uno o varios
 * archivos. Reglas, recorriendo los mensajes del más antiguo al más nuevo:
 *  - los mensajes del mismo álbum van juntos;
 *  - un archivo sin texto se añade a la ficha en curso;
 *  - un archivo que es otra parte de un paquete ya visto (`.7z.002`, `.part2.rar`)
 *    se añade aunque tenga texto ("Parte 2");
 *  - un texto o una foto empiezan ficha nueva si la actual ya tiene archivos;
 *  - más de [MAX_GAP_SECONDS] entre mensajes siempre empieza ficha nueva.
 * Las fichas sin archivos (avisos, encuestas…) se descartan.
 */
object CatalogBuilder {

    const val MAX_GAP_SECONDS = 6 * 60 * 60

    private class Draft(first: ChannelMessage) {
        val ids = mutableListOf<Long>()
        val texts = mutableListOf<String>()
        val photos = mutableListOf<PhotoRef>()
        val docs = mutableListOf<RemoteFile>()
        val firstId = first.id
        val date = first.date
        var lastDate = first.date
        var lastAlbumId = 0L

        fun add(m: ChannelMessage) {
            ids += m.id
            // El pie de un archivo ("Parte 2", "Datos") solo cuenta si aún no hay descripción.
            if (m.text.isNotBlank() && (m.document == null || texts.isEmpty())) texts += m.text.trim()
            m.photo?.let { photos += it }
            m.document?.let { docs += it }
            lastDate = m.date
            lastAlbumId = m.albumId
        }

        fun hasText() = texts.isNotEmpty()

        fun hasVolumeOf(file: RemoteFile): Boolean {
            val v = FileNames.volumeOf(file.name)
            if (v.style == FileNames.SplitStyle.NONE) return false
            return docs.any { FileNames.volumeOf(it.name).baseName.equals(v.baseName, ignoreCase = true) }
        }
    }

    fun build(messages: List<ChannelMessage>): List<Game> {
        val drafts = mutableListOf<Draft>()
        var current: Draft? = null
        for (m in messages.sortedBy { it.id }) {
            val cur = current
            val startsNew = when {
                cur == null -> true
                m.albumId != 0L && m.albumId == cur.lastAlbumId -> false
                m.date - cur.lastDate > MAX_GAP_SECONDS -> true
                m.document != null && cur.hasVolumeOf(m.document) -> false
                m.document != null && m.text.isBlank() -> false
                // Archivo con texto justo después de una descripción sin archivos.
                m.document != null && cur.docs.isEmpty() -> false
                m.text.isBlank() && m.photo != null -> cur.docs.isNotEmpty()
                m.text.isNotBlank() -> cur.docs.isNotEmpty() || cur.hasText()
                else -> false
            }
            if (startsNew) {
                current = Draft(m).also { drafts += it }
            }
            current!!.add(m)
        }
        return drafts
            .filter { it.docs.isNotEmpty() }
            .map { it.toGame() }
            .sortedByDescending { it.id }
    }

    private fun Draft.toGame(): Game {
        val packages = groupPackages(docs)
        val allText = texts.joinToString("\n\n")
        val lines = allText.lines().map { it.trim() }
        val titleIndex = lines.indexOfFirst { cleanTitle(it).isNotEmpty() }
        val title = if (titleIndex >= 0) cleanTitle(lines[titleIndex]).take(80)
        else packages.first().name.substringBeforeLast('.')
        val description = if (titleIndex >= 0) {
            lines.drop(titleIndex + 1).joinToString("\n").trim()
        } else ""
        return Game(
            id = firstId,
            title = title,
            description = description,
            date = date,
            images = photos.toList(),
            packages = packages,
            messageIds = ids.toList(),
        )
    }

    fun groupPackages(docs: List<RemoteFile>): List<GamePackage> {
        val groups = LinkedHashMap<String, MutableList<RemoteFile>>()
        for (doc in docs) {
            val base = FileNames.volumeOf(doc.name).baseName.lowercase()
            groups.getOrPut(base) { mutableListOf() } += doc
        }
        return groups.values.map { files ->
            val sorted = FileNames.sortVolumes(files.distinctBy { it.id }) { it.name }
            val first = FileNames.volumeOf(sorted.first().name)
            GamePackage(
                name = first.baseName,
                kind = FileNames.kindOf(first.baseName, first.style),
                parts = sorted,
            )
        }
    }

    private val rootHint = Regex("""ra[ií]z|memoria interna|almacenamiento interno|internal storage""", RegexOption.IGNORE_CASE)

    /**
     * ¿Las instrucciones piden descomprimir en la raíz de la memoria interna?
     * ("descomprimir el archivo comprimido en la raiz de la memoria interna")
     */
    fun wantsStorageRoot(description: String): Boolean = rootHint.containsMatchIn(description)

    /** Quita marcas de formato y adornos típicos del título ("**Juego**", "🎮 Juego"). */
    fun cleanTitle(line: String): String =
        line.replace(Regex("""[*_`~#>|]"""), "")
            .replace(Regex("""^[^\p{L}\p{N}]+"""), "")
            .trim()
}

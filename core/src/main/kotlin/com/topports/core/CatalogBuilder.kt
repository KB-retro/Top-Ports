package com.topports.core

/**
 * Agrupa los mensajes del canal en fichas de juego.
 *
 * En el canal cada juego empieza con una foto (la carátula, con la descripción) y le
 * siguen sus archivos. Reglas, recorriendo los mensajes del más antiguo al más nuevo:
 *  - **solo una foto empieza un juego nuevo**, y solo si la ficha en curso ya tiene
 *    archivos. Archivos (con o sin texto, aunque lleguen horas después), vídeos y textos
 *    sin foto siempre van con el juego anterior;
 *  - los mensajes del mismo álbum van juntos;
 *  - antes de los archivos, fotos y textos que llegan en menos de [SAME_POST_SECONDS]
 *    son más partes del mismo post ("Configuración del mando", "Mods"…) y se añaden a la
 *    ficha; tras un aviso suelto sin archivos, una foto más tardía empieza otro juego;
 *  - un texto suelto después de los archivos (avisos) no se añade a la descripción.
 * Las fichas sin archivos (avisos, encuestas…) se descartan.
 */
object CatalogBuilder {

    /** Textos e imágenes seguidos sin archivos todavía: mismo post si llegan dentro de este margen. */
    const val SAME_POST_SECONDS = 30 * 60


    private class Draft(first: ChannelMessage) {
        val ids = mutableListOf<Long>()
        val texts = mutableListOf<String>()
        val photos = mutableListOf<PhotoRef>()
        val docs = mutableListOf<RemoteFile>()
        val videos = mutableListOf<VideoRef>()
        val firstId = first.id
        val date = first.date
        var lastDate = first.date
        var lastAlbumId = 0L

        fun add(m: ChannelMessage) {
            ids += m.id
            m.video?.let { videos += it }
            // El pie de un archivo ("Parte 2", "Datos") solo cuenta si aún no hay descripción.
            // El texto de un vídeo es su título y ya va en VideoRef.caption.
            if (m.video == null && m.text.isNotBlank() && (m.document == null || texts.isEmpty())) {
                texts += m.text.trim()
            }
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
                // Sin foto no hay juego nuevo: archivos, vídeos y textos van con el anterior.
                m.photo == null -> false
                cur.docs.isNotEmpty() -> true
                else -> cur.hasText() && m.date - cur.lastDate > SAME_POST_SECONDS
            }
            // Avisos de texto tras los archivos: no forman parte de la descripción del juego.
            if (!startsNew && cur != null && cur.docs.isNotEmpty() &&
                m.photo == null && m.document == null && m.video == null
            ) {
                continue
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
        val tags = tagsOf(allText)
        // Las etiquetas se muestran aparte: fuera del título y sin líneas que solo tengan etiquetas.
        val lines = allText.lines().map { it.trim() }
            .filterNot { it.isNotEmpty() && stripTags(it).isEmpty() }
        val titleIndex = lines.indexOfFirst { cleanTitle(stripTags(it)).isNotEmpty() }
        val title = if (titleIndex >= 0) cleanTitle(stripTags(lines[titleIndex])).take(80)
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
            videos = videos.toList(),
            tags = tags,
        )
    }

    private val tagPattern = Regex("""(?<![\p{L}\p{N}_&/])#([\p{L}\p{N}_]*\p{L}[\p{L}\p{N}_]*)""")

    /** Etiquetas de sistema del post ("#N64 #X360" → N64, X360), sin repetir. */
    fun tagsOf(text: String): List<String> =
        tagPattern.findAll(text).map { it.groupValues[1].uppercase() }.distinct().toList()

    /** Quita las etiquetas de una línea. */
    fun stripTags(line: String): String =
        line.replace(tagPattern, "").replace(Regex("""\s{2,}"""), " ").trim()

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

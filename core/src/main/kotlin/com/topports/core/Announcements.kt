package com.topports.core

import java.text.Normalizer
import java.util.Locale

/** Aviso de novedades del canal ("ACTUALIZACIÓN DE PORTS" con la lista de ports). */
data class Announcement(
    val id: Long,
    val date: Int,
    val title: String,
    val body: String,
    /** Líneas de la lista ("- AM2R", "• Hades"…), sin el guion. */
    val items: List<String>,
)

object Announcements {

    private val header = Regex("""(?iu)actualizaci[oó]n(es)?\s+de\s+ports|nuevos\s+ports|novedades""")
    private val bullet = Regex("""^\s*[-•*–—·]\s*(.+)$""")

    private fun firstLine(text: String) = text.lineSequence().firstOrNull { it.isNotBlank() }

    /** Post solo de texto cuya primera línea anuncia novedades. */
    fun isAnnouncement(m: ChannelMessage): Boolean =
        m.photo == null && m.document == null && m.video == null &&
            firstLine(m.text)?.let { header.containsMatchIn(it) } == true

    fun parse(m: ChannelMessage): Announcement {
        val lines = m.text.lines()
        val first = lines.indexOfFirst { it.isNotBlank() }
        // "❗️❗️ACTUALIZACIÓN DE PORTS❗️❗️" → "ACTUALIZACIÓN DE PORTS"
        val title = lines[first].replace(Regex("""[^\p{L}\p{N} '\-:]"""), "").replace(Regex("""\s+"""), " ").trim()
        val body = lines.drop(first + 1).joinToString("\n").trim()
        val items = lines.mapNotNull { bullet.matchEntire(it)?.groupValues?.get(1)?.trim() }.filter { it.isNotEmpty() }
        return Announcement(m.id, m.date, title, body, items)
    }

    /** Todos los avisos, del más nuevo al más antiguo. */
    fun all(messages: List<ChannelMessage>): List<Announcement> =
        messages.filter(::isAnnouncement).sortedByDescending { it.id }.map(::parse)

    private fun key(text: String): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return UpdateFinder.normalizeLoose(plain.lowercase(Locale.ROOT).replace(Regex("""\bthe\b"""), ""))
    }

    /**
     * ¿El elemento de la lista se refiere a este juego? Igual tras normalizar, o uno contiene al
     * otro si es lo bastante largo ("Zelda Windwaker HD" ↔ "The Legend of Zelda - The Windwaker HD…").
     */
    fun matches(item: String, game: Game): Boolean {
        val a = key(item)
        val b = key(game.title)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        return minOf(a.length, b.length) >= 5 && (a in b || b in a)
    }

    /** Juegos de la lista del aviso que están en el catálogo. */
    fun gamesIn(announcement: Announcement, games: List<Game>): List<Game> =
        announcement.items.mapNotNull { item ->
            games.filter { it.id < announcement.id && matches(item, it) }.maxByOrNull { it.id }
        }.distinctBy { it.id }

    /** Los juegos que aparecen en un aviso cuentan como actualizados en la fecha del aviso. */
    fun applyTo(games: List<Game>, announcements: List<Announcement>): List<Game> {
        if (announcements.isEmpty()) return games
        val latest = HashMap<Long, Int>()
        for (a in announcements) {
            for (g in gamesIn(a, games)) latest[g.id] = maxOf(latest[g.id] ?: 0, a.date)
        }
        return games.map { g -> latest[g.id]?.let { g.copy(updated = maxOf(g.updated, it)) } ?: g }
    }
}

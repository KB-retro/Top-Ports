package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsTest {

    private val text = """
        ❗️❗️❗️❗️❗️ACTUALIZACIÓN DE PORTS❗️❗️❗️❗️❗️

        Actualizados los siguientes ports:

        - AM2R
        - Hades
        - ICO
        - The Legend of Zelda - The Windwaker HD Android Edition

        Que lo disfrutéis piratas🤘🤘🤘
    """.trimIndent()

    private fun game(id: Long, title: String) = Game(
        id = id, title = title, description = "", date = id.toInt(), images = emptyList(),
        packages = CatalogBuilder.groupPackages(listOf(RemoteFile(id.toInt(), "$title.apk", 1))),
        messageIds = listOf(id),
    )

    @Test
    fun parsesTheChannelPost() {
        val m = ChannelMessage(100, 5000, text = text)
        assertTrue(Announcements.isAnnouncement(m))
        val a = Announcements.parse(m)
        assertEquals("ACTUALIZACIÓN DE PORTS", a.title)
        assertEquals(listOf("AM2R", "Hades", "ICO", "The Legend of Zelda - The Windwaker HD Android Edition"), a.items)
        assertFalse(Announcements.isAnnouncement(ChannelMessage(1, 1, text = "Hades\nUn roguelike", photo = PhotoRef(1, 1))))
    }

    @Test
    fun matchesGamesAndMarksThemUpdated() {
        val games = listOf(
            game(10, "AM2R"), game(11, "Hades"), game(12, "Zelda Windwaker HD"), game(13, "Icewind Dale"),
            game(14, "ICO Port"),
        )
        val a = Announcements.parse(ChannelMessage(100, 5000, text = text))
        assertEquals(listOf(10L, 11L, 14L, 12L), Announcements.gamesIn(a, games).map { it.id })
        val updated = Announcements.applyTo(games, listOf(a))
        assertEquals(5000, updated.first { it.id == 11L }.updated)
        assertEquals(13, updated.first { it.id == 13L }.updated)
    }

    @Test
    fun announcementIsNotPartOfAGame() {
        val messages = listOf(
            ChannelMessage(1, 100, text = "Juego A", photo = PhotoRef(1, 1)),
            ChannelMessage(2, 110, document = RemoteFile(5, "a.apk", 10)),
            ChannelMessage(3, 900_000, text = text),
        )
        val games = CatalogBuilder.build(messages.filterNot(Announcements::isAnnouncement))
        assertEquals(110, games.single().updated)
    }
}

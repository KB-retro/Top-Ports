package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledFinderTest {

    private fun game(id: Long, title: String, vararg files: Pair<Int, String>) = Game(
        id = id, title = title, description = "", date = 0, images = emptyList(),
        packages = CatalogBuilder.groupPackages(files.map { RemoteFile(it.first, it.second, 100) }),
        messageIds = listOf(id),
    )

    @Test
    fun matchesTrackedAndUntrackedApps() {
        val dw5 = game(10, "Dynasty Warriors 5", 1 to "DW5-Android-v1.5.apk", 2 to "DW5.zip.001")
        val dw5new = game(20, "Dynasty Warriors 5", 3 to "DW5-Android-v1.6.apk")
        val mario = game(30, "Super Mario Sunshine", 4 to "Mareshine-2.0.0.apk")
        val other = game(40, "Otro", 5 to "Otro.apk")
        val apps = listOf(
            InstalledApp("com.dw5", "DW5", "1.5", InstallRecord("com.dw5", "DW5-Android-v1.5.apk", 1, 10)),
            InstalledApp("com.mare", "Mareshine", "2.0.0"),
            InstalledApp("com.whatsapp", "WhatsApp", "2.24"),
        )
        val found = InstalledFinder.find(apps, listOf(dw5, dw5new, mario, other))
        assertEquals(listOf("Dynasty Warriors 5", "Super Mario Sunshine"), found.map { it.game.title })
        // El juego instalado apunta a la publicación más reciente del mismo APK.
        assertEquals(20L, found[0].game.id)
        assertEquals("com.mare", found[1].apps.single().packageName)
    }

    @Test
    fun unrelatedAppsAreIgnored() {
        val g = game(1, "Half-Life", 1 to "HalfLife.apk")
        assertTrue(InstalledFinder.find(listOf(InstalledApp("com.x", "YouTube", "19.0")), listOf(g)).isEmpty())
    }
}

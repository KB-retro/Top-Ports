package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFinderTest {

    private fun game(id: Long, title: String, vararg files: Pair<Int, String>) = Game(
        id = id, title = title, description = "", date = 0, images = emptyList(),
        packages = CatalogBuilder.groupPackages(files.map { RemoteFile(it.first, it.second, 100) }),
        messageIds = listOf(id),
    )

    @Test
    fun versionsAndNames() {
        assertEquals("1.5", UpdateFinder.version("DW5-Android-v1.5.apk"))
        assertEquals("2.0.0", UpdateFinder.version("Mareshine-2.0.0.apk"))
        assertNull(UpdateFinder.version("MK9Recomp-snapdragon.apk"))
        assertEquals(UpdateFinder.normalize("DW5-Android-v1.5.apk"), UpdateFinder.normalize("DW5-Android-v1.6.apk"))
        assertTrue(UpdateFinder.compareVersions("1.10", "1.9") > 0)
        assertEquals(0, UpdateFinder.compareVersions("2.0", "2.0.0"))
    }

    @Test
    fun trackedInstallGetsNewerVersion() {
        val old = game(10, "Dynasty Warriors 5", 1 to "DW5-Android-v1.5.apk")
        val new = game(20, "Dynasty Warriors 5", 2 to "DW5-Android-v1.6.apk")
        val app = InstalledApp(
            "com.dw5", "DW5", "1.5",
            InstallRecord("com.dw5", "DW5-Android-v1.5.apk", fileId = 1, gameId = 10),
        )
        val updates = UpdateFinder.find(listOf(app), listOf(new, old))
        assertEquals(1, updates.size)
        assertEquals("1.5", updates[0].installedVersion)
        assertEquals("1.6", updates[0].newVersion)
        assertEquals(20L, updates[0].game.id)
    }

    @Test
    fun sameFileIsNotAnUpdate() {
        val g = game(10, "Dynasty Warriors 5", 1 to "DW5-Android-v1.5.apk")
        val app = InstalledApp("com.dw5", "DW5", "1.5", InstallRecord("com.dw5", "DW5-Android-v1.5.apk", 1, 10))
        assertTrue(UpdateFinder.find(listOf(app), listOf(g)).isEmpty())
    }

    @Test
    fun trackedWithoutVersionUsesNewerPost() {
        val old = game(10, "MK9", 1 to "MK9Recomp-snapdragon.apk")
        val new = game(30, "MK9", 5 to "MK9Recomp-snapdragon.apk")
        val app = InstalledApp("com.mk9", "MK9", null, InstallRecord("com.mk9", "MK9Recomp-snapdragon.apk", 1, 10))
        assertEquals(30L, UpdateFinder.find(listOf(app), listOf(new, old)).single().game.id)
    }

    @Test
    fun untrackedNeedsComparableVersion() {
        val g = game(10, "Mareshine", 1 to "Mareshine-2.1.0.apk")
        val older = InstalledApp("com.mare", "Mareshine", "2.0.0")
        val same = InstalledApp("com.mare", "Mareshine", "2.1.0")
        val unknown = InstalledApp("com.other", "Otra app", "9.9")
        assertEquals("2.1.0", UpdateFinder.find(listOf(older), listOf(g)).single().newVersion)
        assertTrue(UpdateFinder.find(listOf(same, unknown), listOf(g)).isEmpty())
    }
}

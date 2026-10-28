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
        // Números pegados a letras (arm64, x86) no son parte de la versión.
        assertEquals("1.1.1", UpdateFinder.version("bigodao_arm64.1.1.1.apk"))
        assertEquals("2.0", UpdateFinder.version("Juego-x86_64-v2.0.apk"))
    }

    @Test
    fun architectureInNameIsNotAVersion() {
        val g = game(10, "Bigodao", 1 to "bigodao_arm64.1.1.1.apk")
        val app = InstalledApp("com.bigodao", "Bigodao 1.1", "1.1")
        assertTrue(UpdateFinder.find(listOf(app), listOf(g)).isEmpty())
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
    fun trackedWithoutVersionNeedsADifferentFile() {
        val old = game(10, "MK9", 1 to "MK9Recomp-snapdragon.apk")
        val repost = game(30, "MK9", 5 to "MK9Recomp-snapdragon.apk")
        val changed = Game(
            id = 40, title = "MK9", description = "", date = 0, images = emptyList(),
            packages = CatalogBuilder.groupPackages(listOf(RemoteFile(6, "MK9Recomp-snapdragon.apk", 250))),
            messageIds = listOf(40),
        )
        val app = InstalledApp(
            "com.mk9", "MK9", null,
            InstallRecord("com.mk9", "MK9Recomp-snapdragon.apk", 1, 10, fileSize = 100),
        )
        // El mismo APK publicado otra vez no es una actualización…
        assertTrue(UpdateFinder.find(listOf(app), listOf(repost, old)).isEmpty())
        // …uno con el mismo nombre pero distinto sí.
        assertEquals(40L, UpdateFinder.find(listOf(app), listOf(changed, repost, old)).single().game.id)
        // Registros antiguos sin tamaño: sin versión no se puede saber, no se avisa.
        val legacy = app.copy(record = app.record!!.copy(fileSize = 0))
        assertTrue(UpdateFinder.find(listOf(legacy), listOf(changed, old)).isEmpty())
    }

    @Test
    fun trackedIgnoresTheAppInternalVersion() {
        // El port se llama v1.2 en el canal pero la app dice 1.08 por dentro: no es una actualización.
        val g = game(10, "GTA", 1 to "GTA-v1.2.apk")
        val repost = game(20, "GTA", 2 to "GTA-v1.2.apk")
        val app = InstalledApp("com.gta", "GTA", "1.08", InstallRecord("com.gta", "GTA-v1.2.apk", 1, 10))
        assertTrue(UpdateFinder.find(listOf(app), listOf(repost, g)).isEmpty())
    }

    @Test
    fun untrackedNeedsComparableVersion() {
        val v2 = game(5, "Mareshine", 1 to "Mareshine-2.0.0.apk")
        val v21 = game(10, "Mareshine", 2 to "Mareshine-2.1.0.apk")
        val older = InstalledApp("com.mare", "Mareshine", "2.0.0")
        val same = InstalledApp("com.mare", "Mareshine", "2.1.0")
        val unknown = InstalledApp("com.other", "Otra app", "9.9")
        // Solo con la versión instalada también en el canal se sabe que se pueden comparar.
        val otherScheme = InstalledApp("com.mare", "Mareshine", "1.0.3")
        assertEquals("2.1.0", UpdateFinder.find(listOf(older), listOf(v21, v2)).single().newVersion)
        assertTrue(UpdateFinder.find(listOf(same, unknown, otherScheme), listOf(v21, v2)).isEmpty())
        assertTrue(UpdateFinder.find(listOf(older), listOf(v21)).isEmpty())
    }
}

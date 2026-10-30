package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun detectsNumberedVolumes() {
        val v = FileNames.volumeOf("GTA SA.7z.002")
        assertEquals("GTA SA.7z", v.baseName)
        assertEquals(2, v.index)
        assertEquals(FileNames.SplitStyle.NUMBERED, v.style)
        assertEquals(PackageKind.ARCHIVE, FileNames.kindOfFile("GTA SA.7z.002"))
        assertEquals(PackageKind.ARCHIVE, FileNames.kindOfFile("juego.zip.001"))
    }

    @Test
    fun detectsRarParts() {
        val v = FileNames.volumeOf("Max Payne.part03.rar")
        assertEquals("Max Payne.rar", v.baseName)
        assertEquals(3, v.index)
        assertEquals(FileNames.SplitStyle.RAR_PART, v.style)
        assertTrue(FileNames.isArchive("Max Payne.part1.rar"))
    }

    @Test
    fun detectsSpannedZip() {
        val z1 = FileNames.volumeOf("Juego.z01")
        assertEquals("Juego.zip", z1.baseName)
        assertEquals(FileNames.SplitStyle.ZIP_SPAN, z1.style)
        assertEquals(PackageKind.ARCHIVE, FileNames.kindOfFile("Juego.z02"))
        // Una ROM de N64 no es una parte de ZIP.
        assertEquals(FileNames.SplitStyle.NONE, FileNames.volumeOf("mario.z64").style)
        // El .zip se agrupa con sus partes y va primero (es el que se abre).
        val pkg = CatalogBuilder.groupPackages(
            listOf(RemoteFile(1, "Juego.z01", 10), RemoteFile(2, "Juego.zip", 5), RemoteFile(3, "Juego.z02", 10)),
        ).single()
        assertEquals(listOf("Juego.zip", "Juego.z01", "Juego.z02"), pkg.parts.map { it.name })
        assertEquals(PackageKind.ARCHIVE, pkg.kind)
    }

    @Test
    fun plainFiles() {
        assertEquals(PackageKind.APK, FileNames.kindOfFile("Juego v1.2.apk"))
        assertEquals(PackageKind.APK, FileNames.kindOfFile("Juego.apk.001"))
        assertEquals(PackageKind.APK_BUNDLE, FileNames.kindOfFile("Juego.xapk"))
        assertEquals(PackageKind.OBB, FileNames.kindOfFile("main.12.com.game.obb"))
        assertEquals(PackageKind.OTHER, FileNames.kindOfFile("leeme.txt"))
        assertFalse(FileNames.isArchive("Juego.apk"))
    }

    @Test
    fun obbPackageName() {
        assertEquals("com.rockstargames.gtasa", FileNames.obbPackage("main.8.com.rockstargames.gtasa.obb"))
        assertNull(FileNames.obbPackage("data.obb"))
    }

    @Test
    fun sortsVolumes() {
        val sorted = FileNames.sortVolumes(listOf("a.7z.010", "a.7z.002", "a.7z.001")) { it }
        assertEquals(listOf("a.7z.001", "a.7z.002", "a.7z.010"), sorted)
    }

    @Test
    fun formatsSizes() {
        assertEquals("512 B", FileNames.formatSize(512))
        assertEquals("1.5 KB", FileNames.formatSize(1536))
        assertEquals("3.7 GB", FileNames.formatSize(3_973_000_000))
    }
}

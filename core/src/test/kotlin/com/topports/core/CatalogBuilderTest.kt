package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBuilderTest {

    private fun photo(id: Int) = PhotoRef(id, id + 1000)
    private fun doc(id: Int, name: String, size: Long = 100) = RemoteFile(id, name, size)

    @Test
    fun photoWithCaptionFollowedByFiles() {
        val games = CatalogBuilder.build(
            listOf(
                ChannelMessage(1, 1000, text = "**🎮 Max Payne Port**\nPort para Android\nRequiere 3GB", photo = photo(1)),
                ChannelMessage(2, 1010, document = doc(10, "MaxPayne.7z.001", 2_000)),
                ChannelMessage(3, 1020, text = "Parte 2", document = doc(11, "MaxPayne.7z.002", 1_000)),
                ChannelMessage(4, 5000, text = "Half-Life port", photo = photo(2)),
                ChannelMessage(5, 5010, document = doc(12, "HalfLife.apk")),
            ),
        )
        assertEquals(2, games.size)
        val hl = games[0]
        val mp = games[1]
        assertEquals("Half-Life port", hl.title)
        assertEquals("Max Payne Port", mp.title)
        assertEquals("Port para Android\nRequiere 3GB", mp.description)
        assertEquals(1, mp.packages.size)
        assertEquals(PackageKind.ARCHIVE, mp.packages[0].kind)
        assertEquals(listOf(10, 11), mp.packages[0].parts.map { it.id })
        assertEquals(3_000L, mp.size)
        assertEquals(PackageKind.APK, hl.packages[0].kind)
    }

    /** Posts reales del canal: foto con descripción + álbum de archivos o archivos sueltos. */
    @Test
    fun realChannelPosts() {
        val mb = 1024L * 1024
        val games = CatalogBuilder.build(
            listOf(
                ChannelMessage(
                    10, 1000,
                    text = "Super Mario Sunshine\n\nControles:\nFísicos y táctiles.\n\nIdioma:\nMulti- Castellano 🇪🇸",
                    photo = photo(1),
                ),
                ChannelMessage(11, 1000, document = doc(20, "Mareshine-2.0.0.apk", 12 * mb)),
                ChannelMessage(12, 1001, document = doc(21, "Super Mario Sunshine (USA).iso", 1392 * mb)),
                ChannelMessage(13, 1001, document = doc(22, "Super Mario Sunshine (Europe).iso", 1392 * mb)),
                ChannelMessage(
                    20, 9000,
                    text = "Dynasty Warriors 5\n\nInstalar apk, descomprimir el archivo comprimido en la raiz de la memoria interna, y abrir la aplicación.",
                    photo = photo(2),
                ),
                ChannelMessage(21, 9001, albumId = 99, document = doc(30, "DW5-Android-v1.5.apk", 19 * mb)),
                ChannelMessage(22, 9001, albumId = 99, document = doc(31, "DW5.zip.001", 3900 * mb)),
                ChannelMessage(23, 9001, albumId = 99, document = doc(32, "DW5.zip.002", 383 * mb)),
            ),
        )
        assertEquals(listOf("Dynasty Warriors 5", "Super Mario Sunshine"), games.map { it.title })

        val dw5 = games[0]
        assertEquals(listOf(PackageKind.APK, PackageKind.ARCHIVE), dw5.packages.map { it.kind })
        assertEquals("DW5.zip", dw5.packages[1].name)
        assertEquals(listOf(31, 32), dw5.packages[1].parts.map { it.id })
        assertTrue(CatalogBuilder.wantsStorageRoot(dw5.description))

        val mario = games[1]
        assertEquals(listOf(PackageKind.APK, PackageKind.OTHER, PackageKind.OTHER), mario.packages.map { it.kind })
        assertEquals(false, CatalogBuilder.wantsStorageRoot(mario.description))
    }

    /** Post con vídeo de configuración entre la descripción y los archivos (Mortal Kombat 9). */
    @Test
    fun installVideoDoesNotStartNewGame() {
        val games = CatalogBuilder.build(
            listOf(
                ChannelMessage(1, 1000, text = "Mortal Kombat Komplete Edition\n\nIdioma:\nMulti - Castellano", photo = photo(1)),
                ChannelMessage(
                    2, 1000, text = "Configuración y rendimiento",
                    video = VideoRef(fileId = 50, thumbFileId = 51, durationSeconds = 260, caption = "Configuración y rendimiento"),
                ),
                ChannelMessage(3, 1001, document = doc(60, "MK9Recomp-snapdragon.apk")),
                ChannelMessage(4, 1001, document = doc(61, "MK9Recomp-mali.apk")),
            ),
        )
        val mk = games.single()
        assertEquals("Mortal Kombat Komplete Edition", mk.title)
        assertEquals("Idioma:\nMulti - Castellano", mk.description)
        assertEquals(2, mk.packages.size)
        assertEquals(listOf("Configuración y rendimiento"), mk.videos.map { it.caption })
    }

    @Test
    fun albumStaysTogether() {
        val games = CatalogBuilder.build(
            listOf(
                ChannelMessage(1, 100, albumId = 7, text = "Juego", photo = photo(1)),
                ChannelMessage(2, 100, albumId = 7, photo = photo(2)),
                ChannelMessage(3, 100, albumId = 7, text = "otro texto", photo = photo(3)),
                ChannelMessage(4, 110, document = doc(9, "juego.apk")),
            ),
        )
        assertEquals(1, games.size)
        assertEquals(3, games[0].images.size)
    }

    @Test
    fun postsWithoutFilesAreDropped() {
        val games = CatalogBuilder.build(
            listOf(
                ChannelMessage(1, 100, text = "Bienvenidos al canal"),
                ChannelMessage(2, 100_000, text = "Juego A", photo = photo(1)),
                ChannelMessage(3, 100_010, document = doc(5, "a.apk")),
                ChannelMessage(4, 200_000, text = "Mañana más ports"),
            ),
        )
        assertEquals(listOf("Juego A"), games.map { it.title })
    }

    @Test
    fun fileOnlyPostUsesFileNameAsTitle() {
        val games = CatalogBuilder.build(listOf(ChannelMessage(1, 100, document = doc(1, "Doom 3.zip"))))
        assertEquals("Doom 3", games.single().title)
    }

    @Test
    fun separatesApkAndDataPackages() {
        val pkgs = CatalogBuilder.groupPackages(
            listOf(doc(1, "Juego.apk"), doc(2, "Datos.part2.rar"), doc(3, "Datos.part1.rar")),
        )
        assertEquals(2, pkgs.size)
        assertEquals(PackageKind.APK, pkgs[0].kind)
        assertEquals(listOf(3, 2), pkgs[1].parts.map { it.id })
        assertTrue(pkgs[1].isMultiPart)
    }
}

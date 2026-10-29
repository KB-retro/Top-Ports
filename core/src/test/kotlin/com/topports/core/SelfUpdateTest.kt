package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateTest {

    private fun doc(id: Long, name: String) =
        ChannelMessage(id, id.toInt(), document = RemoteFile(id.toInt(), name, 30_000_000))

    @Test
    fun recognisesTheStoreApk() {
        assertEquals("0.1.47", SelfUpdate.versionOf("TopPorts-0.1.47.apk"))
        assertEquals("0.2.0", SelfUpdate.versionOf("Top_Ports v0.2.0.apk"))
        assertNull(SelfUpdate.versionOf("TopPorts-data.zip"))
        assertNull(SelfUpdate.versionOf("Bigodao-1.1.apk"))
    }

    @Test
    fun picksNewestOnlyIfNewerThanInstalled() {
        val messages = listOf(doc(1, "TopPorts-0.1.40.apk"), doc(2, "TopPorts-0.1.47.apk"), doc(3, "Juego.apk"))
        assertEquals("0.1.47", SelfUpdate.newest(messages, "0.1.45")?.version)
        assertNull(SelfUpdate.newest(messages, "0.1.47"))
        assertNull(SelfUpdate.newest(messages, "0.1.50"))
    }

    @Test
    fun storeApkIsNotAGame() {
        val messages = listOf(
            ChannelMessage(1, 100, text = "Juego A", photo = PhotoRef(1, 1)),
            ChannelMessage(2, 110, document = RemoteFile(5, "a.apk", 10)),
            // Publicado justo después: sin foto se uniría al juego anterior si no se apartara.
            ChannelMessage(3, 120, document = RemoteFile(6, "TopPorts-0.1.47.apk", 10)),
        )
        val games = CatalogBuilder.build(messages)
        assertEquals(listOf("a.apk"), games.single().packages.map { it.name })
        assertTrue(SelfUpdate.isAppRelease(messages[2]))
    }

    @Test
    fun storePromoPostIsNotAGameAndLaterFilesGoBack() {
        val messages = listOf(
            ChannelMessage(1, 100, text = "Patrulla's Crown", photo = PhotoRef(1, 1)),
            ChannelMessage(2, 110, document = RemoteFile(5, "PatrullasCrown.apk", 10)),
            // Anuncio de la app: foto + texto y su APK.
            ChannelMessage(3, 5000, text = "Top Ports Android\nLa Store de confianza de este canal.", photo = PhotoRef(2, 2)),
            ChannelMessage(4, 5010, document = RemoteFile(6, "TopPorts-0.1.54.apk", 10)),
            // Parche sin foto publicado después: es del juego anterior, no de la app.
            ChannelMessage(5, 6000, document = RemoteFile(7, "Patrulla's Crown Parche al Español.zip", 10)),
            ChannelMessage(6, 7000, text = "Juego B", photo = PhotoRef(3, 3)),
            ChannelMessage(7, 7010, document = RemoteFile(8, "b.apk", 10)),
        )
        val games = CatalogBuilder.build(messages)
        assertEquals(listOf("Juego B", "Patrulla's Crown"), games.map { it.title })
        assertEquals(
            listOf("PatrullasCrown.apk", "Patrulla's Crown Parche al Español.zip"),
            games[1].packages.map { it.name },
        )
    }
}

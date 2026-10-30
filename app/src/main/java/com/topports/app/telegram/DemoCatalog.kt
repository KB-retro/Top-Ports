package com.topports.app.telegram

import com.topports.core.ChannelMessage
import com.topports.core.PhotoRef
import com.topports.core.RemoteFile

/**
 * Canal de ejemplo para el modo demo (solo en la compilación de las capturas automáticas de
 * pantalla, BuildConfig.DEMO): llena la tienda sin necesitar una cuenta de Telegram.
 */
object DemoCatalog {

    private data class Demo(val title: String, val tag: String, val files: List<Pair<String, Long>>)

    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024

    private val games = listOf(
        Demo("Super Port 64", "N64", listOf("SuperPort64-v1.2.apk" to 140 * MB)),
        Demo("Racing Legends Collection", "PS2", listOf("RacingLegends-v2.0.apk" to 90 * MB, "RacingLegends-Datos.7z.001" to 4 * GB, "RacingLegends-Datos.7z.002" to 2 * GB)),
        Demo("Street Football", "X360", listOf("StreetFootball.apk" to 60 * MB, "StreetFootball-Datos.zip" to 6 * GB)),
        Demo("Cave Explorer 2", "PC", listOf("CaveExplorer2-v1.0.apk" to 144 * MB)),
        Demo("Arcade Fighters Retro Starz", "ARCADE", listOf("ArcadeFighters.zip" to 9 * GB)),
        Demo("Leyendas del Reino Perdido", "SNES", listOf("Leyendas.apk" to 40 * MB, "Leyendas-Datos.zip" to 90 * MB)),
        Demo("Burnout Night Revenge", "X360", listOf("BurnoutNight.apk" to 80 * MB, "BurnoutNight-Datos.part1.rar" to 4 * GB, "BurnoutNight-Datos.part2.rar" to 2 * GB)),
        Demo("Pinball Space Cadet", "PC", listOf("Pinball-v1.1.apk" to 30 * MB)),
        Demo("Animal Village", "GC", listOf("AnimalVillage.apk" to 70 * MB, "AnimalVillage.z01" to 2 * GB, "AnimalVillage.zip" to GB)),
        Demo("Banjo Recompiled", "N64", listOf("BanjoRecomp-v1.3.apk" to 120 * MB)),
        Demo("Metroid Return", "GB", listOf("MetroidReturn-v2.1.apk" to 353 * MB)),
        Demo("Tetris Retro", "PC", listOf("TetrisRetro.apk" to 25 * MB)),
    )

    /** Mensajes del canal de ejemplo, con el mismo formato que la plantilla de posts. */
    fun messages(): List<ChannelMessage> {
        val out = mutableListOf<ChannelMessage>()
        var id = 1L
        var date = 1_760_000_000
        var fileId = 1_000
        games.forEachIndexed { index, g ->
            val text = buildString {
                append("🎮 ${g.title}\n#${g.tag}\n\n")
                append("📝 Port nativo para Android con mejoras gráficas, soporte de mando y guardado rápido. ")
                append("Funciona a 60 fps en la mayoría de consolas con Snapdragon 8 Gen 2 o superior.\n\n")
                append("📦 Versión: v1.${index % 4}\n🌐 Idioma: Español (textos) · Inglés (voces)\n")
                append("📱 Requisitos: Android 10+ · 4 GB de RAM\n\n")
                append("⚙️ Instalación:\n1. Instala el APK.\n2. Descomprime los datos en /TopPorts/${g.title}\n")
                append("3. Abre el juego y selecciona la carpeta.\n\n🎮 Controles: mando compatible / táctil\n\n")
                append("🔗 Fuente: https://github.com/autor/proyecto")
            }
            out += ChannelMessage(id++, date, text = text, photo = PhotoRef(fileId, fileId + 1))
            fileId += 2
            g.files.forEach { (name, size) ->
                out += ChannelMessage(id++, date + 30, document = RemoteFile(fileId++, name, size))
            }
            date += 86_400
        }
        return out
    }
}

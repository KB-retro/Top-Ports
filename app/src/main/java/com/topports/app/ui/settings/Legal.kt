package com.topports.app.ui.settings

import com.topports.app.BuildConfig
import com.topports.app.telegram.ChannelRepository

/** Aviso legal (Ajustes → Aviso legal). Debe coincidir con la sección del README. */
val LEGAL_POINTS = listOf(
    "Top Ports es una app no oficial que usa la API de Telegram. No está afiliada a Telegram, " +
        "Google ni a ninguna de las empresas o marcas de los juegos que aparecen en el canal.",
    "La app no aloja ningún archivo: solo muestra y descarga lo que se publica en el canal de " +
        "Telegram. Los nombres, carátulas y marcas pertenecen a sus respectivos dueños.",
    "Cada usuario es responsable de lo que descarga e instala. Descarga solo contenido que " +
        "tengas derecho a usar; si un port necesita los archivos de un juego, usa los de tu copia original.",
    "Si eres titular de derechos y crees que algún contenido del canal los infringe, escríbenos " +
        "con el enlace a la publicación y lo retiraremos.",
)

/** Contacto para reclamaciones: el configurado al compilar o, si no hay, el canal. */
fun legalContactLink(): String {
    val contact = BuildConfig.LEGAL_CONTACT.trim()
    return when {
        contact.isEmpty() -> ChannelRepository.telegramLink()
        contact.startsWith("@") -> "https://t.me/${contact.drop(1)}"
        "@" in contact && !contact.startsWith("http") -> "mailto:$contact"
        else -> contact
    }
}

/** Software de terceros que incluye la app, con su licencia. */
data class License(val name: String, val license: String, val url: String, val note: String = "")

val LICENSES = listOf(
    License("TDLib", "Boost Software License 1.0", "https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt"),
    License(
        "7-Zip-JBinding-4Android", "LGPL 2.1 + restricción unRAR",
        "https://github.com/omicronapps/7-Zip-JBinding-4Android",
        "El código unRAR solo se usa para extraer, nunca para crear archivos RAR.",
    ),
    License("Jetpack Compose y AndroidX", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Media3 ExoPlayer", "Apache 2.0", "https://github.com/androidx/media/blob/release/LICENSE"),
    License("Coil", "Apache 2.0", "https://github.com/coil-kt/coil/blob/main/LICENSE.txt"),
    License("Kotlin y kotlinx.coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"),
    License("Press Start 2P (fuente)", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Press+Start+2P/license"),
    License("VT323 (fuente)", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/VT323/license"),
    License(
        "Robot de Android (logo)", "CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/",
        "El robot de Android se reproduce o modifica a partir de trabajo creado y compartido por Google.",
    ),
)

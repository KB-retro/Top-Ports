package com.topports.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.topports.app.R

/** Colores de un tema. Los nombres son los de PICO-8; cada tema los reinterpreta. */
data class PxPalette(
    val black: Color,
    val night: Color,
    val navy: Color,
    val navyDark: Color,
    val purple: Color,
    val darkGreen: Color,
    val brown: Color,
    val darkGrey: Color,
    val grey: Color,
    val white: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val blue: Color,
    val lavender: Color,
    val pink: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** Temas de color elegibles en Ajustes. */
enum class AppTheme(val label: String, val palette: PxPalette) {
    PICO8(
        "PICO-8",
        PxPalette(
            black = c(0x000000), night = c(0x0B0F1E), navy = c(0x1D2B53), navyDark = c(0x131C3A),
            purple = c(0x7E2553), darkGreen = c(0x008751), brown = c(0xAB5236), darkGrey = c(0x5F574F),
            grey = c(0xC2C3C7), white = c(0xFFF1E8), red = c(0xFF004D), orange = c(0xFFA300),
            yellow = c(0xFFEC27), green = c(0x00E436), blue = c(0x29ADFF), lavender = c(0x83769C),
            pink = c(0xFF77A8),
        ),
    ),
    GAMEBOY(
        "Game Boy",
        PxPalette(
            black = c(0x071F07), night = c(0x0F380F), navy = c(0x306230), navyDark = c(0x1E4A1E),
            purple = c(0x306230), darkGreen = c(0x306230), brown = c(0x5A7A1A), darkGrey = c(0x4E7A2E),
            grey = c(0x8BAC0F), white = c(0xCADC9F), red = c(0x306230), orange = c(0x9BBC0F),
            yellow = c(0xCADC9F), green = c(0x9BBC0F), blue = c(0x8BAC0F), lavender = c(0x8BAC0F),
            pink = c(0xB5CC5A),
        ),
    ),
    NES(
        "NES",
        PxPalette(
            black = c(0x000000), night = c(0x0C0C0C), navy = c(0x3C3C3C), navyDark = c(0x232323),
            purple = c(0xA4005C), darkGreen = c(0x00A800), brown = c(0xAC7C00), darkGrey = c(0x7C7C7C),
            grey = c(0xBCBCBC), white = c(0xFCFCFC), red = c(0xD82800), orange = c(0xFCA044),
            yellow = c(0xF8D878), green = c(0x58D854), blue = c(0x3CBCFC), lavender = c(0x9878F8),
            pink = c(0xF878F8),
        ),
    ),
    SYNTHWAVE(
        "Synthwave",
        PxPalette(
            black = c(0x07000F), night = c(0x120024), navy = c(0x2B0F54), navyDark = c(0x1E0A3C),
            purple = c(0xB967FF), darkGreen = c(0x00B377), brown = c(0xFF8B3D), darkGrey = c(0x6B4E8C),
            grey = c(0xC9B6E4), white = c(0xF8F0FF), red = c(0xFF2A6D), orange = c(0xFF6AD5),
            yellow = c(0xFFF56D), green = c(0x05FFA1), blue = c(0x01CDFE), lavender = c(0x9D8CFF),
            pink = c(0xFF71CE),
        ),
    ),
    VIRTUAL_BOY(
        "Virtual Boy",
        PxPalette(
            black = c(0x000000), night = c(0x050000), navy = c(0x2A0000), navyDark = c(0x160000),
            purple = c(0x7A0A0A), darkGreen = c(0x6E0E0E), brown = c(0x901010), darkGrey = c(0x6E1414),
            grey = c(0xC83232), white = c(0xFF6060), red = c(0x8C0000), orange = c(0xFF3030),
            yellow = c(0xFF7070), green = c(0xE02020), blue = c(0xB01818), lavender = c(0xA82828),
            pink = c(0xFF4C4C),
        ),
    ),

    // Consola gris, cruceta y botones morados, y los colores de A/B/X/Y.
    SNES(
        "SNES",
        PxPalette(
            black = c(0x0C0B12), night = c(0x1B1A24), navy = c(0x3B3550), navyDark = c(0x2A2639),
            purple = c(0x4F43AE), darkGreen = c(0x1E7A3C), brown = c(0x8A6E58), darkGrey = c(0x6E6A7C),
            grey = c(0xC4C2CF), white = c(0xECEBF2), red = c(0xD72631), orange = c(0xA08CF0),
            yellow = c(0xF2C230), green = c(0x2BA84A), blue = c(0x3A5FCD), lavender = c(0x8F86C9),
            pink = c(0xE58FB4),
        ),
    ),

    // Carcasa negra con el rojo y el dorado del logo de 16-BIT.
    MEGA_DRIVE(
        "Mega Drive",
        PxPalette(
            black = c(0x000000), night = c(0x0A0A0A), navy = c(0x1E1E22), navyDark = c(0x141416),
            purple = c(0x6A2C8C), darkGreen = c(0x1F7A3A), brown = c(0x8C5A2B), darkGrey = c(0x555555),
            grey = c(0xB5B5B5), white = c(0xF2F2F2), red = c(0xB3121A), orange = c(0xE3262A),
            yellow = c(0xE8B923), green = c(0x36B04A), blue = c(0x2E6BD9), lavender = c(0x8A8FA0),
            pink = c(0xFF6F7D),
        ),
    ),

    // La paleta de 64 colores de la Master System (cada canal: 00, 55, AA o FF).
    MASTER_SYSTEM(
        "Master System",
        PxPalette(
            black = c(0x000000), night = c(0x000000), navy = c(0x0000AA), navyDark = c(0x000055),
            purple = c(0xAA00AA), darkGreen = c(0x00AA00), brown = c(0xAA5500), darkGrey = c(0x555555),
            grey = c(0xAAAAAA), white = c(0xFFFFFF), red = c(0xAA0000), orange = c(0xFF5500),
            yellow = c(0xFFFF55), green = c(0x55FF55), blue = c(0x55AAFF), lavender = c(0xAAAAFF),
            pink = c(0xFF55AA),
        ),
    ),

    // Las cuatro tonalidades verdeazuladas de la pantalla de la Game Boy original (DMG-01).
    GB_DMG(
        "GB DMG",
        PxPalette(
            black = c(0x040C10), night = c(0x081820), navy = c(0x1F4038), navyDark = c(0x10281F),
            purple = c(0x346856), darkGreen = c(0x346856), brown = c(0x346856), darkGrey = c(0x4E7F68),
            grey = c(0x88C070), white = c(0xE0F8D0), red = c(0x346856), orange = c(0x88C070),
            yellow = c(0xE0F8D0), green = c(0x88C070), blue = c(0x6FA88A), lavender = c(0x88C070),
            pink = c(0xB8E0A0),
        ),
    ),

    // El tema Gruvbox (oscuro) del menú de RetroArch.
    GRUVBOX(
        "Gruvbox",
        PxPalette(
            black = c(0x141617), night = c(0x1D2021), navy = c(0x3C3836), navyDark = c(0x282828),
            purple = c(0xB16286), darkGreen = c(0x98971A), brown = c(0xD65D0E), darkGrey = c(0x665C54),
            grey = c(0xA89984), white = c(0xEBDBB2), red = c(0xFB4934), orange = c(0xFE8019),
            yellow = c(0xFABD2F), green = c(0xB8BB26), blue = c(0x83A598), lavender = c(0x8EC07C),
            pink = c(0xD3869B),
        ),
    ),

    // Consola blanca, espiral naranja (azul en Europa) y la VMU.
    DREAMCAST(
        "Dreamcast",
        PxPalette(
            black = c(0x05080B), night = c(0x101418), navy = c(0x2A3038), navyDark = c(0x1C2128),
            purple = c(0x7A5CC0), darkGreen = c(0x22834A), brown = c(0xA0603A), darkGrey = c(0x66707A),
            grey = c(0xC8CCD0), white = c(0xF4F4F4), red = c(0xE0312C), orange = c(0xF26522),
            yellow = c(0xFFC20E), green = c(0x3DBE6A), blue = c(0x1E90D6), lavender = c(0x9AA5B8),
            pink = c(0xFF7AA2),
        ),
    ),

    // Consola gris y los colores de triángulo, círculo, cruz y cuadrado.
    PSX(
        "PSX",
        PxPalette(
            black = c(0x08090B), night = c(0x16171A), navy = c(0x34363C), navyDark = c(0x232428),
            purple = c(0x8A5CC0), darkGreen = c(0x00865A), brown = c(0x8C6A4A), darkGrey = c(0x6A6C74),
            grey = c(0xBDBDC4), white = c(0xEDEDF0), red = c(0xE2475B), orange = c(0x7EA6E0),
            yellow = c(0xFFC20E), green = c(0x3EC6A0), blue = c(0x5B8FD8), lavender = c(0xA3A8C8),
            pink = c(0xE68DC0),
        ),
    ),

    // Madera, negro y el naranja del logo de Atari.
    ATARI_2600(
        "Atari 2600",
        PxPalette(
            black = c(0x000000), night = c(0x0E0B08), navy = c(0x3A2A1C), navyDark = c(0x24190F),
            purple = c(0x8A4FA0), darkGreen = c(0x3E7A30), brown = c(0x7A5230), darkGrey = c(0x5C5048),
            grey = c(0xC9B79C), white = c(0xF2E6D0), red = c(0xD23A26), orange = c(0xE8862A),
            yellow = c(0xF2C641), green = c(0x5CA845), blue = c(0x4A8FD0), lavender = c(0xB39A7A),
            pink = c(0xE07A5A),
        ),
    ),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: PICO8
    }
}

/**
 * Colores activos. Son estado de Compose: al cambiar de tema en Ajustes, toda la interfaz
 * se vuelve a pintar con la paleta nueva.
 */
object Px {
    var theme by mutableStateOf(AppTheme.PICO8)
    private val p get() = theme.palette

    val black get() = p.black
    val night get() = p.night
    val navy get() = p.navy
    val navyDark get() = p.navyDark
    val purple get() = p.purple
    val darkGreen get() = p.darkGreen
    val brown get() = p.brown
    val darkGrey get() = p.darkGrey
    val grey get() = p.grey
    val white get() = p.white
    val red get() = p.red
    val orange get() = p.orange
    val yellow get() = p.yellow
    val green get() = p.green
    val blue get() = p.blue
    val lavender get() = p.lavender
    val pink get() = p.pink
}

/** Fuente de 8 bits para títulos y botones. */
val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** Fuente de terminal, más legible para textos largos. */
val TermFont = FontFamily(Font(R.font.vt323))

object PxText {
    val title get() = TextStyle(fontFamily = PixelFont, fontSize = 14.sp, lineHeight = 20.sp, color = Px.white)
    val heading get() = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, lineHeight = 16.sp, color = Px.white)
    val label get() = TextStyle(fontFamily = PixelFont, fontSize = 9.sp, lineHeight = 13.sp, color = Px.white)
    val tiny get() = TextStyle(fontFamily = PixelFont, fontSize = 7.sp, lineHeight = 10.sp, color = Px.white)
    val body get() = TextStyle(fontFamily = TermFont, fontSize = 21.sp, lineHeight = 22.sp, color = Px.white)
    val small get() = TextStyle(fontFamily = TermFont, fontSize = 18.sp, lineHeight = 19.sp, color = Px.grey)
}

private fun colors() = darkColorScheme(
    primary = Px.orange,
    onPrimary = Px.black,
    secondary = Px.blue,
    onSecondary = Px.black,
    tertiary = Px.pink,
    background = Px.night,
    onBackground = Px.white,
    surface = Px.navy,
    onSurface = Px.white,
    surfaceVariant = Px.navyDark,
    onSurfaceVariant = Px.grey,
    error = Px.red,
    onError = Px.white,
    outline = Px.white,
)

@Composable
fun TopPortsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors(),
        typography = Typography(
            bodyLarge = PxText.body,
            bodyMedium = PxText.body,
            bodySmall = PxText.small,
            titleLarge = PxText.title,
            titleMedium = PxText.heading,
            labelLarge = PxText.label,
        ),
        content = content,
    )
}

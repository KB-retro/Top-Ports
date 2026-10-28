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

package com.topports.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.topports.app.R

/** Paleta de 16 colores al estilo PICO-8. */
object Px {
    val black = Color(0xFF000000)
    val night = Color(0xFF0B0F1E)
    val navy = Color(0xFF1D2B53)
    val navyDark = Color(0xFF131C3A)
    val purple = Color(0xFF7E2553)
    val darkGreen = Color(0xFF008751)
    val brown = Color(0xFFAB5236)
    val darkGrey = Color(0xFF5F574F)
    val grey = Color(0xFFC2C3C7)
    val white = Color(0xFFFFF1E8)
    val red = Color(0xFFFF004D)
    val orange = Color(0xFFFFA300)
    val yellow = Color(0xFFFFEC27)
    val green = Color(0xFF00E436)
    val blue = Color(0xFF29ADFF)
    val lavender = Color(0xFF83769C)
    val pink = Color(0xFFFF77A8)
}

/** Fuente de 8 bits para títulos y botones. */
val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** Fuente de terminal, más legible para textos largos. */
val TermFont = FontFamily(Font(R.font.vt323))

object PxText {
    val title = TextStyle(fontFamily = PixelFont, fontSize = 14.sp, lineHeight = 20.sp, color = Px.white)
    val heading = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, lineHeight = 16.sp, color = Px.white)
    val label = TextStyle(fontFamily = PixelFont, fontSize = 9.sp, lineHeight = 13.sp, color = Px.white)
    val tiny = TextStyle(fontFamily = PixelFont, fontSize = 7.sp, lineHeight = 10.sp, color = Px.white)
    val body = TextStyle(fontFamily = TermFont, fontSize = 21.sp, lineHeight = 22.sp, color = Px.white)
    val small = TextStyle(fontFamily = TermFont, fontSize = 18.sp, lineHeight = 19.sp, color = Px.grey)
}

private val colors = darkColorScheme(
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
        colorScheme = colors,
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

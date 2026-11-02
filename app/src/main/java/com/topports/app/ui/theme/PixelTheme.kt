package com.topports.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
    /**
     * Lo seleccionado con el mando. Por defecto el amarillo, pero en las paletas donde el
     * amarillo es casi el blanco de los bordes (Game Boy, Virtual Boy…) se usa otro.
     */
    val focus: Color = yellow,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** Paletas de colores elegibles en Ajustes (en la interfaz, "Paleta de colores"). */
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
            focus = c(0x9BBC0F),
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
            focus = c(0xFFE000),
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
            focus = c(0xFFE600),
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
            focus = c(0xFF1A1A),
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
            focus = c(0xFFFF00),
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
            // El tono más claro: con solo cuatro verdes, es el que más destaca sobre el fondo.
            focus = c(0xE0F8D0),
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
            focus = c(0xFFB000),
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

    /**
     * Tamaño mínimo (sp) de Press Start 2P: 8 píxeles reales. Está dibujada sobre una cuadrícula
     * de 8 px y por debajo se emborrona; solo se nota en pantallas de baja densidad (640x480).
     * Lo calcula [LimitFontScale] con la densidad de la pantalla.
     */
    var minPressStartSp by mutableFloatStateOf(0f)

    /** Animación al cambiar de pestaña, abrir un juego o abrir la app. */
    var anim by mutableStateOf(UiAnim.FADE)

    /** Tema: estilo de interfaz (letras, marcos, barras y fondo); se combina con cualquier paleta ([theme]). */
    var style by mutableStateOf(UiStyle.PIXEL)
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
    val focus get() = p.focus
}

/**
 * Latido del marco de lo seleccionado (0..1), como un cursor. Lo mueve [TopPortsTheme] a
 * saltos (unas 10 veces por segundo) y solo se lee al dibujar: redibuja el elemento
 * seleccionado, sin recomponer la pantalla.
 */
object FocusPulse {
    var value by mutableFloatStateOf(1f)
}

/** Fuente de 8 bits para títulos y botones. */
val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** Fuente de terminal, más legible para textos largos. */
val TermFont = FontFamily(Font(R.font.vt323))

/** Pixel de 16 bits, redondeada y legible (estilo RPG). */
val PixelifyFont = FontFamily(Font(R.font.pixelify_sans))

/** Pixel fina en versalitas (títulos de Relieve). */
val SilkFont = FontFamily(Font(R.font.silkscreen))

/** Futurista de los 80 (títulos de Neón). */
val OrbitronFont = FontFamily(Font(R.font.orbitron))

/** Monoespaciada técnica (botones de Neón y Relieve). */
val TechMonoFont = FontFamily(Font(R.font.share_tech_mono))

/** Redondeada y gruesa (Cartucho). */
val NunitoFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/** Cómo se dibuja un recuadro (panel, botón, tarjeta…). */
enum class FrameKind {
    /** Borde de un "píxel gordo" sin esquinas y sombra dura. */
    PIXEL,

    /** Doble línea fina y esquinas rectas, como los menús de texto. */
    DOUBLE_LINE,

    /** Cristal: relleno translúcido, esquinas muy redondas y borde tenue. */
    GLASS,

    /** Bloque plano sin borde; el seleccionado se marca con un borde de color. */
    FLAT,

    /** Ventana de RPG: degradado, borde claro entre dos líneas oscuras y esquinas romas. */
    WINDOW,

    /** Tubo de neón: fondo oscuro translúcido y borde que brilla. */
    NEON,

    /** Relieve de escritorio de los 90: borde claro arriba a la izquierda y oscuro abajo a la derecha. */
    BEVEL,

    /** Tecla gorda de consola portátil: esquinas muy redondas y canto inferior más oscuro. */
    KEYCAP,
}

/** Decoración del fondo de la app. */
enum class Backdrop { NONE, SCANLINES, WAVES, GRADIENT, GRID, DITHER, DOTS }

/** Tamaño y altura de línea de un tipo de texto. */
data class TextSize(val size: TextUnit, val line: TextUnit)

/*
 * Tamaños de letra. Dos reglas para que todos los temas ocupen lo mismo:
 *  - el alto de línea es el mismo en todos: botones, pestañas, ayuda y logo miden igual;
 *  - el tamaño de cada fuente se ha sacado igualando el ANCHO de un texto de muestra con el de
 *    Ondas y Plano (letra del sistema): así caben las mismas opciones por línea. Press Start 2P
 *    y Pixelify son muy anchas y por eso llevan números más pequeños; VT323 es estrecha. Los
 *    botones (label/tiny) se igualaron con la fila de secciones de Ajustes, con un 3 % de margen
 *    por si la letra del sistema del aparato es algo más estrecha que Roboto;
 *  - el relleno de los botones compensa el grosor del marco ([StyleSpec.buttonPad]).
 */
private fun lineTitle(size: Float) = TextSize(size.sp, 22.sp)
private fun lineHeading(size: Float) = TextSize(size.sp, 18.sp)
private fun lineLabel(size: Float) = TextSize(size.sp, 15.sp)
private fun lineTiny(size: Float) = TextSize(size.sp, 13.sp)
private fun lineBody(size: Float) = TextSize(size.sp, 20.sp)
private fun lineSmall(size: Float) = TextSize(size.sp, 18.sp)

/** Todo lo que cambia un estilo de interfaz, además de los colores. */
data class StyleSpec(
    val headFont: FontFamily,
    val headWeight: FontWeight = FontWeight.Normal,
    /** Fuente de botones y etiquetas (label/tiny). */
    val labelFont: FontFamily = headFont,
    val labelWeight: FontWeight = headWeight,
    val bodyFont: FontFamily,
    /** Fuente del campo de texto (EditText): recurso o null para la del sistema. */
    val bodyFontRes: Int?,
    val title: TextSize,
    val heading: TextSize,
    val label: TextSize,
    val tiny: TextSize,
    val body: TextSize,
    val small: TextSize,
    /** Espaciado extra de títulos (en em). */
    val headSpacing: Float = 0f,
    /** Títulos y botones en mayúsculas. */
    val uppercase: Boolean = true,
    val frame: FrameKind,
    /** Grosor del borde (en los estilos que no son pixel). */
    val stroke: Dp = 3.dp,
    val radius: Dp = 0.dp,
    /** Sombra dura desplazada bajo los recuadros. */
    val shadow: Boolean = false,
    /** Barra de progreso por bloques (si no, continua). */
    val blocks: Boolean = true,
    val backdrop: Backdrop = Backdrop.NONE,
    /** Marca delante de los títulos de sección; null = cuadrado. */
    val marker: String? = null,
) {
    /**
     * Relleno lateral de los botones pequeños. Compensa el grosor del marco (y la sombra) de cada
     * tema: marco + relleno ocupan lo mismo en todos, así una fila de botones mide igual y no
     * salta a dos líneas en unos temas sí y en otros no.
     */
    val buttonPad: Dp
        get() {
            val frameWidth = when (frame) {
                FrameKind.PIXEL -> 3.dp * 2 + 3.dp
                FrameKind.DOUBLE_LINE -> (stroke * 2 + 2.dp) * 2
                FrameKind.GLASS -> stroke * 2
                FrameKind.FLAT -> stroke * 2
                FrameKind.WINDOW -> (stroke + 2.dp) * 2 + 3.dp
                FrameKind.NEON -> (stroke + 1.dp) * 2
                FrameKind.BEVEL -> (stroke + 1.dp) * 2
                FrameKind.KEYCAP -> stroke * 2
            }
            // 18 dp: lo que ya ocupaban marco y relleno en Ondas (8 + 1 por lado).
            return ((18.dp - frameWidth) / 2).coerceAtLeast(3.dp)
        }
}

/**
 * Estilos de interfaz elegibles en Ajustes (en la interfaz, "Temas"). Inspirados en los menús
 * de RetroArch y de las consolas: cambian las letras, los recuadros, las barras y el fondo, no
 * los colores (eso es la paleta).
 */
enum class UiStyle(val label: String, val description: String, val spec: StyleSpec) {
    PIXEL(
        "Pixel",
        "8 bits: letra gruesa, marcos sin esquinas y sombra dura.",
        StyleSpec(
            headFont = PixelFont, bodyFont = TermFont, bodyFontRes = R.font.vt323,
            // Press Start 2P es muy gruesa: a igual ancho "pesa" más que las demás, así que va
            // algo más pequeña para verse del mismo tamaño que el resto de temas.
            title = lineTitle(8f), heading = lineHeading(6.8f), label = lineLabel(5.4f),
            tiny = lineTiny(5f), body = lineBody(16f), small = lineSmall(14.5f),
            frame = FrameKind.PIXEL, shadow = true,
        ),
    ),
    TERMINAL(
        "Terminal",
        "Menú de texto, como RGUI: todo en letra de terminal, doble línea y líneas de barrido.",
        StyleSpec(
            headFont = TermFont, bodyFont = TermFont, bodyFontRes = R.font.vt323,
            title = lineTitle(21f), heading = lineHeading(17f), label = lineLabel(14.5f),
            tiny = lineTiny(12.4f), body = lineBody(19f), small = lineSmall(17f),
            headSpacing = 0.05f, frame = FrameKind.DOUBLE_LINE, stroke = 1.5.dp,
            backdrop = Backdrop.SCANLINES, marker = ">",
        ),
    ),
    WAVES(
        "Ondas",
        "Suave y moderno, como XMB: letra fina, cristal, esquinas redondas y ondas de fondo.",
        StyleSpec(
            headFont = FontFamily.SansSerif, headWeight = FontWeight.Light,
            labelFont = FontFamily.SansSerif, labelWeight = FontWeight.Medium,
            bodyFont = FontFamily.SansSerif, bodyFontRes = null,
            title = lineTitle(17f), heading = lineHeading(13.5f), label = lineLabel(12f),
            tiny = lineTiny(10f), body = lineBody(15f), small = lineSmall(13.5f),
            headSpacing = 0.04f, uppercase = false, frame = FrameKind.GLASS, stroke = 1.dp, radius = 14.dp,
            blocks = false, backdrop = Backdrop.WAVES, marker = "◆",
        ),
    ),
    FLAT(
        "Plano",
        "Limpio, como Ozone: letra del sistema, bloques planos sin borde y barra lisa.",
        StyleSpec(
            headFont = FontFamily.Default, headWeight = FontWeight.SemiBold,
            labelWeight = FontWeight.Medium,
            bodyFont = FontFamily.Default, bodyFontRes = null,
            title = lineTitle(17f), heading = lineHeading(13.5f), label = lineLabel(12f),
            tiny = lineTiny(10f), body = lineBody(15f), small = lineSmall(13.5f),
            uppercase = false, frame = FrameKind.FLAT, stroke = 2.dp, radius = 6.dp, blocks = false,
        ),
    ),
    RPG(
        "RPG 16 bits",
        "Ventanas de RPG: degradado, borde doble, esquinas romas y letra pixel redondeada.",
        StyleSpec(
            headFont = PixelifyFont, bodyFont = PixelifyFont, bodyFontRes = R.font.pixelify_sans,
            title = lineTitle(16f), heading = lineHeading(13f), label = lineLabel(10f),
            tiny = lineTiny(8.6f), body = lineBody(14.5f), small = lineSmall(13f),
            frame = FrameKind.WINDOW, stroke = 2.dp, radius = 5.dp, shadow = true, blocks = false,
            backdrop = Backdrop.GRADIENT, marker = "▶",
        ),
    ),
    NEON(
        "Neón",
        "Recreativa de los 80: tubos de neón que brillan, letra futurista y rejilla en el horizonte.",
        StyleSpec(
            headFont = OrbitronFont, labelFont = TechMonoFont,
            bodyFont = FontFamily.SansSerif, bodyFontRes = null,
            title = lineTitle(12.5f), heading = lineHeading(10f), label = lineLabel(10.7f),
            tiny = lineTiny(9.2f), body = lineBody(15f), small = lineSmall(13.5f),
            headSpacing = 0.06f, frame = FrameKind.NEON, stroke = 1.5.dp, radius = 4.dp, blocks = false,
            backdrop = Backdrop.GRID, marker = "//",
        ),
    ),
    BEVEL(
        "Relieve",
        "Escritorio de los 90: ventanas y botones en relieve, letra técnica y fondo tramado.",
        StyleSpec(
            headFont = SilkFont, labelFont = TechMonoFont,
            bodyFont = TechMonoFont, bodyFontRes = R.font.share_tech_mono,
            title = lineTitle(12.5f), heading = lineHeading(10f), label = lineLabel(10.7f),
            tiny = lineTiny(9.2f), body = lineBody(13.5f), small = lineSmall(12f),
            uppercase = false, frame = FrameKind.BEVEL, stroke = 2.dp,
            backdrop = Backdrop.DITHER,
        ),
    ),
    KEYCAP(
        "Cartucho",
        "Botones gordos de consola portátil: letra redondeada, esquinas muy curvas y fondo de puntos.",
        StyleSpec(
            headFont = NunitoFont, headWeight = FontWeight.ExtraBold,
            bodyFont = NunitoFont, bodyFontRes = R.font.nunito_regular,
            title = lineTitle(17f), heading = lineHeading(13.5f), label = lineLabel(11.1f),
            tiny = lineTiny(9.2f), body = lineBody(15f), small = lineSmall(13.5f),
            uppercase = false, frame = FrameKind.KEYCAP, stroke = 2.dp, radius = 12.dp, blocks = false,
            backdrop = Backdrop.DOTS, marker = "●",
        ),
    ),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: PIXEL
    }
}

/** Animaciones al cambiar de pestaña, abrir una ficha o abrir la app (Ajustes → Apariencia). */
enum class UiAnim(val label: String, val description: String) {
    NONE("Ninguna", "Cambio instantáneo."),
    FADE("Fundido", "La pantalla nueva aparece sobre la anterior."),
    SLIDE("Deslizar", "La pantalla entra de lado, hacia donde vas."),
    WIPE("Cortinilla", "Una barra de color barre la pantalla y descubre la nueva."),
    BLINDS("Persiana", "La pantalla nueva baja en franjas, como una persiana."),
    PIXELS("Píxeles", "La pantalla nueva aparece en bloques sueltos, como un fundido de 8 bits."),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: FADE
    }
}

/** Títulos y botones: en mayúsculas solo si el estilo lo pide. */
fun String.pxCaps(): String = if (Px.style.spec.uppercase) uppercase() else this

object PxText {
    private val s get() = Px.style.spec

    /** Press Start 2P nunca por debajo de 8 píxeles reales ([Px.minPressStartSp]). */
    private fun sized(font: FontFamily, t: TextSize): TextUnit =
        if (font == PixelFont && t.size.value < Px.minPressStartSp) Px.minPressStartSp.sp else t.size

    private fun head(t: TextSize) = TextStyle(
        fontFamily = s.headFont, fontWeight = s.headWeight, fontSize = sized(s.headFont, t), lineHeight = t.line,
        letterSpacing = s.headSpacing.em, color = Px.white,
    )

    private fun label(t: TextSize) = TextStyle(
        fontFamily = s.labelFont, fontWeight = s.labelWeight, fontSize = sized(s.labelFont, t), lineHeight = t.line, color = Px.white,
    )

    val title get() = head(s.title)
    val heading get() = head(s.heading)
    val label get() = label(s.label)
    val tiny get() = label(s.tiny)
    val body get() = TextStyle(fontFamily = s.bodyFont, fontSize = s.body.size, lineHeight = s.body.line, color = Px.white)
    val small get() = TextStyle(fontFamily = s.bodyFont, fontSize = s.small.size, lineHeight = s.small.line, color = Px.grey)
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

/**
 * Tope al tamaño de letra del sistema (Ajustes de Android → Tamaño de la fuente). Quien la tenga
 * grande la ve algo mayor, pero sin pasar de aquí: más allá, las filas de botones se parten y
 * las pestañas del menú lateral, de altura fija, cortan el texto.
 */
const val MAX_FONT_SCALE = 1.15f

@Composable
fun TopPortsTheme(content: @Composable () -> Unit) {
    LimitFontScale { TopPortsThemeContent(content) }
}

/** Aplica [MAX_FONT_SCALE]. Los diálogos son otra ventana y lo vuelven a aplicar. */
@Composable
fun LimitFontScale(content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val limited = remember(density) {
        val scale = density.fontScale.coerceAtMost(MAX_FONT_SCALE)
        Px.minPressStartSp = 8f / (density.density * scale)
        androidx.compose.ui.unit.Density(density.density, scale)
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides limited, content = content)
}

@Composable
private fun TopPortsThemeContent(content: @Composable () -> Unit) {
    LaunchedEffect(Unit) {
        var step = 0
        while (true) {
            FocusPulse.value = 0.5f + 0.5f * kotlin.math.cos(step * (2 * Math.PI / 12)).toFloat()
            step = (step + 1) % 12
            kotlinx.coroutines.delay(100)
        }
    }
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

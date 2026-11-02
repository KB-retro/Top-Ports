package com.topports.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.topports.app.R
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.PxPalette
import com.topports.app.ui.theme.Px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logo del canal (pixel art, fondo transparente) recoloreado con la paleta elegida: cada píxel
 * se clasifica por su tono (verde, azul/cian, morado, naranja o neutro) y se cambia por el color
 * equivalente de la paleta, conservando sus luces y sombras. Se calcula una vez por paleta, en
 * segundo plano, y se guarda; mientras tanto se ve el logo sin recolorear.
 */
@Composable
fun ThemedLogo(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = Px.theme
    val bitmap = produceState<ImageBitmap?>(LogoCache.cached(theme), theme) {
        value = withContext(Dispatchers.Default) { LogoCache.get(context, theme) }
    }.value
    if (bitmap != null) {
        Image(bitmap, contentDescription = "Top Ports", modifier = modifier.size(size))
    } else {
        Image(
            androidx.compose.ui.res.painterResource(R.drawable.logo_topports_px),
            contentDescription = "Top Ports",
            modifier = modifier.size(size),
        )
    }
}

private object LogoCache {
    private var source: Bitmap? = null
    private var lastTheme: AppTheme? = null
    private var lastImage: ImageBitmap? = null

    @Synchronized
    fun cached(theme: AppTheme): ImageBitmap? = lastImage.takeIf { lastTheme == theme }

    @Synchronized
    fun get(context: Context, theme: AppTheme): ImageBitmap {
        cached(theme)?.let { return it }
        val src = source ?: BitmapFactory.decodeResource(context.resources, R.drawable.logo_topports_px, BitmapFactory.Options().apply {
            inScaled = false
        }).also { source = it }
        val image = recolor(src, theme.palette).asImageBitmap()
        lastTheme = theme
        lastImage = image
        return image
    }

    private fun recolor(src: Bitmap, p: PxPalette): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val black = p.black.toArgb()
        val darkGrey = p.darkGrey.toArgb()
        val grey = p.grey.toArgb()
        val white = p.white.toArgb()
        val green = p.green.toArgb()
        val blue = p.blue.toArgb()
        val purple = p.purple.toArgb()
        val orange = p.orange.toArgb()
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            android.graphics.Color.colorToHSV(c, hsv)
            val (hue, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            val rgb = if (s < 0.25f || v < 0.12f) {
                // Neutros (contornos, metal, letras blancas): por su luminosidad.
                when {
                    v < 0.45f -> mix(black, darkGrey, v / 0.45f)
                    v < 0.8f -> mix(darkGrey, grey, (v - 0.45f) / 0.35f)
                    else -> mix(grey, white, (v - 0.8f) / 0.2f)
                }
            } else {
                val base = when (hue) {
                    in 65f..165f -> green
                    in 165f..265f -> blue
                    in 265f..330f -> purple
                    else -> orange
                }
                // Sombras hacia el negro de la paleta y colores lavados (brillos) hacia el blanco.
                var col = if (v < 0.75f) mix(black, base, v / 0.75f) else base
                if (s < 0.55f) col = mix(col, white, ((0.55f - s) / 0.4f).coerceAtMost(1f) * 0.8f)
                col
            }
            pixels[i] = (a shl 24) or (rgb and 0xFFFFFF)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * k).toInt()
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

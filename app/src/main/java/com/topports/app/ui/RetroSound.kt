package com.topports.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sonidos de menú de 8 bits: mover el cursor, aceptar y volver. Se sintetizan al momento (sin
 * archivos de audio), con el tipo de sonido elegido ([soft]): 8 bits o suave. Se activan o quitan
 * en Ajustes → Versión.
 */
object RetroSound {
    enum class Kind { MOVE, ACCEPT, BACK }

    /** Activados (Ajustes → Apariencia). */
    @Volatile
    var enabled = true

    private const val RATE = 22050
    private const val VOLUME = 0.22f

    private enum class Wave { SQUARE, PULSE, SINE, TRIANGLE, SAW, CLICK }

    /**
     * Tipo de sonido (Ajustes → Versión): false = 8 bits (onda cuadrada), true = suave (senoidal,
     * como el tema Ondas). Vale para estos sonidos y para la música ([ChipMusic]).
     */
    @Volatile
    var soft = false

    /** Timbre y nota base (Hz) según el tipo de sonido. */
    private fun voiceOf(soft: Boolean): Pair<Wave, Float> = if (soft) Wave.SINE to 990f else Wave.SQUARE to 880f

    private val tracks = HashMap<Pair<Boolean, Kind>, AudioTrack>()
    private var lastMove = 0L

    fun play(kind: Kind) {
        if (!enabled) return
        // Con la cruceta mantenida llegan muchas repeticiones: como mucho un bip cada 70 ms.
        if (kind == Kind.MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - lastMove < 70) return
            lastMove = now
        }
        runCatching {
            val track = synchronized(tracks) {
                tracks.getOrPut(soft to kind) { build(soft, kind) }
            }
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun build(soft: Boolean, kind: Kind): AudioTrack {
        val (wave, base) = voiceOf(soft)
        // Notas (frecuencia, duración en ms): mover = un bip corto; aceptar = dos notas que suben;
        // volver = dos notas que bajan.
        val notes = when (kind) {
            Kind.MOVE -> listOf(base to 28)
            Kind.ACCEPT -> listOf(base to 40, base * 1.5f to 60)
            Kind.BACK -> listOf(base * 1.25f to 40, base * 0.75f to 60)
        }
        val samples = notes.flatMap { (freq, ms) -> tone(wave, freq, ms).asIterable() }.toShortArray()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private fun tone(wave: Wave, freq: Float, ms: Int): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toFloat() / RATE
            val phase = (t * freq) % 1f
            val v = when (wave) {
                Wave.SQUARE -> if (phase < 0.5f) 1f else -1f
                Wave.PULSE -> if (phase < 0.25f) 1f else -1f
                Wave.SINE -> sin(2 * PI * phase).toFloat()
                Wave.TRIANGLE -> 4f * kotlin.math.abs(phase - 0.5f) - 1f
                Wave.SAW -> 2f * phase - 1f
                // Clic: ruido breve que se apaga enseguida, como una tecla de ordenador antiguo.
                Wave.CLICK -> if (i < n / 4) ((i * 1103515245 + 12345) shr 16 and 0xFF) / 127.5f - 1f else 0f
            }
            // Envolvente: ataque de 2 ms y caída lineal, para que no chasquee.
            val attack = (i / (RATE * 0.002f)).coerceAtMost(1f)
            val decay = 1f - i.toFloat() / n
            (v * attack * decay * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

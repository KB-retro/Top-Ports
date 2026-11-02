package com.topports.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sonidos de menú de 8 bits: mover el cursor, aceptar y volver. Se sintetizan al momento (sin
 * archivos de audio), con el tipo de sonido elegido ([type]). Se activan o quitan
 * en Ajustes → Sonido.
 */
object RetroSound {
    enum class Kind { MOVE, ACCEPT, BACK }

    /** Activados (Ajustes → Sonido). */
    @Volatile
    var enabled = true

    private const val RATE = 22050
    private const val VOLUME = 0.22f

    private enum class Wave { SQUARE, PULSE, NARROW, SINE, TRIANGLE, SAW, BELL, CLICK }

    /** Tipos de sonido (Ajustes → Sonido), el de por defecto primero. Valen también para la música. */
    enum class SoundType(val label: String) {
        BIT8("8 bits"),
        SOFT("Suave"),
        ARCADE("Arcade"),
        BEEPER("Beeper"),
        BELL("Campanita"),
        ;

        companion object {
            fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: BIT8
        }
    }

    /** Tipo de sonido elegido (Ajustes → Sonido). Vale para estos sonidos y para la música ([ChipMusic]). */
    @Volatile
    var type = SoundType.BIT8

    /** Timbre y nota base (Hz) de cada tipo de sonido. */
    private fun voiceOf(type: SoundType): Pair<Wave, Float> = when (type) {
        SoundType.BIT8 -> Wave.SQUARE to 880f
        SoundType.SOFT -> Wave.SINE to 990f
        SoundType.ARCADE -> Wave.SAW to 660f
        SoundType.BEEPER -> Wave.NARROW to 1200f
        SoundType.BELL -> Wave.BELL to 1320f
    }

    private val tracks = HashMap<Pair<SoundType, Kind>, AudioTrack>()
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
                tracks.getOrPut(type to kind) { build(type, kind) }
            }
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun build(type: SoundType, kind: Kind): AudioTrack {
        val (wave, base) = voiceOf(type)
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
                // Pulso muy estrecho: el pitido fino de un altavoz de ordenador antiguo.
                Wave.NARROW -> if (phase < 0.125f) 1f else -1f
                // Campana: fundamental más un parcial inarmónico que se apaga antes.
                Wave.BELL -> (sin(2 * PI * phase) + 0.5 * sin(2 * PI * ((t * freq * 2.76f) % 1f)) * (1f - i.toFloat() / n)).toFloat() * 0.7f
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

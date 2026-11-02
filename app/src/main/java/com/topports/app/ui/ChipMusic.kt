package com.topports.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Banda sonora chiptune generada al momento: no ocupa nada y nunca se repite. Cada canción se
 * compone al azar con reglas sencillas (tonalidad mayor o menor, tempo, progresión de acordes,
 * un motivo de melodía que se repite y varía, bajo, arpegio y batería) y dura uno o dos
 * minutos; después empieza otra. Suena con el tipo de sonido elegido (8 bits o suave).
 *
 * Solo suena con la app en pantalla y se calla mientras se ve un vídeo. Volumen en
 * Ajustes → Versión → Música (0 = apagada, por defecto).
 */
object ChipMusic {

    /** 0 apagada, 1 baja, 2 media, 3 alta. */
    @Volatile
    var level = 0
        set(value) {
            field = value
            refresh()
        }

    /** Título de la canción que suena ("Pista 4821"), para Ajustes. */
    @Volatile
    var trackName = ""
        private set

    @Volatile
    private var foreground = false

    @Volatile
    private var holds = 0

    private var thread: Thread? = null
    private var audio: AudioManager? = null

    @Volatile
    private var skip = false

    fun init(context: Context) {
        audio = context.getSystemService(AudioManager::class.java)
    }

    /** La app pasa a primer plano o sale de él (MainActivity.onResume / onPause). */
    fun setForeground(on: Boolean) {
        foreground = on
        // Al abrir la app: si el usuario ya está escuchando música en otra app, no se pisa.
        refresh(respectOthers = on)
    }

    /** Silencio mientras algo más suena (un vídeo): [hold] true al empezar, false al acabar. */
    fun hold(on: Boolean) {
        synchronized(this) { holds = (holds + if (on) 1 else -1).coerceAtLeast(0) }
        refresh()
    }

    /** Pasa a otra canción. */
    fun next() {
        skip = true
    }

    @Synchronized
    private fun refresh(respectOthers: Boolean = false) {
        val shouldPlay = level > 0 && foreground && holds == 0
        if (shouldPlay && thread == null) {
            if (respectOthers && audio?.isMusicActive == true) return
            thread = Thread(::run, "ChipMusic").apply { isDaemon = true; start() }
        } else if (!shouldPlay && thread != null) {
            thread?.interrupt()
            thread = null
        }
    }

    // --- Síntesis ---

    private const val RATE = 22050

    private fun run() {
        val bufferBytes = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes * 2)
                .build()
        }.getOrNull() ?: return
        try {
            track.play()
            val buffer = ShortArray(1024)
            while (!Thread.currentThread().isInterrupted) {
                val song = Song(Random.nextInt(1, 10_000))
                trackName = "Pista ${song.seed}"
                skip = false
                val synth = Synth(song)
                while (!synth.done && !skip && !Thread.currentThread().isInterrupted) {
                    synth.render(buffer)
                    track.write(buffer, 0, buffer.size)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.stop() }
            track.release()
            trackName = ""
        }
    }

    /** Un paso (semicorchea) de la canción: notas MIDI (-1 = silencio, 0 = sigue la anterior). */
    private class Step(val lead: Int, val bass: Int, val arp: Int, val drum: Int)

    private class Song(val seed: Int) {
        val bpm: Int
        val steps: List<Step>

        init {
            val rnd = Random(seed)
            val minor = rnd.nextBoolean()
            val scale = if (minor) intArrayOf(0, 2, 3, 5, 7, 8, 10) else intArrayOf(0, 2, 4, 5, 7, 9, 11)
            val root = 45 + rnd.nextInt(12)
            bpm = 96 + rnd.nextInt(5) * 10
            // Progresiones habituales (grados de la escala, desde 0).
            val progressions = if (minor) {
                listOf(intArrayOf(0, 5, 2, 6), intArrayOf(0, 3, 6, 2), intArrayOf(0, 6, 5, 6), intArrayOf(0, 3, 4, 0))
            } else {
                listOf(intArrayOf(0, 4, 5, 3), intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 4, 3), intArrayOf(5, 3, 0, 4))
            }
            val progA = progressions[rnd.nextInt(progressions.size)]
            val progB = progressions[rnd.nextInt(progressions.size)]
            fun note(degree: Int, octave: Int): Int {
                val d = Math.floorMod(degree, 7)
                val o = Math.floorDiv(degree, 7)
                return root + scale[d] + 12 * (octave + o)
            }

            // Motivo de melodía de 2 compases (32 pasos), en grados relativos al acorde.
            fun motif(): IntArray {
                val m = IntArray(32) { Int.MIN_VALUE }
                var deg = 2 * rnd.nextInt(3)
                for (i in 0 until 32 step 2) {
                    val strong = i % 8 == 0
                    if (strong || rnd.nextFloat() < 0.6f) {
                        deg = if (strong) 2 * rnd.nextInt(3) else (deg + rnd.nextInt(-2, 3)).coerceIn(-2, 7)
                        m[i] = deg
                    } else if (rnd.nextFloat() < 0.35f) {
                        m[i] = REST
                    }
                    if (rnd.nextFloat() < 0.12f) m[i + 1] = (deg + 1).coerceAtMost(7)
                }
                return m
            }
            val motifA = motif()
            val motifB = motif()
            val arp = rnd.nextFloat() < 0.65f
            val pumpingBass = rnd.nextBoolean()
            val halfTime = rnd.nextFloat() < 0.3f

            val out = ArrayList<Step>()
            // Forma A A B A, dos veces.
            val form = listOf(false, false, true, false, false, false, true, false)
            for (section in form) {
                val prog = if (section) progB else progA
                val motifX = if (section) motifB else motifA
                for (bar in 0 until 4) {
                    val chord = prog[bar]
                    val last = bar == 3
                    for (s in 0 until 16) {
                        val mi = (bar % 2) * 16 + s
                        val lead = when {
                            last && s == 0 -> note(chord, 2)
                            last && s > 0 && s < 12 -> HOLD
                            last && s >= 12 -> REST
                            motifX[mi] == Int.MIN_VALUE -> HOLD
                            motifX[mi] == REST -> REST
                            else -> note(chord + motifX[mi], 2)
                        }
                        val bass = when {
                            pumpingBass && s % 2 == 0 -> note(chord, 0) + if (s % 4 == 2) 12 else 0
                            !pumpingBass && (s == 0 || s == 8) -> note(chord, 0)
                            !pumpingBass && s == 6 && rnd.nextFloat() < 0.4f -> note(chord + 4, 0)
                            else -> HOLD
                        }
                        val arpNote = if (arp) note(chord + intArrayOf(0, 2, 4, 7)[s % 4], 1) else REST
                        val drum = when {
                            s == 0 || (!halfTime && s == 8) || (s == 10 && rnd.nextFloat() < 0.3f) -> KICK
                            (!halfTime && (s == 4 || s == 12)) || (halfTime && s == 8) -> SNARE
                            s % 2 == 0 -> HAT
                            else -> 0
                        }
                        out += Step(lead, bass, arpNote, drum)
                    }
                }
            }
            steps = out
        }
    }

    private const val HOLD = 0
    private const val REST = -1
    private const val KICK = 1
    private const val SNARE = 2
    private const val HAT = 3

    private class Voice {
        var freq = 0f
        var phase = 0f
        var age = 0
        var on = false

        fun trigger(midi: Int) {
            when (midi) {
                HOLD -> Unit
                REST -> on = false
                else -> {
                    freq = 440f * 2f.pow((midi - 69) / 12f)
                    age = 0
                    on = true
                }
            }
        }
    }

    private class Synth(private val song: Song) {
        private val samplesPerStep = RATE * 60 / song.bpm / 4
        private var step = 0
        private var sampleInStep = 0
        private val lead = Voice()
        private val bass = Voice()
        private val arp = Voice()
        private var drum = 0
        private var drumAge = 0
        private var noise = 0x1234567
        val done get() = step >= song.steps.size

        fun render(out: ShortArray) {
            val volume = when (level) { 1 -> 0.12f; 2 -> 0.22f; 3 -> 0.35f; else -> 0f }
            val soft = RetroSound.soft
            val wave = if (soft) SINE else SQUARE
            for (i in out.indices) {
                if (sampleInStep == 0 && step < song.steps.size) {
                    val s = song.steps[step]
                    lead.trigger(s.lead)
                    bass.trigger(s.bass)
                    arp.trigger(s.arp)
                    if (s.drum != 0) { drum = s.drum; drumAge = 0 }
                }
                var v = 0f
                v += 0.28f * voice(lead, wave, 0.9f)
                v += 0.30f * voice(bass, TRIANGLE, 0.95f)
                v += 0.09f * voice(arp, if (soft) SINE else PULSE, 0.5f)
                v += 0.22f * drumSample()
                out[i] = (v * volume * Short.MAX_VALUE).coerceIn(-32767f, 32767f).toInt().toShort()
                if (++sampleInStep >= samplesPerStep) {
                    sampleInStep = 0
                    step++
                }
            }
        }

        /** Una muestra de una voz, con caída suave y un pequeño ataque para que no chasquee. */
        private fun voice(v: Voice, wave: Int, sustain: Float): Float {
            if (!v.on) return 0f
            v.phase = (v.phase + v.freq / RATE) % 1f
            val t = v.age++.toFloat() / RATE
            val env = (t / 0.004f).coerceAtMost(1f) * (sustain + (1 - sustain) * kotlin.math.exp(-t * 6f))
            return env * shape(wave, v.phase)
        }

        private fun drumSample(): Float {
            if (drum == 0) return 0f
            val t = drumAge++.toFloat() / RATE
            noise = noise * 1103515245 + 12345
            val white = ((noise ushr 16) and 0x7FFF) / 16383.5f - 1f
            return when (drum) {
                KICK -> if (t > 0.15f) 0f else sin(2 * PI * (110 - 400 * t) * t).toFloat() * (1 - t / 0.15f)
                SNARE -> if (t > 0.12f) 0f else white * (1 - t / 0.12f) * 0.7f
                HAT -> if (t > 0.03f) 0f else white * (1 - t / 0.03f) * 0.35f
                else -> 0f
            }
        }
    }

    private const val SQUARE = 0
    private const val PULSE = 1
    private const val SINE = 2
    private const val TRIANGLE = 3
    private const val SAW = 4

    private fun shape(wave: Int, phase: Float): Float = when (wave) {
        SQUARE -> if (phase < 0.5f) 1f else -1f
        PULSE -> if (phase < 0.25f) 1f else -1f
        SINE -> sin(2 * PI * phase).toFloat()
        TRIANGLE -> 4f * kotlin.math.abs(phase - 0.5f) - 1f
        else -> 2f * phase - 1f
    }
}

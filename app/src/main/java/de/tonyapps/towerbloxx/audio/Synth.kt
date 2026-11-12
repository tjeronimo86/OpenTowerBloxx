package de.tonyapps.towerbloxx.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Erzeugt alle Klaenge der App per Synthese: keine fremden Aufnahmen.
 * Ergebnis sind 16-Bit-Mono-WAVs mit 44,1 kHz.
 */
object Synth {
    const val RATE = 44100
    private const val TAU = (2 * PI).toFloat()

    private fun buf(seconds: Float) = FloatArray((seconds * RATE).toInt())

    private fun env(t: Float, attack: Float, decay: Float): Float =
        if (t < attack) t / attack else exp(-(t - attack) * decay)

    /** Sinus mit Frequenzverlauf; f(t) liefert Hz. */
    private fun tone(out: FloatArray, start: Float, len: Float, amp: Float, attack: Float, decay: Float, f: (Float) -> Float) {
        var phase = 0f
        val s0 = (start * RATE).toInt()
        val n = (len * RATE).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx !in out.indices) break
            val t = i.toFloat() / RATE
            phase += TAU * f(t) / RATE
            out[idx] += sin(phase) * amp * env(t, attack, decay)
        }
    }

    /** Glocke: unharmonische Teiltoene wie bei echten Glocken. */
    private fun bell(out: FloatArray, start: Float, freq: Float, amp: Float, decay: Float = 4f) {
        val partials = floatArrayOf(1f, 2f, 2.76f, 5.4f)
        val gains = floatArrayOf(1f, 0.45f, 0.3f, 0.12f)
        for (k in partials.indices) {
            tone(out, start, 1.6f, amp * gains[k], 0.002f, decay * (1f + k * 0.8f)) { freq * partials[k] }
        }
    }

    /** Marimba-artiger Schlag fuer die Melodie. */
    private fun mallet(out: FloatArray, start: Float, freq: Float, amp: Float, len: Float = 0.6f) {
        tone(out, start, len, amp, 0.003f, 7f) { freq }
        tone(out, start, len, amp * 0.25f, 0.002f, 22f) { freq * 4f }
        tone(out, start, len, amp * 0.1f, 0.001f, 40f) { freq * 9.9f }
    }

    /** Gefiltertes Rauschen (Zustandsvariablenfilter), Mittenfrequenz ueber die Zeit. */
    private fun noise(
        out: FloatArray, start: Float, len: Float, amp: Float, attack: Float, decay: Float,
        seed: Int, q: Float = 0.6f, mode: Int = 1, freq: (Float) -> Float,
    ) {
        val rnd = Random(seed)
        var low = 0f
        var band = 0f
        val s0 = (start * RATE).toInt()
        val n = (len * RATE).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx !in out.indices) break
            val t = i.toFloat() / RATE
            val fc = 2f * sin(PI.toFloat() * min(freq(t), 8000f) / RATE)
            val x = rnd.nextFloat() * 2f - 1f
            low += fc * band
            val high = x - low - q * band
            band += fc * high
            val v = when (mode) { 0 -> low; 1 -> band; else -> high }
            out[idx] += v * amp * env(t, attack, decay)
        }
    }

    private fun normalize(out: FloatArray, peak: Float = 0.9f): FloatArray {
        var m = 1e-6f
        for (v in out) m = max(m, abs(v))
        val k = peak / m
        for (i in out.indices) out[i] = tanh(out[i] * k * 1.1f) / tanh(1.1f)
        // Weiche Enden gegen Knacken
        val fade = min(200, out.size / 4)
        for (i in 0 until fade) {
            out[i] *= i / fade.toFloat()
            out[out.size - 1 - i] *= i / fade.toFloat()
        }
        return out
    }

    // ------------------------------------------------------------ Effekte

    fun drop(): FloatArray = normalize(buf(0.35f).also {
        noise(it, 0f, 0.35f, 1f, 0.04f, 7f, 1, q = 0.5f) { t -> 2400f * exp(-t * 5f) + 300f }
        tone(it, 0f, 0.3f, 0.25f, 0.01f, 9f) { t -> 520f - t * 700f }
    }, 0.55f)

    fun land(): FloatArray = normalize(buf(0.45f).also {
        tone(it, 0f, 0.45f, 1f, 0.002f, 9f) { t -> 48f + 70f * exp(-t * 18f) }
        tone(it, 0f, 0.2f, 0.45f, 0.001f, 30f) { 410f }
        tone(it, 0.005f, 0.2f, 0.3f, 0.001f, 38f) { 690f }
        noise(it, 0f, 0.12f, 0.9f, 0.001f, 40f, 2, q = 0.7f, mode = 0) { 900f }
        // Kleines Nachrumpeln
        noise(it, 0.04f, 0.3f, 0.35f, 0.01f, 14f, 3, mode = 0) { 260f }
    }, 0.95f)

    fun perfect(): FloatArray = normalize(buf(1.4f).also {
        val notes = floatArrayOf(1046.5f, 1318.5f, 1568f, 2093f)
        notes.forEachIndexed { i, f -> bell(it, i * 0.065f, f, 0.6f, 3.5f) }
        noise(it, 0f, 0.6f, 0.12f, 0.01f, 6f, 4, q = 0.3f, mode = 2) { 7000f }
    }, 0.7f)

    fun miss(): FloatArray = normalize(buf(1.1f).also {
        // Pfeifen nach unten mit Vibrato
        tone(it, 0f, 0.9f, 0.6f, 0.03f, 1.8f) { t -> (1150f - 980f * (t / 0.9f).pow(0.8f)) * (1f + 0.02f * sin(TAU * 9f * t)) }
        tone(it, 0f, 0.9f, 0.15f, 0.03f, 1.8f) { t -> 2f * (1150f - 980f * (t / 0.9f).pow(0.8f)) }
    }, 0.6f)

    fun crash(): FloatArray = normalize(buf(1.0f).also {
        noise(it, 0f, 1f, 1f, 0.003f, 4f, 5, q = 0.9f, mode = 0) { t -> 1800f * exp(-t * 2f) + 200f }
        tone(it, 0f, 0.6f, 0.8f, 0.002f, 6f) { t -> 40f + 50f * exp(-t * 10f) }
        val rnd = Random(6)
        repeat(9) { k ->
            val at = 0.03f + k * 0.07f + rnd.nextFloat() * 0.04f
            tone(it, at, 0.15f, 0.25f, 0.001f, 35f) { 300f + rnd.nextFloat() * 900f }
        }
    }, 0.8f)

    fun resident(): FloatArray = normalize(buf(0.35f).also {
        tone(it, 0f, 0.08f, 0.5f, 0.002f, 40f) { t -> 700f - t * 3000f }
        bell(it, 0.03f, 1760f, 0.35f, 9f)
    }, 0.45f)

    fun spawn(): FloatArray = normalize(buf(0.5f).also {
        // Metallisches Klacken des Krans mit Kettenrasseln
        for ((f, g) in listOf(523f to 1f, 1210f to 0.6f, 1835f to 0.35f, 2740f to 0.2f)) {
            tone(it, 0f, 0.4f, g * 0.5f, 0.001f, 16f) { f }
        }
        val rnd = Random(7)
        repeat(6) { k ->
            noise(it, 0.05f + k * 0.045f, 0.05f, 0.25f, 0.001f, 60f, 10 + k, q = 0.4f) { 3000f + rnd.nextFloat() * 2000f }
        }
    }, 0.35f)

    fun tap(): FloatArray = normalize(buf(0.08f).also {
        tone(it, 0f, 0.08f, 1f, 0.001f, 60f) { t -> 1200f - t * 4000f }
        noise(it, 0f, 0.02f, 0.4f, 0.0005f, 200f, 8, mode = 2) { 5000f }
    }, 0.4f)

    fun tick(): FloatArray = normalize(buf(0.1f).also {
        tone(it, 0f, 0.1f, 1f, 0.001f, 70f) { 1900f }
        noise(it, 0f, 0.03f, 0.5f, 0.0005f, 150f, 9, mode = 2) { 6000f }
    }, 0.4f)

    fun over(): FloatArray = normalize(buf(1.8f).also {
        val notes = floatArrayOf(784f, 659.3f, 523.3f, 392f)
        notes.forEachIndexed { i, f ->
            val len = if (i == 3) 1.2f else 0.32f
            tone(it, i * 0.26f, len, 0.6f, 0.01f, if (i == 3) 2.2f else 6f) { t -> f * (1f + 0.004f * sin(TAU * 5f * t)) }
            tone(it, i * 0.26f, len, 0.2f, 0.01f, if (i == 3) 3f else 8f) { f * 2f }
        }
    }, 0.6f)

    fun win(): FloatArray = normalize(buf(2.4f).also {
        // Fanfare: Dreiklang aufwaerts, dann Akkord mit Glocken
        val seq = floatArrayOf(523.3f, 659.3f, 784f)
        seq.forEachIndexed { i, f -> brass(it, i * 0.16f, 0.2f, f, 0.5f) }
        for (f in floatArrayOf(523.3f, 659.3f, 784f, 1046.5f)) brass(it, 0.5f, 1.7f, f, 0.32f)
        bell(it, 0.5f, 2093f, 0.25f, 2.5f)
        bell(it, 0.62f, 2637f, 0.2f, 2.5f)
    }, 0.75f)

    private fun brass(out: FloatArray, start: Float, len: Float, freq: Float, amp: Float) {
        // Saegezahn aus wenigen Obertoenen mit weichem Einschwingen
        for (k in 1..7) {
            tone(out, start, len, amp / k, 0.04f, 1.2f + k * 0.3f) { t -> freq * k * (1f + 0.003f * sin(TAU * 5.5f * t)) }
        }
    }

    // ------------------------------------------------------------ Atmosphaere und Musik

    /** Stadtgeraeusche als Schleife: Verkehrsrauschen, ab und zu Voegel. */
    fun ambience(): FloatArray {
        val out = buf(8f)
        noise(out, 0f, 8f, 1f, 0.001f, 0f, 20, q = 1.2f, mode = 0) { t -> 180f + 60f * sin(TAU * t / 8f) }
        val rnd = Random(21)
        repeat(7) {
            val at = 0.6f + rnd.nextFloat() * 6.6f
            val base = 3200f + rnd.nextFloat() * 1500f
            repeat(2 + rnd.nextInt(3)) { k ->
                tone(out, at + k * 0.11f, 0.09f, 0.12f, 0.005f, 30f) { t -> base + 1800f * sin(TAU * 12f * t) }
            }
        }
        return normalize(out, 0.5f)
    }

    /** Froehliche Schleife, 16 Takte bei 116 BPM. */
    fun music(): FloatArray {
        val bpm = 116f
        val beat = 60f / bpm
        val bars = 16
        val out = buf(bars * 4 * beat + 0.02f)
        // C  G  Am F | C  G  F  G (zweimal mit anderer Melodie)
        val roots = floatArrayOf(130.8f, 98f, 110f, 87.3f, 130.8f, 98f, 87.3f, 98f)
        val chords = arrayOf(
            floatArrayOf(261.6f, 329.6f, 392f), floatArrayOf(246.9f, 293.7f, 392f), floatArrayOf(261.6f, 329.6f, 440f),
            floatArrayOf(261.6f, 349.2f, 440f), floatArrayOf(261.6f, 329.6f, 392f), floatArrayOf(246.9f, 293.7f, 392f),
            floatArrayOf(261.6f, 349.2f, 440f), floatArrayOf(246.9f, 293.7f, 392f),
        )
        // Melodie in Achteln: Skalenstufen in C-Dur, -1 = Pause
        val scale = floatArrayOf(523.3f, 587.3f, 659.3f, 698.5f, 784f, 880f, 987.8f, 1046.5f)
        val melodyA = intArrayOf(
            4, -1, 4, 5, 4, 2, 0, -1, 1, -1, 1, 2, 4, -1, 1, -1,
            2, -1, 2, 4, 5, 4, 2, -1, 3, -1, 3, 2, 3, 5, 4, -1,
            4, -1, 4, 5, 7, 5, 4, -1, 1, 2, 4, 2, 1, -1, 4, -1,
            5, 4, 3, -1, 2, 1, 0, -1, 1, -1, 4, 2, 1, -1, -1, -1,
        )
        val melodyB = intArrayOf(
            0, 2, 4, 7, 4, 2, 4, -1, 1, 4, 6, 4, 1, -1, 4, -1,
            2, 4, 5, 7, 5, 4, 2, -1, 3, 5, 7, 5, 3, -1, 2, -1,
            0, 2, 4, 7, 6, 4, 2, -1, 1, 4, 6, 4, 6, 7, 6, -1,
            5, 4, 3, 2, 3, 4, 5, -1, 4, -1, 6, -1, 7, -1, -1, -1,
        )
        for (bar in 0 until bars) {
            val half = bar / 8
            val idx = bar % 8
            val t0 = bar * 4 * beat
            // Bass: Grundton auf 1 und 3, Quinte als Durchgang
            for (b in 0 until 4) {
                val f = if (b == 3) roots[idx] * 1.5f else roots[idx]
                tone(out, t0 + b * beat, beat * 0.9f, 0.32f, 0.005f, 3.2f) { f }
                tone(out, t0 + b * beat, beat * 0.9f, 0.12f, 0.005f, 6f) { f * 2f }
            }
            // Akkorde als weiche Stoesse auf den Offbeats
            for (b in 0 until 4) {
                for (f in chords[idx]) {
                    tone(out, t0 + b * beat + beat / 2, beat * 0.45f, 0.07f, 0.01f, 7f) { t -> f * (1f + 0.002f * sin(TAU * 6f * t)) }
                }
            }
            // Melodie
            val mel = if (half == 0) melodyA else melodyB
            for (e in 0 until 8) {
                val step = mel[idx * 8 + e]
                if (step >= 0) mallet(out, t0 + e * beat / 2, scale[step], 0.22f)
            }
            // Schlagzeug: Kick 1+3, Snare 2+4, Hi-Hat in Achteln
            for (b in 0 until 4) {
                val tb = t0 + b * beat
                if (b % 2 == 0) tone(out, tb, 0.3f, 0.6f, 0.001f, 14f) { t -> 45f + 110f * exp(-t * 30f) }
                else noise(out, tb, 0.2f, 0.3f, 0.001f, 18f, 100 + bar * 4 + b, q = 0.8f) { 1800f }
                noise(out, tb, 0.05f, 0.08f, 0.0005f, 70f, 200 + bar * 8 + b, q = 0.3f, mode = 2) { 7500f }
                noise(out, tb + beat / 2, 0.05f, 0.06f, 0.0005f, 70f, 300 + bar * 8 + b, q = 0.3f, mode = 2) { 7500f }
            }
        }
        return normalize(out, 0.55f)
    }

    // ------------------------------------------------------------ WAV

    fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2)
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use {
            it.write(header.array())
            it.write(data.array())
        }
        tmp.renameTo(file)
    }
}

package de.tonyapps.towerbloxx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

enum class Sfx { DROP, LAND, PERFECT, MISS, CRASH, RESIDENT, SPAWN, TAP, TICK, OVER, WIN }

/** Klaenge, Musik und Vibration. Alles wird beim ersten Start erzeugt und zwischengespeichert. */
object Audio {
    private const val VERSION = 1

    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<Sfx, Int>()
    private var ambienceId = 0
    private var ambienceStream = 0
    private var music: MediaPlayer? = null
    private var musicFile: File? = null
    private var vibrator: Vibrator? = null
    @Volatile private var ready = false

    @Volatile var soundOn = true
    @Volatile var musicOn = true
        set(value) {
            field = value
            if (value) startMusic() else stopMusic()
        }
    @Volatile var vibrationOn = true

    private var wantMusic = false

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        pool = sp
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val dir = File(context.cacheDir, "sound-v$VERSION")
        thread(name = "synth", priority = Thread.NORM_PRIORITY - 1) {
            fun file(name: String, make: () -> FloatArray): File {
                val f = File(dir, "$name.wav")
                if (!f.exists()) Synth.writeWav(f, make())
                return f
            }
            val makers: Map<Sfx, () -> FloatArray> = mapOf(
                Sfx.DROP to Synth::drop, Sfx.LAND to Synth::land, Sfx.PERFECT to Synth::perfect,
                Sfx.MISS to Synth::miss, Sfx.CRASH to Synth::crash, Sfx.RESIDENT to Synth::resident,
                Sfx.SPAWN to Synth::spawn, Sfx.TAP to Synth::tap, Sfx.TICK to Synth::tick,
                Sfx.OVER to Synth::over, Sfx.WIN to Synth::win,
            )
            for ((s, make) in makers) ids[s] = sp.load(file(s.name.lowercase(), make).path, 1)
            ambienceId = sp.load(file("ambience", Synth::ambience).path, 1)
            ready = true
            musicFile = file("music", Synth::music)
            if (wantMusic) startMusic()
        }
    }

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!soundOn || !ready) return
        val id = ids[s] ?: return
        pool?.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun ambience(on: Boolean) {
        val sp = pool ?: return
        if (on && soundOn && ready && ambienceStream == 0) {
            ambienceStream = sp.play(ambienceId, 0.35f, 0.35f, 0, -1, 1f)
        } else if (!on && ambienceStream != 0) {
            sp.stop(ambienceStream)
            ambienceStream = 0
        }
    }

    /** Musik soll laufen (Menue und Spiel), sofern eingeschaltet. */
    fun wantMusic(on: Boolean) {
        wantMusic = on
        if (on) startMusic() else stopMusic()
    }

    @Synchronized
    private fun startMusic() {
        if (!musicOn || !wantMusic) return
        val f = musicFile ?: return
        if (music != null) {
            music?.start()
            return
        }
        music = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(f.path)
                isLooping = true
                setVolume(0.42f, 0.42f)
                prepare()
                start()
            }
        }.getOrNull()
    }

    @Synchronized
    private fun stopMusic() {
        music?.let { runCatching { if (it.isPlaying) it.pause() } }
    }

    fun buzz(ms: Long, strength: Int = 120) {
        if (!vibrationOn) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, strength.coerceIn(1, 255))) }
    }

    fun buzzPattern(timings: LongArray, amps: IntArray) {
        if (!vibrationOn) return
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(timings, amps, -1)) }
    }
}

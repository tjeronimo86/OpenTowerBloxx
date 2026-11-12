package de.tonyapps.towerbloxx.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** GL-Flaeche fuer ein oder zwei Spieler; Tippen links/rechts geht an den jeweiligen Spieler. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class GameSurface(
    context: Context,
    val renderer: GameRenderer,
    private val players: Int,
    private val onTap: (Int) -> Unit,
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MultisampleChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = e.getX(e.actionIndex)
                    val p = if (players <= 1) 0 else (x / width * players).toInt().coerceIn(0, players - 1)
                    queueEvent { onTap(p) }
                }
            }
            true
        }
    }

    /** 4x MSAA fuer glatte Kanten, sonst ohne. */
    private class MultisampleChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val withMsaa = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT
                EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE,
            )
            val plain = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, 0x40, EGL10.EGL_NONE,
            )
            for (spec in listOf(withMsaa, plain)) {
                val num = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                if (egl.eglChooseConfig(display, spec, configs, 1, num) && num[0] > 0) {
                    configs[0]?.let { return it }
                }
            }
            throw IllegalStateException("Keine passende EGL-Konfiguration")
        }
    }
}

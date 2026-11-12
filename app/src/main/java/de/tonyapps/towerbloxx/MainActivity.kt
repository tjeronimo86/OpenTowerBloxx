package de.tonyapps.towerbloxx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.ui.TowerApp
import de.tonyapps.towerbloxx.ui.TowerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Vollbild wie ein Spiel: Leisten erscheinen nur beim Wischen
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent { TowerTheme { TowerApp() } }
    }

    override fun onResume() {
        super.onResume()
        Audio.wantMusic(true)
    }

    override fun onPause() {
        super.onPause()
        Audio.wantMusic(false)
    }
}

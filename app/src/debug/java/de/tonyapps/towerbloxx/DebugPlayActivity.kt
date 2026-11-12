package de.tonyapps.towerbloxx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.ThemeMode
import de.tonyapps.towerbloxx.game.Kind
import de.tonyapps.towerbloxx.game.Modes
import de.tonyapps.towerbloxx.ui.Screen
import de.tonyapps.towerbloxx.ui.TowerApp
import de.tonyapps.towerbloxx.ui.TowerTheme

/**
 * Nur fuer die automatische Pruefung in tools/ci/verify.sh:
 * startet einen Modus direkt, der Computer spielt selbst.
 */
class DebugPlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = intent.getStringExtra("mode") ?: "quick"
        when (intent.getStringExtra("theme")) {
            "night" -> Store.changeTheme(ThemeMode.DARK)
            "day" -> Store.changeTheme(ThemeMode.LIGHT)
        }
        val start = when (mode) {
            "time" -> Screen.Play(listOf(Modes.time().copy(seed = 7)))
            "challenge" -> Screen.Play(listOf(Modes.challenge(intent.getIntExtra("level", 0)).copy(seed = 7)))
            "city" -> Screen.Play(listOf(Modes.city(Kind.entries[intent.getIntExtra("kind", 0)], 12).copy(seed = 7)))
            "party" -> Screen.Play(listOf(Modes.party().copy(seed = 7), Modes.party().copy(seed = 8)))
            "citymap" -> Screen.City
            "hall" -> Screen.Hall
            "settings" -> Screen.Settings
            "challenges" -> Screen.Challenge
            "menu" -> Screen.Menu
            else -> Screen.Play(listOf(Modes.quick().copy(seed = 7)))
        }
        setContent { TowerTheme { TowerApp(start, autoplay = true) } }
    }
}

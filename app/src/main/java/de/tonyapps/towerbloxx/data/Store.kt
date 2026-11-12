package de.tonyapps.towerbloxx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.tonyapps.towerbloxx.game.Kind
import org.json.JSONArray
import org.json.JSONObject

/** Texte ohne Compose-Kontext; folgt der Sprache des Geraets. */
object AppContext {
    lateinit var app: Context
}

fun tr(id: Int, vararg args: Any): String = AppContext.app.getString(id, *args)

data class Score(val population: Int, val floors: Int, val date: Long)

data class Plot(val kind: Kind, val floors: Int, val population: Int)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Einstellungen und Spielstaende in den SharedPreferences. */
object Store {
    private lateinit var prefs: SharedPreferences

    var sound by mutableStateOf(true); private set
    var music by mutableStateOf(true); private set
    var vibration by mutableStateOf(true); private set
    var theme by mutableStateOf(ThemeMode.SYSTEM); private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("tower", Context.MODE_PRIVATE)
        sound = prefs.getBoolean("sound", true)
        music = prefs.getBoolean("music", true)
        vibration = prefs.getBoolean("vibration", true)
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun changeSound(v: Boolean) { sound = v; prefs.edit().putBoolean("sound", v).apply() }
    fun changeMusic(v: Boolean) { music = v; prefs.edit().putBoolean("music", v).apply() }
    fun changeVibration(v: Boolean) { vibration = v; prefs.edit().putBoolean("vibration", v).apply() }
    fun changeTheme(v: ThemeMode) { theme = v; prefs.edit().putString("theme", v.name).apply() }

    // ------------------------------------------------------------ Ruhmeshalle

    fun quickScores(): List<Score> = readScores("quick")
    fun timeScores(): List<Score> = readScores("time")

    private fun readScores(key: String): List<Score> {
        val arr = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Score(o.getInt("p"), o.getInt("f"), o.getLong("d"))
        }
    }

    /** Traegt ein Ergebnis ein; liefert den Platz (0 = Rekord) oder -1. */
    fun addScore(key: String, s: Score, byFloors: Boolean = false): Int {
        val list = (readScores(key) + s).sortedWith(
            if (byFloors) compareByDescending<Score> { it.floors }.thenByDescending { it.population }
            else compareByDescending<Score> { it.population }.thenByDescending { it.floors },
        ).take(10)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("p", it.population).put("f", it.floors).put("d", it.date)) }
        prefs.edit().putString(key, arr.toString()).apply()
        return list.indexOf(s)
    }

    fun clearScores() {
        prefs.edit().remove("quick").remove("time").remove("stars").remove("city").apply()
    }

    // ------------------------------------------------------------ Herausforderung

    fun stars(): IntArray {
        val s = prefs.getString("stars", "") ?: ""
        return IntArray(LEVELS) { i -> s.getOrNull(i)?.digitToIntOrNull() ?: 0 }
    }

    fun setStars(level: Int, stars: Int) {
        val all = stars()
        if (stars <= all[level]) return
        all[level] = stars
        prefs.edit().putString("stars", all.joinToString("")).apply()
    }

    /** Level i ist offen, wenn i == 0 oder das vorige geschafft ist. */
    fun unlocked(level: Int): Boolean = level == 0 || stars()[level - 1] > 0

    const val LEVELS = 15

    // ------------------------------------------------------------ Stadt

    const val CITY = 5

    fun city(): Array<Plot?> {
        val arr = runCatching { JSONArray(prefs.getString("city", "[]")) }.getOrDefault(JSONArray())
        val plots = arrayOfNulls<Plot>(CITY * CITY)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val idx = o.getInt("i")
            if (idx in plots.indices) plots[idx] = Plot(Kind.valueOf(o.getString("k")), o.getInt("f"), o.getInt("p"))
        }
        return plots
    }

    fun setPlot(index: Int, plot: Plot?) {
        val plots = city()
        plots[index] = plot
        val arr = JSONArray()
        plots.forEachIndexed { i, p ->
            if (p != null) arr.put(JSONObject().put("i", i).put("k", p.kind.name).put("f", p.floors).put("p", p.population))
        }
        prefs.edit().putString("city", arr.toString()).apply()
    }

    fun resetCity() = prefs.edit().remove("city").apply()
}

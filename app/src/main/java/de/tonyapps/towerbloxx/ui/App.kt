package de.tonyapps.towerbloxx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.data.Changelog
import de.tonyapps.towerbloxx.data.Plot
import de.tonyapps.towerbloxx.data.Score
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.tr
import de.tonyapps.towerbloxx.game.GameConfig
import de.tonyapps.towerbloxx.game.GameStats
import de.tonyapps.towerbloxx.game.Mode
import de.tonyapps.towerbloxx.game.Modes
import de.tonyapps.towerbloxx.update.Updater
import java.text.NumberFormat

sealed interface Screen {
    data object Menu : Screen
    data object Challenge : Screen
    data object City : Screen
    data object Hall : Screen
    data object Settings : Screen
    data class Play(val configs: List<GameConfig>, val serial: Long = System.nanoTime()) : Screen
    data class Result(val stats: List<GameStats>, val rank: Int, val stars: Int, val improved: Boolean) : Screen
}

@Composable
fun TowerApp(start: Screen = Screen.Menu, autoplay: Boolean = false) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(start) }
    var showNews by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Changelog.isNew(context) && !autoplay) showNews = true
        Updater.autoCheck(context)
    }
    LaunchedEffect(Unit) { Audio.wantMusic(true) }

    fun play(vararg c: GameConfig) {
        screen = Screen.Play(c.toList())
    }

    fun finish(stats: List<GameStats>) {
        screen = record(stats)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            contentKey = { if (it is Screen.Play) "play-${it.serial}" else it::class.simpleName },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Menu -> MenuScreen(
                    onQuick = { play(Modes.quick()) },
                    onTime = { play(Modes.time()) },
                    onChallenge = { screen = Screen.Challenge },
                    onCity = { screen = Screen.City },
                    onParty = { play(Modes.party(), Modes.party()) },
                    onHall = { screen = Screen.Hall },
                    onSettings = { screen = Screen.Settings },
                    onNews = { showNews = true },
                )
                Screen.Challenge -> ChallengeScreen(onBack = { screen = Screen.Menu }) { play(Modes.challenge(it)) }
                Screen.City -> CityScreen(onBack = { screen = Screen.Menu }) { kind, plot -> play(Modes.city(kind, plot)) }
                Screen.Hall -> HallScreen(onBack = { screen = Screen.Menu })
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu }, onNews = { showNews = true })
                is Screen.Play -> key(s.serial) {
                    GameScreen(
                        configs = s.configs,
                        autoplay = autoplay,
                        onFinish = { finish(it) },
                        onQuit = { screen = backTarget(s.configs.first()) },
                        onRestart = { screen = Screen.Play(s.configs.map { it.copy(seed = System.nanoTime()) }) },
                    )
                }
                is Screen.Result -> ResultScreen(
                    s,
                    onAgain = { screen = Screen.Play(s.stats.map { it.config.copy(seed = System.nanoTime()) }) },
                    onNext = {
                        val lvl = s.stats.first().config.level + 1
                        screen = if (lvl < Store.LEVELS) Screen.Play(listOf(Modes.challenge(lvl))) else Screen.Challenge
                    },
                    onBack = { screen = backTarget(s.stats.first().config) },
                )
            }
        }
        if (showNews) WhatsNewScreen { showNews = false }
        UpdateSheet()
    }
}

private fun backTarget(c: GameConfig): Screen = when (c.mode) {
    Mode.CHALLENGE -> Screen.Challenge
    Mode.CITY -> Screen.City
    else -> Screen.Menu
}

/** Ergebnis eintragen: Ruhmeshalle, Sterne oder Grundstueck. */
private fun record(stats: List<GameStats>): Screen {
    val s = stats.first()
    val now = System.currentTimeMillis()
    var rank = -1
    var stars = 0
    var improved = false
    when (s.config.mode) {
        Mode.QUICK -> if (s.floors > 0) rank = Store.addScore("quick", Score(s.population, s.floors, now))
        Mode.TIME -> if (s.floors > 0) rank = Store.addScore("time", Score(s.population, s.floors, now), byFloors = true)
        Mode.CHALLENGE -> {
            stars = Modes.challengeStars(s)
            if (stars > 0) Store.setStars(s.config.level, stars)
        }
        Mode.CITY -> {
            val old = Store.city()[s.config.plot]
            if (s.floors > 0 && (old == null || old.kind != s.config.kind || s.population > old.population)) {
                Store.setPlot(s.config.plot, Plot(s.config.kind, s.floors, s.population))
                improved = true
            }
        }
        Mode.PARTY -> {}
    }
    return Screen.Result(stats, rank, stars, improved)
}

@Composable
fun ResultScreen(r: Screen.Result, onAgain: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s = r.stats.first()
    val nf = NumberFormat.getIntegerInstance()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val title = when (s.config.mode) {
        Mode.CHALLENGE -> if (s.won) tr(R.string.level_done) else tr(R.string.level_failed)
        Mode.CITY -> if (s.won) tr(R.string.tower_done) else tr(R.string.tower_unfinished)
        Mode.TIME -> tr(R.string.time_up)
        Mode.PARTY -> {
            val a = r.stats[0].population
            val b = r.stats.getOrNull(1)?.population ?: 0
            if (a == b) tr(R.string.draw) else tr(R.string.player_wins, if (a > b) 1 else 2)
        }
        Mode.QUICK -> tr(R.string.game_over)
    }
    SkyBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top + 24.dp, bottom = bottom + 16.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTitle(title, 38.sp, Modifier.fillMaxWidth())
            if (r.rank == 0) {
                Spacer(Modifier.height(6.dp))
                OutlinedTitle(
                    tr(R.string.new_record), 28.sp,
                    fill = Brush.verticalGradient(listOf(Color(0xFFFFE0F0), Color(0xFFFF5D9E))), outlineWidth = 5.dp,
                )
            }
            if (s.config.mode == Mode.CHALLENGE && s.won) {
                Spacer(Modifier.height(10.dp))
                Row { for (k in 0 until 3) StarIcon(k < r.stars, Modifier.size(56.dp).padding(4.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            if (s.config.mode == Mode.PARTY) {
                Row(Modifier.fillMaxWidth()) {
                    r.stats.forEachIndexed { i, p ->
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Card {
                                androidx.compose.material3.Text(tr(R.string.player, i + 1), color = textColor(), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                                StatLine(tr(R.string.stat_floors), "${p.floors}")
                                StatLine(tr(R.string.stat_residents), nf.format(p.population))
                                StatLine(tr(R.string.stat_perfects), "${p.perfects}")
                            }
                        }
                    }
                }
            } else {
                Card {
                    StatLine(tr(R.string.stat_floors), if (s.config.targetFloors > 0) "${s.floors} / ${s.config.targetFloors}" else "${s.floors}")
                    StatLine(tr(R.string.stat_residents), nf.format(s.population))
                    StatLine(tr(R.string.stat_perfects), "${s.perfects}")
                    StatLine(tr(R.string.stat_combo), "×${s.bestCombo}")
                    if (r.rank > 0) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.Text(tr(R.string.placed, r.rank + 1), color = subColor(), fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (s.config.mode == Mode.CHALLENGE && s.won && s.config.level + 1 < Store.LEVELS) {
                ChunkyButton(tr(R.string.next_level), Palette.Green, onClick = onNext)
            }
            if (s.config.mode != Mode.CITY) {
                ChunkyButton(tr(R.string.play_again), if (s.config.mode == Mode.CHALLENGE && s.won) Palette.Blue else Palette.Green, onClick = onAgain)
            }
            ChunkyButton(
                if (s.config.mode == Mode.CITY) tr(R.string.to_city) else tr(R.string.menu),
                if (s.config.mode == Mode.CITY) Palette.Green else Palette.Red, onClick = onBack,
            )
        }
    }
}

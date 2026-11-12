package de.tonyapps.towerbloxx.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.audio.Sfx
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.tr
import de.tonyapps.towerbloxx.game.Game
import de.tonyapps.towerbloxx.game.GameConfig
import de.tonyapps.towerbloxx.game.GameEvent
import de.tonyapps.towerbloxx.game.GameStats
import de.tonyapps.towerbloxx.game.Kind
import de.tonyapps.towerbloxx.game.Mode
import de.tonyapps.towerbloxx.gl.GameRenderer
import de.tonyapps.towerbloxx.gl.GameSurface
import de.tonyapps.towerbloxx.gl.ViewInfo
import kotlin.math.min
import kotlin.random.Random

/** Was das HUD eines Spielers zeigt; wird aus dem GL-Thread veroeffentlicht. */
data class Hud(
    val floors: Int = 0,
    val population: Int = 0,
    val lives: Int = 3,
    val maxLives: Int = 3,
    val combo: Int = 0,
    val comboTimer: Int = 0,
    val timeLeft: Int = 0,
    val gainSerial: Int = 0,
    val lastGain: Int = 0,
    val perfect: Boolean = false,
    val missSerial: Int = 0,
    val markers: List<Pair<String, Float>> = emptyList(),
    val finished: Boolean = false,
    val target: Int = 0,
    val hint: Boolean = true,
    val kind: Kind = Kind.BLUE,
)

/** Verbindet Spiele, Klang und HUD. */
class GameSession(val configs: List<GameConfig>, autoplay: Boolean = false) {
    private val main = Handler(Looper.getMainLooper())
    private val rnd = Random(1)
    val huds = mutableStateListOf<Hud>().apply { repeat(configs.size) { add(Hud()) } }
    var allFinished by mutableStateOf(false); private set

    val games: List<Game> = configs.mapIndexed { i, c ->
        Game(c) { g, e -> onEvent(i, g, e) }.also { it.autoplay = autoplay }
    }

    private var lastPublish = 0L
    private val last = arrayOfNulls<Hud>(configs.size)

    init {
        games.forEach { it.markers = staticMarkers(it.config) }
    }

    private fun staticMarkers(c: GameConfig): List<Pair<String, Int>> = when (c.mode) {
        Mode.QUICK -> Store.quickScores().take(3).mapIndexed { i, s -> (if (i == 0) tr(R.string.record) else "#${i + 1}") to s.floors }
        Mode.TIME -> Store.timeScores().take(1).map { tr(R.string.record) to it.floors }
        Mode.CHALLENGE -> listOf(tr(R.string.goal) to c.targetFloors)
        Mode.CITY -> buildList {
            add(tr(R.string.roof) to c.targetFloors)
            Store.city().getOrNull(c.plot)?.let { if (it.kind == c.kind) add(tr(R.string.best) to it.floors) }
        }
        Mode.PARTY -> emptyList()
    }.filter { it.second > 0 }

    private fun onEvent(player: Int, g: Game, e: GameEvent) {
        val solo = games.size == 1
        val vol = if (solo) 1f else 0.75f
        when (e) {
            GameEvent.SPAWN -> Audio.play(Sfx.SPAWN, 0.45f * vol)
            GameEvent.DROP -> Audio.play(Sfx.DROP, 0.7f * vol)
            GameEvent.LAND -> {
                Audio.play(Sfx.LAND, vol)
                Audio.buzz(22, 150)
            }
            GameEvent.PERFECT -> {
                Audio.play(Sfx.PERFECT, 0.8f * vol, 1f + 0.06f * min(g.combo - 1, 8))
                Audio.buzzPattern(longArrayOf(0, 18, 50, 18), intArrayOf(0, 200, 0, 120))
            }
            GameEvent.MISS -> {
                Audio.play(Sfx.MISS, 0.8f * vol)
                Audio.buzz(90, 220)
            }
            GameEvent.CRASH -> Audio.play(Sfx.CRASH, 0.6f * vol)
            GameEvent.RESIDENT -> Audio.play(Sfx.RESIDENT, 0.35f * vol, 0.9f + rnd.nextFloat() * 0.3f)
            GameEvent.ROOF -> Audio.play(Sfx.PERFECT, 0.9f, 0.8f)
            GameEvent.OVER -> Audio.play(if (g.config.mode == Mode.CITY && g.floors.isNotEmpty()) Sfx.OVER else Sfx.OVER)
            GameEvent.WIN -> Audio.play(Sfx.WIN)
            GameEvent.TICK -> Audio.play(Sfx.TICK, 0.6f)
        }
        if (player == 0 && e == GameEvent.OVER && games.size == 1) Audio.ambience(false)
    }

    fun tap(player: Int) = games.getOrNull(player)?.tap()

    fun setPaused(p: Boolean) = games.forEach { it.paused = p }

    /** Aufruf je Bild aus dem GL-Thread. */
    fun onFrame(infos: List<ViewInfo>) {
        // Im Party-Modus sieht jeder die Hoehe des anderen
        if (games.size == 2) {
            games[0].markers = listOf(tr(R.string.player, 2) to games[1].floors.size).filter { it.second > 0 }
            games[1].markers = listOf(tr(R.string.player, 1) to games[0].floors.size).filter { it.second > 0 }
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastPublish < 33) return
        lastPublish = now
        val list = games.mapIndexed { i, g ->
            val ys = infos.getOrNull(i)?.markerY.orEmpty()
            Hud(
                floors = g.floors.size,
                population = g.population,
                lives = g.lives,
                maxLives = g.config.lives,
                combo = g.combo,
                comboTimer = (g.comboTimer * 100).toInt(),
                timeLeft = kotlin.math.ceil(g.timeLeft).toInt(),
                gainSerial = g.gainSerial,
                lastGain = g.lastGain,
                perfect = g.lastPerfect,
                missSerial = g.missSerial,
                markers = g.markers.mapIndexedNotNull { k, m ->
                    val y = ys.getOrNull(k) ?: return@mapIndexedNotNull null
                    if (y in 0.04f..0.96f) m.first to ((y * 500).toInt() / 500f) else null
                },
                finished = g.finished,
                target = g.config.targetFloors,
                hint = g.floors.isEmpty() && g.missSerial == 0 && g.phase != Game.Phase.FALLING,
                kind = g.blockKind,
            )
        }
        val changed = list.indices.any { list[it] != last[it] }
        if (!changed) return
        list.forEachIndexed { i, h -> last[i] = h }
        val done = games.all { it.finished }
        main.post {
            list.forEachIndexed { i, h -> if (i < huds.size) huds[i] = h }
            allFinished = done
        }
    }

    fun stats(): List<GameStats> = games.map { it.stats() }
}

@Composable
fun GameScreen(
    configs: List<GameConfig>,
    autoplay: Boolean = false,
    onFinish: (List<GameStats>) -> Unit,
    onQuit: () -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    val night = LocalNight.current
    val session = remember(configs) { GameSession(configs, autoplay) }
    var paused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surface by remember { mutableStateOf<GameSurface?>(null) }

    DisposableEffect(lifecycle, surface) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> {
                    surface?.onPause()
                    Audio.ambience(false)
                    if (!session.allFinished) paused = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    surface?.onResume()
                    Audio.ambience(true)
                }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // Party wie im Original im Querformat: beide Spieler nebeneinander
    DisposableEffect(configs.size) {
        val activity = context as? android.app.Activity
        if (configs.size > 1) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (configs.size > 1) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    DisposableEffect(Unit) {
        Audio.ambience(true)
        onDispose {
            Audio.ambience(false)
            surface?.onPause()
        }
    }
    LaunchedEffect(paused) { surface?.queueEvent { session.setPaused(paused) } }
    LaunchedEffect(session.allFinished) {
        if (session.allFinished) {
            kotlinx.coroutines.delay(1400)
            onFinish(session.stats())
        }
    }
    BackHandler { paused = !paused }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val renderer = GameRenderer(session.games, night) { session.onFrame(it) }
                GameSurface(ctx, renderer, configs.size) { session.tap(it) }.also { surface = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.fillMaxSize()) {
            session.huds.forEachIndexed { i, hud ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PlayerHud(hud, session.configs[i], if (configs.size > 1) i + 1 else 0)
                }
                if (i < session.huds.size - 1) Box(Modifier.width(4.dp).fillMaxHeight().background(Color.Black))
            }
        }
        if (configs.size > 1) PartyGauge(session.huds.map { it.floors }, Modifier.align(Alignment.Center))
        // Pause-Knopf
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .align(if (configs.size > 1) Alignment.TopCenter else Alignment.TopEnd)
                .padding(top = top + 8.dp, end = if (configs.size > 1) 0.dp else 12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable { Audio.play(Sfx.TAP); paused = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp, 18.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Box(Modifier.size(6.dp, 18.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
        }
        AnimatedVisibility(paused, enter = fadeIn(), exit = fadeOut()) {
            PauseMenu(
                onResume = { paused = false },
                onRestart = onRestart,
                onQuit = onQuit,
            )
        }
    }
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(panelColor())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTitle(tr(R.string.paused), 40.sp)
            Spacer(Modifier.height(18.dp))
            ChunkyButton(tr(R.string.resume), Palette.Green, onClick = onResume)
            Spacer(Modifier.height(6.dp))
            ChunkyButton(tr(R.string.restart), Palette.Blue, onClick = onRestart)
            Spacer(Modifier.height(6.dp))
            ChunkyButton(tr(R.string.quit_to_menu), Palette.Red, onClick = onQuit)
        }
    }
}

@Composable
private fun PlayerHud(hud: Hud, config: GameConfig, player: Int) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val narrow = player > 0
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        // Hoehenmarken: gestrichelte Linie mit Beschriftung rechts
        Canvas(Modifier.fillMaxSize()) {
            for ((_, y) in hud.markers) {
                drawLine(
                    Color.White.copy(alpha = 0.75f), Offset(size.width * 0.08f, y * size.height), Offset(size.width * 0.8f, y * size.height),
                    strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f)),
                )
            }
        }
        // Namensschild wie im Original: weisse Plakette rechts an der Linie
        for ((label, y) in hud.markers) {
            Text(
                label, color = Palette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = h * y - 13.dp)
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White)
                    .border(2.dp, Palette.Ink, RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Oben: Kombo-Leiste, Zeit, Spieler
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = top + (if (narrow) 62.dp else 10.dp), start = 12.dp, end = if (narrow) 12.dp else 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (player > 0) {
                Text(
                    tr(R.string.player, player), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 4f)),
                )
            }
            ComboBar(hud.comboTimer / 100f, hud.combo)
            if (config.timeLimit > 0f) {
                Spacer(Modifier.height(6.dp))
                val warn = hud.timeLeft <= 10
                OutlinedTitle(
                    "${hud.timeLeft / 60}:${(hud.timeLeft % 60).toString().padStart(2, '0')}", 30.sp,
                    fill = Brush.verticalGradient(if (warn) listOf(Color(0xFFFFB0A0), Color(0xFFE84A4A)) else listOf(Color.White, Color(0xFFCDE6FF))),
                    outlineWidth = 5.dp,
                )
            }
        }

        // Links unten: Turm-Symbol mit Hoehe und Leben
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = bottom + 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MiniTower(hud.kind, hud.floors, hud.target)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(3.dp, Palette.Ink, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    val label = if (hud.target > 0) "${hud.floors}/${hud.target}" else "${hud.floors}"
                    Text(label, color = Palette.Ink, fontSize = if (narrow) 18.sp else 22.sp, fontWeight = FontWeight.Black)
                }
            }
            if (hud.maxLives > 0) {
                Spacer(Modifier.width(6.dp))
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(2.dp, Palette.Ink, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (k in hud.maxLives - 1 downTo 0) Heart(k < hud.lives)
                }
            }
        }

        // Rechts unten: Einwohner
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottom + 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                tr(R.string.hud_residents), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 4f)),
            )
            OutlinedTitle(
                "${hud.population}", if (narrow) 24.sp else 32.sp,
                fill = Brush.verticalGradient(listOf(Color.White, Color(0xFFD7F0FF))), outlineWidth = 5.dp,
            )
        }

        GainPopup(hud, Modifier.align(Alignment.TopStart).padding(top = top + if (narrow) 110.dp else 64.dp, start = 14.dp))
        PerfectBanner(hud, Modifier.align(Alignment.Center).offset(y = -h * 0.12f))

        if (hud.hint) {
            Text(
                tr(R.string.tap_to_drop), color = Color.White, fontSize = if (narrow) 13.sp else 16.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f)),
                modifier = Modifier.align(Alignment.Center).offset(y = h * 0.18f).padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun ComboBar(fill: Float, combo: Int) {
    Box(contentAlignment = Alignment.CenterStart) {
        ComboTrack(fill, combo, Modifier.padding(start = 22.dp))
        StarIcon(true, Modifier.size(40.dp))
    }
}

@Composable
private fun ComboTrack(fill: Float, combo: Int, modifier: Modifier) {
    Box(
        modifier
            .widthIn(min = 120.dp, max = 220.dp)
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .border(3.dp, Palette.Ink, RoundedCornerShape(11.dp))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFB6F27A), Color(0xFF4CC46A)))),
        )
        if (combo >= 2) {
            Text(
                "×$combo", color = Palette.Ink, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun Heart(full: Boolean) {
    Canvas(Modifier.size(22.dp, 20.dp)) {
        val w = size.width
        val hh = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, hh * 0.95f)
            cubicTo(-w * 0.15f, hh * 0.45f, w * 0.15f, -hh * 0.15f, w / 2, hh * 0.28f)
            cubicTo(w * 0.85f, -hh * 0.15f, w * 1.15f, hh * 0.45f, w / 2, hh * 0.95f)
            close()
        }
        if (full) {
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF8A8A), Color(0xFFE02F45))))
            drawCircle(Color.White.copy(alpha = 0.7f), w * 0.09f, Offset(w * 0.3f, hh * 0.32f))
        } else {
            drawPath(path, Color(0xFFCAD2DE))
        }
        drawPath(path, Palette.Ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
    }
}

@Composable
private fun MiniTower(kind: Kind, floors: Int, target: Int) {
    val color = kindColor(kind)
    Canvas(Modifier.size(34.dp, 70.dp)) {
        val w = size.width
        val hh = size.height
        val steps = 8
        val fillTo = if (target > 0) floors.toFloat() / target else ((floors % 10) / 10f + if (floors > 0 && floors % 10 == 0) 1f else 0f)
        val bh = hh / (steps + 1)
        for (k in 0 until steps) {
            val y = hh - (k + 1) * bh
            val lit = (k + 1f) / steps <= fillTo + 0.001f
            drawRect(if (lit) color else Color.White.copy(alpha = 0.7f), Offset(w * 0.15f, y), Size(w * 0.7f, bh - 1f))
            drawRect(Palette.Ink.copy(alpha = 0.5f), Offset(w * 0.3f, y + bh * 0.25f), Size(w * 0.12f, bh * 0.45f))
            drawRect(Palette.Ink.copy(alpha = 0.5f), Offset(w * 0.58f, y + bh * 0.25f), Size(w * 0.12f, bh * 0.45f))
        }
        val roof = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.1f, bh); lineTo(w * 0.5f, 0f); lineTo(w * 0.9f, bh); close()
        }
        drawPath(roof, Color(0xFFD4553A))
        drawRect(Palette.Ink, Offset(w * 0.15f, bh), Size(w * 0.7f, hh - bh), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
    }
}

fun kindColor(kind: Kind) = when (kind) {
    Kind.BLUE -> Color(0xFF5AB4EF)
    Kind.RED -> Color(0xFFE6693F)
    Kind.GREEN -> Color(0xFF55C963)
    Kind.YELLOW -> Color(0xFFF5CC55)
}

@Composable
private fun GainPopup(hud: Hud, modifier: Modifier) {
    val anim = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(hud.gainSerial) {
        if (hud.gainSerial == 0) return@LaunchedEffect
        shown = hud.lastGain
        anim.snapTo(1f)
        anim.animateTo(0f, tween(1400))
    }
    if (anim.value > 0f) {
        Box(modifier.offset(y = (-24).dp * (1f - anim.value))) {
            OutlinedTitle(
                "+$shown", 26.sp,
                fill = Brush.verticalGradient(listOf(Color(0xFFDFFFD0), Color(0xFF6BDB5A))),
                outlineWidth = 5.dp,
                modifier = Modifier.scale(0.8f + 0.2f * anim.value),
            )
        }
    }
}

@Composable
private fun PerfectBanner(hud: Hud, modifier: Modifier) {
    val anim = remember { Animatable(0f) }
    var text by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(true) }
    LaunchedEffect(hud.gainSerial) {
        if (hud.gainSerial == 0 || !hud.perfect) return@LaunchedEffect
        text = if (hud.combo >= 2) tr(R.string.perfect) + "\n" + tr(R.string.combo, hud.combo) else tr(R.string.perfect)
        good = true
        anim.snapTo(0.01f)
        anim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        kotlinx.coroutines.delay(500)
        anim.animateTo(0f, tween(250))
    }
    LaunchedEffect(hud.missSerial) {
        if (hud.missSerial == 0) return@LaunchedEffect
        text = tr(R.string.miss)
        good = false
        anim.snapTo(0.01f)
        anim.animateTo(1f, spring(dampingRatio = 0.5f))
        kotlinx.coroutines.delay(450)
        anim.animateTo(0f, tween(250))
    }
    if (anim.value > 0f) {
        OutlinedTitle(
            text, 40.sp, modifier = modifier.scale(anim.value),
            fill = if (good) Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC531), Color(0xFFFF8F1C)))
            else Brush.verticalGradient(listOf(Color(0xFFFFC2B8), Color(0xFFE84A4A))),
        )
    }
}

/** Hoehenvergleich in der Mitte des geteilten Bildschirms. */
@Composable
private fun PartyGauge(floors: List<Int>, modifier: Modifier) {
    val max = ((floors.maxOrNull() ?: 0) + 6).coerceAtLeast(20)
    val colors = listOf(Color(0xFF3D8BFF), Color(0xFFE8643C))
    BoxWithConstraints(modifier.fillMaxHeight(0.62f).width(56.dp)) {
        val barH = maxHeight
        Box(
            Modifier
                .align(Alignment.Center)
                .width(14.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF5D7A), Color(0xFFFFC531), Color(0xFF4CC46A), Color(0xFF3D8BFF))))
                .border(3.dp, Palette.Ink, RoundedCornerShape(7.dp)),
        )
        floors.forEachIndexed { i, f ->
            val frac = (f.toFloat() / max).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = if (i == 0) (-14).dp else 14.dp, y = -(barH - 26.dp) * frac)
                    .size(30.dp, 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors[i % 2])
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$f", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

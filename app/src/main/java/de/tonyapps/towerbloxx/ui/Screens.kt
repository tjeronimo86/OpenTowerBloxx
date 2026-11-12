package de.tonyapps.towerbloxx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.audio.Sfx
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.ThemeMode
import de.tonyapps.towerbloxx.data.tr
import de.tonyapps.towerbloxx.game.Kind
import de.tonyapps.towerbloxx.game.Modes
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

// ------------------------------------------------------------ Grundgeruest

@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SkyBackground {
        Column(Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = bottom + 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(onBack) { d -> arrowBack(d) }
                OutlinedTitle(title, 30.sp, Modifier.weight(1f).padding(end = 48.dp))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun RoundIcon(onClick: () -> Unit, draw: (DrawScope) -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F7))))
            .border(3.dp, Palette.Ink, CircleShape)
            .clickable { Audio.play(Sfx.TAP); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { draw(this) }
    }
}

private fun arrowBack(d: DrawScope) {
    val w = d.size.width
    d.drawLine(Palette.Ink, Offset(w * 0.8f, w / 2), Offset(w * 0.2f, w / 2), 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    d.drawLine(Palette.Ink, Offset(w * 0.2f, w / 2), Offset(w * 0.5f, w * 0.2f), 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    d.drawLine(Palette.Ink, Offset(w * 0.2f, w / 2), Offset(w * 0.5f, w * 0.8f), 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

private fun gear(d: DrawScope) {
    val c = d.center
    val r = d.size.width * 0.32f
    for (k in 0 until 8) {
        val a = k * Math.PI / 4
        d.drawLine(Palette.Ink, c, Offset(c.x + (r * 1.45f * kotlin.math.cos(a)).toFloat(), c.y + (r * 1.45f * kotlin.math.sin(a)).toFloat()), 7f)
    }
    d.drawCircle(Palette.Ink, r, c)
    d.drawCircle(Color.White, r * 0.45f, c)
}

private fun trophy(d: DrawScope) {
    val w = d.size.width
    d.drawRect(Palette.Yellow, Offset(w * 0.25f, w * 0.1f), Size(w * 0.5f, w * 0.45f))
    d.drawRect(Palette.Ink, Offset(w * 0.25f, w * 0.1f), Size(w * 0.5f, w * 0.45f), style = Stroke(3f))
    d.drawRect(Palette.Ink, Offset(w * 0.44f, w * 0.55f), Size(w * 0.12f, w * 0.2f))
    d.drawRect(Palette.Ink, Offset(w * 0.25f, w * 0.75f), Size(w * 0.5f, w * 0.12f))
}

private fun spark(d: DrawScope) {
    val w = d.size.width
    val p = Path().apply {
        moveTo(w / 2, 0f); lineTo(w * 0.62f, w * 0.38f); lineTo(w, w / 2); lineTo(w * 0.62f, w * 0.62f)
        lineTo(w / 2, w); lineTo(w * 0.38f, w * 0.62f); lineTo(0f, w / 2); lineTo(w * 0.38f, w * 0.38f); close()
    }
    d.drawPath(p, Palette.Yellow)
    d.drawPath(p, Palette.Ink, style = Stroke(3f))
}

// ------------------------------------------------------------ Hauptmenue

@Composable
fun MenuScreen(
    onQuick: () -> Unit, onTime: () -> Unit, onChallenge: () -> Unit, onCity: () -> Unit, onParty: () -> Unit,
    onHall: () -> Unit, onSettings: () -> Unit, onNews: () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bob by rememberInfiniteTransition(label = "logo").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    SkyBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top + 8.dp, bottom = bottom + 16.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundIcon(onNews) { spark(it) }
                Row {
                    RoundIcon(onHall) { trophy(it) }
                    Spacer(Modifier.width(10.dp))
                    RoundIcon(onSettings) { gear(it) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.graphicsLayer { translationY = bob * 10f; rotationZ = bob * 1.5f }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoTower(Modifier.size(110.dp, 120.dp))
                    OutlinedTitle(
                        "OPEN TOWER", 38.sp,
                        fill = Brush.verticalGradient(listOf(Color(0xFFE6FF9C), Color(0xFF9BDD3A), Color(0xFF4CAF2A))),
                        outline = Color(0xFF1A3A7A), outlineWidth = 8.dp,
                    )
                    OutlinedTitle(
                        "BLOXX", 56.sp,
                        fill = Brush.verticalGradient(listOf(Color(0xFFE6FF9C), Color(0xFF9BDD3A), Color(0xFF4CAF2A))),
                        outline = Color(0xFF1A3A7A), outlineWidth = 9.dp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ChunkyButton(tr(R.string.mode_quick), Palette.Green, subtitle = tr(R.string.mode_quick_desc), onClick = onQuick)
            ChunkyButton(tr(R.string.mode_time), Palette.Blue, subtitle = tr(R.string.mode_time_desc), onClick = onTime)
            ChunkyButton(tr(R.string.mode_challenge), Palette.Red, subtitle = tr(R.string.mode_challenge_desc), onClick = onChallenge)
            ChunkyButton(tr(R.string.mode_city), Color(0xFFF2A922), subtitle = tr(R.string.mode_city_desc), onClick = onCity)
            ChunkyButton(tr(R.string.mode_party), Palette.Purple, subtitle = tr(R.string.mode_party_desc), onClick = onParty)
        }
    }
}

/** Kleiner Turm am Kran fuers Logo. */
@Composable
fun LogoTower(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(Palette.Ink, Offset(w / 2, 0f), Offset(w / 2, h * 0.12f), 5f)
        drawRect(Palette.Yellow, Offset(w * 0.42f, h * 0.1f), Size(w * 0.16f, h * 0.05f))
        val colors = listOf(kindColor(Kind.RED), kindColor(Kind.BLUE), kindColor(Kind.GREEN))
        val bh = h * 0.24f
        for ((i, c) in colors.withIndex()) {
            val y = h * 0.16f + i * (bh + if (i == 0) h * 0.06f else 0f)
            val shift = if (i == 0) w * 0.04f else 0f
            block(this, Offset(w * 0.18f + shift, y), Size(w * 0.56f, bh), c)
        }
    }
}

private fun block(d: DrawScope, o: Offset, s: Size, c: Color) {
    val depth = s.width * 0.22f
    // Seite
    val side = Path().apply {
        moveTo(o.x + s.width, o.y); lineTo(o.x + s.width + depth, o.y - depth * 0.5f)
        lineTo(o.x + s.width + depth, o.y + s.height - depth * 0.5f); lineTo(o.x + s.width, o.y + s.height); close()
    }
    d.drawPath(side, c.darken(0.3f))
    val topP = Path().apply {
        moveTo(o.x, o.y); lineTo(o.x + depth, o.y - depth * 0.5f); lineTo(o.x + s.width + depth, o.y - depth * 0.5f); lineTo(o.x + s.width, o.y); close()
    }
    d.drawPath(topP, c.lighten(0.45f))
    d.drawRect(Brush.verticalGradient(listOf(c.lighten(0.15f), c), o.y, o.y + s.height), o, s)
    val ww = s.width * 0.26f
    for (k in 0..1) {
        val wx = o.x + s.width * (0.14f + k * 0.46f)
        d.drawRoundRect(Color(0xFF1F3B63), Offset(wx, o.y + s.height * 0.2f), Size(ww, s.height * 0.6f), androidx.compose.ui.geometry.CornerRadius(4f))
        d.drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD9F7FF), Color(0xFF3B93D6))), Offset(wx + 3f, o.y + s.height * 0.2f + 3f), Size(ww - 6f, s.height * 0.6f - 6f), androidx.compose.ui.geometry.CornerRadius(3f))
    }
    d.drawRect(Palette.Ink, o, s, style = Stroke(4f))
    d.drawPath(side, Palette.Ink, style = Stroke(4f))
    d.drawPath(topP, Palette.Ink, style = Stroke(4f))
}

// ------------------------------------------------------------ Herausforderung

@Composable
fun ChallengeScreen(onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val stars = remember { Store.stars() }
    Page(tr(R.string.mode_challenge), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            for (row in 0 until (Store.LEVELS + 2) / 3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0 until 3) {
                        val lvl = row * 3 + col
                        if (lvl >= Store.LEVELS) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val open = Store.unlocked(lvl)
                            LevelTile(lvl, stars[lvl], open, Modifier.weight(1f)) { if (open) onPlay(lvl) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LevelTile(level: Int, stars: Int, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (open) kindColor(Kind.entries[level % 4]) else Color(0xFF9AA3B2)
    Column(
        modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(color.lighten(0.3f), color.darken(0.1f))))
            .border(3.dp, Palette.Ink, RoundedCornerShape(20.dp))
            .clickable(enabled = open) { Audio.play(Sfx.TAP); onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OutlinedTitle("${level + 1}", 34.sp, fill = Brush.verticalGradient(listOf(Color.White, Color(0xFFE5F1FF))), outlineWidth = 6.dp)
        Text(
            if (open) tr(R.string.level_goal, Modes.challengeTarget(level)) else tr(R.string.locked),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row {
            for (k in 0 until 3) StarIcon(k < stars, Modifier.size(20.dp))
        }
    }
}

@Composable
fun StarIcon(full: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val p = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) w * 0.48f else w * 0.21f
            val a = -Math.PI / 2 + k * Math.PI / 5
            val x = w / 2 + (r * kotlin.math.cos(a)).toFloat()
            val y = w * 0.53f + (r * kotlin.math.sin(a)).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, if (full) Brush.verticalGradient(listOf(Color(0xFFFFF27A), Color(0xFFFFB000))) else Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x44FFFFFF))))
        drawPath(p, Palette.Ink, style = Stroke(w * 0.07f))
    }
}

// ------------------------------------------------------------ Ruhmeshalle

@Composable
fun HallScreen(onBack: () -> Unit) {
    val quick = remember { Store.quickScores() }
    val time = remember { Store.timeScores() }
    val city = remember { Store.city() }
    val stars = remember { Store.stars().sum() }
    val nf = NumberFormat.getIntegerInstance()
    Page(tr(R.string.hall_of_fame), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card {
                Text(tr(R.string.hall_quick), fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor())
                Spacer(Modifier.height(8.dp))
                if (quick.isEmpty()) {
                    Text(tr(R.string.hall_empty), color = subColor())
                } else {
                    TowerChart(quick.take(5).map { it.floors }, quick.take(5).map { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.date)) }, quick.take(5).map { nf.format(it.population) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Card {
                Text(tr(R.string.hall_time), fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor())
                Spacer(Modifier.height(8.dp))
                if (time.isEmpty()) {
                    Text(tr(R.string.hall_empty), color = subColor())
                } else {
                    TowerChart(time.take(5).map { it.floors }, time.take(5).map { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.date)) }, time.take(5).map { nf.format(it.population) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Card {
                StatLine(tr(R.string.hall_city), tr(R.string.city_population, nf.format(city.filterNotNull().sumOf { it.population })))
                StatLine(tr(R.string.hall_stars), "$stars / ${Store.LEVELS * 3}")
            }
        }
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(panelColor())
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, color = subColor(), fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 18.sp, color = textColor(), fontWeight = FontWeight.Black)
    }
}

/** Saeulen als kleine Hochhaeuser, wie die Bestenliste im Original. */
@Composable
private fun TowerChart(floors: List<Int>, labels: List<String>, sub: List<String>) {
    val max = (floors.maxOrNull() ?: 1).coerceAtLeast(1)
    val colors = listOf(kindColor(Kind.BLUE), kindColor(Kind.RED), kindColor(Kind.GREEN), kindColor(Kind.YELLOW), Palette.Purple)
    val ink = textColor()
    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        floors.forEachIndexed { i, f ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("$f", fontWeight = FontWeight.Black, color = ink, fontSize = 16.sp)
                val frac = f.toFloat() / max
                Canvas(Modifier.width(26.dp).height(150.dp * frac.coerceAtLeast(0.04f))) {
                    val c = colors[i % colors.size]
                    drawRect(Brush.horizontalGradient(listOf(c.lighten(0.2f), c.darken(0.2f))))
                    var y = 6f
                    while (y < size.height - 6f) {
                        drawRect(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.2f, y), Size(size.width * 0.22f, 7f))
                        drawRect(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.58f, y), Size(size.width * 0.22f, 7f))
                        y += 14f
                    }
                    drawRect(Palette.Ink, style = Stroke(3f))
                }
                Spacer(Modifier.height(4.dp))
                Text(sub[i], fontSize = 11.sp, color = ink, fontWeight = FontWeight.Bold)
                Text(labels[i], fontSize = 10.sp, color = subColor())
            }
        }
    }
}

// ------------------------------------------------------------ Einstellungen

@Composable
fun SettingsScreen(onBack: () -> Unit, onNews: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Page(tr(R.string.settings), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card {
                Text(tr(R.string.s_game), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subColor())
                ToggleRow(tr(R.string.s_sound), Store.sound) { Store.changeSound(it); Audio.soundOn = it }
                ToggleRow(tr(R.string.s_music), Store.music) { Store.changeMusic(it); Audio.musicOn = it }
                ToggleRow(tr(R.string.s_vibration), Store.vibration) { Store.changeVibration(it); Audio.vibrationOn = it; if (it) Audio.buzz(30) }
                Spacer(Modifier.height(8.dp))
                Text(tr(R.string.s_theme), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor())
                Spacer(Modifier.height(6.dp))
                Segmented(
                    listOf(tr(R.string.theme_system), tr(R.string.theme_light), tr(R.string.theme_dark)),
                    Store.theme.ordinal,
                ) { Store.changeTheme(ThemeMode.entries[it]) }
            }
            Spacer(Modifier.height(12.dp))
            Card {
                Text(tr(R.string.s_app), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subColor())
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onNews() }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr(R.string.whats_new), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor(), modifier = Modifier.weight(1f))
                    Text("›", fontSize = 24.sp, color = subColor())
                }
            }
            Spacer(Modifier.height(12.dp))
            VersionRow()
            Spacer(Modifier.height(12.dp))
            if (!confirm) {
                ChunkyButton(tr(R.string.s_reset_scores), Palette.Red) { confirm = true }
            } else {
                Card {
                    Text(tr(R.string.s_reset_confirm), color = textColor(), fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        ChunkyButton(tr(R.string.cancel), Color(0xFF8A94A6), Modifier.weight(1f)) { confirm = false }
                        Spacer(Modifier.width(10.dp))
                        ChunkyButton(tr(R.string.confirm), Palette.Red, Modifier.weight(1f)) { Store.clearScores(); confirm = false }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(tr(R.string.s_about), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor(), modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Green))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Ink.copy(alpha = 0.12f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Palette.Blue else Color.Transparent)
                    .clickable { Audio.play(Sfx.TAP); onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s, color = if (on) Color.White else textColor(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

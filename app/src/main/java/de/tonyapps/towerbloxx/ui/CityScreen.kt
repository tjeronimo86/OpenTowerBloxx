package de.tonyapps.towerbloxx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.audio.Sfx
import de.tonyapps.towerbloxx.data.Plot
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.tr
import de.tonyapps.towerbloxx.game.Kind
import de.tonyapps.towerbloxx.game.Modes
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val N = Store.CITY

fun neighbours(plots: Array<Plot?>, index: Int): Set<Kind> {
    val r = index / N
    val c = index % N
    return listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
        .filter { (a, b) -> a in 0 until N && b in 0 until N }
        .mapNotNull { (a, b) -> plots[a * N + b]?.kind }
        .toSet()
}

fun kindName(k: Kind) = tr(
    when (k) {
        Kind.BLUE -> R.string.kind_blue
        Kind.RED -> R.string.kind_red
        Kind.GREEN -> R.string.kind_green
        Kind.YELLOW -> R.string.kind_yellow
    },
)

private fun kindNeed(k: Kind) = when (k) {
    Kind.BLUE -> ""
    Kind.RED -> tr(R.string.need_red)
    Kind.GREEN -> tr(R.string.need_green)
    Kind.YELLOW -> tr(R.string.need_yellow)
}

@Composable
fun CityScreen(onBack: () -> Unit, onBuild: (Kind, Int) -> Unit) {
    var plots by remember { mutableStateOf(Store.city()) }
    var selected by remember { mutableIntStateOf(-1) }
    var choice by remember { mutableStateOf(Kind.BLUE) }
    var confirmReset by remember { mutableStateOf(false) }
    val nf = NumberFormat.getIntegerInstance()
    val night = LocalNight.current

    Page(tr(R.string.city_title), onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTitle(
                tr(R.string.city_population, nf.format(plots.filterNotNull().sumOf { it.population })), 22.sp,
                Modifier.fillMaxWidth(),
                fill = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFD7F0FF))),
                outlineWidth = 5.dp,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (night) Color(0x66000000) else Color(0x33FFFFFF)),
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .pointerInput(plots) {
                            detectTapGestures { p ->
                                val geo = Geo(size.width.toFloat(), size.height.toFloat())
                                val idx = geo.hit(p)
                                if (idx >= 0) {
                                    Audio.play(Sfx.TAP)
                                    selected = idx
                                    val nb = neighbours(plots, idx)
                                    choice = Kind.entries.lastOrNull { Modes.allowed(it, nb) } ?: Kind.BLUE
                                    plots[idx]?.let { if (Modes.allowed(it.kind, nb)) choice = it.kind }
                                }
                            }
                        },
                ) {
                    val geo = Geo(size.width, size.height)
                    val order = (0 until N * N).sortedBy { (it / N) + (it % N) }
                    for (i in order) {
                        val center = geo.center(i)
                        tile(this, center, geo.tw, geo.th, i == selected, night)
                        plots[i]?.let { building(this, center, geo.tw, geo.th, it) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (selected < 0) {
                Card { Text(tr(R.string.city_rules), color = textColor(), fontSize = 15.sp) }
            } else {
                val plot = plots[selected]
                val nb = neighbours(plots, selected)
                Card {
                    Text(
                        plot?.let { "${kindName(it.kind)} · ${it.floors} · ${tr(R.string.city_population, nf.format(it.population))}" } ?: tr(R.string.plot_empty),
                        color = textColor(), fontWeight = FontWeight.Black, fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(tr(R.string.choose_building), color = subColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    for (k in Kind.entries) {
                        val ok = Modes.allowed(k, nb)
                        KindRow(k, ok, k == choice) { if (ok) choice = k }
                    }
                    if (plot != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(tr(R.string.city_better), color = subColor(), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    ChunkyButton(
                        if (plot == null) tr(R.string.build) else tr(R.string.rebuild), Palette.Green,
                        enabled = Modes.allowed(choice, nb),
                    ) { onBuild(choice, selected) }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!confirmReset) {
                ChunkyButton(tr(R.string.reset_city), Color(0xFF8A94A6)) { confirmReset = true }
            } else {
                Card {
                    Text(tr(R.string.reset_city_confirm), color = textColor())
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ChunkyButton(tr(R.string.cancel), Color(0xFF8A94A6), Modifier.weight(1f)) { confirmReset = false }
                        Spacer(Modifier.width(10.dp))
                        ChunkyButton(tr(R.string.confirm), Palette.Red, Modifier.weight(1f)) {
                            Store.resetCity()
                            plots = Store.city()
                            selected = -1
                            confirmReset = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindRow(k: Kind, ok: Boolean, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (chosen) kindColor(k).copy(alpha = 0.3f) else Color.Transparent)
            .border(2.dp, if (chosen) kindColor(k) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(enabled = ok) { Audio.play(Sfx.TAP); onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (ok) kindColor(k) else Color(0xFFB0B7C3)).border(2.dp, Palette.Ink, RoundedCornerShape(7.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(kindName(k), color = if (ok) textColor() else subColor(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                if (ok) tr(R.string.floors_cap, Modes.cityCap(k)) else kindNeed(k),
                color = subColor(), fontSize = 12.sp,
            )
        }
    }
}

/** Isometrisches Raster. */
private class Geo(w: Float, h: Float) {
    val tw = minOf(w / (N + 0.6f), h / (N * 0.5f + 3.2f) * 2f)
    val th = tw / 2f
    private val ox = w / 2f
    private val oy = h - (N * th) - th * 0.3f

    fun center(i: Int): Offset {
        val r = i / N
        val c = i % N
        return Offset(ox + (c - r) * tw / 2f, oy + (c + r) * th / 2f + th / 2f)
    }

    fun hit(p: Offset): Int {
        val a = (p.x - ox) / (tw / 2f)
        val b = (p.y - oy - th / 2f) / (th / 2f)
        val c = ((a + b) / 2f).roundToInt()
        val r = ((b - a) / 2f).roundToInt()
        return if (r in 0 until N && c in 0 until N) r * N + c else -1
    }
}

private fun diamond(center: Offset, tw: Float, th: Float) = Path().apply {
    moveTo(center.x, center.y - th / 2); lineTo(center.x + tw / 2, center.y); lineTo(center.x, center.y + th / 2); lineTo(center.x - tw / 2, center.y); close()
}

private fun tile(d: DrawScope, center: Offset, tw: Float, th: Float, selected: Boolean, night: Boolean) {
    val p = diamond(center, tw * 0.96f, th * 0.96f)
    d.drawPath(p, if (night) Color(0xFF2E5A3A) else Color(0xFF7ED35A))
    val inner = diamond(center, tw * 0.62f, th * 0.62f)
    d.drawPath(inner, if (night) Color(0xFF4A4A55) else Color(0xFFC9C2B2))
    d.drawPath(p, if (selected) Color(0xFFFFE14D) else Palette.Ink.copy(alpha = 0.35f), style = Stroke(if (selected) 6f else 2f))
}

private fun building(d: DrawScope, center: Offset, tw: Float, th: Float, plot: Plot) {
    val c = kindColor(plot.kind)
    val w = tw * 0.31f
    val hgt = (plot.floors * th * 0.09f).coerceAtLeast(th * 0.25f)
    val base = Offset(center.x, center.y + th * 0.155f)
    // Linke Seite, rechte Seite, Dach
    val left = Path().apply {
        moveTo(base.x - w, base.y - w / 2); lineTo(base.x, base.y); lineTo(base.x, base.y - hgt); lineTo(base.x - w, base.y - w / 2 - hgt); close()
    }
    val right = Path().apply {
        moveTo(base.x, base.y); lineTo(base.x + w, base.y - w / 2); lineTo(base.x + w, base.y - w / 2 - hgt); lineTo(base.x, base.y - hgt); close()
    }
    val top = Path().apply {
        moveTo(base.x, base.y - hgt); lineTo(base.x + w, base.y - w / 2 - hgt); lineTo(base.x, base.y - w - hgt); lineTo(base.x - w, base.y - w / 2 - hgt); close()
    }
    d.drawPath(left, c)
    d.drawPath(right, c.darken(0.28f))
    d.drawPath(top, c.lighten(0.4f))
    // Fensterreihen
    val rows = (hgt / (th * 0.09f)).toInt().coerceAtMost(60)
    for (k in 0 until rows step 1) {
        val y = base.y - th * 0.045f - k * th * 0.09f
        if (y < base.y - hgt + th * 0.03f) break
        d.drawLine(Color.White.copy(alpha = 0.7f), Offset(base.x - w * 0.8f, y - w * 0.4f + w * 0.1f), Offset(base.x - w * 0.2f, y - w * 0.1f + w * 0.1f), 2.5f)
        d.drawLine(Color.White.copy(alpha = 0.45f), Offset(base.x + w * 0.2f, y - w * 0.1f + w * 0.1f), Offset(base.x + w * 0.8f, y - w * 0.4f + w * 0.1f), 2.5f)
    }
    d.drawPath(left, Palette.Ink, style = Stroke(2.5f))
    d.drawPath(right, Palette.Ink, style = Stroke(2.5f))
    d.drawPath(top, Palette.Ink, style = Stroke(2.5f))
    // Dach, wenn das Gebaeude fertig ist
    if (plot.floors >= Modes.cityCap(plot.kind)) {
        val apex = Offset(base.x, base.y - hgt - w * 1.3f)
        val roofL = Path().apply { moveTo(base.x - w, base.y - w / 2 - hgt); lineTo(base.x, base.y - hgt); lineTo(apex.x, apex.y); close() }
        val roofR = Path().apply { moveTo(base.x, base.y - hgt); lineTo(base.x + w, base.y - w / 2 - hgt); lineTo(apex.x, apex.y); close() }
        d.drawPath(roofL, Color(0xFFD4553A))
        d.drawPath(roofR, Color(0xFF9C3A26))
        d.drawPath(roofL, Palette.Ink, style = Stroke(2.5f))
        d.drawPath(roofR, Palette.Ink, style = Stroke(2.5f))
    }
}

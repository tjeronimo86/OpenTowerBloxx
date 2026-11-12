package de.tonyapps.towerbloxx.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.audio.Sfx
import de.tonyapps.towerbloxx.data.Store
import de.tonyapps.towerbloxx.data.ThemeMode
import kotlin.math.sin
import kotlin.random.Random

/** Tag (blauer Himmel) oder Nacht (Lichter der Stadt); gilt fuer Menues und Spiel. */
val LocalNight = staticCompositionLocalOf { false }

object Palette {
    val Ink = Color(0xFF1C2A4A)
    val Blue = Color(0xFF3D8BFF)
    val Green = Color(0xFF4CC46A)
    val Red = Color(0xFFE8643C)
    val Yellow = Color(0xFFFFC531)
    val Purple = Color(0xFF8A5CFF)
    val Panel = Color(0xFFFFFFFF)
    val PanelNight = Color(0xFF1E2747)
}

@Composable
fun isNight(): Boolean = when (Store.theme) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun TowerTheme(content: @Composable () -> Unit) {
    val night = isNight()
    val scheme = if (night) darkColorScheme(primary = Palette.Blue) else lightColorScheme(primary = Palette.Blue)
    androidx.compose.runtime.CompositionLocalProvider(LocalNight provides night) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun panelColor() = if (LocalNight.current) Palette.PanelNight.copy(alpha = 0.92f) else Palette.Panel.copy(alpha = 0.92f)

@Composable
fun textColor() = if (LocalNight.current) Color.White else Palette.Ink

@Composable
fun subColor() = if (LocalNight.current) Color.White.copy(alpha = 0.65f) else Palette.Ink.copy(alpha = 0.6f)

/** Comic-Schrift: dicke Kontur mit Fuellverlauf. */
@Composable
fun OutlinedTitle(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    fill: Brush = Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC531), Color(0xFFFF9F1C))),
    outline: Color = Palette.Ink,
    outlineWidth: Dp = 6.dp,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val base = TextStyle(fontSize = size, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Text(
            text,
            style = base.copy(
                color = outline,
                drawStyle = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { outlineWidth.toPx() }, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 8f), 6f),
            ),
        )
        Text(text, style = base.copy(brush = fill))
    }
}

/** Dicker Knopf mit 3D-Kante, wie ein Spielzeugbaustein. */
@Composable
fun ChunkyButton(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, label = "press")
    val c = if (enabled) color else Color(0xFF9AA3B2)
    val lip = 6.dp
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().padding(bottom = lip).scale(1f - press * 0.02f)) {
        // Untere Kante
        Box(Modifier.matchParentSize().offset(y = lip).clip(shape).background(c.darken(0.35f)))
        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = lip * press)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(c.lighten(0.25f), c, c.darken(0.1f))))
                .border(3.dp, Color.White.copy(alpha = 0.35f), shape)
                .clickable(interactionSource = source, indication = null, enabled = enabled) {
                    Audio.play(Sfx.TAP)
                    onClick()
                }
                .padding(horizontal = 20.dp, vertical = if (subtitle != null) 10.dp else 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 2f)),
                )
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

fun Color.darken(t: Float) = Color(red * (1 - t), green * (1 - t), blue * (1 - t), alpha)
fun Color.lighten(t: Float) = Color(red + (1 - red) * t, green + (1 - green) * t, blue + (1 - blue) * t, alpha)

/** Himmel mit ziehenden Wolken und Skyline: Hintergrund aller Menues. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val night = LocalNight.current
    val t by rememberInfiniteTransition(label = "sky").animateFloat(
        0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "clouds",
    )
    val skyline = remember { Skyline.make() }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val top = if (night) Color(0xFF050818) else Color(0xFF2F7FD8)
            val bottom = if (night) Color(0xFF26295A) else Color(0xFFBFE6FF)
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            if (night) stars(this) else clouds(this, t)
            skyline.draw(this, night)
        }
        content()
    }
}

private fun stars(d: DrawScope) {
    val rnd = Random(3)
    repeat(120) {
        val x = rnd.nextFloat() * d.size.width
        val y = rnd.nextFloat() * d.size.height * 0.7f
        d.drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.7f), 1f + rnd.nextFloat() * 2.2f, Offset(x, y))
    }
    d.drawCircle(Color(0xFFFFF6D0), d.size.width * 0.07f, Offset(d.size.width * 0.8f, d.size.height * 0.12f))
    d.drawCircle(Color(0xFF050818), d.size.width * 0.06f, Offset(d.size.width * 0.775f, d.size.height * 0.11f))
}

private fun clouds(d: DrawScope, t: Float) {
    val w = d.size.width
    val h = d.size.height
    val rnd = Random(9)
    repeat(7) { i ->
        val speed = 0.5f + rnd.nextFloat()
        val baseX = rnd.nextFloat()
        val y = h * (0.06f + rnd.nextFloat() * 0.55f)
        val s = w * (0.12f + rnd.nextFloat() * 0.12f)
        var x = ((baseX + t * speed) % 1.3f) * (w + 2 * s * 2) - s * 2
        if (i % 2 == 1) x = w - x
        cloud(d, x, y, s)
    }
}

private fun cloud(d: DrawScope, x: Float, y: Float, s: Float) {
    val parts = listOf(Triple(-0.9f, 0.15f, 0.55f), Triple(-0.3f, -0.15f, 0.75f), Triple(0.4f, -0.05f, 0.65f), Triple(0.95f, 0.18f, 0.48f))
    for ((dx, dy, r) in parts) d.drawCircle(Color(0xFFC7DAF2), s * r, Offset(x + dx * s, y + dy * s + s * 0.1f))
    for ((dx, dy, r) in parts) {
        d.drawCircle(
            Brush.radialGradient(listOf(Color.White, Color(0xFFE6EFFA)), Offset(x + dx * s - s * r * 0.3f, y + dy * s - s * r * 0.4f), s * r * 1.3f),
            s * r, Offset(x + dx * s, y + dy * s),
        )
    }
}

/** Skyline am unteren Rand, einmal zufaellig erzeugt. */
class Skyline private constructor(private val rects: List<FloatArray>) {
    companion object {
        fun make(): Skyline {
            val rnd = Random(11)
            val list = ArrayList<FloatArray>()
            var x = -0.05f
            while (x < 1.05f) {
                val w = 0.06f + rnd.nextFloat() * 0.1f
                list += floatArrayOf(x, w, 0.08f + rnd.nextFloat() * rnd.nextFloat() * 0.3f, rnd.nextFloat(), 0f)
                x += w * (0.55f + rnd.nextFloat() * 0.4f)
            }
            x = -0.05f
            while (x < 1.05f) {
                val w = 0.07f + rnd.nextFloat() * 0.09f
                list += floatArrayOf(x, w, 0.05f + rnd.nextFloat() * 0.14f, rnd.nextFloat(), 1f)
                x += w * (0.7f + rnd.nextFloat() * 0.3f)
            }
            return Skyline(list)
        }
    }

    fun draw(d: DrawScope, night: Boolean) {
        val w = d.size.width
        val h = d.size.height
        for (r in rects) {
            val front = r[4] > 0f
            val bw = r[1] * w
            val bh = r[2] * h
            val left = r[0] * w
            val top = h - bh
            val body = when {
                night && front -> Color(0xFF1A2040)
                night -> Color(0xFF2A3160)
                front -> Color(0xFF8EB6E6)
                else -> Color(0xFFB4D2F2)
            }
            d.drawRect(body, Offset(left, top), Size(bw, bh))
            d.drawRect(Color.White.copy(alpha = if (night) 0.05f else 0.25f), Offset(left, top), Size(bw * 0.18f, bh))
            // Fenster
            val cols = (bw / 14f).toInt().coerceAtLeast(1)
            val rows = (bh / 18f).toInt()
            val rnd = Random((r[3] * 1000).toInt())
            for (row in 0 until rows) for (c in 0 until cols) {
                val lit = night && rnd.nextFloat() < 0.35f
                val col = when {
                    lit -> Color(0xFFFFD27A)
                    night -> Color(0xFF2E3B6E)
                    else -> Color.White.copy(alpha = 0.45f)
                }
                d.drawRect(col, Offset(left + 5f + c * 14f, top + 8f + row * 18f), Size(6f, 9f))
            }
        }
        d.drawRect(if (night) Color(0xFF14361F) else Color(0xFF6CC24A), Offset(0f, h - 10f), Size(w, 10f))
    }
}

@Suppress("unused")
private fun wobble(t: Float) = sin(t * 6.28f)

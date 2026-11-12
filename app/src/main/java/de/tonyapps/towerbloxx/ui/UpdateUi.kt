package de.tonyapps.towerbloxx.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.data.Changelog
import de.tonyapps.towerbloxx.data.tr
import de.tonyapps.towerbloxx.update.AppRelease
import de.tonyapps.towerbloxx.update.UpdateState
import de.tonyapps.towerbloxx.update.Updater

/** App-Version mit Update-Knopf, in den Einstellungen. */
@Composable
fun VersionRow() {
    val context = LocalContext.current
    val state = Updater.state
    val status = when (state) {
        UpdateState.Idle -> tr(R.string.u_tap_search)
        UpdateState.Checking -> tr(R.string.u_searching)
        UpdateState.UpToDate -> tr(R.string.u_current)
        is UpdateState.Available -> tr(R.string.u_available_named, state.release.title)
        is UpdateState.Failed -> state.message
    }
    val highlight = state is UpdateState.Available
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(panelColor())
            .clickable {
                when (state) {
                    is UpdateState.Available -> Updater.sheetHidden = false
                    else -> Updater.check(context)
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Changelog.versionLabel(context), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor())
            Text(
                status, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (highlight) Palette.Blue else subColor(),
            )
        }
        Spacer(Modifier.width(10.dp))
        val spin by rememberInfiniteTransition(label = "update").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin",
        )
        val busy = state is UpdateState.Checking
        val col = textColor()
        Canvas(Modifier.size(24.dp).graphicsLayer { rotationZ = if (busy) spin else 0f }) {
            val r = size.minDimension * 0.38f
            drawArc(col, -60f, 300f, false, topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(width = 5f))
            val tip = Offset(center.x + r * 0.5f, center.y - r * 0.87f)
            drawLine(col, tip, Offset(tip.x - 9f, tip.y - 6f), strokeWidth = 5f)
            drawLine(col, tip, Offset(tip.x - 3f, tip.y + 10f), strokeWidth = 5f)
        }
    }
}

/** Hinweis auf ein Update; liegt ueber allem anderen. */
@Composable
fun UpdateSheet() {
    val state = Updater.state
    val release: AppRelease? = (state as? UpdateState.Available)?.release
    val shown = release != null && !Updater.sheetHidden
    var last by remember { mutableStateOf(release) }
    if (release != null) last = release

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .5f))
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .9f, stiffness = 320f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(),
        ) {
            last?.let { SheetContent(it) }
        }
    }
}

@Composable
private fun SheetContent(release: AppRelease) {
    val context = LocalContext.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(if (LocalNight.current) Palette.PanelNight else Color.White)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottom + 16.dp),
    ) {
        Text(tr(R.string.u_available), fontSize = 24.sp, fontWeight = FontWeight.Black, color = textColor())
        Text(release.title, fontSize = 14.sp, color = subColor())
        Spacer(Modifier.height(10.dp))
        release.notes.take(6).forEach { note ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", fontSize = 14.sp, color = subColor())
                Spacer(Modifier.width(8.dp))
                Text(note, fontSize = 14.sp, lineHeight = 19.sp, color = textColor())
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            ChunkyButton(tr(R.string.u_later), Color(0xFF8A94A6), Modifier.weight(1f)) { Updater.sheetHidden = true }
            Spacer(Modifier.width(10.dp))
            ChunkyButton(tr(R.string.u_download), Palette.Green, Modifier.weight(1f)) { Updater.download(context, release) }
        }
    }
}

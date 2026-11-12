package de.tonyapps.towerbloxx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.towerbloxx.R
import de.tonyapps.towerbloxx.data.Changelog
import de.tonyapps.towerbloxx.data.tr

/** Der Update-Verlauf. Erscheint nach jedem Update einmal und liegt in den Einstellungen. */
@Composable
fun WhatsNewScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val releases = remember { Changelog.read(context) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SkyBackground(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().padding(top = top + 12.dp, start = 16.dp, end = 16.dp)) {
            OutlinedTitle(tr(R.string.whats_new), 36.sp, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(releases, key = { it.title }) { release ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(panelColor())
                            .padding(16.dp),
                    ) {
                        Text(release.title, fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor())
                        Spacer(Modifier.height(6.dp))
                        release.items.forEach { item ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("•", fontSize = 14.sp, color = subColor())
                                Spacer(Modifier.width(8.dp))
                                Text(item, fontSize = 14.sp, lineHeight = 19.sp, color = textColor())
                            }
                        }
                    }
                }
            }
            ChunkyButton(tr(R.string.done), Palette.Green, Modifier.padding(bottom = bottom + 8.dp), onClick = onClose)
        }
    }
}

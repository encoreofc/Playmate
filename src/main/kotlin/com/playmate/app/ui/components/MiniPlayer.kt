package com.playmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.playmate.app.data.Load
import com.playmate.app.data.Playing
import com.playmate.app.ui.player.PlayerHolder
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.playmate.app.ui.theme.Tonal
import androidx.compose.foundation.shape.RoundedCornerShape

/** The bar shown above the tab bar while a video plays in the background of the app. */
@Composable
fun MiniPlayer(playing: Playing, onExpand: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.get(context) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            val total = player.duration
            progress = if (total > 0) (player.currentPosition.toFloat() / total).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    val glide by animateFloatAsState(progress, tween(500, easing = LinearEasing), label = "miniProgress")

    val info = (playing.info as? Load.Ready)?.data
    val title = info?.title ?: playing.seed.title
    val channel = info?.channel ?: playing.seed.channel
    val thumb = info?.thumb ?: playing.seed.thumb

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onExpand)
    ) {
        LinearProgressIndicator(
            progress = { glide },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Tonal.hairline()
        )
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tonal.fillStrong())
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    title.ifBlank { "Loading…" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (channel.isNotBlank()) {
                    Text(
                        channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }
    }
}

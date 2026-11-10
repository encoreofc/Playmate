package com.playmate.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import com.playmate.app.ui.theme.LocalPmDark
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.components.ShortsShelf
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.HomeTopBar
import com.playmate.app.ui.components.SignInPill
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private sealed interface Entry {
    val key: String
}

private class LongEntry(val video: VideoItem) : Entry {
    override val key get() = video.url
}

private class ShelfEntry(override val key: String, val shorts: List<VideoItem>) : Entry

private const val SHELF_SIZE = 10

/**
 * Splits the feed the way YouTube does: normal videos as cards, and the short videos
 * together in "Shorts" shelves between them (the first after the third video, then one
 * after every eight more).
 */
private fun buildEntries(items: List<VideoItem>, hasMore: Boolean): List<Entry> {
    val longs = items.filter { !it.isShort }
    val shorts = items.filter { it.isShort }
    val out = ArrayList<Entry>()
    var next = 0
    var shelf = 0
    fun addShelf() {
        if (next >= shorts.size) return
        val chunk = shorts.subList(next, minOf(next + SHELF_SIZE, shorts.size))
        out += ShelfEntry("shorts-$shelf", chunk)
        next += chunk.size
        shelf++
    }
    longs.forEachIndexed { i, video ->
        out += LongEntry(video)
        if (i == 2 || (i > 2 && (i - 2) % 8 == 0)) addShelf()
    }
    if (longs.size <= 2) addShelf()
    if (!hasMore) while (next < shorts.size) addShelf()
    return out
}

/**
 * One topic chip of the row under the bar: a round-ended pill 32dp high with 14sp text. Idle, it is
 * an outline (a thin line in a soft grey) with grey text. Selected, it fills with a light grey,
 * the text turns to the full text colour and a thin tick appears before it. The greys are the
 * text colour at a low strength, so the same chip works on the light and the dark theme.
 */
@Composable
private fun FeedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    val fill = ink.copy(alpha = 0.14f)
    val line = ink.copy(alpha = 0.20f)
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) fill else Color.Transparent)
            .border(1.dp, if (selected) fill else line, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(start = if (selected) 6.5.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            CheckMark(color = ink, box = 22.dp)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            label,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            color = if (selected) ink else ink.copy(alpha = 0.62f)
        )
    }
}

/** A thin round-ended tick on a 24 grid, [box] wide and high. */
@Composable
private fun CheckMark(color: Color, box: Dp) {
    Canvas(Modifier.size(box)) {
        val u = this.size.width / 24f
        val tick = Path().apply {
            moveTo(4.5f * u, 12.8f * u)
            lineTo(9.3f * u, 17.3f * u)
            lineTo(19.5f * u, 6.9f * u)
        }
        drawPath(
            tick,
            color,
            style = Stroke(width = 1.45f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** The stacked-videos glyph of the empty feed (a 24 grid, shown in a 46dp box). */
private val EmptyFeedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyFeedVideos",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12zM12 5.5v9l6-4.5-6-4.5z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}

/**
 * What the list shows when a feed came back with nothing in it: a soft scalloped badge with the
 * stacked-videos glyph, "Nothing here right now" and a quiet "Pull down to look again later."
 * line. It sits inside the pull-to-refresh list, so pulling down really does look again.
 */
@Composable
private fun EmptyFeed() {
    val ink = MaterialTheme.colorScheme.onBackground
    val badge = ink.copy(alpha = 0.14f)
    val quiet = ink.copy(alpha = 0.62f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(94.dp)) {
                val c = center
                val r = this.size.width / 2f
                val path = Path()
                val steps = 360
                for (i in 0..steps) {
                    val a = (2.0 * PI * i / steps).toFloat()
                    val rr = r * (0.955f + 0.045f * cos(12f * a))
                    val x = c.x + rr * cos(a)
                    val y = c.y + rr * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, badge)
            }
            Icon(
                imageVector = EmptyFeedIcon,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = quiet
            )
        }
        Spacer(Modifier.height(12.5.dp))
        Text(
            "Nothing here right now",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = quiet,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.5.dp))
        Text(
            "Pull down to look again later.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = quiet,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val hasMore = vm.feedToken != null
    // Chip colours measured from YouTube's own screenshots, one set per theme.
    val dark = LocalPmDark.current
    val chipIdle = if (dark) Color(0xFF3D3E40) else Color(0xFFEBECEE)
    val chipIdleText = if (dark) MaterialTheme.colorScheme.onBackground else Color(0xFF595959)
    val chipSelected = if (dark) MaterialTheme.colorScheme.onBackground else Color(0xFF606060)
    val chipSelectedText = if (dark) MaterialTheme.colorScheme.background else Color.White
    val entries = remember(vm.feed, hasMore) {
        (vm.feed as? Load.Ready)?.data?.let { buildEntries(it, hasMore) }.orEmpty()
    }
    // The logo bar and the chip row sit OUTSIDE the scrolling list, so they stay pinned at the top like YouTube.
    Column(Modifier.fillMaxSize()) {
        HomeTopBar(
            onNotifications = {
                vm.openNotifications()
            },
            onSearch = { vm.select(Tab.Search); vm.startEditing() }
        )

        // The chip row: round-ended topic chips (see [FeedChip]), 32dp tall, 8dp apart, 12dp from the
        // screen edges, 10dp under the bar and 12.5dp above the first video. They come with the
        // account's history, so with none there is no row at all, only the 8dp left between the bar
        // and the first video.
        if (vm.feedChips.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(vm.feedChips) { index, chip ->
                    FeedChip(
                        label = chip.label,
                        selected = index == vm.selectedChip,
                        onClick = { vm.selectChip(index) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.loadFeed(force = true) },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Nobody signed in: YouTube's rounded sign-in bar above the feed (12dp sides, 17dp before the first video).
            if (!vm.signedIn) {
                item(key = "sign-in-bar") {
                    SignInPill(
                        text = "Sign in to find more videos you like.",
                        onClick = vm::openLogin,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 17.dp)
                    )
                }
            }
            vm.feedNote?.let { note ->
                item {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            when (val feed = vm.feed) {
                is Load.Loading -> items(4) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(feed.message, onRetry = { vm.loadFeed(force = true) }) }
                is Load.Ready -> {
                    // A feed that came back empty (and has nothing more to load) says so, a little
                    // below the middle of the list, as in the reference.
                    if (entries.isEmpty() && vm.feedToken == null) {
                        item(key = "empty") {
                            Box(
                                Modifier.fillParentMaxSize().padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyFeed()
                            }
                        }
                    }
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is LongEntry -> {
                                LaunchedEffect(entry.video.url) { vm.onImpression(entry.video.url) }
                                VideoCard(entry.video, onClick = { vm.open(entry.video) })
                            }
                            is ShelfEntry -> ShortsShelf(
                                shorts = entry.shorts,
                                onOpen = { vm.openShorts(entry.shorts, entry.shorts.indexOf(it)) },
                                onShown = { vm.onImpression(it.url) }
                            )
                        }
                    }
                    if (vm.feedToken != null) {
                        item(key = "more") {
                            LaunchedEffect(vm.feedToken) { vm.loadMoreFeed() }
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

package com.playmate.app.ui.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.ChannelState
import com.playmate.app.data.ChannelInfo
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val KEY_BANNER = "banner"
private const val KEY_IDENTITY = "identity"
private const val KEY_ABOUT = "about"
private const val KEY_TABS = "tabs"
private const val SHORTS_COLUMNS = 3

/**
 * A channel's page, laid out like YouTube's: banner, identity, description, actions, tabs and the tab's content.
 * The Shorts tab is a 3-column grid of the channel's real Shorts; every other tab is the usual video list.
 */
@Composable
fun ChannelScreen(vm: AppViewModel) {
    val state = vm.channel ?: return
    val info = (state.info as? Load.Ready)?.data
    val listState = rememberLazyListState()
    val channelId = state.id.toString()

    // The banner is not part of the channel data the app already loads, so it is fetched once per channel.
    val bannerUrl by produceState<String?>(ChannelBanners.cached(channelId), channelId, info != null) {
        if (value == null && info != null && channelId.startsWith("UC")) {
            value = ChannelBanners.load(channelId)
        }
    }

    // The channel name moves into the top bar once the identity block has scrolled away (like YouTube).
    val showBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 &&
                listState.layoutInfo.visibleItemsInfo.none { it.key == KEY_IDENTITY }
        }
    }
    val barTitleAlpha by animateFloatAsState(
        targetValue = if (showBarTitle) 1f else 0f,
        animationSpec = tween(150),
        label = "channelBarTitle"
    )

    val isShortsTab = info?.tabs?.getOrNull(state.tab)?.title.equals("Shorts", ignoreCase = true)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = vm::closeChannel,
                modifier = Modifier.semantics { contentDescription = "Back" }
            ) {
                LineIcon(Glyph.BACK, 24.dp, MaterialTheme.colorScheme.onSurface, strokeWidth = 1.9f)
            }
            Text(
                info?.name ?: state.seedName.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp).alpha(barTitleAlpha)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            val loaded = state.info
            if (loaded is Load.Failed) {
                item { ErrorState(loaded.message, onRetry = vm::retryChannel) }
                return@LazyColumn
            }

            item(key = KEY_BANNER) {
                AnimatedVisibility(
                    visible = bannerUrl != null,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220))
                ) {
                    ChannelBanner(bannerUrl.orEmpty())
                }
            }

            item(key = KEY_IDENTITY) { ChannelIdentity(state, info) }

            if (info != null) {
                item(key = KEY_ABOUT) { ChannelAbout(vm, state, info) }
            }

            if (info != null && info.tabs.size > 1) {
                item(key = KEY_TABS) {
                    ChannelTabs(
                        titles = info.tabs.map { it.title },
                        selected = state.tab,
                        onSelect = vm::selectChannelTab
                    )
                }
            }

            when (val videos = state.videos) {
                is Load.Loading -> {
                    if (isShortsTab) {
                        item(key = "shorts-skeleton") { ShortsSkeleton() }
                    } else {
                        items(3) { SkeletonCard() }
                    }
                }
                is Load.Failed -> item { ErrorState(videos.message, onRetry = vm::retryChannel) }
                is Load.Ready -> {
                    if (videos.data.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No videos here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp)
                            )
                        }
                    }
                    if (isShortsTab) {
                        items(videos.data.chunked(SHORTS_COLUMNS), key = { row -> row.first().url }) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                row.forEach { video ->
                                    ShortTile(
                                        url = video.url.toString(),
                                        onClick = { vm.open(video) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(SHORTS_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        items(videos.data, key = { it.url }) { video ->
                            VideoCard(video, onClick = { vm.open(video) })
                        }
                    }
                    if (state.token != null) {
                        item(key = "more") {
                            LaunchedEffect(state.token) { vm.loadMoreChannel() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Header ─────────────────────────────

@Composable
private fun ChannelBanner(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .aspectRatio(3.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun ChannelIdentity(state: ChannelState, info: ChannelInfo?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = info?.avatar ?: state.seedAvatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                info?.name ?: state.seedName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            info?.handle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            val stats = listOfNotNull(info?.subscribers, info?.videoCount)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (stats.isNotEmpty()) {
                Text(
                    stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelAbout(vm: AppViewModel, state: ChannelState, info: ChannelInfo) {
    val subscribed = vm.subscriptions[state.id] ?: info.subscribed ?: false
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    val tint = MaterialTheme.colorScheme.onSurface
    // "Community" jumps to the channel's Posts tab; it is only offered when the channel really has one.
    val postsTab = info.tabs.indexOfFirst {
        it.title.equals("Posts", ignoreCase = true) || it.title.equals("Community", ignoreCase = true)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        info.description?.takeIf { it.isNotBlank() }?.let { ExpandableDescription(it) }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subscribed) {
                PillButton(
                    label = "Subscribed",
                    filled = false,
                    onClick = { confirmUnsubscribe = true },
                    icon = { LineIcon(Glyph.CHECK, 16.dp, tint, strokeWidth = 2.1f) }
                )
            } else {
                PillButton(
                    label = "Subscribe",
                    filled = true,
                    onClick = { vm.toggleSubscribe(state.id, subscribed) }
                )
            }
            if (postsTab >= 0) {
                PillButton(
                    label = "Community",
                    filled = false,
                    onClick = { vm.selectChannelTab(postsTab) },
                    icon = { LineIcon(Glyph.PEOPLE, 18.dp, tint, strokeWidth = 1.7f) }
                )
            }
        }
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text("Unsubscribe from ${info.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    vm.toggleSubscribe(state.id, subscribed)
                }) { Text("Unsubscribe") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = false }) { Text("Cancel") }
            }
        )
    }
}

/** Compact pill: 36dp tall, sized to its content. */
@Composable
private fun PillButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) scheme.onSurface else scheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon?.invoke()
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (filled) scheme.surface else scheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** Two lines of the description ending in "…more"; a tap expands it, another tap collapses it. */
@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var cutAt by remember(text) { mutableStateOf<Int?>(null) }

    val shown: AnnotatedString = remember(text, expanded, cutAt) {
        val cut = cutAt
        if (expanded || cut == null) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text.substring(0, cut).trimEnd())
                append("… ")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("more") }
            }
        }
    }

    Text(
        text = shown,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (!expanded && cutAt == null && layout.hasVisualOverflow) {
                // Leave room on the second line for "… more".
                val end = layout.getLineEnd(lineIndex = 1, visibleEnd = true)
                cutAt = (end - 10).coerceAtLeast(0)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = !expanded }
    )
    if (expanded) {
        Text(
            "Show less",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = false }
        )
    }
}

/** YouTube-style tabs: scrolling text tabs with an underline under the selected one. */
@Composable
private fun ChannelTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
        itemsIndexed(titles) { index, title ->
            val isSelected = index == selected
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 9.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 12.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                )
            }
        }
    }
}

// ───────────────────────────── Shorts grid ─────────────────────────────

private val VIDEO_ID = Regex("""(?:[?&]v=|/shorts/|youtu\.be/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

private fun videoIdOf(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

/** One Short: its real thumbnail (taken from the video id), cropped to 9:16. */
@Composable
private fun ShortTile(url: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val id = remember(url) { videoIdOf(url) }
    var useFallback by remember(id) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        if (id != null) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$id/${if (useFallback) "hqdefault" else "hq720"}.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { useFallback = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ShortsSkeleton() {
    Column {
        repeat(2) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(SHORTS_COLUMNS) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Icons (drawn in Kotlin, 24×24 grid, stroked) ─────────────────────────────

private object Glyph {
    val BACK = listOf("M19 12H5", "M11 6l-6 6 6 6")
    val CHECK = listOf("M5 12.5l4.5 4.5L19 7.5")
    val PEOPLE = listOf(
        "M9 11a3 3 0 1 0 0 -6a3 3 0 1 0 0 6z",
        "M3 19c0 -2.6 2.7 -4.2 6 -4.2s6 1.6 6 4.2",
        "M15.5 5.2a3 3 0 0 1 0 5.6",
        "M17.5 14.9c2 0.5 3.5 1.7 3.5 4.1"
    )
}

@Composable
private fun LineIcon(paths: List<String>, iconSize: Dp, tint: Color, strokeWidth: Float = 1.8f) {
    val compiled = remember(paths) {
        paths.mapNotNull { data ->
            runCatching { PathParser.createPathFromPathData(data).asComposePath() }.getOrNull()
        }
    }
    Canvas(Modifier.size(iconSize)) {
        val factor = size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            compiled.forEach { path ->
                drawPath(
                    path,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// ───────────────────────────── Banner source ─────────────────────────────

/**
 * Loads a channel's banner from YouTube's own channel endpoint (the same one the website uses) and caches it.
 * Any failure simply means "no banner": nothing is shown and nothing crashes.
 */
private object ChannelBanners {
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20250620.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val cache = ConcurrentHashMap<String, String>()

    fun cached(channelId: String): String? = cache[channelId]

    suspend fun load(channelId: String): String? {
        cache[channelId]?.let { return it }
        val url = withContext(Dispatchers.IO) { runCatching { fetch(channelId) }.getOrNull() }
        if (url != null) cache[channelId] = url
        return url
    }

    private fun fetch(channelId: String): String? {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", "en")
                        .put("gl", "US")
                )
            )
            .put("browseId", channelId)
            .toString()

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val header = JSONObject(text).optJSONObject("header") ?: return null
            return findBanner(header)
        } finally {
            conn.disconnect()
        }
    }

    /** Finds the header's "banner" node (new `imageBannerViewModel` or old `thumbnails` shape). */
    private fun findBanner(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("banner")?.let { pickImage(it) }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findBanner(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) findBanner(node.opt(i))?.let { return it }
            }
        }
        return null
    }

    private fun collectImages(node: Any?, out: MutableList<Pair<String, Int>>) {
        when (node) {
            is JSONObject -> {
                val url = node.optString("url")
                if (url.startsWith("http") || url.startsWith("//")) out.add(url to node.optInt("width", 0))
                val keys = node.keys()
                while (keys.hasNext()) collectImages(node.opt(keys.next()), out)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectImages(node.opt(i), out)
            }
        }
    }

    /** Smallest image that is at least ~1000px wide, otherwise the largest one available. */
    private fun pickImage(node: JSONObject): String? {
        val found = mutableListOf<Pair<String, Int>>()
        collectImages(node, found)
        if (found.isEmpty()) return null
        val sorted = found.sortedBy { it.second }
        val url = (sorted.firstOrNull { it.second >= 1000 } ?: sorted.last()).first
        return if (url.startsWith("//")) "https:$url" else url
    }
}

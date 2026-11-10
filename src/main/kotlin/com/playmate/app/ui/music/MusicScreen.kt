package com.playmate.app.ui.music

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PullToRefreshDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.MusicChip
import com.playmate.app.data.MusicImage
import com.playmate.app.data.MusicItem
import com.playmate.app.data.MusicKind
import com.playmate.app.data.MusicLayout
import com.playmate.app.data.MusicShelf
import com.playmate.app.data.ShelfStyle
import com.playmate.app.ui.components.MusicLogo
import com.playmate.app.ui.components.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.graphics.Color as AColor

/** YouTube Music is always dark, whichever theme the rest of Playmate is in. */
private val MBlack = Color(0xFF000000)
private val MWhite = Color(0xFFFFFFFF)
private val MGrey = Color(0xFFAAAAAA)
private val MButtonFill = Color(0xFF1A1A1A)
private val MTileFill = Color(0xFF181818)
private val MBadgeRed = Color(0xFFFF0033)
private val MProgressRed = Color(0xFFFF2D55)

/** Sizes measured from YouTube Music's own screen (dp, 360 dp wide phone). */
private val ShelfGap = 38.dp
private val SquareTile = 148.dp
private val HeaderBodyHeight = 104.dp

/**
 * The Music tab, drawn like YouTube Music's home: the coloured glow at the top, the logo with the
 * bell and the account picture, the mood chips (they leave when you scroll down and come back
 * when you scroll up), then every shelf the account really gets, in the order and under the
 * names YouTube Music sent. Long-press the logo to copy a report of what the server sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val density = LocalDensity.current

    // Light icons in the status bar on the black screen; put back when the tab is left.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose {
            if (bars != null) lightStatus?.let { bars.isAppearanceLightStatusBars = it }
        }
    }

    // The bell's number comes from the account's real notifications.
    LaunchedEffect(vm.signedIn) {
        if (vm.signedIn && vm.notifications !is Load.Ready) vm.loadNotifications()
    }
    val unread = (vm.notifications as? Load.Ready)?.data?.count { it.unread } ?: 0

    val listState = rememberLazyListState()
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    var headerPx by remember { mutableFloatStateOf(statusTop + with(density) { HeaderBodyHeight.toPx() }) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    val headerDp = with(density) { headerPx.toDp() }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffset = (headerOffset + available.y).coerceIn(-headerPx, 0f)
                return Offset.Zero
            }
        }
    }
    val atTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6 }
    }
    val glowShift by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f
        }
    }
    LaunchedEffect(atTop) { if (atTop) headerOffset = 0f }

    val state = vm.music
    LaunchedEffect(state is Load.Ready) { if (state !is Load.Ready) headerOffset = 0f }

    val glowUrls = (state as? Load.Ready)?.data?.firstOrNull()?.items?.take(3)
        ?.map { MusicImage.sized(it.thumb, 64) }.orEmpty()
    val glowColors = rememberArtColors(glowUrls)
    val currentUrl = vm.playing?.seed?.url

    fun openItem(item: MusicItem) = vm.openMusic(item)
    fun showMenu(item: MusicItem) {
        Toast.makeText(context, "Item menu comes in the next step", Toast.LENGTH_SHORT).show()
    }
    fun showMore(shelf: MusicShelf) {
        Toast.makeText(context, "This page opens in the next step", Toast.LENGTH_SHORT).show()
    }
    fun playAllOf(shelf: MusicShelf) {
        val first = shelf.items.firstOrNull { it.videoId != null }
        if (first != null) vm.openMusic(first)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MBlack)
            .nestedScroll(connection)
    ) {
        // The glow at the top, painted from the covers of the first shelf. It scrolls away with the list.
        Box(
            Modifier
                .fillMaxWidth()
                .height(470.dp)
                .graphicsLayer { translationY = -glowShift }
                .drawBehind { drawGlow(glowColors) }
        )

        when (state) {
            is Load.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = headerDp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MWhite, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            }

            is Load.Failed -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = headerDp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.message, color = MGrey, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.loadMusic(force = false) }) { Text("Try again", color = MWhite) }
            }

            is Load.Ready -> {
                val nearEnd by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= info.totalItemsCount - 3
                    }
                }
                LaunchedEffect(nearEnd, state.data.size) { if (nearEnd) vm.loadMoreMusic() }

                val pull = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = vm.musicRefreshing,
                    onRefresh = { vm.loadMusic(force = true) },
                    state = pull,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pull,
                            isRefreshing = vm.musicRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = headerDp),
                            containerColor = MButtonFill,
                            color = MWhite
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = headerDp, bottom = 28.dp)
                    ) {
                        itemsIndexed(state.data, key = { i, shelf -> "$i|${shelf.debug}" }) { index, shelf ->
                            ShelfView(
                                shelf = shelf,
                                first = index == 0,
                                currentUrl = currentUrl,
                                onOpen = ::openItem,
                                onMenu = ::showMenu,
                                onMore = ::showMore,
                                onPlayAll = ::playAllOf
                            )
                        }
                    }
                }
            }
        }

        // Behind the status icons the screen is black once the list has moved.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (scrolled) MBlack else Color.Transparent)
        )

        MusicHeader(
            modifier = Modifier
                .offset { IntOffset(0, headerOffset.roundToInt()) }
                .onSizeChanged { if (it.height > 0) headerPx = it.height.toFloat() },
            scrolled = scrolled,
            chips = vm.musicChips,
            selected = vm.musicChip,
            unread = unread,
            avatar = vm.account?.avatar,
            onReport = {
                clipboard.setText(AnnotatedString(vm.musicReport()))
                Toast.makeText(context, "Music report copied", Toast.LENGTH_SHORT).show()
            },
            onBell = { if (vm.signedIn) vm.openNotifications() else vm.select(Tab.You) },
            onAvatar = { vm.select(Tab.You) },
            onChip = vm::selectMusicChip
        )
    }
}

// ------------------------------------------------------------------ glow

private fun DrawScope.drawGlow(colors: List<Color>) {
    val fallback = Color(0xFF3A3F4B)
    val base = glow(colors.getOrNull(0) ?: fallback)
    val left = glow(colors.getOrNull(1) ?: colors.getOrNull(0) ?: fallback)
    val right = glow(colors.getOrNull(2) ?: colors.getOrNull(0) ?: fallback)
    drawRect(
        Brush.verticalGradient(
            0f to base.copy(alpha = 0.80f),
            0.50f to base.copy(alpha = 0.34f),
            1f to Color.Transparent
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(left.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.30f),
            radius = size.width * 0.85f
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(right.copy(alpha = 0.60f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.42f),
            radius = size.width * 0.80f
        )
    )
}

/** A cover's average colour, lifted so it glows on black without going neon. */
private fun glow(c: Color): Color {
    val hsv = FloatArray(3)
    AColor.colorToHSV(c.toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.9f).coerceIn(0f, 0.8f)
    hsv[2] = hsv[2].coerceIn(0.38f, 0.55f)
    return Color(AColor.HSVToColor(hsv))
}

/** The dark tint of a big card: the cover's colour, pushed down until white text sits on it. */
private fun cardTint(c: Color): Color {
    val hsv = FloatArray(3)
    AColor.colorToHSV(c.toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.75f).coerceIn(0f, 0.6f)
    hsv[2] = (hsv[2] * 0.42f).coerceIn(0.15f, 0.30f)
    return Color(AColor.HSVToColor(hsv))
}

private val colorCache = ConcurrentHashMap<String, Int>()

private suspend fun loadArtColor(context: Context, url: String): Color? {
    colorCache[url]?.let { return Color(it) }
    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context).data(url).size(32, 32).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val bitmap = result.drawable.toBitmap(8, 8)
        var r = 0L
        var g = 0L
        var b = 0L
        for (x in 0 until 8) for (y in 0 until 8) {
            val p = bitmap.getPixel(x, y)
            r += AColor.red(p)
            g += AColor.green(p)
            b += AColor.blue(p)
        }
        val argb = AColor.rgb((r / 64).toInt(), (g / 64).toInt(), (b / 64).toInt())
        colorCache[url] = argb
        Color(argb)
    }
}

@Composable
private fun rememberArtColors(urls: List<String?>): List<Color> {
    val context = LocalContext.current
    val state = produceState(initialValue = emptyList<Color>(), urls) {
        value = urls.mapNotNull { url -> url?.let { loadArtColor(context, it) } }
    }
    return state.value
}

@Composable
private fun rememberArtColor(url: String?): Color? {
    val context = LocalContext.current
    val state = produceState<Color?>(initialValue = null, url) {
        value = url?.let { loadArtColor(context, it) }
    }
    return state.value
}

// ------------------------------------------------------------------ header

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicHeader(
    modifier: Modifier,
    scrolled: Boolean,
    chips: List<MusicChip>,
    selected: Int,
    unread: Int,
    avatar: String?,
    onReport: () -> Unit,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    onChip: (Int) -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(if (scrolled) MBlack else Color.Transparent)
            .drawBehind {
                if (scrolled) {
                    drawLine(Color(0xFF212121), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
                }
            }
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicLogo(
                Modifier
                    .weight(1f)
                    .combinedClickable(onClick = {}, onLongClick = onReport)
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBell),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = MWhite, modifier = Modifier.size(24.dp))
                if (unread > 0) {
                    Box(
                        Modifier
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MBadgeRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unread > 9) "9+" else unread.toString(),
                            color = MWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatar),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "Account",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MButtonFill)
                    )
                } else {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MButtonFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "Account", tint = MGrey, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (chips.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
            ) {
                itemsIndexed(chips, key = { i, chip -> "$i|${chip.label}" }) { index, chip ->
                    MoodChip(chip.label, selected = index == selected, dim = scrolled) { onChip(index) }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MoodChip(label: String, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    val fill = when {
        selected -> MWhite
        dim -> Color(0x1FFFFFFF)
        else -> Color(0x38FFFFFF)
    }
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MBlack else if (dim) Color(0xCCFFFFFF) else MWhite,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}

// ------------------------------------------------------------------ shelves

@Composable
private fun ShelfView(
    shelf: MusicShelf,
    first: Boolean,
    currentUrl: String?,
    onOpen: (MusicItem) -> Unit,
    onMenu: (MusicItem) -> Unit,
    onMore: (MusicShelf) -> Unit,
    onPlayAll: (MusicShelf) -> Unit
) {
    Column(Modifier.padding(top = if (first) 12.dp else ShelfGap)) {
        if (shelf.title.isNotBlank()) {
            ShelfHeader(shelf, onMore = { onMore(shelf) }, onPlayAll = { onPlayAll(shelf) })
            Spacer(Modifier.height(12.dp))
        }
        when (shelf.style) {
            ShelfStyle.Rows -> RowsShelf(shelf, currentUrl, onOpen, onMenu)
            ShelfStyle.Tiles -> TilesShelf(shelf, onOpen)
            ShelfStyle.Grid -> GridShelf(shelf, onOpen)
            ShelfStyle.Buttons -> ButtonsShelf(shelf, onOpen)
            ShelfStyle.Episodes, ShelfStyle.Immersive -> CardsShelf(shelf, onOpen, onMenu)
        }
    }
}

@Composable
private fun ShelfHeader(shelf: MusicShelf, onMore: () -> Unit, onPlayAll: () -> Unit) {
    val canPlayAll = shelf.playAllVideoId != null || shelf.playAllPlaylistId != null
    val moreTap = if (!canPlayAll && shelf.moreBrowseId != null) Modifier.clickable(onClick = onMore) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(moreTap)
            .padding(horizontal = 16.dp)
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (shelf.avatar != null) {
            AsyncImage(
                model = shelf.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MButtonFill)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            shelf.strapline?.let {
                Text(
                    it.uppercase(),
                    color = MGrey,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                shelf.title,
                color = MWhite,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canPlayAll) {
            Box(
                Modifier
                    .height(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x4DFFFFFF), CircleShape)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 8.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Play all", color = Color(0xFFF1F1F3), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        } else if (shelf.moreBrowseId != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "More",
                tint = MWhite,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ---------------------------------------------------------- rows (Quick picks, Covers and remixes...)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowsShelf(
    shelf: MusicShelf,
    currentUrl: String?,
    onOpen: (MusicItem) -> Unit,
    onMenu: (MusicItem) -> Unit
) {
    val perColumn = if (shelf.rowsPerColumn > 1) shelf.rowsPerColumn else minOf(4, shelf.items.size)
    val columns = shelf.items.chunked(perColumn.coerceAtLeast(1))
    val width = LocalConfiguration.current.screenWidthDp.dp - 40.dp
    val listState: LazyListState = rememberLazyListState()
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(columns, key = { i, col -> "$i|${col.firstOrNull()?.key}" }) { _, column ->
            Column(Modifier.width(width)) {
                column.forEach { item ->
                    val current = item.videoId != null && currentUrl?.contains("v=${item.videoId}") == true
                    SongRow(item, current, onClick = { onOpen(item) }, onMenu = { onMenu(item) })
                }
            }
        }
    }
}

@Composable
private fun SongRow(item: MusicItem, current: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (current) Color(0x24FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MTileFill)
        ) {
            AsyncImage(
                model = MusicImage.sized(item.thumb, 168),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(MWhite)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = MWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SubtitleLine(item.subtitle, item.explicit)
        }
        MenuDots(onMenu)
    }
}

@Composable
private fun MenuDots(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MWhite, modifier = Modifier.size(24.dp))
    }
}

/** The grey line under a title, with the small "E" box in front when the item is explicit. */
@Composable
private fun SubtitleLine(text: String, explicit: Boolean, center: Boolean = false) {
    if (text.isBlank() && !explicit) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        if (explicit) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MGrey),
                contentAlignment = Alignment.Center
            ) {
                Text("E", color = MBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = MGrey,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.weight(1f, fill = !center)
        )
    }
}

// ---------------------------------------------------------- cover tiles

@Composable
private fun TilesShelf(shelf: MusicShelf, onOpen: (MusicItem) -> Unit) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val large = shelf.itemSize.endsWith("MEDIUM") || shelf.itemSize.contains("LARGE")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(shelf.items, key = { i, item -> "$i|${item.key}" }) { _, item ->
            CoverTile(item, screenW, large) { onOpen(item) }
        }
    }
}

@Composable
private fun CoverTile(item: MusicItem, screenW: Dp, large: Boolean, onClick: () -> Unit) {
    val video = item.kind == MusicKind.Video
    val artist = item.kind == MusicKind.Artist
    val wide = screenW - 48.dp
    val width = when {
        video && large -> wide
        video -> 263.dp
        else -> SquareTile
    }
    val height = if (video && large) wide * 9f / 16f else SquareTile
    val shape = if (artist) CircleShape else RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(width)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(width, height)
                .clip(shape)
                .background(MTileFill)
        ) {
            AsyncImage(
                model = MusicImage.sized(item.thumb, if (video) 640 else 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            item.duration?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(it, color = MWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            item.progress?.let { ProgressStrip(it, Modifier.align(Alignment.BottomStart)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = MWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (artist) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        SubtitleLine(item.subtitle, item.explicit, center = artist)
    }
}

/** The red bar along the bottom of a cover that was started before. */
@Composable
private fun ProgressStrip(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x4DFFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .background(MProgressRed)
        )
    }
}

// ---------------------------------------------------------- Speed dial grid

@Composable
private fun GridShelf(shelf: MusicShelf, onOpen: (MusicItem) -> Unit) {
    val tile = (LocalConfiguration.current.screenWidthDp.dp - 32.dp - 8.dp) / 3
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shelf.items.take(6).chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.forEach { item -> SpeedTile(item, tile) { onOpen(item) } }
            }
        }
    }
}

@Composable
private fun SpeedTile(item: MusicItem, size: Dp, onClick: () -> Unit) {
    val artist = item.kind == MusicKind.Artist
    val leads = item.kind != MusicKind.Song && item.kind != MusicKind.Video
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MTileFill)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = MusicImage.sized(item.thumb, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (artist) Modifier.clip(CircleShape) else Modifier)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(size / 2)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.title,
                color = MWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (leads) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        item.progress?.let { ProgressStrip(it, Modifier.align(Alignment.BottomStart)) }
    }
}

// ---------------------------------------------------------- round buttons (Explore)

@Composable
private fun ButtonsShelf(shelf: MusicShelf, onOpen: (MusicItem) -> Unit) {
    val button = (LocalConfiguration.current.screenWidthDp.dp - 32.dp - 32.dp) / 3
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        shelf.items.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                line.forEach { item ->
                    Column(
                        Modifier
                            .size(button, 106.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MButtonFill)
                            .clickable { onOpen(item) }
                            .padding(16.dp)
                    ) {
                        Icon(buttonIcon(item), contentDescription = null, tint = MWhite, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.weight(1f))
                        Text(
                            item.title,
                            color = MWhite,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun buttonIcon(item: MusicItem): ImageVector {
    val label = item.title.lowercase()
    val icon = item.icon.orEmpty().lowercase()
    return when {
        "release" in label || "release" in icon -> Icons.Outlined.NewReleases
        "chart" in label || "trending" in icon || "chart" in icon -> Icons.AutoMirrored.Outlined.TrendingUp
        "mood" in label || "genre" in label || "mood" in icon || "emoji" in icon -> Icons.Outlined.EmojiEmotions
        else -> Icons.Outlined.MusicNote
    }
}

// ---------------------------------------------------------- big cards (Featured playlists, Shows for you)

@Composable
private fun CardsShelf(shelf: MusicShelf, onOpen: (MusicItem) -> Unit, onMenu: (MusicItem) -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp.dp - 48.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(shelf.items, key = { i, item -> "$i|${item.key}" }) { _, item ->
            BigCard(item, width, onOpen, onMenu)
        }
    }
}

@Composable
private fun BigCard(item: MusicItem, width: Dp, onOpen: (MusicItem) -> Unit, onMenu: (MusicItem) -> Unit) {
    val tint = rememberArtColor(MusicImage.sized(item.thumb, 64))?.let(::cardTint) ?: Color(0xFF1E1E1E)
    val show = item.kind == MusicKind.Podcast || item.kind == MusicKind.Episode
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .clickable { onOpen(item) }
            .padding(16.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = MusicImage.sized(item.thumb, 544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MTileFill)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = MWhite,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SubtitleLine(item.subtitle, item.explicit)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (show) Modifier.border(1.dp, Color(0x66FFFFFF), CircleShape)
                        else Modifier.background(Color(0x33FFFFFF))
                    )
                    .clickable { onOpen(item) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (show) Icons.Outlined.BookmarkBorder else Icons.Filled.PlayArrow,
                    contentDescription = if (show) "Save" else "Play",
                    tint = MWhite,
                    modifier = Modifier.size(if (show) 24.dp else 30.dp)
                )
            }
        }
        if (item.tracks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            item.tracks.forEach { track -> TrackRow(track, onClick = { onOpen(track) }, onMenu = { onMenu(track) }) }
        }
    }
}

/** One song or episode inside a big card. */
@Composable
private fun TrackRow(track: MusicItem, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(63.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.layout == MusicLayout.Row) {
            AsyncImage(
                model = MusicImage.sized(track.thumb, 144),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MTileFill)
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = MWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) { SubtitleLine(track.subtitle, track.explicit) }
                track.progress?.let { percent ->
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(Color(0x4DFFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                                .background(MProgressRed)
                        )
                    }
                }
            }
        }
        MenuDots(onMenu)
    }
}

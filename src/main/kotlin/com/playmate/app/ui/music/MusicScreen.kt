package com.playmate.app.ui.music

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.Load
import com.playmate.app.data.MusicImage
import com.playmate.app.data.MusicItem
import com.playmate.app.data.MusicShelf
import com.playmate.app.ui.components.MusicLogo
import com.playmate.app.ui.components.findActivity

/** YouTube Music is always dark, whichever theme the rest of Playmate is in. */
private val MusicBlack = Color(0xFF000000)
private val MusicWhite = Color(0xFFFFFFFF)
private val MusicGrey = Color(0xFFAAAAAA)
private val MusicChipFill = Color(0xFF272727)

/**
 * The Music tab. This is the working frame: the real YouTube Music home (mood chips, then every
 * shelf the account gets, in YouTube Music's own order) with plain rows. The final look of each
 * shelf comes next, one shelf type at a time. Long-press the logo to copy a report of what
 * YouTube Music really sent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current

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

    Box(
        Modifier
            .fillMaxSize()
            .background(MusicBlack)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            MusicLogo(
                Modifier
                    .padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(vm.musicReport()))
                            Toast.makeText(context, "Music report copied", Toast.LENGTH_SHORT).show()
                        }
                    )
            )

            if (vm.musicChips.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    itemsIndexed(vm.musicChips, key = { i, chip -> "$i|${chip.label}" }) { index, chip ->
                        MoodChip(chip.label, selected = index == vm.musicChip) { vm.selectMusicChip(index) }
                    }
                }
            }

            when (val state = vm.music) {
                is Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicWhite, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                }

                is Load.Failed -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.message, color = MusicGrey, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.loadMusic(force = false) }) {
                        Text("Try again", color = MusicWhite)
                    }
                }

                is Load.Ready -> {
                    val shelves = state.data
                    val listState = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= info.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(nearEnd, shelves.size) {
                        if (nearEnd) vm.loadMoreMusic()
                    }
                    PullToRefreshBox(
                        isRefreshing = vm.musicRefreshing,
                        onRefresh = { vm.loadMusic(force = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(shelves, key = { i, shelf -> "$i|${shelf.debug}" }) { _, shelf ->
                                ShelfFrame(shelf, onOpen = vm::openMusic)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) MusicWhite else MusicChipFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MusicBlack else MusicWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** A shelf as plain rows: its title as YouTube Music wrote it, then its first items. */
@Composable
private fun ShelfFrame(shelf: MusicShelf, onOpen: (MusicItem) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        shelf.strapline?.let {
            Text(
                it.uppercase(),
                color = MusicGrey,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Text(
            shelf.title,
            color = MusicWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
        shelf.items.take(6).forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = MusicImage.sized(item.thumb, 160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MusicChipFill)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = MusicWhite, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, color = MusicGrey, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

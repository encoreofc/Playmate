package com.playmate.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.RecentSearch
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keeps Bengali joined and uncut: no letter spacing, and a roomy line with nothing trimmed. */
private fun TextStyle.bangla() = copy(
    letterSpacing = 0.sp,
    lineHeight = fontSize * 1.5f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
fun SearchScreen(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (vm.query.isEmpty() && vm.results == null) focus.requestFocus()
    }

    // While typing over a finished search, Back returns to those results instead of leaving search.
    val canReturnToResults = vm.editing && vm.results != null
    BackHandler(enabled = canReturnToResults) {
        vm.stopEditing()
        focusManager.clearFocus()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            onBack = {
                if (canReturnToResults) {
                    vm.stopEditing()
                    focusManager.clearFocus()
                } else {
                    focusManager.clearFocus()
                    vm.select(Tab.Home)
                }
            },
            onSubmit = {
                vm.submit()
                focusManager.clearFocus()
            },
            onClear = {
                vm.onQueryChange("")
                focus.requestFocus()
            },
            onFocused = vm::startEditing,
            focus = focus
        )

        val showHints = vm.editing && (vm.suggestions.isNotEmpty() || vm.query.isBlank())

        when {
            showHints && vm.query.isBlank() -> Recents(vm)
            showHints -> Suggestions(vm, onPicked = { focusManager.clearFocus() })
            else -> Results(vm)
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onFocused: () -> Unit,
    focus: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val rim = if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.outlineVariant

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(PmIcons.Back, contentDescription = "Back", modifier = Modifier.size(24.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(if (focused) 1.2.dp else 0.8.dp, rim, shape)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                PmIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        "Search or paste a link",
                        style = MaterialTheme.typography.bodyLarge.bangla(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.bangla().copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onFocused()
                        }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(44.dp)) {
                    Icon(
                        PmIcons.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Recents(vm: AppViewModel) {
    var removing by remember { mutableStateOf<RecentSearch?>(null) }
    var clearing by remember { mutableStateOf(false) }

    if (vm.recents.isEmpty()) {
        EmptySearch()
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { clearing = true }) {
                    Text("Clear all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        items(vm.recents, key = { it.text }) { entry ->
            RecentRow(
                entry = entry,
                onClick = { vm.submit(entry.text) },
                onLongClick = { removing = entry },
                onInsert = { vm.onQueryChange(entry.text) }
            )
        }
    }

    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from search history?") },
            text = { Text(entry.text, style = MaterialTheme.typography.bodyMedium.bangla(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { vm.removeRecent(entry.text); removing = null }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel") }
            }
        )
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear search history?") },
            text = { Text("Your saved searches will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearRecents(); clearing = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearing = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    entry: RecentSearch,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInsert: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            PmIcons.Clock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyLarge.bangla(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (entry.thumb != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(entry.thumb).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 76.dp, height = 43.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        IconButton(onClick = onInsert) {
            Icon(
                PmIcons.Insert,
                contentDescription = "Use in search box",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptySearch() {
    Column(
        Modifier.fillMaxSize().padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            PmIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp).alpha(0.7f)
        )
        Spacer(Modifier.height(14.dp))
        Text("Search Playmate", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Videos, channels, or paste a link",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Suggestions(vm: AppViewModel, onPicked: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.suggestions, key = { it }) { text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable {
                        vm.submit(text)
                        onPicked()
                    }
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    PmIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    suggestionText(text, vm.query),
                    style = MaterialTheme.typography.bodyLarge.bangla(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)
                )
                IconButton(onClick = { vm.onQueryChange(text) }) {
                    Icon(
                        PmIcons.Insert,
                        contentDescription = "Use in search box",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** What you already typed stays light; the part the suggestion adds is bold, so the next words stand out. */
private fun suggestionText(suggestion: String, typed: String) = buildAnnotatedString {
    val t = typed.trim()
    if (t.isNotEmpty() && suggestion.startsWith(t, ignoreCase = true)) {
        append(suggestion.substring(0, t.length))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.substring(t.length)) }
    } else {
        append(suggestion)
    }
}

@Composable
private fun Results(vm: AppViewModel) {
    when (val state = vm.results) {
        null -> Box(Modifier.fillMaxSize())
        is Load.Loading -> Column { repeat(3) { SkeletonCard() } }
        is Load.Failed -> ErrorState(state.message, onRetry = { vm.submit() })
        is Load.Ready -> {
            val items = state.data.items
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No videos found for \"${vm.query}\"",
                        style = MaterialTheme.typography.bodyMedium.bangla(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return
            }
            val listState = rememberLazyListState()
            LaunchedEffect(listState, items.size) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .distinctUntilChanged()
                    .collect { last -> if (last >= items.size - 4) vm.loadMore() }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.url }) { video ->
                    VideoCard(video, onClick = { vm.open(video) })
                }
                if (vm.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

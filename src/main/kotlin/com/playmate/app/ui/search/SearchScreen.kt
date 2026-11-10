package com.playmate.app.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.playmate.app.ui.theme.LocalPmDark
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keeps Bengali joined and uncut: no letter spacing, and a roomy line with nothing trimmed. */
private fun TextStyle.bangla() = copy(
    letterSpacing = 0.sp,
    lineHeight = fontSize * 1.5f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
fun SearchScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Google's own voice search window. What was said goes straight into a search.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                vm.submit(spoken)
                focusManager.clearFocus()
            }
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice search is not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (vm.editing) {
            // Opened from the search icon: an empty box with the saved searches, like YouTube.
            vm.beginSearch()
            focus.requestFocus()
        } else if (vm.query.isEmpty() && vm.results == null) {
            focus.requestFocus()
        }
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
            onVoice = startVoice,
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

/**
 * The search bar, copied from the reference (dp): a 56 high row, back arrow in a 48 button,
 * a 252 wide grey pill 40 high with 16 of space before the text, and a black mic in a 48 button.
 * The cursor is red.
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    onFocused: () -> Unit,
    focus: FocusRequester
) {
    val dark = LocalPmDark.current
    val boxColor = if (dark) Color(0xFF2B2B2B) else Color(0xFFE3E3E3)
    val hintColor = if (dark) Color(0xFFAAAAAA) else Color(0xFF626262)
    val ink = MaterialTheme.colorScheme.onBackground

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(2.dp))
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(boxColor)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        "Search videos, channels...",
                        style = MaterialTheme.typography.bodyLarge.bangla(),
                        color = hintColor,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.bangla().copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Color(0xFFFF0000)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(
                        PmIcons.Close,
                        contentDescription = "Clear",
                        tint = hintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onVoice) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Voice search",
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Recents(vm: AppViewModel) {
    var removing by remember { mutableStateOf<RecentSearch?>(null) }
    var clearing by remember { mutableStateOf(false) }

    // No saved searches: a plain empty page, like the reference (no logo, no extra text).
    if (vm.recents.isEmpty()) {
        Box(Modifier.fillMaxSize())
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

package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.CommentItem
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState

/**
 * The comments, laid out the way YouTube does it: a panel right under the player (the video stays
 * visible above), "Comments" with an info and a close button, Top / Newest chips, the list, and the
 * box to write one pinned at the bottom.
 */
@Composable
fun CommentsPanel(vm: AppViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var infoOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadComments() }

    PanelFrame(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { infoOpen = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "Comment guidelines")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }

        if (vm.commentSorts.size > 1) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.commentSorts.forEach { option ->
                    SortChip(option.title, option.selected) { vm.loadComments(token = option.token) }
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        Column(Modifier.weight(1f)) {
            when (val state = vm.comments) {
                null, Load.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }

                is Load.Failed -> ErrorState(state.message, onRetry = { vm.loadComments(force = true) })

                is Load.Ready -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (state.data.isEmpty()) {
                        item {
                            Text(
                                "No comments yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                    items(state.data) { comment ->
                        val hasReplies = comment.repliesToken != null
                        Column {
                            Column(if (hasReplies) Modifier.threadLine(scheme.outline) else Modifier) {
                                CommentRow(comment, onReply = { vm.toggleReplies(comment) })
                                if (hasReplies) RepliesToggle(vm, comment)
                            }
                            if (hasReplies) RepliesList(vm, comment)
                        }
                    }
                    if (vm.commentsNext != null) {
                        item {
                            LaunchedEffect(vm.commentsNext) { vm.loadMoreComments() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        Composer(vm, onSignIn = {
            onDismiss()
            vm.openLogin()
        })
    }

    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text("Comment guidelines") },
            text = { Text("Remember to keep comments respectful and to follow YouTube's Community Guidelines.") },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("OK") } }
        )
    }
}

/**
 * The same comments as a bottom sheet, for the Shorts screen, where there is no player strip to
 * stay visible above. Same look as [CommentsPanel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            CommentsPanel(vm = vm, onDismiss = onDismiss)
        }
    }
}

/** Top / Newest: the chosen one is filled grey, the other is an outline. */
@Composable
private fun SortChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .height(32.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(ytGrey())
                else Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.4f), shape)
            )
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = scheme.onSurface
        )
    }
}

/** The thin line that runs from a comment's avatar, curves, and ends at its "N replies" row. */
private fun Modifier.threadLine(color: Color): Modifier = drawBehind {
    val x = 28.dp.toPx()
    val top = 42.dp.toPx()
    val centre = size.height - 20.dp.toPx()
    val radius = 12.dp.toPx()
    val path = Path().apply {
        moveTo(x, top)
        lineTo(x, centre - radius)
        quadraticBezierTo(x, centre, x + radius, centre)
        lineTo(x + radius + 10.dp.toPx(), centre)
    }
    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
}

/** The pill pinned at the bottom: write a comment, or sign in first. */
@Composable
private fun Composer(vm: AppViewModel, onSignIn: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(scheme.surface)) {
        HorizontalDivider(color = scheme.outlineVariant)
        Box(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val pill = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ytGrey())

            if (!vm.signedIn) {
                Row(
                    pill.clickable(onClick = onSignIn).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Sign in to comment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).padding(vertical = 12.dp)
                    )
                }
                return@Box
            }

            var text by remember { mutableStateOf("") }
            Row(pill.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm.account?.avatar, 28.dp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.onSurface),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "Add a comment…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    }
                )
                if (vm.postingComment) {
                    CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = { vm.postComment(text) { text = "" } },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "Post")
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ytGrey())
    )
}

/** One comment: avatar, "@name • 3mo ago", the text, then like, dislike and reply icons. */
@Composable
private fun CommentRow(comment: CommentItem, modifier: Modifier = Modifier, onReply: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    var open by remember(comment.id) { mutableStateOf(false) }
    var long by remember(comment.id) { mutableStateOf(false) }

    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Avatar(comment.avatar, if (comment.isReply) 20.dp else 24.dp)
        Column(Modifier.weight(1f).padding(start = if (comment.isReply) 20.dp else 16.dp)) {
            if (comment.pinned) {
                Text(
                    "Pinned",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = scheme.onSurfaceVariant
                )
            }
            val head = buildString {
                append(comment.author)
                if (comment.published.isNotBlank()) append(" • ").append(shortAgo(comment.published))
            }
            Text(
                head,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                maxLines = if (open) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!open && it.hasVisualOverflow) long = true },
                modifier = Modifier.padding(top = 2.dp)
            )
            if (long && !open) {
                Text(
                    "Read more",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.clickable { open = true }.padding(top = 4.dp)
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ThumbUpOffAlt,
                    contentDescription = "Like",
                    modifier = Modifier.size(20.dp)
                )
                comment.likes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.width(36.dp))
                Icon(
                    Icons.Rounded.ThumbDownOffAlt,
                    contentDescription = "Dislike",
                    modifier = Modifier.size(20.dp)
                )
                if (onReply != null) {
                    Spacer(Modifier.width(36.dp))
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Replies",
                        modifier = Modifier.size(20.dp).clickable(onClick = onReply)
                    )
                }
            }
        }
    }
}

/** "1 reply ›" under a comment; tap to open or hide its replies. */
@Composable
private fun RepliesToggle(vm: AppViewModel, comment: CommentItem) {
    val open = vm.replies[comment.id]?.open == true
    val label = when {
        open -> "Hide replies"
        comment.replies.isNullOrBlank() -> "Replies"
        comment.replies == "1" -> "1 reply"
        else -> "${comment.replies} replies"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { vm.toggleReplies(comment) }
            .padding(start = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        Icon(
            when {
                open -> Icons.Rounded.KeyboardArrowUp
                else -> Icons.Rounded.KeyboardArrowRight
            },
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp).size(20.dp)
        )
    }
}

/** The replies of a comment once opened, with "Show more replies" at the end. */
@Composable
private fun RepliesList(vm: AppViewModel, comment: CommentItem) {
    val state = vm.replies[comment.id]
    if (state == null || !state.open) return
    val scheme = MaterialTheme.colorScheme
    state.items.forEach { reply -> CommentRow(reply, Modifier.padding(start = 40.dp)) }
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        state.error != null -> Text(
            state.error,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 96.dp, bottom = 8.dp)
        )

        state.next != null -> Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable { vm.loadMoreReplies(comment) }
                .padding(start = 96.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show more replies", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp).size(20.dp)
            )
        }
    }
}

package com.playmate.app.ui.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.CommentVote
import com.playmate.app.data.CommentItem
import com.playmate.app.data.Load
import com.playmate.app.ui.components.AnimatedThumb
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.components.RollingCount
import com.playmate.app.ui.components.YtIcons
import kotlinx.coroutines.launch

private val HeartRed = Color(0xFFFF2D55)

/**
 * The comments, laid out the way YouTube does it: a panel right under the player (the video stays
 * visible above), "Comments" with an info and a close button, Top / Newest chips, the list, and the
 * box to write one pinned at the bottom. Pressing a comment's reply button (or "N replies") opens
 * that comment's Replies screen in the same panel, with its own box to write a reply.
 */
@Composable
fun CommentsPanel(vm: AppViewModel, onDismiss: () -> Unit) {
    var infoOpen by remember { mutableStateOf(false) }
    var thread by remember { mutableStateOf<CommentItem?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { vm.loadComments() }
    BackHandler(enabled = thread != null) { thread = null }

    PanelFrame(onDismiss) {
        val open = thread
        if (open == null) {
            CommentsList(
                vm = vm,
                listState = listState,
                onDismiss = onDismiss,
                onInfo = { infoOpen = true },
                onOpenThread = { comment ->
                    vm.openReplies(comment)
                    thread = comment
                }
            )
        } else {
            ThreadScreen(vm = vm, parent = open, onBack = { thread = null }, onClose = onDismiss)
        }
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

// ------------------------------------------------------------------------------ the list

@Composable
private fun ColumnScope.CommentsList(
    vm: AppViewModel,
    listState: LazyListState,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onOpenThread: (CommentItem) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Comments",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onInfo) {
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

            is Load.Ready -> LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
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
                    val hasReplies = comment.repliesToken != null ||
                        (!comment.replies.isNullOrBlank() && comment.replies != "0")
                    Column(if (hasReplies) Modifier.threadLine(scheme.outline) else Modifier) {
                        CommentRow(
                            comment = comment,
                            vote = vm.voteOf(comment),
                            onLike = { vm.likeComment(comment) },
                            onDislike = { vm.dislikeComment(comment) },
                            onReply = { onOpenThread(comment) }
                        )
                        if (hasReplies) RepliesToggle(comment) { onOpenThread(comment) }
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

    ComposerBar(
        vm = vm,
        placeholder = "Add a comment…",
        signInLabel = "Sign in to comment",
        value = draft,
        onValueChange = { draft = it },
        busy = vm.postingComment,
        onSend = { vm.postComment(draft) { draft = "" } },
        onSignIn = {
            onDismiss()
            vm.openLogin()
        }
    )
}

// ------------------------------------------------------------------------------ the Replies screen

/** One comment on top, its replies under it, and a box to write a reply at the bottom. */
@Composable
private fun ColumnScope.ThreadScreen(
    vm: AppViewModel,
    parent: CommentItem,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val state = vm.replies[parent.id]
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var target by remember(parent.id) { mutableStateOf(parent) }
    var draft by remember(parent.id) { mutableStateOf("") }

    // Pressing Reply on the comment itself, or on one of its replies (which starts "@name ").
    val replyTo: (CommentItem) -> Unit = { comment ->
        if (vm.ensureSignedIn("reply")) {
            target = comment
            draft = if (comment.id == parent.id) "" else comment.author.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
            runCatching { focus.requestFocus() }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(PmIcons.Back, contentDescription = "Back")
        }
        Text(
            "Replies",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close")
        }
    }
    HorizontalDivider(color = scheme.outlineVariant)

    val replies = state?.items.orEmpty()
    LazyColumn(
        Modifier.weight(1f),
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Box(Modifier.parentLine(scheme.outline, show = replies.isNotEmpty() || state?.loading == true)) {
                CommentRow(
                    comment = parent,
                    vote = vm.voteOf(parent),
                    onLike = { vm.likeComment(parent) },
                    onDislike = { vm.dislikeComment(parent) },
                    onReply = { replyTo(parent) }
                )
            }
        }
        itemsIndexed(replies) { index, reply ->
            val last = index == replies.lastIndex && state?.next == null
            Box(Modifier.replyConnector(scheme.outline, last)) {
                CommentRow(
                    comment = reply,
                    vote = vm.voteOf(reply),
                    onLike = { vm.likeComment(reply) },
                    onDislike = { vm.dislikeComment(reply) },
                    onReply = { replyTo(reply) },
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
        when {
            state == null -> Unit

            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            state.error != null -> item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (state.items.isEmpty()) vm.openReplies(parent) else vm.loadMoreReplies(parent)
                        }
                        .padding(horizontal = 56.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "  Try again",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = scheme.onSurface
                    )
                }
            }

            state.next != null -> item {
                LaunchedEffect(state.next) { vm.loadMoreReplies(parent) }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    ComposerBar(
        vm = vm,
        placeholder = "Reply…",
        signInLabel = "Sign in to reply",
        value = draft,
        onValueChange = { draft = it },
        busy = vm.postingReply,
        focus = focus,
        onSend = {
            vm.postReply(parent, target, draft) {
                draft = ""
                target = parent
                scope.launch { listState.animateScrollToItem(0) }
            }
        },
        onSignIn = {
            onClose()
            vm.openLogin()
        }
    )
}

// ------------------------------------------------------------------------------ pieces

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

/** On the Replies screen: the line going down from the top comment's avatar to its first reply. */
private fun Modifier.parentLine(color: Color, show: Boolean): Modifier =
    if (!show) this else drawBehind {
        val x = 28.dp.toPx()
        drawLine(color, Offset(x, 40.dp.toPx()), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
    }

/** On the Replies screen: the line that bends into a reply's avatar, and goes on to the next reply. */
private fun Modifier.replyConnector(color: Color, last: Boolean): Modifier = drawBehind {
    val x = 28.dp.toPx()
    val centre = 22.dp.toPx()
    val radius = 12.dp.toPx()
    val path = Path().apply {
        moveTo(x, 0f)
        lineTo(x, centre - radius)
        quadraticBezierTo(x, centre, x + radius, centre)
        lineTo(52.dp.toPx(), centre)
    }
    val stroke = Stroke(width = 1.5.dp.toPx())
    drawPath(path, color, style = stroke)
    if (!last) drawLine(color, Offset(x, centre - radius), Offset(x, size.height), strokeWidth = stroke.width)
}

/** The pill pinned at the bottom: write a comment or a reply, or sign in first. */
@Composable
private fun ComposerBar(
    vm: AppViewModel,
    placeholder: String,
    signInLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onSignIn: () -> Unit,
    focus: FocusRequester? = null
) {
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
                        signInLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).padding(vertical = 12.dp)
                    )
                }
            } else {
                Row(pill.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(vm.account?.avatar, 28.dp)
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.onSurface),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                            Icon(Icons.Rounded.Send, contentDescription = "Post")
                        }
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

/** The like count to show: the printed one, moved up by the account's own like when it has just been given. */
private fun likeText(comment: CommentItem, vote: CommentVote): String? {
    val plain = comment.likes
    if (!vote.liked) return plain
    comment.likesWhenLiked?.let { return it }
    if (plain == null) return "1"
    val n = plain.replace(",", "").toLongOrNull() ?: return plain
    return (n + 1).toString()
}

/** One comment: avatar, "@name • 3mo ago", the text, then like, dislike and reply, and a menu. */
@Composable
private fun CommentRow(
    comment: CommentItem,
    vote: CommentVote,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onReply: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var open by remember(comment.id) { mutableStateOf(false) }
    var long by remember(comment.id) { mutableStateOf(false) }
    var menu by remember(comment.id) { mutableStateOf(false) }
    val count = likeText(comment, vote)
    val countStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)

    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)) {
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
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

            Row(
                Modifier.padding(top = 6.dp).offset(x = (-8).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like: the thumb fills in, drops, tilts back up and throws a few rays; the count rolls.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClickLabel = "Like") {
                            if (!vote.liked) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLike()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedThumb(
                        on = vote.liked,
                        outline = YtIcons.ThumbUp,
                        solid = YtIcons.ThumbUpSolid,
                        size = 20.dp,
                        tint = scheme.onSurface,
                        reach = 9.dp,
                        resetKey = comment.id,
                        description = "Like"
                    )
                    if (count != null) {
                        RollingCount(
                            count,
                            style = countStyle,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClickLabel = "Dislike", onClick = onDislike)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    AnimatedThumb(
                        on = vote.disliked,
                        outline = YtIcons.ThumbDown,
                        solid = YtIcons.ThumbDownSolid,
                        size = 20.dp,
                        tint = scheme.onSurface,
                        rays = false,
                        resetKey = comment.id,
                        description = "Dislike"
                    )
                }
                if (onReply != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClickLabel = "Reply", onClick = onReply)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = "Reply",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (comment.hearted) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Liked by the creator",
                        tint = HeartRed,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp)
                    )
                }
            }
        }

        Box {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = "More",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { menu = true }
                    .padding(8.dp)
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        clipboard.setText(AnnotatedString(comment.text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        menu = false
                    }
                )
            }
        }
    }
}

/** "1 reply ›" under a comment; tap to open its Replies screen. */
@Composable
private fun RepliesToggle(comment: CommentItem, onOpen: () -> Unit) {
    val label = when {
        comment.replies.isNullOrBlank() -> "Replies"
        comment.replies == "1" -> "1 reply"
        else -> "${comment.replies} replies"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onOpen)
            .padding(start = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp).size(20.dp)
        )
    }
}

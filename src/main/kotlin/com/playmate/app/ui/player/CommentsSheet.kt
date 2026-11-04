package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.CommentItem
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState

/** The comments of the video on screen, with sort, replies and (when signed in) a box to write one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { vm.loadComments() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comments", style = MaterialTheme.typography.titleMedium)
                (vm.commentsTotal ?: vm.meta?.commentCount)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (vm.commentSorts.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vm.commentSorts) { option ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (option.selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !option.selected) { vm.loadComments(token = option.token) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (option.selected) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Composer(vm, onSignIn = {
                onDismiss()
                vm.openLogin()
            })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (val state = vm.comments) {
                null, Load.Loading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }

                is Load.Failed -> ErrorState(state.message, onRetry = { vm.loadComments(force = true) })

                is Load.Ready -> LazyColumn(
                    Modifier.weight(1f).navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (state.data.isEmpty()) {
                        item {
                            Text(
                                "No comments yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                    items(state.data) { comment ->
                        Column {
                            CommentRow(comment)
                            Replies(vm, comment)
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
    }
}

@Composable
private fun Composer(vm: AppViewModel, onSignIn: () -> Unit) {
    if (!vm.signedIn) {
        TextButton(onClick = onSignIn, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Sign in to comment")
        }
        return
    }
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(vm.account?.avatar, 32.dp)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add a comment…") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        if (vm.postingComment) {
            CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
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

@Composable
private fun Avatar(url: String?, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun CommentRow(comment: CommentItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Avatar(comment.avatar, if (comment.isReply) 24.dp else 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            val head = buildString {
                if (comment.pinned) append("Pinned  ·  ")
                append(comment.author)
                if (comment.published.isNotBlank()) append("  ·  ").append(comment.published)
            }
            Text(
                head,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            comment.likes?.let {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/** The reply toggle under a comment, and its replies once opened. */
@Composable
private fun Replies(vm: AppViewModel, comment: CommentItem) {
    if (comment.repliesToken == null) return
    val state = vm.replies[comment.id]
    val open = state?.open == true
    val label = when {
        open -> "Hide replies"
        comment.replies.isNullOrBlank() -> "Replies"
        comment.replies == "1" -> "1 reply"
        else -> "${comment.replies} replies"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 64.dp, bottom = 4.dp)
            .clickable { vm.toggleReplies(comment) }
            .padding(vertical = 4.dp)
    )
    if (state != null && open) {
        state.items.forEach { reply -> CommentRow(reply, Modifier.padding(start = 40.dp)) }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            state.error != null -> Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 64.dp, bottom = 4.dp)
            )

            state.next != null -> Text(
                "Show more replies",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 64.dp, bottom = 4.dp)
                    .clickable { vm.loadMoreReplies(comment) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

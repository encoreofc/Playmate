package com.playmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.Tab
import com.playmate.app.ui.theme.glass

/**
 * The bottom bar, laid out like the YouTube app: Home, Shorts, a circled action in the middle
 * (here: Downloads) and your own picture on the right. No labels; the open tab shows as a glass pill.
 */
@Composable
fun PmBottomBar(
    tab: Tab,
    signedIn: Boolean,
    avatar: String?,
    activeDownloads: Int,
    onHome: () -> Unit,
    onShorts: () -> Unit,
    onDownloads: () -> Unit,
    onYou: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.onBackground
    val hasAvatar = signedIn && avatar != null

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.94f), ink)))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(tint.copy(alpha = 0.12f))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(label = "Home", pill = tab == Tab.Home, onClick = onHome) {
                Icon(
                    if (tab == Tab.Home) Icons.Rounded.Home else Icons.Outlined.Home,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }
            BarItem(label = "Shorts", pill = false, onClick = onShorts) {
                ShortsLogo(size = 26.dp, color = tint)
            }
            BarItem(label = "Downloads", pill = tab == Tab.Downloads, onClick = onDownloads) {
                BadgedBox(badge = { if (activeDownloads > 0) Badge { Text("$activeDownloads") } }) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .border(1.7.dp, tint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            BarItem(label = "You", pill = tab == Tab.You && !hasAvatar, onClick = onYou) {
                if (hasAvatar) {
                    val open = tab == Tab.You
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .then(if (open) Modifier.border(1.8.dp, tint, CircleShape) else Modifier)
                            .padding(if (open) 3.dp else 0.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    label: String,
    pill: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 60.dp, height = 38.dp)
                .then(if (pill) Modifier.glass(RoundedCornerShape(19.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

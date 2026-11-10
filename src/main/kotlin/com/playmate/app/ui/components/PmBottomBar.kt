package com.playmate.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.Tab
import com.playmate.app.ui.theme.LocalPmDark

// Colours measured from the reference screenshot. Change them here if you want to fine-tune.
private val BarLight = Color(0xFFF3F3F3)
private val PillLight = Color(0xFFE2E2E2)
private val ActiveLight = Color(0xFF1F1F1F)
private val IdleLight = Color(0xFF666666)

private val BarDark = Color(0xFF1E1E1E)
private val PillDark = Color(0xFF3A3A3A)
private val ActiveDark = Color(0xFFFFFFFF)
private val IdleDark = Color(0xFFB0B0B0)

/**
 * The bottom bar, copied from the reference: Home, Shorts, Music, Subs and Library.
 *
 * Sizes (dp): bar 64 high, a 56 x 32 rounded pill behind the selected icon (6 from the top),
 * 24 icon, 4 gap, 12sp label on a 16sp line, 6 left under it. Flat tinted background, no divider line.
 * The [signedIn] and [avatar] parameters are kept so existing callers still compile; the Library
 * tab now shows the library icon like the reference.
 */
@Composable
fun PmBottomBar(
    tab: Tab,
    signedIn: Boolean,
    avatar: String?,
    onHome: () -> Unit,
    onShorts: () -> Unit,
    onMusic: () -> Unit,
    onSubscriptions: () -> Unit,
    onYou: () -> Unit
) {
    val dark = LocalPmDark.current
    val barColor = if (dark) BarDark else BarLight
    val pillColor = if (dark) PillDark else PillLight
    val active = if (dark) ActiveDark else ActiveLight
    val idle = if (dark) IdleDark else IdleLight

    Row(
        Modifier
            .fillMaxWidth()
            .background(barColor)
            .navigationBarsPadding()
            .height(64.dp)
    ) {
        BarItem("Home", tab == Tab.Home, onHome, pillColor, active, idle) { c ->
            Icon(
                if (tab == Tab.Home) Icons.Filled.Home else Icons.Outlined.Home,
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(24.dp)
            )
        }
        BarItem("Shorts", false, onShorts, pillColor, active, idle) { c ->
            ShortsLogo(size = 24.dp, color = c)
        }
        BarItem("Music", tab == Tab.Music, onMusic, pillColor, active, idle) { c ->
            MusicTabIcon(size = 24.dp, color = c, filled = tab == Tab.Music)
        }
        BarItem("Subs", tab == Tab.Subscriptions, onSubscriptions, pillColor, active, idle) { c ->
            Icon(
                if (tab == Tab.Subscriptions) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(24.dp)
            )
        }
        BarItem("Library", tab == Tab.You, onYou, pillColor, active, idle) { c ->
            Icon(
                if (tab == Tab.You) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RowScope.BarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    pillColor: Color,
    activeColor: Color,
    idleColor: Color,
    icon: @Composable (Color) -> Unit
) {
    val tint = if (selected) activeColor else idleColor
    val pill by animateColorAsState(
        targetValue = if (selected) pillColor else pillColor.copy(alpha = 0f),
        animationSpec = tween(200),
        label = "pill"
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
            }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(CircleShape)
                .background(pill),
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            color = tint,
            maxLines = 1
        )
    }
}

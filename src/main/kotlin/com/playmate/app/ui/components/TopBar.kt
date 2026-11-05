package com.playmate.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top of Home, like YouTube: the logo on the left, notifications and search on the right. Nothing else. */
@Composable
fun HomeTopBar(onNotifications: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaymateMark()
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                append("Play")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("mate") }
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            ),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNotifications) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
        }
    }
}

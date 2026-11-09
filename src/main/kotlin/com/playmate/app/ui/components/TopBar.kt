package com.playmate.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Top of Home, like YouTube: the logo on the left, notifications and search on the right. Nothing else. */
@Composable
fun HomeTopBar(onNotifications: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(44.dp)
            .padding(start = 15.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaymateLogo(Modifier.weight(1f))
        IconButton(onClick = onNotifications) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
        }
    }
}

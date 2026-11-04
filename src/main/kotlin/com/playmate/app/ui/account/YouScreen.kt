package com.playmate.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoRow

@Composable
fun YouScreen(vm: AppViewModel) {
    LaunchedEffect(vm.signedIn) {
        if (vm.signedIn) vm.loadHistory()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { AccountHeader(vm) }

        if (!vm.signedIn) {
            item { SignInCard(onClick = vm::openLogin) }
        } else {
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            when (val h = vm.history) {
                is Load.Loading -> items(3) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(h.message, onRetry = vm::loadHistory) }
                is Load.Ready -> {
                    if (h.data.isEmpty()) {
                        item {
                            Text(
                                "Nothing here yet. Videos you watch show up in this list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(h.data, key = { it.url }) { video ->
                            VideoRow(video, onClick = { vm.open(video) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeader(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatar = vm.account?.avatar
        if (vm.signedIn && avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = if (vm.signedIn) vm.account?.name ?: "Signed in" else "Not signed in",
                style = MaterialTheme.typography.titleLarge
            )
            val handle = vm.account?.handle
            if (vm.signedIn && handle != null) {
                Text(
                    handle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (vm.signedIn) {
            TextButton(onClick = vm::signOut) { Text("Sign out") }
        }
    }
}

@Composable
private fun SignInCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your recommendations, watch history and subscriptions, exactly as on your YouTube account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "You sign in on Google's own page. Playmate never sees your password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onClick) { Text("Sign in") }
    }
}

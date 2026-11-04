package com.playmate.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.SaveCenter
import com.playmate.app.ui.account.LoginScreen
import com.playmate.app.ui.account.YouScreen
import com.playmate.app.ui.channel.ChannelScreen
import com.playmate.app.ui.components.MiniPlayer
import com.playmate.app.ui.downloads.DownloadsScreen
import com.playmate.app.ui.home.HomeScreen
import com.playmate.app.ui.player.PlayerScreen
import com.playmate.app.ui.search.SearchScreen

@Composable
fun AppRoot(vm: AppViewModel) {
    val playerOnScreen = vm.playing != null && vm.playerExpanded
    val context = LocalContext.current
    val activeDownloads = SaveCenter.jobs.count { it.offline && it.running }

    // Messages from actions (sign in needed, a like that failed...) show as a short toast.
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }

    BackHandler(enabled = !playerOnScreen && vm.channel != null) {
        vm.closeChannel()
    }
    BackHandler(enabled = !playerOnScreen && vm.channel == null && vm.tab != Tab.Home) {
        vm.select(Tab.Home)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    vm.playing?.let { playing ->
                        if (!vm.playerExpanded) {
                            MiniPlayer(
                                playing = playing,
                                onExpand = vm::expandPlayer,
                                onClose = vm::closePlayer
                            )
                        }
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        val colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationBarItem(
                            selected = vm.tab == Tab.Home,
                            onClick = { vm.select(Tab.Home) },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            label = { Text("Home") },
                            colors = colors
                        )
                        NavigationBarItem(
                            selected = vm.tab == Tab.Search,
                            onClick = { vm.select(Tab.Search) },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            label = { Text("Search") },
                            colors = colors
                        )
                        NavigationBarItem(
                            selected = vm.tab == Tab.Downloads,
                            onClick = { vm.select(Tab.Downloads) },
                            icon = {
                                BadgedBox(badge = { if (activeDownloads > 0) Badge { Text("$activeDownloads") } }) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                }
                            },
                            label = { Text("Downloads") },
                            colors = colors
                        )
                        NavigationBarItem(
                            selected = vm.tab == Tab.You,
                            onClick = { vm.select(Tab.You) },
                            icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                            label = { Text("You") },
                            colors = colors
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                if (vm.channel != null) {
                    ChannelScreen(vm)
                } else {
                    when (vm.tab) {
                        Tab.Home -> HomeScreen(vm)
                        Tab.Search -> SearchScreen(vm)
                        Tab.Downloads -> DownloadsScreen(vm)
                        Tab.You -> YouScreen(vm)
                    }
                }
            }
        }

        // The player stays composed while minimized (zero size), so playback never restarts.
        vm.playing?.let { playing ->
            Box(if (vm.playerExpanded) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                PlayerScreen(
                    playing = playing,
                    expanded = vm.playerExpanded,
                    onClose = vm::minimizePlayer,
                    onOpen = vm::open,
                    onRetry = vm::retryPlayer,
                    vm = vm
                )
            }
        }

        if (vm.showLogin) {
            LoginScreen(onCookies = vm::onSignedIn, onCancel = vm::cancelLogin)
        }
    }
}

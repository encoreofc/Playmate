package com.playmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.ui.home.HomeScreen
import com.playmate.app.ui.player.PlayerScreen
import com.playmate.app.ui.search.SearchScreen

@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.playing == null && vm.tab != Tab.Home) {
        vm.select(Tab.Home)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
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
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (vm.tab) {
                    Tab.Home -> HomeScreen(vm)
                    Tab.Search -> SearchScreen(vm)
                }
            }
        }

        vm.playing?.let { playing ->
            PlayerScreen(
                playing = playing,
                onClose = vm::closePlayer,
                onOpen = vm::open,
                onRetry = vm::retryPlayer
            )
        }
    }
}

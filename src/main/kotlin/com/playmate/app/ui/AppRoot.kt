package com.playmate.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.ui.account.LoginScreen
import com.playmate.app.ui.account.YouScreen
import com.playmate.app.ui.channel.ChannelScreen
import com.playmate.app.ui.components.MiniPlayer
import com.playmate.app.ui.components.PmBottomBar
import com.playmate.app.ui.downloads.DownloadsScreen
import com.playmate.app.ui.home.HomeScreen
import com.playmate.app.ui.player.PlayerScreen
import com.playmate.app.ui.search.SearchScreen
import com.playmate.app.ui.shorts.ShortsScreen
import com.playmate.app.ui.subscriptions.SubscriptionsScreen
import com.playmate.app.ui.components.BatteryPrompt

@Composable
fun AppRoot(vm: AppViewModel) {
    val playerOnScreen = vm.playing != null && vm.playerExpanded
    val context = LocalContext.current

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
            // Home pads for the status bar itself; letting Scaffold add it too doubled the gap at the top.
            // Other screens keep exactly what they had.
            contentWindowInsets = if (vm.tab == Tab.Home && vm.channel == null) WindowInsets(0, 0, 0, 0)
            else ScaffoldDefaults.contentWindowInsets,
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
                    PmBottomBar(
                        tab = vm.tab,
                        signedIn = vm.signedIn,
                        avatar = vm.account?.avatar,
                        onHome = { vm.select(Tab.Home) },
                        onShorts = {
                            val shorts = (vm.feed as? Load.Ready)?.data?.filter { it.isShort }.orEmpty()
                            if (shorts.isEmpty()) {
                                Toast.makeText(context, "Shorts are loading, try again in a moment", Toast.LENGTH_SHORT).show()
                            } else {
                                vm.openShorts(shorts, 0)
                            }
                        },
                        onSubscriptions = { vm.select(Tab.Subscriptions) },
                        onYou = { vm.select(Tab.You) }
                    )
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
                        Tab.Subscriptions -> SubscriptionsScreen(vm)
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

        // Full-screen Shorts (above everything except the sign-in page).
        vm.shorts?.let { launch -> ShortsScreen(launch, vm) }

        if (vm.showLogin) {
            LoginScreen(onCookies = vm::onSignedIn, onCancel = vm::cancelLogin)
        }

        // Asks once to run without battery limits, so the phone never stops the sound.
        BatteryPrompt(videoOpen = vm.playing != null)
    }
}

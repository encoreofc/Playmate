package com.playmate.app.ui.subscriptions

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.AppViewModel
import com.playmate.app.R
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.HomeTopBar
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard
import com.playmate.app.ui.components.YtSignInButton
import com.playmate.app.ui.theme.LocalPmDark

/** The Subscriptions tab: the newest videos from the channels the account follows, in the same cards as Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(vm: AppViewModel) {
    val context = LocalContext.current

    LaunchedEffect(vm.signedIn) { if (vm.signedIn) vm.loadSubs() }

    if (!vm.signedIn) {
        SignedOutSubscriptions(
            onSignIn = vm::openLogin,
            onNotifications = {
                Toast.makeText(context, "Notifications are coming soon", Toast.LENGTH_SHORT).show()
            },
            onSearch = { vm.select(Tab.Search); vm.startEditing() }
        )
        return
    }

    PullToRefreshBox(
        isRefreshing = vm.subsRefreshing,
        onRefresh = { vm.loadSubs(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                HomeTopBar(
                    onNotifications = {
                        Toast.makeText(context, "Notifications are coming soon", Toast.LENGTH_SHORT).show()
                    },
                    onSearch = { vm.select(Tab.Search); vm.startEditing() }
                )
            }

            when (val feed = vm.subsFeed) {
                is Load.Loading -> items(4) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(feed.message, onRetry = { vm.loadSubs(force = true) }) }
                is Load.Ready -> {
                    items(feed.data, key = { it.url }) { video ->
                        VideoCard(video, onClick = { vm.open(video) })
                    }
                    if (vm.subsToken != null) {
                        item(key = "more") {
                            LaunchedEffect(vm.subsToken) { vm.loadMoreSubs() }
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Subscriptions with nobody signed in, laid out like YouTube's own page: the soft shadow under the
 * bar, the parachute picture, "Don't miss new videos", one grey line and the red Sign in button.
 * Sizes and spacing are measured from YouTube's screenshots.
 */
@Composable
private fun SignedOutSubscriptions(
    onSignIn: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: () -> Unit
) {
    val dark = LocalPmDark.current
    val grey = if (dark) Color(0xFFAAAAAA) else Color(0xFF909090)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) {
            HomeTopBar(onNotifications = onNotifications, onSearch = onSearch)
            if (!dark) {
                // The faint shadow YouTube casts under its bar on this page.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = 12.dp)
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.14f),
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Image(
            painter = painterResource(R.drawable.ic_subs_signed_out),
            contentDescription = null,
            modifier = Modifier.size(260.dp, 215.dp).alpha(if (dark) 0.5f else 1f)
        )
        Spacer(Modifier.height(17.5.dp))
        Text(
            "Don’t miss new videos",
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Sign in to see updates from your favorite\nYouTube channels",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = grey,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(13.dp))
        YtSignInButton(onClick = onSignIn, width = 112.dp, height = 41.dp, textSize = 16.sp)
        Spacer(Modifier.height(16.dp))
    }
}

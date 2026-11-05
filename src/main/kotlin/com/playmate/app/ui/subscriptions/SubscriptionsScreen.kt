package com.playmate.app.ui.subscriptions

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.HomeTopBar
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard

/** The Subscriptions tab: the newest videos from the channels the account follows, in the same cards as Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(vm: AppViewModel) {
    val context = LocalContext.current

    LaunchedEffect(vm.signedIn) { if (vm.signedIn) vm.loadSubs() }

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

            if (!vm.signedIn) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 80.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Don't miss new videos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Sign in to see updates from your favourite channels",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )
                        Button(onClick = vm::openLogin) { Text("Sign in") }
                    }
                }
            } else {
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
                                    contentAlignment = androidx.compose.ui.Alignment.Center
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
}

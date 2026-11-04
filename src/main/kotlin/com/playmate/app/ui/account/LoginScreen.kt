package com.playmate.app.ui.account

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&hl=en" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
        "%26app%3Ddesktop%26hl%3Den%26next%3Dhttps%253A%252F%252Fwww.youtube.com%252F"

// A plain Chrome-for-Android identity: Google refuses sign-in inside a "wv" web view.
private const val LOGIN_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Mobile Safari/537.36"

/** Google's own sign-in page. Playmate only keeps the session cookies, never the password. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onCookies: (String) -> Unit, onCancel: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var finished by remember { mutableStateOf(false) }
    val latestDone by rememberUpdatedState(onCookies)

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
            Column {
                Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium)
                Text(
                    "You sign in on Google's own page. Playmate never sees your password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.removeAllCookies(null)
                    cookies.setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = LOGIN_UA

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (finished || view == null || url == null) return
                            val host = Uri.parse(url).host.orEmpty()
                            if (!host.endsWith("youtube.com")) return
                            val now = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
                            if (!now.contains("SAPISID=")) return
                            finished = true
                            // Give the sign-in hand-off a moment to set its last cookies.
                            view.postDelayed({
                                val all = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
                                CookieManager.getInstance().flush()
                                latestDone(all.ifBlank { now })
                            }, 800)
                        }
                    }
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            }
        )
    }
}

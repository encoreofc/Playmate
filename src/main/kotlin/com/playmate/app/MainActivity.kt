package com.playmate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.playmate.app.data.ThemePrefs
import com.playmate.app.ui.AppRoot
import com.playmate.app.ui.splash.PlaymateSplash
import com.playmate.app.ui.theme.PlaymateTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: it swaps the launch theme for the normal one.
        val splash = installSplashScreen()
        // The opening animation plays on a fresh start only, not when the system restores the screen.
        val playIntro = savedInstanceState == null
        splash.setOnExitAnimationListener { provider ->
            if (playIntro) {
                // The intro already shows the same logo in the same place, so the hand-over is a straight swap.
                provider.remove()
            } else {
                provider.view.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction { provider.remove() }
                    .start()
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemePrefs.load(this)
        setContent {
            PlaymateTheme {
                var introDone by remember { mutableStateOf(!playIntro) }
                // propagateMinConstraints keeps the app root measured exactly as before: full window, tight.
                Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
                    AppRoot(vm)
                    if (!introDone) PlaymateSplash(onFinished = { introDone = true })
                }
            }
        }
        if (savedInstanceState == null) handle(intent)
        askNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            if (vm.playing != null) vm.minimizePlayer()
            vm.select(Tab.Downloads)
            return
        }
        val link = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Regex("https?://\\S+").find(it)?.value }
            else -> null
        }
        if (!link.isNullOrBlank()) vm.openUrl(link)
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.playmate.app.OPEN_DOWNLOADS"
    }
}

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
import androidx.core.content.ContextCompat
import com.playmate.app.ui.AppRoot
import com.playmate.app.ui.theme.PlaymateTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PlaymateTheme {
                AppRoot(vm)
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

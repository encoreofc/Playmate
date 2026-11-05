package com.playmate.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.playmate.app.data.BatteryHelper
import kotlinx.coroutines.delay

/**
 * Asks once, a few seconds after the first video starts, to let the app run without battery limits,
 * so that the phone does not stop the sound in the background.
 */
@Composable
fun BatteryPrompt(videoOpen: Boolean) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(videoOpen) {
        if (videoOpen && BatteryHelper.shouldAsk(context)) {
            delay(4_000)
            if (BatteryHelper.shouldAsk(context)) show = true
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = {
                BatteryHelper.markAsked(context)
                show = false
            },
            title = { Text("Keep the sound going") },
            text = {
                Text(
                    "Some phones stop apps in the background to save battery, and the video or sound " +
                        "can stop. Allow Playmate to run without battery limits so playback never cuts out."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    BatteryHelper.markAsked(context)
                    show = false
                    BatteryHelper.openSettings(context)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    BatteryHelper.markAsked(context)
                    show = false
                }) { Text("Not now") }
            }
        )
    }
}

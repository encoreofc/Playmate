package com.playmate.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.playmate.app.data.ThemeMode
import com.playmate.app.data.ThemePrefs

/** "Appearance": use the phone's theme, or force dark or light. The choice applies at once and is remembered. */
@Composable
fun ThemeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.75.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(vertical = 20.dp)
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            ThemeRow("Use device theme", ThemeMode.System) { ThemePrefs.set(context, it); onDismiss() }
            ThemeRow("Dark theme", ThemeMode.Dark) { ThemePrefs.set(context, it); onDismiss() }
            ThemeRow("Light theme", ThemeMode.Light) { ThemePrefs.set(context, it); onDismiss() }
        }
    }
}

@Composable
private fun ThemeRow(label: String, mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(mode) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = ThemePrefs.mode == mode,
            onClick = { onPick(mode) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

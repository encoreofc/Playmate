package com.playmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The red of YouTube's own "Sign in" button, measured from its screenshots (#F31E20). */
private val SignInRed = Color(0xFFF31E20)

/** Top to bottom colours of the thin outline round the sign-in bar: blue, magenta, orange. */
private val PillOutline = listOf(Color(0xFF3E69D1), Color(0xFFB92555), Color(0xFFD55224))

/** The plain red "Sign in" button: square-ish corners, bold white text. */
@Composable
fun YtSignInButton(
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(2.dp))
            .background(SignInRed)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Sign in",
            color = Color.White,
            fontSize = textSize,
            lineHeight = textSize * 1.25f,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * The rounded sign-in bar YouTube shows when nobody is signed in: a short line on the left, the
 * red "Sign in" button on the right, all inside a thin blue-to-orange outline. Used under the
 * Home chips and at the bottom of the Library page.
 */
@Composable
fun SignInPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.background, shape)
            .border(1.dp, Brush.verticalGradient(PillOutline), shape)
            .padding(horizontal = 10.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        YtSignInButton(onClick = onClick, width = 91.dp, height = 31.dp, textSize = 14.sp)
    }
}

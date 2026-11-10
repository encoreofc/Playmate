package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.indication
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.playmate.app.data.CommentItem
import com.playmate.app.ui.components.agoText
import com.playmate.app.ui.components.formatCount
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PlaybackPrefs
import com.playmate.app.data.Reaction
import com.playmate.app.data.SaveState
import com.playmate.app.data.WatchMeta
import com.playmate.app.data.activeDownload
import com.playmate.app.ui.components.AnimatedThumb
import com.playmate.app.ui.components.RollingCount
import com.playmate.app.ui.components.SubscribeButton
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.splash.LogoGeometry
import com.playmate.app.ui.theme.Tonal
import com.playmate.app.ui.theme.tonalPress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Title and the quiet line under it (views and the upload age, like "1 week ago"), followed by a
 * bold "...more". Tapping the title or the line opens the description panel under the player (see
 * [DescriptionPanel]); [onOpen] is null while the video is loading, and then there is no "...more".
 *
 * The title is one line that ends in an ellipsis. The like / dislike buttons no longer sit on the
 * quiet line: they are the first buttons of the scrolling row under the channel (see [ActionRow]).
 * The channel name is not printed in the quiet line: it lives in the channel row below.
 */
@Composable
fun TitleBlock(
    title: String,
    channel: String,
    views: Long,
    uploaded: String?,
    onOpen: (() -> Unit)?,
    description: String = ""
) {
    val viewsText = formatCount(views)?.let { "$it views" }
    val ago = agoText(uploaded)

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpen != null, onClick = { onOpen?.invoke() })
            .padding(horizontal = px720(24))
            .padding(top = px720(22))
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = pxSp720(40),
                lineHeight = pxSp720(52),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(px720(3)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(viewsText, ago).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = pxSp720(26),
                    lineHeight = pxSp720(32)
                ),
                color = quietText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onOpen != null) {
                Spacer(Modifier.width(px720(8)))
                Text(
                    "...more",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pxSp720(26),
                        lineHeight = pxSp720(32),
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Channel avatar, name, subscribers and the subscribe button. Tapping the channel opens it.
 * [badge] puts the channel's badge after the name (the round seal for a verified channel, the
 * music note for an official artist channel); [ChannelBadge.None] shows nothing.
 */
@Composable
fun ChannelRow(
    vm: AppViewModel,
    info: PlayableInfo?,
    meta: WatchMeta?,
    seedChannel: String,
    badge: ChannelBadge = ChannelBadge.None
) {
    val channelId = meta?.channelId ?: info?.channelId
    val name = meta?.channelName ?: info?.channel?.takeIf { it.isNotBlank() } ?: seedChannel
    val avatar = meta?.channelAvatar ?: info?.channelAvatar
    val subscribed = channelId?.let { vm.subscriptions[it] } ?: meta?.subscribed ?: false

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = px720(24))
            .padding(top = px720(50), bottom = px720(44)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(px720(24)))
                .clickable(enabled = channelId != null) { vm.openChannel(channelId, name, avatar) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(px720(88))
                    .clip(CircleShape)
                    .background(Tonal.fill())
            )
            Column(Modifier.weight(1f).padding(start = px720(24), end = px720(24))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = pxSp720(33),
                            lineHeight = pxSp720(36),
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badge != ChannelBadge.None) {
                        Spacer(Modifier.width(px(16)))
                        BadgeMark(badge)
                    }
                }
                meta?.subscribers?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = pxSp720(25),
                            lineHeight = pxSp720(30)
                        ),
                        color = quietText(),
                        maxLines = 1
                    )
                }
            }
        }
        SubscribeButton(subscribed = subscribed, onClick = { vm.toggleSubscribe(channelId, subscribed) })
    }
}

/** The badge a channel carries next to its name. */
enum class ChannelBadge {
    None, Verified, Artist;

    companion object {
        /**
         * Reads a badge from the style text YouTube sends with a channel (for example
         * BADGE_STYLE_TYPE_VERIFIED or BADGE_STYLE_TYPE_VERIFIED_ARTIST). When a channel carries
         * several, the artist one wins. Anything it does not know shows as a verified seal, since
         * a badge was sent at all.
         */
        fun fromStyles(styles: List<String?>): ChannelBadge {
            val clean = styles.filterNotNull().filter { it.isNotBlank() }
            if (clean.isEmpty()) return None
            return if (clean.any { it.contains("ARTIST", ignoreCase = true) }) Artist else Verified
        }
    }
}

/** The mark itself: a wavy-edged seal with a tick, or a round disc with a music note. */
@Composable
private fun BadgeMark(badge: ChannelBadge) {
    val scheme = MaterialTheme.colorScheme
    val size = px(28)
    when (badge) {
        ChannelBadge.None -> Unit
        ChannelBadge.Verified -> {
            val seal = scheme.onSurface
            val mark = scheme.surface
            Canvas(
                Modifier
                    .size(size)
                    .semantics { contentDescription = "Verified" }
            ) {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val r = this.size.width / 2f
                val path = Path()
                val steps = 160
                for (i in 0..steps) {
                    val a = (2.0 * PI * i / steps).toFloat()
                    val rr = r * (0.92f + 0.08f * cos(8f * a))
                    val x = c.x + rr * cos(a)
                    val y = c.y + rr * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, seal)
                val tick = Path().apply {
                    moveTo(c.x - r * 0.34f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.08f, c.y + r * 0.28f)
                    lineTo(c.x + r * 0.38f, c.y - r * 0.24f)
                }
                drawPath(
                    tick,
                    mark,
                    style = Stroke(
                        width = r * 0.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        ChannelBadge.Artist -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(scheme.onSurfaceVariant)
                .semantics { contentDescription = "Official artist channel" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                ActionIcons.Note,
                contentDescription = null,
                tint = scheme.surface,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Every size from here down is measured off a reference screenshot and then scaled to the width of
 * the phone it runs on, so the title, the buttons and the cards keep exactly the same proportions
 * on every screen. To make the whole look a little bigger or smaller, change [PX_SCALE].
 *
 * There are two rulers. [px] / [pxSp] count in a phone 852 pixels wide (the older measurements:
 * the badge, the Up next header, the Autoplay switch). [px720] / [pxSp720] count in a phone 720
 * pixels wide (the current watch screen: title, channel row, the row of buttons, the comments
 * card). Both end up as the same share of the real screen width.
 */
private const val REF_WIDTH = 852f
private const val SHOT_WIDTH = 720f
private const val PX_SCALE = 1f

@Composable
private fun px(v: Int): Dp {
    val config = LocalConfiguration.current
    val width = minOf(config.screenWidthDp, config.screenHeightDp)
    return (v * PX_SCALE * width / REF_WIDTH).dp
}

@Composable
private fun pxSp(v: Int): TextUnit = with(LocalDensity.current) { px(v).toSp() }

/** Same as [px], but the number counts in a phone 720 pixels wide. */
@Composable
private fun px720(v: Int): Dp {
    val config = LocalConfiguration.current
    val width = minOf(config.screenWidthDp, config.screenHeightDp)
    return (v * PX_SCALE * width / SHOT_WIDTH).dp
}

@Composable
private fun pxSp720(v: Int): TextUnit = with(LocalDensity.current) { px720(v).toSp() }

/** The light green of the Autoplay switch. (The Play Audio glyph, when it is on, is the logo yellow.) */
private val AccentGreen = Color(0xFF98D89A)

/**
 * The dark-theme colours of the watch screen, measured off the reference screenshot. The screen
 * is a dark grey, not pure black: [Background] is the value the dark theme's background (and
 * surface) should have, so that the cards and the pill below sit on it the way they do in the
 * reference (nearly melting into it).
 */
object WatchColors {
    /** The screen behind everything: dark grey, not pure black. */
    val Background = Color(0xFF151515)

    /** The Comments card, the buttons of the row and the like / dislike pill. */
    val Card = Color(0xFF232324)

    /** The thin line under the row of buttons. */
    val Line = Color(0xFF242424)

    /** The thin line between the like and the dislike half of the pill. */
    val Divider = Color(0xFF3C3C3C)

    /** The like count inside the pill: a light grey, not white. */
    val Count = Color(0xFFE8E8E8)

    /** The views line and the subscribers line: a clearly brighter grey than the app had. */
    val Quiet = Color(0xFFDADADA)
}

/** True while the theme is a dark one (the reference colours above are for the dark theme only). */
@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Fill of the Comments card, of the buttons of the row and of the like / dislike pill. */
@Composable
private fun cardFill(): Color = if (isDarkTheme()) WatchColors.Card else Tonal.fill()

/** The thin line under the row of buttons. */
@Composable
private fun rowLine(): Color = if (isDarkTheme()) WatchColors.Line else Tonal.hairline()

/** The thin line between the like and the dislike half of the pill. */
@Composable
private fun pillDivider(): Color = if (isDarkTheme()) WatchColors.Divider else Tonal.hairline()

/** The like count inside the pill. */
@Composable
private fun countColor(): Color = if (isDarkTheme()) WatchColors.Count else LocalContentColor.current

/** The quiet grey of the views line and the subscribers line. */
@Composable
private fun quietText(): Color =
    if (isDarkTheme()) WatchColors.Quiet else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * The icons of the row under the video: thin round-ended outlines on a 24 grid, drawn at three
 * screen pixels to the grid unit (the glyph box is 72 pixels), so each lands at the size it has
 * in the reference.
 */
private object ActionIcons {
    private fun thin(name: String, width: Float, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private const val LINE = 1.1f

    val Save: ImageVector by lazy {
        thin(
            "ThinSave", LINE,
            "M7.03 18.38V7.42A1.1 1.1 0 0 1 8.12 6.32H16.12A1.1 1.1 0 0 1 17.23 7.42V18.38L12.12 14.7Z"
        )
    }
    val Popup: ImageVector by lazy {
        thin(
            "ThinPopup", LINE,
            "M11.93 7.83H7.93A1.3 1.3 0 0 0 6.62 9.12V16.15A1.3 1.3 0 0 0 7.93 17.45H16.18A1.3 1.3 0 0 0 17.48 16.15V13.07",
            "M13.9 10.83L18.15 6.47",
            "M14.2 6.47H18.15V10.38"
        )
    }
    val Headphones: ImageVector by lazy {
        thin(
            "ThinHeadphones", LINE,
            "M5.23 13.7V12.05A6.9 6.9 0 0 1 19.02 12.05V13.7",
            "M4.83 14.22A0.95 0.95 0 0 1 5.78 13.28H7.03A0.95 0.95 0 0 1 7.98 14.22V17.35A0.95 0.95 0 0 1 7.03 18.3H5.78A0.95 0.95 0 0 1 4.83 17.35Z",
            "M16.28 14.22A0.95 0.95 0 0 1 17.23 13.28H18.48A0.95 0.95 0 0 1 19.43 14.22V17.35A0.95 0.95 0 0 1 18.48 18.3H17.23A0.95 0.95 0 0 1 16.28 17.35Z"
        )
    }
    val Download: ImageVector by lazy {
        thin("ThinDownload", LINE, "M12.12 6.17V15.55", "M8.78 12.15L12.12 15.55L15.47 12.15", "M6.95 18.3H17.55")
    }
    val DownloadDone: ImageVector by lazy {
        thin("ThinDownloadDone", LINE, "M8.8 12L11.9 15.1L16.3 10.5", "M6.95 18.3H17.55")
    }
    val Phone: ImageVector by lazy {
        thin(
            "ThinPhone", LINE,
            "M7.5 5.85A1.25 1.25 0 0 1 8.75 4.6H15.75A1.25 1.25 0 0 1 17 5.85V18.35A1.25 1.25 0 0 1 15.75 19.6H8.75A1.25 1.25 0 0 1 7.5 18.35Z",
            "M10.25 6.53H14.25",
            "M12.25 5.75V6.53",
            "M9.03 17.35H10.25",
            "M14.25 17.35H15.48",
            "M12.25 16.7L12.85 17.35L12.25 18L11.65 17.35Z"
        )
    }

    /** Share: three small rings joined by two lines (one on the left, two on the right). */
    val Share: ImageVector by lazy {
        thin(
            "ThinShare", LINE,
            "M6.4 12 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0 Z",
            "M14.4 7.7 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0 Z",
            "M14.4 16.3 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0 Z",
            "M9.41 11.24 L14.59 8.46",
            "M9.41 12.76 L14.59 15.54"
        )
    }
    val Note: ImageVector by lazy {
        thin(
            "ThinNote", 1.9f,
            "M11.6 15.9a1.9 1.9 0 1 1-3.8 0 1.9 1.9 0 0 1 3.8 0z",
            "M11.6 15.9V7.6",
            "M11.6 7.6c1.7.1 3.2.9 3.4 2.9"
        )
    }
    val Check: ImageVector by lazy {
        thin("ThinCheck", 2.6f, "M7.4 12.3l3.1 3.1 6.1-6.3")
    }

    /**
     * The like and dislike thumbs, traced off the reference: a hand outline with a separate open
     * cuff. Drawn on a 44-unit grid (one unit is one reference pixel, the glyph box of the pill).
     * The solid ones (switched on) fill the hand and close the cuff.
     */
    private fun thumb(name: String, hand: String, cuff: String, solid: Boolean): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 44.dp,
            defaultHeight = 44.dp,
            viewportWidth = 44f,
            viewportHeight = 44f
        )
        listOf(hand, if (solid) cuff + "Z" else cuff).forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (solid) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private const val UP_HAND = "M16.7 38.47A2.73 2.73 0 0 1 13.95 35.72L14.07 19.38A1.48 1.48 0 0 1 14.41 18.44L23.77 7.13A2.19 2.19 0 0 1 27.62 8.88L25.84 19.66A0.05 0.05 0 0 0 25.89 19.72L35.34 19.62A3.61 3.61 0 0 1 38.88 24.12L35.75 36.38A2.58 2.58 0 0 1 33.27 38.32Z"
    private const val UP_CUFF = "M9.65 19.9L7.87 19.9A1.77 1.77 0 0 0 6.1 21.68L6.15 37.25A1.33 1.33 0 0 0 7.66 38.56L10.27 38.2"
    private const val DOWN_HAND = "M29.3 6.53A2.73 2.73 0 0 1 32.05 9.28L31.93 25.62A1.48 1.48 0 0 1 31.59 26.56L22.23 37.87A2.19 2.19 0 0 1 18.38 36.12L20.16 25.34A0.05 0.05 0 0 0 20.11 25.28L10.66 25.38A3.61 3.61 0 0 1 7.12 20.88L10.25 8.62A2.58 2.58 0 0 1 12.73 6.68Z"
    private const val DOWN_CUFF = "M36.35 25.1L38.13 25.1A1.77 1.77 0 0 0 39.9 23.32L39.85 7.75A1.33 1.33 0 0 0 38.34 6.44L35.73 6.8"

    val ThumbUp: ImageVector by lazy { thumb("ThinThumbUp", UP_HAND, UP_CUFF, false) }
    val ThumbUpSolid: ImageVector by lazy { thumb("ThinThumbUpSolid", UP_HAND, UP_CUFF, true) }
    val ThumbDown: ImageVector by lazy { thumb("ThinThumbDown", DOWN_HAND, DOWN_CUFF, false) }
    val ThumbDownSolid: ImageVector by lazy { thumb("ThinThumbDownSolid", DOWN_HAND, DOWN_CUFF, true) }
}

/**
 * One button of the row under the channel: a round-ended chip, 79 pixels high, with a thin glyph
 * at the left and its name printed at the right, on the same grey as the Comments card. The row
 * scrolls sideways, so a chip is as wide as its name needs. Pressed, a soft patch fades in over
 * the chip and the glyph shrinks a little. [label] is what a screen reader says; [text] is what
 * is printed. [lit] is the switched-on look: the glyph takes the colour [litColor] and the chip
 * fills softly with that colour, and pressing sends a ripple of that colour across the chip;
 * [state] is what a screen reader says about a toggle.
 */
@Composable
private fun RowScope.ActionTile(
    label: String,
    text: String,
    lit: Boolean = false,
    litColor: Color? = null,
    state: String? = null,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // The patch over the chip. A plain button gets the grey press patch. A toggle that has a
    // [litColor] (Play Audio) works like the headphones button in the corner of the player: a ripple
    // of that colour spreads from the finger, the chip fills while it is pressed, and while the
    // toggle is on the chip stays softly filled behind the glyph.
    val patchTarget = if (litColor != null) {
        when {
            pressed -> litColor.copy(alpha = 0.30f)
            lit -> litColor.copy(alpha = 0.20f)
            else -> litColor.copy(alpha = 0f)
        }
    } else if (pressed) {
        Tonal.fillStrong()
    } else {
        Tonal.fillStrong().copy(alpha = 0f)
    }
    val patch by animateColorAsState(
        targetValue = patchTarget,
        animationSpec = tween(120),
        label = "tilePatch"
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "tileSqueeze"
    )
    val tint by animateColorAsState(
        targetValue = if (lit && litColor != null) litColor else scheme.onSurface,
        animationSpec = tween(180),
        label = "tileTint"
    )

    Row(
        Modifier
            .height(px720(79))
            .clip(CircleShape)
            .background(cardFill())
            .background(patch)
            .then(
                if (litColor != null) Modifier.indication(source, ripple(color = litColor))
                else Modifier
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
            }
            .padding(start = px720(15), end = px720(29)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(px720(61))
                .graphicsLayer {
                    scaleX = squeeze
                    scaleY = squeeze
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides tint) { glyph() }
        }
        Text(
            text,
            style = TextStyle(
                fontSize = pxSp720(28),
                lineHeight = pxSp720(34),
                fontWeight = FontWeight.Medium
            ),
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * A glyph in the 61-pixel box of a chip. [size] is the glyph's own size inside that box (it is
 * centred, so the name next to it does not move).
 */
@Composable
private fun SlotGlyph(icon: ImageVector, size: Dp? = null) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(size ?: px720(61)))
}

/**
 * Like and dislike as two grey halves with a thin gap between them, the first chips of the row
 * under the channel. The like half is round at the left and nearly square at the right, the
 * dislike half the other way round. Liked / disliked shows the usual way: the thumb is an outline
 * when off and a solid shape when on, and the like thumb throws a few rays and the count rolls
 * when it is turned on. [dislikeCount] is printed next to the dislike thumb when there is one;
 * without it the half is just the thumb.
 */
@Composable
private fun ReactionPill(
    reaction: Reaction,
    likeCount: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    dislikeCount: String? = null
) {
    val liked = reaction == Reaction.Liked
    val disliked = reaction == Reaction.Disliked
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val glyph = px720(46)
    val full = px720(79) / 2
    val edge = px720(10)
    val likeShape = RoundedCornerShape(topStart = full, bottomStart = full, topEnd = edge, bottomEnd = edge)
    val dislikeShape = RoundedCornerShape(topStart = edge, bottomStart = edge, topEnd = full, bottomEnd = full)
    val countStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = pxSp720(28),
        lineHeight = pxSp720(34),
        fontWeight = FontWeight.Medium
    )

    Row(
        Modifier.height(px720(79)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clip(likeShape)
                .background(cardFill())
                .tonalPress(
                    shape = likeShape,
                    color = Color.Transparent,
                    onClick = {
                        if (!liked) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLike()
                    }
                )
                .padding(
                    start = px720(22),
                    end = if (likeCount != null) px720(29) else px720(22)
                )
                .semantics {
                    contentDescription = "Like"
                    if (liked) stateDescription = "Liked"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = liked,
                    outline = ActionIcons.ThumbUp,
                    solid = ActionIcons.ThumbUpSolid,
                    size = glyph,
                    reach = px720(14)
                )
                likeCount?.let {
                    RollingCount(
                        it,
                        style = countStyle,
                        color = countColor(),
                        modifier = Modifier.padding(start = px720(8))
                    )
                }
            }
        }
        Spacer(Modifier.width(px720(4)))
        Row(
            Modifier
                .fillMaxHeight()
                .clip(dislikeShape)
                .background(cardFill())
                .tonalPress(
                    shape = dislikeShape,
                    color = Color.Transparent,
                    onClick = onDislike
                )
                .padding(
                    start = px720(22),
                    end = if (dislikeCount != null) px720(30) else px720(22)
                )
                .semantics {
                    contentDescription = "Dislike"
                    if (disliked) stateDescription = "Disliked"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = disliked,
                    outline = ActionIcons.ThumbDown,
                    solid = ActionIcons.ThumbDownSolid,
                    size = glyph,
                    rays = false
                )
                dislikeCount?.let {
                    RollingCount(
                        it,
                        style = countStyle,
                        color = countColor(),
                        modifier = Modifier.padding(start = px720(8))
                    )
                }
            }
        }
    }
}

/** Download: a ring with the percent while it downloads, a tick once it is saved in the app. */
@Composable
private fun RowScope.DownloadTile(info: PlayableInfo?, onClick: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val active = info?.let { activeDownload(it.url) }
    LaunchedEffect(active) {
        while (active != null) {
            delay(500)
            tick++
        }
    }
    val percent = remember(tick, active) { active?.percent ?: 0 }
    val saved = info?.let { OfflineStore.forUrl(it.url).isNotEmpty() } == true
    val label = when {
        active != null -> if (active.state == SaveState.Queued) "Download waiting" else "Downloading $percent%"
        saved -> "Downloaded"
        else -> "Download"
    }
    val text = when {
        active != null -> if (active.state == SaveState.Queued) "Waiting" else "$percent%"
        saved -> "Downloaded"
        else -> "Download"
    }
    ActionTile(label = label, text = text, onClick = onClick) {
        when {
            active != null -> CircularProgressIndicator(
                progress = { percent / 100f },
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = 0.25f),
                strokeWidth = px720(3),
                modifier = Modifier.size(px720(34))
            )
            saved -> SlotGlyph(ActionIcons.DownloadDone)
            else -> SlotGlyph(ActionIcons.Download)
        }
    }
}

/**
 * The row under the channel: one line of grey chips that scrolls sideways and is cut at the page
 * margin on both sides. In this order: the like / dislike pair, Share, Save, Popup, Play Audio,
 * Download, Save to phone. Share comes first after the pair because it is the thing people reach
 * for right after a reaction; the rest keep the order the app already had. (Comments sit right
 * below this row.)
 *
 * Each chip is a thin glyph with its name beside it. Play Audio is the one toggle (it is the
 * background-play switch): it looks like the others until it is switched on, then its glyph turns
 * yellow on a soft yellow chip. Popup is only drawn for now: it does nothing yet. The like /
 * dislike pair is hidden while the video is still loading.
 */
@Composable
fun ActionRow(
    vm: AppViewModel,
    meta: WatchMeta?,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val info = (vm.playing?.info as? Load.Ready)?.data
    var phoneOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var background by remember { mutableStateOf(PlaybackPrefs.background(context)) }

    // 0 = closed, 1 = pick a quality to download, 2 = this video's download status
    var downloadSheet by remember { mutableIntStateOf(0) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = px720(24))
                .padding(bottom = px720(25))
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(px720(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (info != null) {
                ReactionPill(
                    reaction = vm.meta?.reaction ?: Reaction.None,
                    likeCount = vm.meta?.likeCount,
                    onLike = { vm.react(Reaction.Liked) },
                    onDislike = { vm.react(Reaction.Disliked) }
                )
            }
            ActionTile(label = "Share", text = "Share", onClick = onShare) {
                SlotGlyph(ActionIcons.Share)
            }
            ActionTile(label = "Save", text = "Save", onClick = onSave) {
                SlotGlyph(ActionIcons.Save)
            }
            ActionTile(
                label = "Popup",
                text = "Popup",
                onClick = {
                    Toast.makeText(context, "Popup player is coming soon.", Toast.LENGTH_SHORT).show()
                }
            ) { SlotGlyph(ActionIcons.Popup) }
            ActionTile(
                label = "Play audio",
                text = "Play Audio",
                lit = background,
                litColor = LogoGeometry.Yellow,
                state = if (background) "On" else "Off",
                onClick = {
                    background = !background
                    PlaybackPrefs.setBackground(context, background)
                    Toast.makeText(
                        context,
                        if (background) "Background play on" else "Background play off",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) { SlotGlyph(ActionIcons.Headphones) }
            DownloadTile(info) {
                if (info == null) {
                    Toast.makeText(context, "This video is still loading.", Toast.LENGTH_SHORT).show()
                } else if (info.live) {
                    Toast.makeText(context, "Live streams can't be downloaded.", Toast.LENGTH_SHORT).show()
                } else {
                    val busy = activeDownload(info.url) != null || OfflineStore.forUrl(info.url).isNotEmpty()
                    downloadSheet = if (busy) 2 else 1
                }
            }
            ActionTile(
                label = "Save to phone",
                text = "Save to phone",
                onClick = { if (info != null) phoneOpen = true }
            ) { SlotGlyph(ActionIcons.Phone) }
        }

        if (phoneOpen && info != null) {
            PhoneSaveSheet(info = info, onDismiss = { phoneOpen = false })
        }

        if (info != null) {
            if (downloadSheet == 1) {
                DownloadSheet(info = info, onDismiss = { downloadSheet = 0 })
            } else if (downloadSheet == 2) {
                DownloadStateSheet(
                    info = info,
                    onAnother = { downloadSheet = 1 },
                    onGoTo = {
                        downloadSheet = 0
                        vm.select(Tab.Downloads)
                        vm.minimizePlayer()
                    },
                    onDismiss = { downloadSheet = 0 }
                )
            }
        }
    }
}

/** The "Comments" card: count, and a preview of the first comment. Tapping it opens all of them. */
@Composable
fun CommentsCard(count: String?, preview: CommentItem?, state: Load<*>?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(start = px720(24), end = px720(24), top = px720(24), bottom = px720(17))
            .fillMaxWidth()
            .clip(RoundedCornerShape(px720(24)))
            .background(cardFill())
            .clickable(onClick = onClick)
            .padding(start = px720(24), end = px720(24), top = px720(22), bottom = px720(22))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = pxSp720(30),
                    lineHeight = pxSp720(36),
                    fontWeight = FontWeight.SemiBold
                )
            )
            count?.let {
                Text(
                    "· $it",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pxSp720(30),
                        lineHeight = pxSp720(36),
                        fontWeight = FontWeight.Medium
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.padding(start = px720(10))
                )
            }
        }
        when {
            preview != null -> Row(Modifier.padding(top = px720(24)), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = preview.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(px720(48))
                        .clip(CircleShape)
                        .background(Tonal.fillStrong())
                )
                Text(
                    preview.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pxSp720(26),
                        lineHeight = pxSp720(34)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = px720(24))
                )
            }
            state is Load.Failed -> Text(
                "Comments are turned off or unavailable",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = pxSp720(26),
                    lineHeight = pxSp720(34)
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = px720(24))
            )
            state == null || state is Load.Loading -> Box(
                Modifier
                    .padding(top = px720(28))
                    .fillMaxWidth(0.7f)
                    .height(px720(22))
                    .clip(RoundedCornerShape(px720(11)))
                    .background(Tonal.fillStrong())
            )
        }
    }
}

/**
 * The "Up next" heading with the Autoplay switch at its right end, in the place of the plain
 * heading above the related videos. The switch is drawn and moves, but it is not connected to
 * anything yet: [onAutoplayChange] is where the real setting goes later.
 */
@Composable
fun UpNextHeader(
    initial: Boolean = true,
    onAutoplayChange: (Boolean) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = px(36))
            .padding(bottom = px(24)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = pxSp(36),
                lineHeight = pxSp(46),
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            "Autoplay",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = pxSp(28),
                lineHeight = pxSp(36)
            ),
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.width(px(27)))
        AutoplaySwitch(on = on) {
            on = it
            onAutoplayChange(it)
        }
    }
}

/**
 * The Autoplay switch: a slim track with a bigger round thumb that sits at its end and spills a
 * little over the track's top and bottom. On, the thumb is light green and the track a muted
 * green; off, both go grey and the thumb slides to the start.
 */
@Composable
private fun AutoplaySwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(180),
        label = "autoplayThumb"
    )
    val track by animateColorAsState(
        targetValue = if (on) AccentGreen.copy(alpha = 0.5f) else scheme.onSurface.copy(alpha = 0.22f),
        animationSpec = tween(180),
        label = "autoplayTrack"
    )
    val thumb by animateColorAsState(
        targetValue = if (on) AccentGreen else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "autoplayThumbColor"
    )
    val trackWidth = px(78)
    val thumbSize = px(46)

    Box(
        Modifier
            .size(width = trackWidth, height = thumbSize)
            .toggleable(
                value = on,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onChange
            )
            .semantics { contentDescription = "Autoplay" },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(px(35))
                .clip(CircleShape)
                .background(track)
        )
        Box(
            Modifier
                .offset(x = (trackWidth - thumbSize) * progress)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}

/** "Save to…": the account's playlists (Watch later included), each a checkbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Save to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            when (val state = vm.playlists) {
                null, Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }

                is Load.Failed -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "Couldn't load your playlists",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = vm::loadPlaylists,
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Try again") }
                }

                is Load.Ready -> Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    state.data.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.togglePlaylist(option) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = option.contains, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import com.playmate.app.ui.theme.Tonal
import com.playmate.app.ui.theme.tonalPress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Title and the quiet line under it (views and a short age like "7y ago"), with the like /
 * dislike pill at the right end of that line. Tapping the title or the line opens the description
 * panel under the player (see [DescriptionPanel]); [onOpen] is null while the video is loading,
 * and then there is no pill.
 *
 * The channel name is not printed in the quiet line any more: it lives in the channel row below.
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
    val ago = agoText(uploaded)?.let { shortAgo(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpen != null, onClick = { onOpen?.invoke() })
            .padding(horizontal = px(36))
            .padding(top = px(38))
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = pxSp(40),
                lineHeight = pxSp(53),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier.offset(y = -px(3)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                listOfNotNull(viewsText, ago).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = pxSp(32),
                    lineHeight = pxSp(40)
                ),
                color = quietText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onOpen != null) {
                val vm: AppViewModel = viewModel()
                Spacer(Modifier.width(px(24)))
                ReactionPill(
                    reaction = vm.meta?.reaction ?: Reaction.None,
                    likeCount = vm.meta?.likeCount,
                    onLike = { vm.react(Reaction.Liked) },
                    onDislike = { vm.react(Reaction.Disliked) }
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
            .padding(horizontal = px(36))
            .padding(top = px(36), bottom = px(37)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(px(24)))
                .clickable(enabled = channelId != null) { vm.openChannel(channelId, name, avatar) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(px(102))
                    .clip(CircleShape)
                    .background(Tonal.fill())
            )
            Column(Modifier.weight(1f).padding(start = px(36), end = px(24))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = pxSp(36),
                            lineHeight = pxSp(44),
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
                    Spacer(Modifier.height(px(3)))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = pxSp(28),
                            lineHeight = pxSp(38)
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
 * Every size from here down is measured off the reference screenshot (a phone 852 pixels wide)
 * and then scaled to the width of the phone it runs on, so the title, the pill, the buttons and
 * the cards keep exactly the same proportions on every screen. To make the whole look a little
 * bigger or smaller, change [PX_SCALE].
 */
private const val REF_WIDTH = 852f
private const val PX_SCALE = 1f

@Composable
private fun px(v: Int): Dp {
    val config = LocalConfiguration.current
    val width = minOf(config.screenWidthDp, config.screenHeightDp)
    return (v * PX_SCALE * width / REF_WIDTH).dp
}

@Composable
private fun pxSp(v: Int): TextUnit = with(LocalDensity.current) { px(v).toSp() }

/** The light green of the Play Audio glyph (when it is on) and of the Autoplay switch. */
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

    /** The Comments card and the like / dislike pill. */
    val Card = Color(0xFF232324)

    /** The thin line under the row of buttons. */
    val Line = Color(0xFF1C1C1C)

    /** The views line and the subscribers line: a clearly brighter grey than the app had. */
    val Quiet = Color(0xFFDADADA)
}

/** True while the theme is a dark one (the reference colours above are for the dark theme only). */
@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Fill of the Comments card and of the like / dislike pill. */
@Composable
private fun cardFill(): Color = if (isDarkTheme()) WatchColors.Card else Tonal.fill()

/** The thin line under the row of buttons. */
@Composable
private fun rowLine(): Color = if (isDarkTheme()) WatchColors.Line else Tonal.hairline()

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

    private const val LINE = 1.25f

    val Save: ImageVector by lazy {
        thin(
            "ThinSave", LINE,
            "M8.2 5.25h7.6a1.2 1.2 0 0 1 1.2 1.2v12.3l-5-3.2-5 3.2V6.45a1.2 1.2 0 0 1 1.2-1.2z"
        )
    }
    val Popup: ImageVector by lazy {
        thin(
            "ThinPopup", LINE,
            "M12 7.7H8a1.8 1.8 0 0 0-1.8 1.8v6.1a1.8 1.8 0 0 0 1.8 1.8h7.5a1.8 1.8 0 0 0 1.8-1.8V12.7",
            "M13.3 10.9L18 6.3",
            "M13.7 6.3H18v4.2"
        )
    }
    val Headphones: ImageVector by lazy {
        thin(
            "ThinHeadphones", LINE,
            "M5.05 12.9v-.5a7.05 7.05 0 0 1 14.1 0v.5",
            "M5.85 13.3h1.1a1.4 1.4 0 0 1 1.4 1.4v2.5a1.4 1.4 0 0 1-1.4 1.4h-1.1a1.4 1.4 0 0 1-1.4-1.4v-2.5a1.4 1.4 0 0 1 1.4-1.4z",
            "M17.15 13.3h1.1a1.4 1.4 0 0 1 1.4 1.4v2.5a1.4 1.4 0 0 1-1.4 1.4h-1.1a1.4 1.4 0 0 1-1.4-1.4v-2.5a1.4 1.4 0 0 1 1.4-1.4z"
        )
    }
    val Download: ImageVector by lazy {
        thin("ThinDownload", LINE, "M12 5v10.2", "M7.8 11.2l4.2 4.2 4.2-4.2", "M6.2 19h11.6")
    }
    val DownloadDone: ImageVector by lazy {
        thin("ThinDownloadDone", LINE, "M7.8 11.5l3 3 5.4-5.6", "M6.2 19h11.6")
    }
    val Phone: ImageVector by lazy {
        thin(
            "ThinPhone", LINE,
            "M8.95 4.7h6.1a1.7 1.7 0 0 1 1.7 1.7v11.2a1.7 1.7 0 0 1-1.7 1.7h-6.1a1.7 1.7 0 0 1-1.7-1.7V6.4a1.7 1.7 0 0 1 1.7-1.7z",
            "M9.8 6.4h4.4",
            "M12 5.4v1",
            "M8.3 17.2h1.8",
            "M13.7 17.2h1.8",
            "M12 16.3l.8.9-.8.9-.8-.9z"
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
}

/**
 * One button of the row under the video: a thin glyph with its name printed under it, no tile
 * behind it. Pressed, a round grey patch fades in behind the glyph and the glyph shrinks a
 * little. [label] is what a screen reader says; [text] is what is printed (it may wrap to a
 * second line). [lit] is the switched-on look: the glyph takes the colour [litColor] (nothing
 * else changes, no disc behind it); [state] is what a screen reader says about a toggle.
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
    val patch by animateColorAsState(
        targetValue = if (pressed) Tonal.fillStrong() else Tonal.fillStrong().copy(alpha = 0f),
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

    Column(
        Modifier
            .weight(1f)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(px(72)), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(px(108))
                    .clip(CircleShape)
                    .background(patch)
            )
            Box(
                Modifier
                    .size(px(72))
                    .graphicsLayer {
                        scaleX = squeeze
                        scaleY = squeeze
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides tint) { glyph() }
            }
        }
        Spacer(Modifier.height(px(3)))
        Text(
            text,
            style = TextStyle(
                fontSize = pxSp(26),
                lineHeight = pxSp(32),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            ),
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A glyph in the 72-pixel box of a button. [size] is the glyph's own size inside that box (it is
 * centred, so the label under it does not move); Save and Download use a smaller one, since the
 * reference draws those two a tenth smaller than the others.
 */
@Composable
private fun SlotGlyph(icon: ImageVector, size: Dp? = null) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(size ?: px(72)))
}

/**
 * Like and dislike as one rounded pill with a thin line between them, at the right end of the
 * line under the title. Liked / disliked shows the YouTube way: the thumb is an outline when off
 * and a solid shape when on, and the like thumb throws a few rays and the count rolls when it is
 * turned on.
 */
@Composable
private fun ReactionPill(
    reaction: Reaction,
    likeCount: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val liked = reaction == Reaction.Liked
    val disliked = reaction == Reaction.Disliked
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val glyph = px(44)

    Row(
        Modifier
            .height(px(75))
            .clip(CircleShape)
            .background(cardFill()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .tonalPress(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    onClick = {
                        if (!liked) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLike()
                    }
                )
                .padding(start = px(24), end = px(25))
                .semantics {
                    contentDescription = "Like"
                    if (liked) stateDescription = "Liked"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = liked,
                    outline = PmIcons.ThumbUp,
                    solid = PmIcons.ThumbUpSolid,
                    size = glyph,
                    reach = px(16)
                )
                likeCount?.let {
                    RollingCount(
                        it,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = pxSp(29),
                            lineHeight = pxSp(36)
                        ),
                        color = LocalContentColor.current,
                        modifier = Modifier.padding(start = px(17))
                    )
                }
            }
        }
        Box(
            Modifier
                .width(px(2))
                .height(px(38))
                .background(Tonal.hairline())
        )
        Box(
            Modifier
                .fillMaxHeight()
                .tonalPress(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    onClick = onDislike
                )
                .padding(horizontal = px(23))
                .semantics {
                    contentDescription = "Dislike"
                    if (disliked) stateDescription = "Disliked"
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = disliked,
                    outline = PmIcons.ThumbDown,
                    solid = PmIcons.ThumbDownSolid,
                    size = glyph,
                    rays = false
                )
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
                strokeWidth = px(4),
                modifier = Modifier.size(px(40))
            )
            saved -> SlotGlyph(ActionIcons.DownloadDone, px(65))
            else -> SlotGlyph(ActionIcons.Download, px(65))
        }
    }
}

/**
 * The row under a video, in this order: Save, Popup, Play Audio, Download, Save to phone. (Like
 * and dislike sit in the pill at the end of the line under the title; comments sit right below
 * this row.) [onShare] is no longer used by this row; sharing is not one of its buttons.
 *
 * Five equal columns spread over the width, each a thin glyph with its name under it, and a thin
 * line under the whole row. Play Audio is the one toggle (it is the background-play switch): it
 * looks like the others until it is switched on, then its glyph turns light green. Popup is only drawn for now: it does nothing yet.
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
                .padding(horizontal = px(16))
                .padding(bottom = px(23)),
            verticalAlignment = Alignment.Top
        ) {
            ActionTile(label = "Save", text = "Save", onClick = onSave) {
                SlotGlyph(ActionIcons.Save, px(65))
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
                litColor = AccentGreen,
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

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = px(36))
                .height(px(2))
                .background(rowLine())
        )

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
            .padding(start = px(36), end = px(36), top = px(23), bottom = px(37))
            .fillMaxWidth()
            .clip(RoundedCornerShape(px(30)))
            .background(cardFill())
            .clickable(onClick = onClick)
            .padding(start = px(31), end = px(31), top = px(18), bottom = px(22))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = pxSp(33),
                    lineHeight = pxSp(42),
                    fontWeight = FontWeight.SemiBold
                )
            )
            count?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pxSp(29),
                        lineHeight = pxSp(42)
                    ),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = px(12))
                )
            }
        }
        when {
            preview != null -> Row(Modifier.padding(top = px(17)), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = preview.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(px(61))
                        .clip(CircleShape)
                        .background(Tonal.fillStrong())
                )
                Text(
                    preview.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pxSp(28),
                        lineHeight = pxSp(40)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = px(29), top = px(10))
                )
            }
            state is Load.Failed -> Text(
                "Comments are turned off or unavailable",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = pxSp(28),
                    lineHeight = pxSp(40)
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = px(17))
            )
            state == null || state is Load.Loading -> Box(
                Modifier
                    .padding(top = px(25))
                    .fillMaxWidth(0.7f)
                    .height(px(26))
                    .clip(RoundedCornerShape(px(13)))
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

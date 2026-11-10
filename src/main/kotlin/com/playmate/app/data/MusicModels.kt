package com.playmate.app.data

/** What a Music item is. Decided from the endpoint YouTube Music itself attaches to it. */
enum class MusicKind { Song, Video, Album, Playlist, Artist, Podcast, Episode, Mix, Other }

/** The shape YouTube Music draws an item in: a cover tile, a list row, an episode row or a round button. */
enum class MusicLayout { Tile, Row, Episode, Button }

/** How a whole shelf is laid out on screen. */
enum class ShelfStyle { Tiles, Rows, Episodes, Buttons, Immersive, Grid }

data class MusicItem(
    val layout: MusicLayout,
    val kind: MusicKind,
    val title: String,
    /** The grey line under the title, exactly as YouTube Music sends it ("Hasan S. Iqbal • 8.7M plays"). */
    val subtitle: String,
    val thumb: String?,
    val explicit: Boolean,
    val videoId: String?,
    val playlistId: String?,
    val browseId: String?,
    val params: String?,
    /** "3:37", when the cover carries a length badge. */
    val duration: String? = null,
    /** 0..100, when the item was started before (the red bar under the cover). */
    val progress: Int? = null,
    /** Icon name for round buttons (New releases, Charts, Moods & genres). */
    val icon: String? = null,
    /** The first songs or episodes listed inside a big card (Bollywood Fire, a podcast show), when the server sends them. */
    val tracks: List<MusicItem> = emptyList()
) {
    /** Stable key for lazy lists. */
    val key: String get() = (videoId ?: playlistId ?: browseId ?: title) + "|" + title.hashCode()

    val playable: Boolean get() = videoId != null || playlistId != null
}

/** A mood chip from the top of the Music home (Workout, Energize, Feel good...). [params] loads that mood. */
data class MusicChip(val label: String, val params: String?, val selected: Boolean)

data class MusicShelf(
    val title: String,
    /** The small line above a title ("ALEXANDER PEARCE" over "Speed dial"). */
    val strapline: String?,
    /** The round picture next to the title, when YouTube Music sends one. */
    val avatar: String?,
    val style: ShelfStyle,
    /** Rows stacked in one swipeable column (4 for Quick picks). 1 for everything else. */
    val rowsPerColumn: Int,
    val items: List<MusicItem>,
    /** The ">" arrow next to the title opens this page. */
    val moreBrowseId: String?,
    val moreParams: String?,
    /** The "Play all" pill plays this. */
    val playAllVideoId: String?,
    val playAllPlaylistId: String?,
    /** What the server really sent for this shelf: used to match the screen to the real data. */
    val debug: String,
    /** The server's own size word for the cover tiles (COLLECTION_STYLE_ITEM_SIZE_...). */
    val itemSize: String = ""
)

data class MusicHome(
    val chips: List<MusicChip>,
    val shelves: List<MusicShelf>,
    /** Loads the next shelves; null when there are no more. */
    val token: String?,
    /** Only set when the page came back with no shelves: a short reason. */
    val note: String? = null
)

/** Google's picture servers resize on request: the size sits in the link ("=w226-h226..."). */
object MusicImage {
    private val sizeTag = Regex("=w\\d+-h\\d+")
    private val squareTag = Regex("=s\\d+")

    fun sized(url: String?, px: Int): String? {
        if (url.isNullOrBlank()) return null
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        return when {
            sizeTag.containsMatchIn(url) -> sizeTag.replaceFirst(url, "=w$px-h$px")
            squareTag.containsMatchIn(url) -> squareTag.replaceFirst(url, "=s$px")
            else -> url
        }
    }
}

/** Lets a Music song or video go through the player Playmate already has. */
fun MusicItem.asVideo(): VideoItem? {
    val id = videoId ?: return null
    return VideoItem(
        url = "https://www.youtube.com/watch?v=$id",
        title = title,
        channel = subtitle.substringBefore(" • "),
        durationSec = durationSeconds(duration),
        views = 0L,
        thumb = thumb,
        uploaded = null
    )
}

private fun durationSeconds(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    val parts = text.trim().split(":").map { it.toLongOrNull() ?: return 0L }
    return parts.fold(0L) { total, part -> total * 60 + part }
}

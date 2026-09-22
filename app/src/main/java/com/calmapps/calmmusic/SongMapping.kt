package com.calmapps.calmmusic

import com.calmapps.calmmusic.data.Song
import com.calmapps.calmmusic.ui.SongUiModel

/**
 * UI compatibility mapping: screens branch on the historical sourceType
 * strings, which are now derived from where the song actually is.
 */
fun Song.toUiModel(): SongUiModel =
    SongUiModel(
        id = id,
        title = title,
        artist = artist,
        durationText = formatDurationMillis(durationMillis),
        durationMillis = durationMillis,
        discNumber = discNumber,
        trackNumber = trackNumber,
        sourceType = when {
            !isYouTube -> "LOCAL_FILE"
            hasLocalCopy -> "YOUTUBE_DOWNLOAD"
            else -> "YOUTUBE"
        },
        audioUri = localUri ?: id,
        album = album,
    )

/** Minimal Song for ids that enter the database outside a scan (playlist adds). */
fun SongUiModel.toSkeletonSong(): Song {
    val artistKey = Song.artistKeyOf(artist, null)
    val isLocal = sourceType == "LOCAL_FILE" || sourceType == "YOUTUBE_DOWNLOAD"
    return Song(
        id = id,
        title = title,
        artist = artist,
        albumArtist = null,
        album = album,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMillis = durationMillis,
        releaseYear = null,
        artistKey = artistKey,
        albumKey = Song.albumKeyOf(artistKey, album),
        localUri = if (isLocal) (audioUri ?: id) else null,
        localLastModified = null,
        localSizeBytes = null,
    )
}

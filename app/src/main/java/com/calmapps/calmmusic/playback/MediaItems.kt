package com.calmapps.calmmusic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.calmapps.calmmusic.data.Song

/**
 * The one queue mapping: a song plays from its local file when it has one,
 * else through the playback service's YouTube-resolving data source.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(localUri ?: "monomusic://yt/$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build(),
        )
        .build()

package com.calmapps.calmmusic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.calmapps.calmmusic.ui.SongUiModel

/**
 * Encapsulates the local playback subqueue and index map for the current
 * playback queue. This is extracted from MainActivity/MonoMusic to reduce the
 * amount of playback bookkeeping state inside the composable.
 */
class PlaybackCoordinator {

    var localPlaybackSubqueue: List<SongUiModel> = emptyList()
        private set

    var localIndexByGlobal: IntArray? = null
        private set

    var localMediaItemsForQueue: List<MediaItem> = emptyList()
        private set

    var localQueueInitialized: Boolean = false

    /**
     * Rebuilds the local subqueue and index map given the full playback queue.
     */
    fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        if (queue.isEmpty()) {
            localPlaybackSubqueue = emptyList()
            localIndexByGlobal = null
            localMediaItemsForQueue = emptyList()
            localQueueInitialized = false
            return
        }

        val localList = mutableListOf<SongUiModel>()
        val localMap = IntArray(queue.size) { -1 }
        var localCounter = 0

        queue.forEachIndexed { globalIndex, song ->
            when (song.sourceType) {
                "LOCAL_FILE", "YOUTUBE_DOWNLOAD" -> {
                    val uri = song.audioUri
                    if (!uri.isNullOrBlank()) {
                        localMap[globalIndex] = localCounter
                        localList += song
                        localCounter++
                    }
                }
            }
        }

        localPlaybackSubqueue = localList
        localIndexByGlobal = localMap

        localMediaItemsForQueue = localList.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build()

            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.audioUri)
                .setMediaMetadata(metadata)
                .build()
        }

        localQueueInitialized = false
    }
}

package com.calmapps.calmmusic

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.calmapps.calmmusic.data.LibraryRepository
import com.calmapps.calmmusic.data.MonoMusicDatabase
import com.calmapps.calmmusic.data.MonoMusicSettingsManager
import com.calmapps.calmmusic.data.NowPlayingRepeatModeKeys
import com.calmapps.calmmusic.data.NowPlayingSnapshot
import com.calmapps.calmmusic.data.NowPlayingStorage
import com.calmapps.calmmusic.data.Song
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.RepeatMode
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the library state and the playback queue. Playback itself lives in the
 * single Media3 player behind [MediaController]: the whole mixed queue (local
 * files and YouTube streams) is handed to the player, which advances, repeats,
 * and buffers on its own; this class only mirrors the player into
 * [PlaybackState] for the screens.
 */
class MonoMusicViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: MonoMusic
        @OptIn(UnstableApi::class)
        get() = getApplication() as MonoMusic

    private val database: MonoMusicDatabase by lazy { MonoMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val libraryRepository: LibraryRepository by lazy { LibraryRepository(app) }
    private val nowPlayingStorage: NowPlayingStorage by lazy { app.nowPlayingStorage }

    private var playbackMonitorJob: Job? = null

    private val _librarySongs = MutableStateFlow<List<SongUiModel>>(emptyList())
    val librarySongs: StateFlow<List<SongUiModel>> = _librarySongs

    private val _libraryAlbums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val libraryAlbums: StateFlow<List<AlbumUiModel>> = _libraryAlbums

    private val _libraryArtists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val libraryArtists: StateFlow<List<ArtistUiModel>> = _libraryArtists

    private val _libraryRefreshTrigger = MutableStateFlow(0)
    val libraryRefreshTrigger: StateFlow<Int> = _libraryRefreshTrigger.asStateFlow()

    private val _isLoadingSongs = MutableStateFlow(true)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _isLoadingAlbums = MutableStateFlow(true)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    private fun SongUiModel.toMediaItem(): MediaItem {
        val isLocal = sourceType == "LOCAL_FILE" || sourceType == "YOUTUBE_DOWNLOAD"
        val builder = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .apply {
                        if (!isLocal) setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                    }
                    .build(),
            )
        return if (isLocal) {
            builder.setUri(audioUri ?: id).build()
        } else {
            // Resolved to a stream URL inside the playback service.
            builder.setUri("https://www.youtube.com/watch?v=$id").setCustomCacheKey(id).build()
        }
    }

    private fun RepeatMode.toPlayerMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        isNewQueue: Boolean = true,
        localController: MediaController?,
        startPositionMs: Long = 0L,
    ) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        val previous = _playbackState.value
        val song = queue[startIndex]

        val newState = previous.copy(
            playbackQueue = queue,
            playbackQueueIndex = startIndex,
            originalPlaybackQueue = if (isNewQueue) queue else previous.originalPlaybackQueue,
            isShuffleOn = if (isNewQueue) false else previous.isShuffleOn,
            currentSongId = song.id,
            nowPlayingSong = song,
            isPlaybackPlaying = true,
            isBuffering = song.sourceType == "YOUTUBE",
            nowPlayingPositionMs = startPositionMs,
            nowPlayingDurationMs = song.durationMillis ?: 0L,
        )
        _playbackState.value = newState
        persistPlaybackSnapshot(newState)

        val controller = localController ?: return
        controller.setMediaItems(queue.map { it.toMediaItem() }, startIndex, startPositionMs)
        controller.repeatMode = newState.repeatMode.toPlayerMode()
        controller.prepare()
        controller.playWhenReady = true
    }

    fun togglePlayback(localController: MediaController?) {
        val state = _playbackState.value
        state.nowPlayingSong ?: return
        val controller = localController

        // A restored queue that was never handed to the player yet.
        if (!state.isPlaybackPlaying && controller != null && controller.mediaItemCount == 0) {
            val index = state.playbackQueueIndex
            if (state.playbackQueue.isNotEmpty() && index != null && index in state.playbackQueue.indices) {
                startPlaybackFromQueue(
                    queue = state.playbackQueue,
                    startIndex = index,
                    isNewQueue = false,
                    localController = controller,
                    startPositionMs = state.nowPlayingPositionMs,
                )
                return
            }
        }

        if (state.isPlaybackPlaying) {
            controller?.pause()
        } else {
            controller?.playWhenReady = true
        }
        _playbackState.value = state.copy(isPlaybackPlaying = !state.isPlaybackPlaying)
        persistPlaybackSnapshot()
    }

    fun startShuffledPlaybackFromQueue(
        queue: List<SongUiModel>,
        localController: MediaController?,
    ) {
        if (queue.isEmpty()) return

        val shuffledQueue = queue.shuffled()
        _playbackState.value = _playbackState.value.copy(
            originalPlaybackQueue = queue,
            isShuffleOn = true,
        )
        startPlaybackFromQueue(shuffledQueue, 0, isNewQueue = false, localController = localController)
    }

    fun playNextInQueue(localController: MediaController?) {
        seekRelative(localController, forward = true)
    }

    fun playPreviousInQueue(localController: MediaController?) {
        seekRelative(localController, forward = false)
    }

    private fun seekRelative(localController: MediaController?, forward: Boolean) {
        val state = _playbackState.value
        val queue = state.playbackQueue
        val currentIndex = state.playbackQueueIndex ?: return
        if (queue.isEmpty() || currentIndex !in queue.indices) return

        val targetIndex = when {
            state.repeatMode == RepeatMode.ONE -> currentIndex
            forward && currentIndex < queue.lastIndex -> currentIndex + 1
            forward && state.repeatMode == RepeatMode.QUEUE -> 0
            !forward && currentIndex > 0 -> currentIndex - 1
            !forward && state.repeatMode == RepeatMode.QUEUE -> queue.lastIndex
            else -> return
        }

        val target = queue[targetIndex]
        _playbackState.value = state.copy(
            playbackQueueIndex = targetIndex,
            currentSongId = target.id,
            nowPlayingSong = target,
            nowPlayingDurationMs = target.durationMillis ?: state.nowPlayingDurationMs,
            nowPlayingPositionMs = 0L,
            isPlaybackPlaying = true,
            isBuffering = target.sourceType == "YOUTUBE",
        )
        persistPlaybackSnapshot()

        val controller = localController ?: return
        if (controller.mediaItemCount == 0) {
            startPlaybackFromQueue(queue, targetIndex, isNewQueue = false, localController = controller)
        } else {
            controller.seekTo(targetIndex, 0L)
            controller.playWhenReady = true
        }
    }

    fun toggleShuffleMode(localController: MediaController?) {
        val state = _playbackState.value
        val queue = state.playbackQueue
        val index = state.playbackQueueIndex ?: return
        val current = state.nowPlayingSong ?: return
        if (queue.isEmpty() || index !in queue.indices) return

        val (newQueue, newIndex, original) = if (!state.isShuffleOn) {
            val remaining = (queue.take(index) + queue.drop(index + 1)).shuffled()
            Triple(listOf(current) + remaining, 0, queue)
        } else {
            val restore = state.originalPlaybackQueue.ifEmpty {
                _playbackState.value = state.copy(isShuffleOn = false)
                persistPlaybackSnapshot()
                return
            }
            val originalIndex = restore.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
            Triple(restore, originalIndex, restore)
        }

        _playbackState.value = state.copy(
            playbackQueue = newQueue,
            playbackQueueIndex = newIndex,
            originalPlaybackQueue = original,
            isShuffleOn = !state.isShuffleOn,
            currentSongId = current.id,
            nowPlayingSong = current,
        )
        persistPlaybackSnapshot()

        // Rebuild the player queue around the playing song without restarting it.
        val controller = localController ?: return
        if (controller.mediaItemCount > 0) {
            val position = controller.currentPosition
            controller.setMediaItems(newQueue.map { it.toMediaItem() }, newIndex, position)
            controller.prepare()
            controller.playWhenReady = state.isPlaybackPlaying
        }
    }

    fun cycleRepeatMode(localController: MediaController?) {
        val state = _playbackState.value
        val newRepeat = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.QUEUE
            RepeatMode.QUEUE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.value = state.copy(repeatMode = newRepeat)
        persistPlaybackSnapshot()
        localController?.repeatMode = newRepeat.toPlayerMode()
    }

    /**
     * Mirrors the player into [PlaybackState] once a second: position, buffering,
     * and the queue index (the player advances on its own). Also keeps the
     * YouTube precache window centered on the playing song.
     */
    fun startLocalPlaybackMonitoring(controller: MediaController) {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = viewModelScope.launch {
            var lastPrecacheIndex: Int? = null
            var lastPersistElapsedMs = 0L

            while (true) {
                val state = _playbackState.value
                val queue = state.playbackQueue

                if (queue.isEmpty() || controller.mediaItemCount == 0) {
                    delay(2000L)
                    continue
                }

                val isPlaying = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED
                val position = controller.currentPosition
                val duration = controller.duration
                val isBufferingNow = controller.playbackState == Player.STATE_BUFFERING

                var newState = state.copy(
                    isPlaybackPlaying = isPlaying,
                    nowPlayingPositionMs = position,
                    nowPlayingDurationMs = if (duration > 0) duration else state.nowPlayingDurationMs,
                    isBuffering = isBufferingNow,
                )

                val currentMediaId = controller.currentMediaItem?.mediaId
                if (currentMediaId != null) {
                    val targetIndex = queue.indexOfFirst { it.id == currentMediaId }
                    if (targetIndex >= 0 && targetIndex != state.playbackQueueIndex) {
                        val newSong = queue[targetIndex]
                        newState = newState.copy(
                            playbackQueueIndex = targetIndex,
                            currentSongId = newSong.id,
                            nowPlayingSong = newSong,
                            nowPlayingDurationMs = newSong.durationMillis ?: newState.nowPlayingDurationMs,
                        )
                    }
                }

                val currentIndex = newState.playbackQueueIndex
                if (currentIndex != null && currentIndex in queue.indices && currentIndex != lastPrecacheIndex) {
                    lastPrecacheIndex = currentIndex
                    val windowIds = (-5..5).mapNotNull { offset ->
                        queue.getOrNull(currentIndex + offset)
                            ?.takeIf { it.sourceType == "YOUTUBE" }?.id
                    }
                    if (windowIds.isNotEmpty()) {
                        app.youTubePrecacheManager.updateQueueWindow(windowIds)
                    }
                }

                if (newState != state) {
                    _playbackState.value = newState
                    // Persist on meaningful changes; position-only ticks at most every 5s.
                    val significantChange = newState.currentSongId != state.currentSongId ||
                        newState.isPlaybackPlaying != state.isPlaybackPlaying ||
                        newState.playbackQueueIndex != state.playbackQueueIndex
                    val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                    if (significantChange || nowElapsedMs - lastPersistElapsedMs >= 5000L) {
                        lastPersistElapsedMs = nowElapsedMs
                        persistPlaybackSnapshot(newState)
                    }
                }

                // Every state emission recomposes the whole UI tree; on e-ink 1s is
                // plenty for the position display and keeps the device responsive.
                delay(if (isPlaying) 1000L else 2000L)
            }
        }
    }

    /**
     * A queued YouTube song finished downloading: swap the queue entry to the
     * local copy. The player picks it up for non-current items; the playing
     * item keeps its stream until it is next played.
     */
    fun onSongDownloaded(youtubeSongId: String, controller: MediaController?) {
        viewModelScope.launch {
            refreshLibraryFromDatabase()

            val state = _playbackState.value
            val local = _librarySongs.value.firstOrNull {
                it.id == youtubeSongId && it.sourceType == "YOUTUBE_DOWNLOAD"
            } ?: return@launch

            val indexInQueue = state.playbackQueue.indexOfFirst { it.id == youtubeSongId }
            if (indexInQueue == -1) return@launch

            val old = state.playbackQueue[indexInQueue]
            val replacement = local.copy(trackNumber = old.trackNumber, discNumber = old.discNumber)
            val newQueue = state.playbackQueue.toMutableList().also { it[indexInQueue] = replacement }

            _playbackState.value = state.copy(
                playbackQueue = newQueue,
                nowPlayingSong = if (state.playbackQueueIndex == indexInQueue) replacement else state.nowPlayingSong,
            )
            persistPlaybackSnapshot()

            if (controller != null &&
                indexInQueue < controller.mediaItemCount &&
                indexInQueue != controller.currentMediaItemIndex
            ) {
                controller.replaceMediaItem(indexInQueue, replacement.toMediaItem())
            }
        }
    }

    private fun persistPlaybackSnapshot(state: PlaybackState = _playbackState.value) {
        val repeatModeKey = when (state.repeatMode) {
            RepeatMode.OFF -> NowPlayingRepeatModeKeys.OFF
            RepeatMode.QUEUE -> NowPlayingRepeatModeKeys.QUEUE
            RepeatMode.ONE -> NowPlayingRepeatModeKeys.ONE
        }
        val snapshot = NowPlayingSnapshot(
            queueSongIds = state.playbackQueue.map { it.id },
            currentIndex = state.playbackQueueIndex,
            isPlaying = state.isPlaybackPlaying,
            positionMs = state.nowPlayingPositionMs,
            repeatModeKey = repeatModeKey,
            isShuffleOn = state.isShuffleOn,
        )
        viewModelScope.launch(Dispatchers.IO) { nowPlayingStorage.save(snapshot) }
    }

    // ------------------------------------------------------------------
    // Library
    // ------------------------------------------------------------------

    suspend fun refreshLibraryFromDatabase() {
        try {
            val songs = withContext(Dispatchers.IO) { songDao.getAll() }
            _librarySongs.value = songs.map { it.toUiModel() }
            _libraryAlbums.value = deriveAlbums(songs)
            _libraryArtists.value = deriveArtists(songs)
            _libraryRefreshTrigger.value += 1
        } catch (_: Exception) {
        }
    }

    private fun deriveAlbums(songs: List<Song>): List<AlbumUiModel> =
        songs.filter { it.albumKey != null }
            .groupBy { it.albumKey!! }
            .map { (albumKey, group) ->
                AlbumUiModel(
                    id = albumKey,
                    title = group.first().album.orEmpty(),
                    artist = group.firstNotNullOfOrNull { it.albumArtist } ?: group.first().artist,
                    sourceType = if (group.any { it.hasLocalCopy || !it.isYouTube }) "LOCAL_FILE" else "YOUTUBE",
                    releaseYear = group.mapNotNull { it.releaseYear }.maxOrNull(),
                )
            }
            .sortedBy { it.title.lowercase() }

    private fun deriveArtists(songs: List<Song>): List<ArtistUiModel> =
        songs.filter { it.artistKey != null }
            .groupBy { it.artistKey!! }
            .map { (artistKey, group) ->
                ArtistUiModel(
                    id = artistKey,
                    name = group.firstNotNullOfOrNull { it.albumArtist } ?: group.first().artist,
                    songCount = group.size,
                    albumCount = group.mapNotNull { it.albumKey }.distinct().size,
                )
            }
            .sortedBy { it.name.lowercase() }

    suspend fun getAlbumSongs(albumId: String): List<SongUiModel> =
        withContext(Dispatchers.IO) {
            songDao.getByAlbumKey(albumId).map { it.toUiModel() }
        }

    /** Albums already looked up this session, successful or not. */
    private val orderRepairAttempted = mutableSetOf<String>()

    suspend fun getAlbumSongsForDetails(album: AlbumUiModel): List<SongUiModel> {
        var localSongs = getAlbumSongs(album.id)

        // Broken ordering (missing or duplicated track numbers) repairs itself
        // from the canonical YouTube Music track list, once, persisted.
        val duplicated = localSongs.filter { it.trackNumber != null }
            .groupBy { (it.discNumber ?: 1) to it.trackNumber }
            .any { it.value.size > 1 }
        val needsOrder = localSongs.isNotEmpty() &&
            (localSongs.any { it.trackNumber == null } || duplicated)
        if (needsOrder && orderRepairAttempted.add(album.id)) {
            try {
                repairAlbumOrder(album)
            } catch (_: Exception) {
            }
            localSongs = getAlbumSongs(album.id)
        }

        val settings = MonoMusicSettingsManager(app)
        val shouldComplete = settings.getCompleteAlbumsWithYouTubeSync()

        if (localSongs.isNotEmpty()) {
            if (shouldComplete) {
                val youtubeSongs = getYouTubeAlbumSongs(album)
                if (youtubeSongs.isNotEmpty()) {
                    return mergeLocalAndYouTubeAlbums(localSongs, youtubeSongs)
                }
            }
            return localSongs
        }

        return if (album.sourceType == "YOUTUBE") getYouTubeAlbumSongs(album) else emptyList()
    }

    private fun mergeLocalAndYouTubeAlbums(
        localSongs: List<SongUiModel>,
        youtubeSongs: List<SongUiModel>,
    ): List<SongUiModel> {
        val mergedList = mutableListOf<SongUiModel>()
        val availableLocal = localSongs.toMutableList()

        for (ytSong in youtubeSongs) {
            val matchIndex = availableLocal.indexOfFirst { local -> areSongsMatching(local, ytSong) }
            if (matchIndex != -1) {
                val localSong = availableLocal.removeAt(matchIndex)
                mergedList.add(
                    localSong.copy(trackNumber = ytSong.trackNumber, discNumber = ytSong.discNumber),
                )
            } else {
                mergedList.add(ytSong)
            }
        }

        if (availableLocal.isNotEmpty()) {
            mergedList.addAll(availableLocal.sortedBy { it.trackNumber ?: Int.MAX_VALUE })
        }
        return mergedList
    }

    private fun areSongsMatching(local: SongUiModel, remote: SongUiModel): Boolean {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        val lTitle = normalize(local.title)
        val rTitle = normalize(remote.title)
        if (lTitle == rTitle) return true
        if (lTitle.length > 3 && rTitle.length > 3) {
            if (lTitle.contains(rTitle) || rTitle.contains(lTitle)) return true
        }
        return false
    }

    private suspend fun getYouTubeAlbumSongs(album: AlbumUiModel): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            // Prefer the real album track list (correct order and track numbers).
            val tracks = try {
                val browseId = album.id.takeIf { it.startsWith("MPRE") }
                if (browseId != null) {
                    app.youTubeInnertubeClient.getAlbumTracks(browseId)
                } else {
                    app.youTubeInnertubeClient.findAlbumTracks(album.title, album.artist)
                }
            } catch (_: Exception) {
                emptyList()
            }

            if (tracks.isNotEmpty()) {
                return@withContext tracks.map { track ->
                    SongUiModel(
                        id = track.videoId,
                        title = track.title,
                        artist = track.artist ?: album.artist.orEmpty(),
                        durationText = formatDurationMillis(track.durationMillis),
                        durationMillis = track.durationMillis,
                        trackNumber = track.trackNumber,
                        discNumber = 1,
                        sourceType = "YOUTUBE",
                        audioUri = track.videoId,
                        album = album.title,
                    )
                }
            }

            // Fallback: song search. Order is search rank, so don't invent track numbers.
            val termBuilder = StringBuilder().apply {
                append(album.title)
                val artist = album.artist
                if (!artist.isNullOrBlank()) {
                    append(' ')
                    append(artist)
                }
            }

            val results = app.youTubeInnertubeClient.searchSongs(
                query = termBuilder.toString(),
                limit = 50,
            )

            val targetAlbumName = album.title.trim()
            val filtered = results.filter { result ->
                val resultAlbum = result.album?.trim().orEmpty()
                if (resultAlbum.isEmpty()) return@filter false
                resultAlbum.equals(targetAlbumName, ignoreCase = true) ||
                    resultAlbum.contains(targetAlbumName, ignoreCase = true) ||
                    targetAlbumName.contains(resultAlbum, ignoreCase = true)
            }

            (filtered.ifEmpty { results }).map { item ->
                SongUiModel(
                    id = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    durationText = formatDurationMillis(item.durationMillis),
                    durationMillis = item.durationMillis,
                    trackNumber = null,
                    discNumber = 1,
                    sourceType = "YOUTUBE",
                    audioUri = item.videoId,
                    album = album.title,
                )
            }
        }
    }

    data class ArtistContent(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>,
    )

    suspend fun getArtistContent(artistId: String): ArtistContent =
        withContext(Dispatchers.IO) {
            val songs = songDao.getByArtistKey(artistId)
            ArtistContent(
                songs = songs.map { it.toUiModel() },
                albums = deriveAlbums(songs).sortedWith(
                    compareByDescending<AlbumUiModel> { it.releaseYear ?: Int.MIN_VALUE }
                        .thenBy { it.title },
                ),
            )
        }

    // ------------------------------------------------------------------
    // Tag editing
    // ------------------------------------------------------------------

    private fun writeTags(song: Song, apply: (org.jaudiotagger.tag.Tag) -> Unit) {
        com.calmapps.calmmusic.data.TagWriter.writeTags(app, song.localUri, apply)
    }

    /**
     * Renames a local/downloaded album and/or its album artist: rewrites file
     * tags and re-keys the songs. Returns the updated album model, or null when
     * the album has no local songs to rename.
     */
    suspend fun renameAlbum(album: AlbumUiModel, newTitle: String, newArtist: String? = null): AlbumUiModel? {
        return withContext(Dispatchers.IO) {
            val songs = songDao.getByAlbumKey(album.id).filter { it.hasLocalCopy }
            if (songs.isEmpty()) return@withContext null

            val albumArtistName = newArtist?.takeIf { it.isNotBlank() }
                ?: album.artist?.takeIf { it.isNotBlank() }
                ?: songs.first().artist

            val updated = songs.map { song ->
                writeTags(song) { tag ->
                    tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, newTitle)
                    tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, albumArtistName)
                }
                val artistKey = Song.artistKeyOf(song.artist, albumArtistName)
                song.copy(
                    album = newTitle,
                    albumArtist = albumArtistName,
                    artistKey = artistKey,
                    albumKey = Song.albumKeyOf(artistKey, newTitle),
                )
            }
            songDao.upsertAll(updated)
            refreshLibraryFromDatabase()

            album.copy(id = updated.first().albumKey ?: album.id, title = newTitle, artist = albumArtistName)
        }
    }

    /**
     * Updates title/artist of a local/downloaded song: rewrites file tags (best
     * effort) and re-keys the row. The uri fallback covers callers holding a
     * stale id, e.g. a queue restored before a scan re-keyed the song. Returns
     * false when the song cannot be found at all.
     */
    suspend fun updateSongMetadata(
        songId: String,
        audioUri: String?,
        newTitle: String,
        newArtist: String,
    ): Boolean {
        val updated = withContext(Dispatchers.IO) {
            val song = songDao.getById(songId)
                ?: audioUri?.let { uri -> songDao.getAll().firstOrNull { it.localUri == uri } }
                ?: return@withContext false

            writeTags(song) { tag ->
                tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, newTitle)
                tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, newArtist)
            }

            val artistKey = Song.artistKeyOf(newArtist, song.albumArtist)
            songDao.upsertAll(
                listOf(
                    song.copy(
                        title = newTitle,
                        artist = newArtist,
                        artistKey = artistKey,
                        albumKey = Song.albumKeyOf(artistKey, song.album),
                    ),
                ),
            )
            true
        }
        if (updated) refreshLibraryFromDatabase()
        return updated
    }

    /**
     * Orders a local album by the canonical YouTube Music track list, writing
     * track and disc numbers into the rows and file tags (best effort).
     * Returns matched/total, or null when no canonical track list was found.
     */
    private suspend fun repairAlbumOrder(album: AlbumUiModel): Pair<Int, Int>? {
        val canonical = getYouTubeAlbumSongs(album)
        if (canonical.none { it.trackNumber != null }) return null

        val result = withContext(Dispatchers.IO) {
            val locals = songDao.getByAlbumKey(album.id)
            val updated = mutableListOf<Song>()
            for (song in locals) {
                val match = canonical.firstOrNull { yt ->
                    yt.trackNumber != null && areSongsMatching(song.toUiModel(), yt)
                } ?: continue
                writeTags(song) { tag ->
                    tag.setField(org.jaudiotagger.tag.FieldKey.TRACK, match.trackNumber.toString())
                    match.discNumber?.let { tag.setField(org.jaudiotagger.tag.FieldKey.DISC_NO, it.toString()) }
                }
                updated += song.copy(
                    trackNumber = match.trackNumber,
                    discNumber = match.discNumber ?: song.discNumber,
                )
            }
            if (updated.isNotEmpty()) songDao.upsertAll(updated)
            updated.size to locals.size
        }
        refreshLibraryFromDatabase()
        return result
    }

    // ------------------------------------------------------------------
    // Library mutations
    // ------------------------------------------------------------------

    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
    ): LibraryRepository.SyncStats {
        val stats = libraryRepository.sync(includeLocal, folders) { processed, total ->
            onScanProgress(if (total > 0) processed.toFloat() / total else 1f)
        }
        refreshLibraryFromDatabase()
        return stats
    }

    suspend fun addStreamingSongToLibrary(song: SongUiModel) {
        if (song.sourceType != "YOUTUBE") return

        try {
            var songToSave = song
            // Singles from search carry no track number; pull it from the album.
            if (songToSave.trackNumber == null && !songToSave.album.isNullOrBlank()) {
                val tempAlbum = AlbumUiModel(
                    id = "",
                    title = songToSave.album!!,
                    artist = songToSave.artist,
                    sourceType = "YOUTUBE",
                    releaseYear = null,
                )
                val match = getYouTubeAlbumSongs(tempAlbum).firstOrNull { areSongsMatching(it, songToSave) }
                if (match?.trackNumber != null) {
                    songToSave = songToSave.copy(
                        trackNumber = match.trackNumber,
                        discNumber = match.discNumber ?: 1,
                    )
                }
            }

            // Group under an existing album's artist when the album is already known.
            val inferredAlbumArtist = songToSave.album?.let { albumTitle ->
                _libraryAlbums.value.firstOrNull { it.title.equals(albumTitle, ignoreCase = true) }?.artist
            }?.takeIf { it.isNotBlank() }

            val artistKey = Song.artistKeyOf(songToSave.artist, inferredAlbumArtist)
            libraryRepository.addStreamSong(
                Song(
                    id = songToSave.id,
                    title = songToSave.title,
                    artist = songToSave.artist.ifBlank { "Unknown artist" },
                    albumArtist = inferredAlbumArtist,
                    album = songToSave.album?.takeIf { it.isNotBlank() },
                    trackNumber = songToSave.trackNumber,
                    discNumber = songToSave.discNumber,
                    durationMillis = songToSave.durationMillis,
                    releaseYear = null,
                    artistKey = artistKey,
                    albumKey = Song.albumKeyOf(artistKey, songToSave.album?.takeIf { it.isNotBlank() }),
                    localUri = null,
                    localLastModified = null,
                    localSizeBytes = null,
                ),
            )
            refreshLibraryFromDatabase()
        } catch (_: Exception) {
        }
    }

    fun removeSongFromLibrary(song: SongUiModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.deleteByIds(listOf(song.id))
            }
            refreshLibraryFromDatabase()
        }
    }

    /**
     * Permanently delete a local song, including its underlying file and any
     * playlist memberships. Returns true only when the file is actually gone;
     * otherwise the row is kept so a rescan doesn't silently resurrect a song
     * the user believes deleted.
     */
    suspend fun deleteLocalMediaSong(song: SongUiModel): Boolean {
        if (song.sourceType != "LOCAL_FILE" && song.sourceType != "YOUTUBE_DOWNLOAD") return false

        return try {
            val fileGone = withContext(Dispatchers.IO) {
                deleteUnderlyingFile(song.audioUri ?: song.id)
            }
            if (!fileGone) return false

            withContext(Dispatchers.IO) {
                playlistDao.deleteTracksForSongId(song.id)
                songDao.deleteByIds(listOf(song.id))
            }
            refreshLibraryFromDatabase()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true when the file no longer exists afterwards. */
    private fun deleteUnderlyingFile(uriString: String): Boolean {
        if (uriString.isBlank()) return true
        return try {
            val uri = Uri.parse(uriString)
            when {
                uri.scheme == null || uri.scheme == "file" -> {
                    val file = uri.path?.let { File(it) } ?: return true
                    !file.exists() || file.delete()
                }

                uri.authority == android.provider.MediaStore.AUTHORITY -> {
                    try {
                        app.contentResolver.delete(uri, null, null) > 0
                    } catch (_: Exception) {
                        false
                    }
                }

                else -> {
                    val doc = DocumentFile.fromSingleUri(app, uri) ?: return false
                    if (!doc.exists()) return true
                    val deleted = try {
                        android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri)
                    } catch (_: Exception) {
                        false
                    }
                    deleted || !doc.exists()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackMonitorJob?.cancel()
    }

    init {
        viewModelScope.launch {
            try {
                libraryRepository.sync(
                    includeLocal = app.settingsManager.includeLocalMusic.value,
                    folders = app.settingsManager.localMusicFolders.value,
                )
            } catch (_: Exception) {
            }

            refreshLibraryFromDatabase()

            // Restore the last queue without starting playback.
            val snapshot = withContext(Dispatchers.IO) { nowPlayingStorage.load() }
            if (snapshot != null && snapshot.queueSongIds.isNotEmpty()) {
                val songsById = withContext(Dispatchers.IO) {
                    snapshot.queueSongIds.chunked(500)
                        .flatMap { songDao.getByIds(it) }
                        .associateBy { it.id }
                }
                val queue = snapshot.queueSongIds.mapNotNull { songsById[it]?.toUiModel() }
                if (queue.isNotEmpty()) {
                    val index = snapshot.currentIndex?.takeIf { it in queue.indices } ?: 0
                    val current = queue[index]
                    _playbackState.value = PlaybackState(
                        playbackQueue = queue,
                        playbackQueueIndex = index,
                        originalPlaybackQueue = if (snapshot.isShuffleOn) queue else emptyList(),
                        repeatMode = when (snapshot.repeatModeKey) {
                            NowPlayingRepeatModeKeys.QUEUE -> RepeatMode.QUEUE
                            NowPlayingRepeatModeKeys.ONE -> RepeatMode.ONE
                            else -> RepeatMode.OFF
                        },
                        isShuffleOn = snapshot.isShuffleOn,
                        currentSongId = current.id,
                        nowPlayingSong = current,
                        isPlaybackPlaying = false,
                        nowPlayingPositionMs = snapshot.positionMs,
                        nowPlayingDurationMs = current.durationMillis ?: 0L,
                    )
                }
            }

            _isLoadingSongs.value = false
            _isLoadingAlbums.value = false

            // Give pre-rewrite downloads their YouTube identity back.
            try {
                if (libraryRepository.identifyLocalSongs() > 0) refreshLibraryFromDatabase()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MonoMusicViewModel::class.java)) {
                        return MonoMusicViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${'$'}modelClass")
                }
            }
    }
}

data class PlaybackState(
    val playbackQueue: List<SongUiModel> = emptyList(),
    val playbackQueueIndex: Int? = null,
    val originalPlaybackQueue: List<SongUiModel> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val currentSongId: String? = null,
    val nowPlayingSong: SongUiModel? = null,
    val isPlaybackPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val nowPlayingPositionMs: Long = 0L,
    val nowPlayingDurationMs: Long = 0L,
)

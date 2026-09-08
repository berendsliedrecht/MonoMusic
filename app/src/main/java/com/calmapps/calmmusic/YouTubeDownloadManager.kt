package com.calmapps.calmmusic

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.LocalMusicScanner
import com.calmapps.calmmusic.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class YouTubeDownloadStatus(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val progress: Float,
    val state: State,
    val errorMessage: String? = null,
) {
    enum class State { PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELED, SKIPPED }
}

class YouTubeDownloadManager(
    private val app: CalmMusic,
    private val appScope: CoroutineScope,
) {
    private val client = OkHttpClient()

    private val _downloads = MutableStateFlow<List<YouTubeDownloadStatus>>(emptyList())
    val downloads: StateFlow<List<YouTubeDownloadStatus>> = _downloads.asStateFlow()

    private val jobsById = mutableMapOf<String, Job>()

    // Caps parallel downloads so bulk (album) enqueues stay queued as PENDING.
    private val downloadSemaphore = Semaphore(3)

    fun enqueueDownload(song: com.calmapps.calmmusic.ui.SongUiModel, albumArtist: String? = null) {
        val id = UUID.randomUUID().toString()
        val initial = YouTubeDownloadStatus(
            id = id,
            songId = song.id,
            title = song.title,
            artist = song.artist,
            progress = 0f,
            state = YouTubeDownloadStatus.State.PENDING,
        )
        _downloads.value = _downloads.value + initial

        val job = appScope.launch {
            val context = app.applicationContext
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)

            if (musicDir == null) {
                updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.FAILED, errorMessage = "Storage inaccessible") }
                return@launch
            }

            if (!musicDir.exists()) musicDir.mkdirs()

            val alreadyDownloaded = try {
                findExistingLocalCopy(song) != null
            } catch (_: Exception) {
                false
            }
            if (alreadyDownloaded) {
                updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.SKIPPED, progress = 1f) }
                return@launch
            }

            var errorMessage: String? = null
            val ok = try {
                downloadSemaphore.withPermit {
                    updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.IN_PROGRESS) }
                    performYouTubeDownloadInternal(
                        app = app,
                        song = song,
                        albumArtist = albumArtist,
                        targetDir = musicDir,
                        context = context,
                        client = client,
                        onProgress = { progress ->
                            updateDownload(id) { status -> status.copy(progress = progress.coerceIn(0f, 1f)) }
                        },
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: e.javaClass.simpleName ?: "Unknown error"
                false
            }

            updateDownload(id) { status ->
                status.copy(
                    progress = if (ok) 1f else status.progress,
                    state = if (ok) YouTubeDownloadStatus.State.COMPLETED else YouTubeDownloadStatus.State.FAILED,
                    errorMessage = if (ok) null else (errorMessage ?: status.errorMessage ?: "Unknown error"),
                )
            }
        }

        jobsById[id] = job
    }

    fun cancelDownload(id: String) {
        jobsById[id]?.cancel()
        jobsById.remove(id)
        updateDownload(id) { it.copy(state = YouTubeDownloadStatus.State.CANCELED) }
    }

    fun clearFinishedDownloads() {
        _downloads.value = _downloads.value.filterNot { status ->
            status.state == YouTubeDownloadStatus.State.COMPLETED ||
                    status.state == YouTubeDownloadStatus.State.FAILED ||
                    status.state == YouTubeDownloadStatus.State.CANCELED ||
                    status.state == YouTubeDownloadStatus.State.SKIPPED
        }
    }

    /** Songs that do not already have a matching local/downloaded copy. */
    suspend fun filterNotDownloaded(songs: List<com.calmapps.calmmusic.ui.SongUiModel>): List<com.calmapps.calmmusic.ui.SongUiModel> =
        songs.filter { song ->
            try {
                findExistingLocalCopy(song) == null
            } catch (_: Exception) {
                true
            }
        }

    private suspend fun findExistingLocalCopy(song: com.calmapps.calmmusic.ui.SongUiModel): SongEntity? {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

        val title = normalize(song.title)
        if (title.isEmpty()) return null

        val songDao = CalmMusicDatabase.getDatabase(app).songDao()
        val locals = withContext(Dispatchers.IO) {
            songDao.getSongsBySourceType("YOUTUBE_DOWNLOAD") + songDao.getSongsBySourceType("LOCAL_FILE")
        }

        return locals.firstOrNull { local ->
            if (normalize(local.title) != title) return@firstOrNull false

            val localArtist = normalize(local.artist)
            val remoteArtist = normalize(song.artist)
            val artistMatches = localArtist.isEmpty() || remoteArtist.isEmpty() ||
                    localArtist.contains(remoteArtist) || remoteArtist.contains(localArtist)

            val durationMatches = local.durationMillis == null || song.durationMillis == null ||
                    kotlin.math.abs(local.durationMillis - song.durationMillis) < 2500L

            artistMatches && durationMatches
        }
    }

    private fun updateDownload(id: String, transform: (YouTubeDownloadStatus) -> YouTubeDownloadStatus) {
        _downloads.value = _downloads.value.map { status ->
            if (status.id == id) transform(status) else status
        }
    }
}

/**
 * Shared internal implementation of the YouTube download pipeline.
 */
@OptIn(UnstableApi::class)
internal suspend fun performYouTubeDownloadInternal(
    app: CalmMusic,
    song: com.calmapps.calmmusic.ui.SongUiModel,
    albumArtist: String?,
    targetDir: File,
    context: Context,
    client: OkHttpClient,
    onProgress: (Float) -> Unit,
): Boolean {
    var tmpFile: File? = null
    try {
        val videoId = song.id
        val TAG = "YouTubeDownload"

        val streamUrl = withContext(Dispatchers.IO) {
            try {
                val url = app.youTubeInnertubeClient.getBestAudioUrl(videoId)
                Log.i(TAG, "[$videoId] Resolved URL via InnerTube/Piped")
                url
            } catch (e: Exception) {
                Log.w(TAG, "[$videoId] InnerTube/Piped failed: ${e.message}. Falling back to NewPipe.")
                val url = app.youTubeStreamResolver.getDownloadAudioUrl(videoId)
                Log.i(TAG, "[$videoId] Resolved URL via NewPipe")
                url
            }
        }

        val safeTitle = (song.title.ifBlank { videoId })
            .replace(Regex("""[\\\\/:*?\"<>|]"""), "_")
        // Include the artist so same-titled tracks (e.g. two versions of one song)
        // don't overwrite each other's files.
        val safeArtist = song.artist.takeIf { it.isNotBlank() }
            ?.replace(Regex("""[\\\\/:*?\"<>|]"""), "_")
        val fileName = if (safeArtist != null) "$safeTitle - $safeArtist.m4a" else "$safeTitle.m4a"
        val targetFile = File(targetDir, fileName)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        tmpFile = withContext(Dispatchers.IO) {
            File.createTempFile("yt-$videoId-", ".m4a", context.cacheDir)
        }

        val downloadSuccess = withContext(Dispatchers.IO) {
            val userAgent = YouTubeStreamResolver.NEWPIPE_USER_AGENT

            val probeRequest = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent)
                .head()
                .build()

            val (contentLength, supportsRanges) = client.newCall(probeRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Probe failed: ${response.code}")
                }
                val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                length to acceptRanges
            }

            if (contentLength > 0L && supportsRanges) {
                val chunkCount = 4.coerceAtMost(((contentLength / (5L * 1024 * 1024)).toInt() + 1).coerceAtLeast(2))
                val chunkSize = contentLength / chunkCount
                val downloaded = AtomicLong(0L)

                kotlinx.coroutines.coroutineScope {
                    repeat(chunkCount) { index ->
                        val start = index * chunkSize
                        val endExclusive = if (index == chunkCount - 1) contentLength else (start + chunkSize)
                        val end = endExclusive - 1

                        launch(Dispatchers.IO) {
                            val rangeRequest = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", userAgent)
                                .addHeader("Range", "bytes=$start-$end")
                                .build()

                            client.newCall(rangeRequest).execute().use { response ->
                                if (!response.isSuccessful) {
                                    throw IllegalStateException("Chunk download failed: ${response.code}")
                                }
                                val body = response.body ?: throw IllegalStateException("Empty body for chunk")

                                RandomAccessFile(tmpFile, "rw").use { raf ->
                                    val buffer = ByteArray(8 * 1024)
                                    var read: Int
                                    var offset = start
                                    while (body.byteStream().read(buffer).also { read = it } != -1) {
                                        if (read <= 0) continue
                                        synchronized(raf) {
                                            raf.seek(offset)
                                            raf.write(buffer, 0, read)
                                        }
                                        offset += read
                                        val totalSoFar = downloaded.addAndGet(read.toLong())
                                        onProgress((totalSoFar.toDouble() / contentLength.toDouble()).toFloat())
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed: ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty body")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L

                    FileOutputStream(tmpFile).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                            var readSoFar = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                if (total > 0) {
                                    readSoFar += read
                                    onProgress(readSoFar.toFloat() / total.toFloat())
                                }
                            }
                        }
                    }
                }
            }
            true
        }

        if (!downloadSuccess) return false

        withContext(Dispatchers.IO) {
            FileInputStream(tmpFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        withContext(Dispatchers.IO) {
            try {
                TagOptionSingleton.getInstance().isAndroid = true
                val audioFile = AudioFileIO.read(targetFile)
                val tag = audioFile.tagAndConvertOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, song.title)
                tag.setField(FieldKey.ARTIST, song.artist)
                if (!song.album.isNullOrBlank()) tag.setField(FieldKey.ALBUM, song.album)

                if (!albumArtist.isNullOrBlank()) {
                    tag.setField(FieldKey.ALBUM_ARTIST, albumArtist)
                }

                song.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
                song.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }

                audioFile.commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onProgress(1f)

        withContext(Dispatchers.IO) {
            try {
                val settings = app.settingsManager
                if (!settings.includeLocalMusic.value) settings.setIncludeLocalMusic(true)

                val database = CalmMusicDatabase.getDatabase(app)
                val songDao = database.songDao()
                val albumDao = database.albumDao()
                val artistDao = database.artistDao()
                val playlistDao = database.playlistDao()

                val fileUri = android.net.Uri.fromFile(targetFile)
                val existingStreamingEntity = SongEntity(
                    id = videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumId = null,
                    discNumber = song.discNumber,
                    trackNumber = song.trackNumber,
                    durationMillis = song.durationMillis,
                    sourceType = "YOUTUBE",
                    audioUri = song.audioUri ?: videoId,
                    artistId = null,
                    releaseYear = null,
                    localLastModifiedMillis = null,
                    localFileSizeBytes = null,
                )

                val scannedAudio = LocalMusicScanner.buildSongEntityFromFile(
                    context = context,
                    uri = fileUri,
                    name = targetFile.name,
                    lastModified = targetFile.lastModified(),
                    fileSize = targetFile.length(),
                    existing = existingStreamingEntity,
                )

                fun String.toIdComponent(): String =
                    trim().replace(Regex("\\s+"), " ").lowercase()

                val trackArtistKey = song.artist.toIdComponent()
                val albumKey = song.album?.toIdComponent()

                val effectiveAlbumArtist = albumArtist?.takeIf { it.isNotBlank() } ?: song.artist
                val albumArtistKey = effectiveAlbumArtist.toIdComponent()

                // Group under the album artist when the song belongs to an album.
                val artistId = if (albumKey != null) {
                    "YOUTUBE_DOWNLOAD:$albumArtistKey"
                } else {
                    "YOUTUBE_DOWNLOAD:$trackArtistKey"
                }

                val albumId = if (albumKey != null) {
                    "YOUTUBE_DOWNLOAD:$albumArtistKey:$albumKey"
                } else null

                val localSongEntity = scannedAudio.song.copy(
                    sourceType = "YOUTUBE_DOWNLOAD",
                    artistId = artistId,
                    albumId = albumId
                )

                if (artistId.isNotBlank()) {
                    artistDao.upsertAll(listOf(ArtistEntity(
                        id = artistId,
                        name = if (albumKey != null) effectiveAlbumArtist else song.artist,
                        sourceType = "YOUTUBE_DOWNLOAD"
                    )))
                }

                if (albumId != null && localSongEntity.album != null) {
                    val albumEntityArtistId = "YOUTUBE_DOWNLOAD:$albumArtistKey"

                    artistDao.upsertAll(listOf(ArtistEntity(
                        id = albumEntityArtistId,
                        name = effectiveAlbumArtist,
                        sourceType = "YOUTUBE_DOWNLOAD"
                    )))

                    albumDao.upsertAll(listOf(AlbumEntity(
                        id = albumId,
                        name = localSongEntity.album,
                        artist = effectiveAlbumArtist,
                        sourceType = "YOUTUBE_DOWNLOAD",
                        artistId = albumEntityArtistId
                    )))
                }

                songDao.upsertAll(listOf(localSongEntity))
                playlistDao.updateSongIdForAllPlaylists(oldSongId = videoId, newSongId = fileUri.toString())

                if (localSongEntity.id != videoId) {
                    songDao.deleteByIds(listOf(videoId))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    } finally {
        tmpFile?.delete()
    }
}
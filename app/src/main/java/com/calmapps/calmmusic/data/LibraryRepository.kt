package com.calmapps.calmmusic.data

import android.net.Uri
import android.os.Environment
import com.calmapps.calmmusic.MonoMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the library: syncing songs from disk (MediaStore downloads + SAF
 * folders) into the database and repairing legacy uri-keyed rows so ids stay
 * stable and playlists never break.
 */
class LibraryRepository(
    private val app: MonoMusic,
) {

    private val database: MonoMusicDatabase by lazy { MonoMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val playlistDao by lazy { database.playlistDao() }

    data class SyncStats(
        val totalFiles: Int,
        val addedOrUpdated: Int,
        val removed: Int,
    )

    /**
     * Bring the database in line with disk. Streamed-only songs are untouched;
     * local-backed songs are added, updated, re-linked, or pruned.
     */
    suspend fun sync(
        includeLocal: Boolean,
        folders: Set<String>,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncStats = withContext(Dispatchers.IO) {
        migrateAppDirDownloads()

        val existing = songDao.getAll()
        val scanned = LibraryScanner.scan(
            context = app,
            folderUris = if (includeLocal) folders else emptySet(),
            existing = existing,
            onProgress = onProgress,
        )

        // Reconcile each scanned file against the row already backed by the
        // same file, so identity survives events that change the content hash
        // (tag edits) and legacy uri-keyed rows merge away instead of lingering.
        val existingByUri = existing.filter { it.localUri != null }.associateBy { it.localUri!! }
        fun isVideoId(id: String) = !id.startsWith(Song.LOCAL_ID_PREFIX) &&
            !id.startsWith("content://") && !id.startsWith("file://")

        val reconciled = scanned.map { song ->
            val old = song.localUri?.let(existingByUri::get)
            when {
                old == null || old.id == song.id -> song
                // The file carries a video id tag: adopt it, playlists follow.
                isVideoId(song.id) -> song.also { relinkPlaylists(old.id, song.id) }
                // The row has a real video id the file's tags lack: keep it.
                isVideoId(old.id) -> song.copy(id = old.id)
                // Hash changed (tag edit) or legacy uri id: move to the new id.
                else -> song.also { relinkPlaylists(old.id, song.id) }
            }
        }

        val existingById = existing.associateBy { it.id }
        val changed = reconciled.filter { it != existingById[it.id] }
        songDao.upsertAll(changed)

        repairLegacyIds(existing, reconciled)
        dedupeStreamTwins()
        repairMissingKeys()

        // Prune local songs whose file vanished; stream-only rows stay.
        val presentIds = reconciled.mapTo(mutableSetOf()) { it.id }
        val vanished = existing.filter { it.localUri != null && it.id !in presentIds }
        val (localOnly, streamBacked) = vanished.partition { !it.isYouTube }
        localOnly.map { it.id }.chunked(500).forEach { songDao.deleteByIds(it) }
        for (song in streamBacked) {
            songDao.updateLocalCopy(song.id, null, null, null)
        }

        SyncStats(
            totalFiles = scanned.size,
            addedOrUpdated = changed.size,
            removed = localOnly.size,
        )
    }

    /** Adds a streamed YouTube song to the library. */
    suspend fun addStreamSong(song: Song) = withContext(Dispatchers.IO) {
        val existing = songDao.getById(song.id)
        if (existing == null) songDao.upsertAll(listOf(song))
    }

    /**
     * Rows keyed by a uri (pre-rewrite schema, carried over by the 9-to-10
     * migration) are merged into their scanned stable-id twin: primary match by
     * localUri, fallback by file name for files that moved storage locations.
     * Playlist rows follow the id.
     */
    private suspend fun repairLegacyIds(existing: List<Song>, scanned: List<Song>) {
        val legacy = existing.filter { it.id.startsWith("content://") || it.id.startsWith("file://") }
        if (legacy.isEmpty()) return

        val scannedByUri = scanned.associateBy { it.localUri }
        val scannedByName = scanned.mapNotNull { song ->
            song.localUri?.let { uri -> baseNameOf(uri)?.let { name -> name to song } }
        }.toMap()

        for (old in legacy) {
            val target = scannedByUri[old.localUri]
                ?: old.localUri?.let { baseNameOf(it)?.let(scannedByName::get) }
            if (target == null || target.id == old.id) continue
            relinkPlaylists(old.id, target.id)
        }
    }

    /**
     * A stream-only row and a local row for the same track in the same album
     * are one song: keep the video id identity and attach the file to it.
     * Pre-rewrite libraries could hold both when an album was added as a
     * stream and downloaded separately.
     */
    private suspend fun dedupeStreamTwins() {
        val all = songDao.getAll()
        val streamOnly = all.filter {
            it.isYouTube && it.localUri == null && it.albumKey != null &&
                !it.id.startsWith("content://") && !it.id.startsWith("file://")
        }
        if (streamOnly.isEmpty()) return

        fun norm(t: String) = t.lowercase().replace(Regex("[^a-z0-9]"), "")
        val localsByAlbum = all.filter { !it.isYouTube && it.localUri != null && it.albumKey != null }
            .groupBy { it.albumKey!! }

        for (stream in streamOnly) {
            val twin = localsByAlbum[stream.albumKey]?.firstOrNull { local ->
                norm(local.title) == norm(stream.title) &&
                    (local.durationMillis == null || stream.durationMillis == null ||
                        kotlin.math.abs(local.durationMillis - stream.durationMillis) < 5000)
            } ?: continue
            songDao.upsertAll(
                listOf(
                    stream.copy(
                        localUri = twin.localUri,
                        localLastModified = twin.localLastModified,
                        localSizeBytes = twin.localSizeBytes,
                        trackNumber = stream.trackNumber ?: twin.trackNumber,
                        discNumber = stream.discNumber ?: twin.discNumber,
                    ),
                ),
            )
            relinkPlaylists(twin.id, stream.id)
        }
    }

    /**
     * Local-only songs are almost always downloads that predate the embedded
     * video id tag. Match them on YouTube Music by title, artist, and duration
     * and adopt the video id, so downloads stop presenting as plain local
     * files. Unmatched songs are remembered and never retried; lookups that
     * error (offline) retry on a later run. Returns how many were adopted.
     */
    suspend fun identifyLocalSongs(): Int = withContext(Dispatchers.IO) {
        val failed = app.settingsManager.getIdentifyFailedIds()
        val candidates = songDao.getAll().filter {
            !it.isYouTube && it.localUri != null && it.durationMillis != null && it.id !in failed
        }
        if (candidates.isEmpty()) return@withContext 0

        fun norm(t: String) = t.lowercase().replace(Regex("[^a-z0-9]"), "")
        var adopted = 0
        val newFailures = mutableSetOf<String>()

        for (song in candidates) {
            val query = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) {
                newFailures += song.id
                continue
            }
            val results = try {
                app.youTubeInnertubeClient.searchSongs(query = query, limit = 5)
            } catch (_: Exception) {
                continue
            }

            val match = results.firstOrNull { result ->
                norm(result.title) == norm(song.title) &&
                    result.durationMillis != null &&
                    kotlin.math.abs(result.durationMillis - song.durationMillis!!) < 3000 &&
                    (song.artist.isBlank() || result.artist.isBlank() ||
                        norm(result.artist).contains(norm(song.artist)) ||
                        norm(song.artist).contains(norm(result.artist)))
            }
            if (match == null || songDao.getById(match.videoId) != null) {
                newFailures += song.id
                continue
            }

            // A tagless file (blank artist) takes YouTube's metadata; otherwise
            // the file's own tags stay authoritative and only the id changes.
            val tagless = song.artist.isBlank()
            val artist = if (tagless) match.artist else song.artist
            val title = if (tagless) match.title else song.title
            val album = song.album ?: match.album?.takeIf { tagless }
            val artistKey = Song.artistKeyOf(artist, song.albumArtist)
            songDao.upsertAll(
                listOf(
                    song.copy(
                        id = match.videoId,
                        title = title,
                        artist = artist,
                        album = album,
                        artistKey = artistKey,
                        albumKey = Song.albumKeyOf(artistKey, album),
                    ),
                ),
            )
            relinkPlaylists(song.id, match.videoId)
            TagWriter.writeTags(app, song.localUri) { tag ->
                tag.setField(org.jaudiotagger.tag.FieldKey.CUSTOM1, LibraryScanner.videoIdTagValue(match.videoId))
                if (tagless) {
                    tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                    tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
                    album?.let { tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, it) }
                }
            }
            adopted++
        }

        if (newFailures.isNotEmpty()) app.settingsManager.addIdentifyFailedIds(newFailures)
        adopted
    }

    /** Moves playlist rows from [oldId] to [newId] and drops the old song row. */
    private suspend fun relinkPlaylists(oldId: String, newId: String) {
        playlistDao.deleteTracksSupersededBy(oldId, newId)
        playlistDao.updateSongIdForAllPlaylists(oldId, newId)
        songDao.deleteByIds(listOf(oldId))
    }

    /** Recomputes grouping keys for rows that lack them (post-migration). */
    private suspend fun repairMissingKeys() {
        val missing = songDao.getAll().filter { it.artistKey == null && it.artist.isNotBlank() }
        if (missing.isEmpty()) return
        songDao.upsertAll(
            missing.map { song ->
                val artistKey = Song.artistKeyOf(song.artist, song.albumArtist)
                song.copy(
                    artistKey = artistKey,
                    albumKey = Song.albumKeyOf(artistKey, song.album),
                )
            },
        )
    }

    private fun baseNameOf(uriString: String): String? =
        try {
            Uri.parse(uriString).lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            null
        }

    /**
     * Move downloads out of the legacy app-specific dirs into MediaStore. Runs on
     * every sync but is a no-op once the dirs are empty. Needs no permission:
     * the app may read its own dirs and insert its own media freely.
     */
    private fun migrateAppDirDownloads() {
        val appDirs = app.getExternalFilesDirs(Environment.DIRECTORY_MUSIC).filterNotNull()
        // Keyed by name alone: inserting under a name MediaStore already tracks
        // (even as a stale row) silently creates a "name (1)" twin.
        val publishedSizeByName = MediaStoreSongs.queryAll(app)
            .associate { it.displayName to it.sizeBytes }
        for (dir in appDirs) {
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in LibraryScanner.AUDIO_EXTENSIONS }
                .orEmpty()
            for (file in files) {
                when (publishedSizeByName[file.name]) {
                    null -> {
                        MediaStoreSongs.insert(app, file, file.name) ?: continue
                        file.delete()
                    }
                    file.length() -> file.delete()
                    // Same name, different content: leave the file rather than
                    // risk a duplicate; a later sync retries once rows settle.
                    else -> {}
                }
            }
        }
    }
}

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

        val existingById = existing.associateBy { it.id }
        val changed = scanned.filter { it != existingById[it.id] }
        songDao.upsertAll(changed)

        repairLegacyIds(existing, scanned)
        repairMissingKeys()

        // Prune local songs whose file vanished; stream-only rows stay.
        val presentIds = scanned.mapTo(mutableSetOf()) { it.id }
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
            playlistDao.deleteTracksSupersededBy(old.id, target.id)
            playlistDao.updateSongIdForAllPlaylists(old.id, target.id)
            songDao.deleteByIds(listOf(old.id))
        }
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
        // Skip files already published (resume after an interrupted migration).
        val published = MediaStoreSongs.queryAll(app)
            .mapTo(mutableSetOf()) { it.displayName to it.sizeBytes }
        for (dir in appDirs) {
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in LibraryScanner.AUDIO_EXTENSIONS }
                .orEmpty()
            for (file in files) {
                if ((file.name to file.length()) !in published) {
                    MediaStoreSongs.insert(app, file, file.name) ?: continue
                }
                file.delete()
            }
        }
    }
}

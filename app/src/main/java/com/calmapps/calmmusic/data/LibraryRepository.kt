package com.calmapps.calmmusic.data

import android.net.Uri
import android.os.Environment
import com.calmapps.calmmusic.MonoMusic
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for performing library-related data work against the
 * local Room database and filesystem scanners.
 */
class LibraryRepository(
    private val app: MonoMusic,
) {

    private val database: MonoMusicDatabase by lazy { MonoMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }

    data class LocalResyncStats(
        val totalDiscovered: Int,
        val skippedUnchanged: Int,
        val indexedNewOrUpdated: Int,
        val deletedMissing: Int,
    )

    data class LocalResyncResult(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>,
        val artists: List<ArtistUiModel>,
        val errorMessage: String?,
        val stats: LocalResyncStats? = null,
    )

    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
        onIngestProgress: (Float) -> Unit,
    ): LocalResyncResult {
        var error: String? = null
        var stats: LocalResyncStats? = null

        try {
            if (!includeLocal) {
                onScanProgress(1f)
                onIngestProgress(0f)
                withContext(Dispatchers.IO) {
                    songDao.deleteBySourceType("LOCAL_FILE")
                    albumDao.deleteBySourceType("LOCAL_FILE")
                    artistDao.deleteBySourceType("LOCAL_FILE")
                }
                onIngestProgress(1f)
            } else {
                if (folders.isNotEmpty()) {
                    try {
                        val lastScanMillis = app.settingsManager.getLastLocalLibraryScanMillis()

                        val existingAlbumsMap = withContext(Dispatchers.IO) {
                            albumDao.getAllAlbums()
                                .filter { it.sourceType == "LOCAL_FILE" }
                                .associateBy { it.id }
                        }

                        val (scannedAudio, existingLocalSongs) = withContext(Dispatchers.IO) {
                            val existingLocalSongs = songDao.getSongsBySourceType("LOCAL_FILE")
                            val existingByUri = existingLocalSongs.associateBy { it.audioUri }
                            val scanned = LocalMusicScanner.scanFolders(
                                context = app,
                                folderUris = folders,
                                existingSongsByUri = existingByUri,
                                lastScanMillis = lastScanMillis,
                            ) { processed, total ->
                                val progress = if (total > 0) {
                                    (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                                onScanProgress(progress)
                            }
                            scanned to existingLocalSongs
                        }

                        onIngestProgress(0f)

                        fun String.normalize() = trim().replace(Regex("\\s+"), " ").lowercase()

                        // Group under the Album Artist tag when present, falling back to the
                        // track artist, so featured/guest artists don't clutter the Artists tab.
                        val artistEntities = mutableListOf<ArtistEntity>()
                        val normalizedLocalEntities = scannedAudio.map { wrapper ->
                            val entity = wrapper.song
                            val albumArtistName = (wrapper.albumArtist?.takeIf { it.isNotBlank() }
                                ?: entity.albumId?.let { existingAlbumsMap[it]?.artist })
                                ?.takeIf { it.isNotBlank() }

                            val result = if (albumArtistName != null) {
                                entity.copy(artistId = "LOCAL_FILE:" + albumArtistName.normalize())
                            } else {
                                entity
                            }

                            val artistName = albumArtistName
                                ?: entity.artist.takeIf { it.isNotBlank() }
                                ?: result.artistId?.removePrefix("LOCAL_FILE:")
                            val id = result.artistId
                            if (id != null && artistName != null) {
                                artistEntities.add(ArtistEntity(id, artistName, entity.sourceType))
                            }

                            result
                        }

                        withContext(Dispatchers.IO) {
                            val uniqueArtists = artistEntities.distinctBy { it.id }

                            onIngestProgress(0.1f)

                            val albumEntities: List<AlbumEntity> = scannedAudio
                                .mapNotNull { wrapper ->
                                    val entity = wrapper.song
                                    val id = entity.albumId ?: return@mapNotNull null
                                    val name = entity.album ?: return@mapNotNull null

                                    val artistName = wrapper.albumArtist?.takeIf { it.isNotBlank() }
                                        ?: existingAlbumsMap[id]?.artist
                                        ?: entity.artist

                                    val albumArtistId = "LOCAL_FILE:" + artistName.normalize()

                                    id to AlbumEntity(
                                        id = id,
                                        name = name,
                                        artist = artistName,
                                        sourceType = entity.sourceType,
                                        artistId = albumArtistId,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            onIngestProgress(0.2f)

                            if (normalizedLocalEntities.isEmpty() && albumEntities.isEmpty() && uniqueArtists.isEmpty()) {
                                onIngestProgress(1f)
                                songDao.deleteBySourceType("LOCAL_FILE")
                                albumDao.deleteBySourceType("LOCAL_FILE")
                                artistDao.deleteBySourceType("LOCAL_FILE")
                                return@withContext
                            }

                            val existingById = existingLocalSongs.associateBy { it.id }
                            val scannedById = normalizedLocalEntities.associateBy { it.id }

                            val songsToDelete = existingById.keys - scannedById.keys
                            val songsToUpsert = scannedById.values.filter { newEntity ->
                                val existing = existingById[newEntity.id]
                                existing == null || existing != newEntity
                            }

                            val totalDiscovered = normalizedLocalEntities.size
                            val indexedNewOrUpdated = songsToUpsert.size
                            val skippedUnchanged = (totalDiscovered - indexedNewOrUpdated).coerceAtLeast(0)
                            val deletedMissing = songsToDelete.size
                            stats = LocalResyncStats(
                                totalDiscovered = totalDiscovered,
                                skippedUnchanged = skippedUnchanged,
                                indexedNewOrUpdated = indexedNewOrUpdated,
                                deletedMissing = deletedMissing,
                            )

                            val totalWriteItems = songsToDelete.size + songsToUpsert.size + albumEntities.size + uniqueArtists.size
                            var writtenItems = 0

                            fun reportWriteProgress() {
                                if (totalWriteItems <= 0) return
                                val writeFraction = (writtenItems.toFloat() / totalWriteItems.toFloat()).coerceIn(0f, 1f)
                                val progress = 0.2f + 0.8f * writeFraction
                                onIngestProgress(progress.coerceIn(0f, 1f))
                            }

                            if (songsToDelete.isNotEmpty()) {
                                songDao.deleteByIds(songsToDelete.toList())
                                writtenItems += songsToDelete.size
                                reportWriteProgress()
                            }

                            if (songsToUpsert.isNotEmpty()) {
                                val chunkSize = 100.coerceAtMost(songsToUpsert.size)
                                songsToUpsert.chunked(chunkSize).forEach { chunk ->
                                    songDao.upsertAll(chunk)
                                    writtenItems += chunk.size
                                    reportWriteProgress()
                                }
                            }

                            albumDao.deleteBySourceType("LOCAL_FILE")
                            artistDao.deleteBySourceType("LOCAL_FILE")

                            if (albumEntities.isNotEmpty()) {
                                albumDao.upsertAll(albumEntities)
                                writtenItems += albumEntities.size
                                reportWriteProgress()
                            }
                            if (uniqueArtists.isNotEmpty()) {
                                artistDao.upsertAll(uniqueArtists)
                                writtenItems += uniqueArtists.size
                                reportWriteProgress()
                            }

                            onIngestProgress(1f)
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to scan local music"
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        songDao.deleteBySourceType("LOCAL_FILE")
                        albumDao.deleteBySourceType("LOCAL_FILE")
                        artistDao.deleteBySourceType("LOCAL_FILE")
                    }
                }
            }

            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                val songsFromDb = songDao.getAllSongs()
                val albumsFromDb = albumDao.getAllAlbums()
                songsFromDb to albumsFromDb
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }

            val songModels = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = com.calmapps.calmmusic.formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }
            val albumIdToYear: Map<String, Int?> = allSongs
                .mapNotNull { entity ->
                    val albumId = entity.albumId ?: return@mapNotNull null
                    albumId to entity.releaseYear
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
                .mapValues { (_, years) ->
                    years.filterNotNull().maxOrNull()
                }

            val albumModels = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
                    releaseYear = albumIdToYear[album.id],
                )
            }
            val artistModels = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
                )
            }

            app.settingsManager.updateLastLocalLibraryScanMillis(System.currentTimeMillis())

            return LocalResyncResult(
                songs = songModels,
                albums = albumModels,
                artists = artistModels,
                errorMessage = error,
                stats = stats,
            )
        } catch (e: Exception) {
            val message = e.message ?: "Failed to scan local music"
            return LocalResyncResult(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                errorMessage = message,
                stats = null,
            )
        }
    }

    suspend fun ingestAppDownloadsIfMissing(): Int {
        return withContext(Dispatchers.IO) {
            val downloadsDir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@withContext 0
            val files = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()

            val existingDownloads = songDao.getSongsBySourceType("YOUTUBE_DOWNLOAD")
            val existingByUri = existingDownloads.associateBy { it.audioUri }

            val newSongs = mutableListOf<SongEntity>()

            for (file in files) {
                val uri = Uri.fromFile(file)
                val uriString = uri.toString()
                if (existingByUri.containsKey(uriString)) continue

                val scanned = LocalMusicScanner.buildSongEntityFromFile(
                    context = app,
                    uri = uri,
                    name = file.name,
                    lastModified = file.lastModified(),
                    fileSize = file.length(),
                    existing = null,
                )
                newSongs += scanned.song.copy(sourceType = "YOUTUBE_DOWNLOAD")
            }

            // The scanner keys artist/album ids with a LOCAL_FILE prefix and creates no
            // artist/album rows, which left ingested downloads invisible in the Artists
            // and Albums lists. Re-key those songs, then recreate any missing artist and
            // album rows (also repairs rows from earlier versions or a rebuilt database).
            fun String.toIdComponent(): String = trim().replace(Regex("\\s+"), " ").lowercase()

            val toRekey = newSongs + existingDownloads.filter {
                it.artistId == null || it.artistId.startsWith("LOCAL_FILE:")
            }
            val rekeyed = toRekey.map { song ->
                val artistKey = song.artist.toIdComponent().takeIf { it.isNotBlank() }
                val albumKey = song.album?.toIdComponent()?.takeIf { it.isNotBlank() }
                song.copy(
                    artistId = artistKey?.let { "YOUTUBE_DOWNLOAD:$it" },
                    albumId = if (artistKey != null && albumKey != null) {
                        "YOUTUBE_DOWNLOAD:$artistKey:$albumKey"
                    } else null,
                )
            }
            if (rekeyed.isNotEmpty()) {
                songDao.upsertAll(rekeyed)
            }

            val allDownloads = songDao.getSongsBySourceType("YOUTUBE_DOWNLOAD")
            val knownArtistIds = artistDao.getAllArtists().mapTo(mutableSetOf()) { it.id }
            val knownAlbumIds = albumDao.getAllAlbums().mapTo(mutableSetOf()) { it.id }

            val missingArtists = mutableMapOf<String, ArtistEntity>()
            val missingAlbums = mutableMapOf<String, AlbumEntity>()
            for (song in allDownloads) {
                val artistId = song.artistId
                if (artistId != null && artistId !in knownArtistIds) {
                    missingArtists.getOrPut(artistId) {
                        ArtistEntity(id = artistId, name = song.artist, sourceType = "YOUTUBE_DOWNLOAD")
                    }
                }
                val albumId = song.albumId
                val albumName = song.album
                if (albumId != null && albumName != null && albumId !in knownAlbumIds) {
                    missingAlbums.getOrPut(albumId) {
                        AlbumEntity(
                            id = albumId,
                            name = albumName,
                            artist = song.artist,
                            sourceType = "YOUTUBE_DOWNLOAD",
                            artistId = artistId,
                        )
                    }
                }
            }
            if (missingArtists.isNotEmpty()) artistDao.upsertAll(missingArtists.values.toList())
            if (missingAlbums.isNotEmpty()) albumDao.upsertAll(missingAlbums.values.toList())

            newSongs.size
        }
    }
}
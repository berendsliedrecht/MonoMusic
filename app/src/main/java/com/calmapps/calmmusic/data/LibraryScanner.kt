package com.calmapps.calmmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Builds the local-backed part of the library: every audio file in the
 * MediaStore downloads folder plus the user's SAF folders becomes a [Song]
 * with its [Song.localUri] set.
 *
 * Identity: a "monomusic-yt" tag embedded at download time makes a file keep
 * its YouTube id across database loss or reinstalls; files without one get a
 * content-hash id, so a moved file keeps its identity and playlists follow.
 */
object LibraryScanner {

    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "mp4", "opus")

    /** Tag value written into [FieldKey.CUSTOM1] at download time: "monomusic-yt:<videoId>". */
    private const val VIDEO_ID_TAG_PREFIX = "monomusic-yt:"
    private val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

    fun videoIdTagValue(videoId: String): String = "$VIDEO_ID_TAG_PREFIX$videoId"

    data class Candidate(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
    )

    /**
     * Scan all sources and return the desired state of every local-backed song.
     * Unchanged files (same uri, size, mtime as [existing]) are passed through
     * without re-reading their metadata.
     */
    suspend fun scan(
        context: Context,
        folderUris: Set<String>,
        existing: List<Song>,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Song> {
        val candidates = (mediaStoreCandidates(context) + safCandidates(context, folderUris))
            .distinctBy { it.uri.toString() }

        val existingByUri = existing.filter { it.localUri != null }.associateBy { it.localUri!! }
        val result = mutableListOf<Song>()
        var processed = 0

        for (candidate in candidates) {
            val known = existingByUri[candidate.uri.toString()]
                // Legacy uri-keyed rows must re-extract so they get a stable id.
                ?.takeUnless { it.id.startsWith("content://") || it.id.startsWith("file://") }
            if (known != null &&
                known.localSizeBytes == candidate.sizeBytes &&
                known.localLastModified == candidate.lastModifiedMillis
            ) {
                result += known
            } else {
                result += extractSong(context, candidate)
            }
            processed++
            onProgress(processed, candidates.size)
        }
        return result
    }

    private fun mediaStoreCandidates(context: Context): List<Candidate> =
        MediaStoreSongs.queryAll(context)
            .filter { it.displayName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
            .map { Candidate(it.uri, it.displayName, it.sizeBytes, it.lastModifiedMillis) }

    private fun safCandidates(context: Context, folderUris: Set<String>): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        for (uriString in folderUris) {
            val treeUri = try {
                uriString.toUri()
            } catch (_: Exception) {
                continue
            }
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val stack = ArrayDeque<DocumentFile>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val dir = stack.removeFirst()
                val children = try {
                    dir.listFiles().toList()
                } catch (_: Exception) {
                    emptyList()
                }
                for (child in children) {
                    if (child.isDirectory) {
                        // Downloads are indexed via MediaStore; scanning them here
                        // would duplicate every downloaded song.
                        if (child.name == MediaStoreSongs.SUBFOLDER) continue
                        stack.add(child)
                    } else if (child.isFile) {
                        val name = child.name ?: continue
                        if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                            candidates += Candidate(child.uri, name, child.length(), child.lastModified())
                        }
                    }
                }
            }
        }
        return candidates
    }

    private fun extractSong(context: Context, candidate: Candidate): Song {
        val meta = extractMetadata(context, candidate.uri)

        val id = meta.videoId
            ?: (Song.LOCAL_ID_PREFIX + contentHash(context, candidate))

        val title = meta.title ?: candidate.name.substringBeforeLast('.', candidate.name)
        val artist = meta.artist.orEmpty()
        val artistKey = Song.artistKeyOf(artist, meta.albumArtist)

        return Song(
            id = id,
            title = title,
            artist = artist,
            albumArtist = meta.albumArtist,
            album = meta.album,
            trackNumber = meta.trackNumber,
            discNumber = meta.discNumber,
            durationMillis = meta.durationMillis,
            releaseYear = meta.year,
            artistKey = artistKey,
            albumKey = Song.albumKeyOf(artistKey, meta.album),
            localUri = candidate.uri.toString(),
            localLastModified = candidate.lastModifiedMillis,
            localSizeBytes = candidate.sizeBytes,
        )
    }

    /** sha1 of file size plus the first 128 KB; enough to follow moved files. */
    private fun contentHash(context: Context, candidate: Candidate): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(candidate.sizeBytes.toString().toByteArray())
        try {
            context.contentResolver.openInputStream(candidate.uri)?.use { input ->
                val buffer = ByteArray(8 * 1024)
                var remaining = 128 * 1024
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
        } catch (_: Exception) {
            // Size-only hash as a last resort.
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class LocalMetadata(
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val discNumber: Int?,
        val trackNumber: Int?,
        val durationMillis: Long?,
        val year: Int?,
        val videoId: String?,
    )

    private fun extractMetadata(context: Context, uri: Uri): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        var meta = try {
            retriever.setDataSource(context, uri)
            LocalMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).normalizeTagString(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).normalizeTagString(),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).normalizeTagString(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).normalizeTagString(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.take(4)?.trim()?.toIntOrNull(),
                videoId = null,
            )
        } catch (_: Exception) {
            LocalMetadata(null, null, null, null, null, null, null, null, null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Deep read: the embedded video id (and album artist when the platform
        // retriever missed it) only exist in tags jaudiotagger can reach.
        val tempFile = copyUriToTempFile(context, uri)
        if (tempFile != null) {
            try {
                TagOptionSingleton.getInstance().isAndroid = true
                val tag = AudioFileIO.read(tempFile).tag
                if (tag != null) {
                    val custom = tag.getFirst(FieldKey.CUSTOM1)
                    if (custom.startsWith(VIDEO_ID_TAG_PREFIX)) {
                        val videoId = custom.removePrefix(VIDEO_ID_TAG_PREFIX)
                        if (VIDEO_ID_PATTERN.matches(videoId)) meta = meta.copy(videoId = videoId)
                    }
                    if (meta.albumArtist.isNullOrBlank()) {
                        meta = meta.copy(albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).normalizeTagString())
                    }
                    if (meta.artist.isNullOrBlank()) {
                        meta = meta.copy(artist = tag.getFirst(FieldKey.ARTIST).normalizeTagString())
                    }
                    if (meta.album.isNullOrBlank()) {
                        meta = meta.copy(album = tag.getFirst(FieldKey.ALBUM).normalizeTagString())
                    }
                }
            } catch (_: Exception) {
                // Ignore deep scan failures
            } finally {
                tempFile.delete()
            }
        }

        return meta
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("scanner_probe", ".tmp", context.cacheDir)
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (_: Exception) {
            null
        }
    }
}

private fun String?.normalizeTagString(): String? {
    if (this == null) return null
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.fixCommonTagMojibake()
}

private fun String.fixCommonTagMojibake(): String {
    var fixed = this
    val replacements = mapOf(
        "â€™" to "’",
        "â€˜" to "‘",
        "â€œ" to "“",
        "â€ " to "”",
        "â€“" to "–",
        "â€”" to "—",
    )
    for ((bad, good) in replacements) {
        if (fixed.contains(bad)) {
            fixed = fixed.replace(bad, good)
        }
    }
    return fixed
}

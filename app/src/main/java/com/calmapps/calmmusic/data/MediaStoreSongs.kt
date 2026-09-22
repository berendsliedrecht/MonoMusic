package com.calmapps.calmmusic.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Downloaded songs live in the shared MediaStore under Music/MonoMusic, on the SD
 * card when one is mounted. Inserting and reading our own rows needs no permission;
 * [hasReadPermission] only gates files added by other apps (e.g. from a computer).
 */
object MediaStoreSongs {

    const val SUBFOLDER = "MonoMusic"
    private val RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER/"

    data class Item(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
    )

    fun hasReadPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun volumeNames(context: Context): List<String> =
        MediaStore.getExternalVolumeNames(context).toList()

    /** SD card volume when mounted, else the primary volume. */
    private fun preferredVolume(context: Context): String =
        volumeNames(context).firstOrNull { it != MediaStore.VOLUME_EXTERNAL_PRIMARY }
            ?: MediaStore.VOLUME_EXTERNAL_PRIMARY

    /** All audio under Music/MonoMusic on every mounted volume. */
    fun queryAll(context: Context): List<Item> {
        val items = mutableListOf<Item>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        for (volume in volumeNames(context)) {
            val collection = MediaStore.Audio.Media.getContentUri(volume)
            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                    arrayOf(RELATIVE_PATH),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        items += Item(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                            displayName = cursor.getString(nameCol) ?: continue,
                            sizeBytes = cursor.getLong(sizeCol),
                            lastModifiedMillis = cursor.getLong(dateCol) * 1000L,
                        )
                    }
                }
            } catch (_: Exception) {
                // Volume unmounted mid-query; skip it.
            }
        }
        return items
    }

    /**
     * Publish [source] into Music/MonoMusic, returning the new item's content URI, or
     * null when every volume rejects it. Falls back to the primary volume when the SD
     * card is full or ejected mid-write. The source file is left in place.
     */
    fun insert(context: Context, source: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
            ?: "audio/mp4"
        for (volume in listOf(preferredVolume(context), MediaStore.VOLUME_EXTERNAL_PRIMARY).distinct()) {
            val collection = MediaStore.Audio.Media.getContentUri(volume)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = try {
                resolver.insert(collection, values)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: error("No stream for $uri")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (_: Exception) {
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
            }
        }
        return null
    }
}

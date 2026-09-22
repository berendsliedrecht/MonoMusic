package com.calmapps.calmmusic.data

import android.content.Context
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.FileOutputStream

/**
 * Rewrites tags on a MediaStore/SAF file. jaudiotagger needs a real file, so
 * the audio round-trips through cache. Best effort: some MP4 layouts cannot
 * be rewritten by jaudiotagger and fail silently.
 */
object TagWriter {

    fun writeTags(context: Context, localUri: String?, apply: (Tag) -> Unit) {
        val uriString = localUri ?: return
        try {
            val uri = Uri.parse(uriString)
            val temp = File.createTempFile("tag_edit", ".m4a", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { input.copyTo(it) }
                } ?: return

                TagOptionSingleton.getInstance().isAndroid = true
                val audioFile = AudioFileIO.read(temp)
                apply(audioFile.tagAndConvertOrCreateAndSetDefault)
                audioFile.commit()

                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    temp.inputStream().use { it.copyTo(output) }
                }
            } finally {
                temp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

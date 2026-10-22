package com.monoapps.monomusic.local

import android.content.Context
import com.monoapps.monomusic.tidal.TidalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A playlist stored on this phone; may hold local and Tidal tracks. */
data class DevicePlaylist(
    val id: String,
    val title: String,
    val tracks: List<TidalTrack>,
)

/** Device playlists persisted as one JSON file in app storage. */
class DevicePlaylistStore(context: Context) {

    private val file = File(context.filesDir, "device_playlists.json")

    suspend fun load(): List<DevicePlaylist> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList()
        else runCatching { parseDevicePlaylists(file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(playlists: List<DevicePlaylist>) = withContext(Dispatchers.IO) {
        runCatching { file.writeText(devicePlaylistsJson(playlists)) }
        Unit
    }
}

// Own flat track format: Tidal's nested shape is parsed with an id > 0
// guard, which would silently drop local tracks (negative ids).

fun devicePlaylistsJson(playlists: List<DevicePlaylist>): String {
    val array = JSONArray()
    playlists.forEach { playlist ->
        array.put(
            JSONObject()
                .put("id", playlist.id)
                .put("title", playlist.title)
                .put("tracks", JSONArray().apply { playlist.tracks.forEach { put(trackJson(it)) } })
        )
    }
    return array.toString()
}

fun parseDevicePlaylists(json: String): List<DevicePlaylist> {
    val array = JSONArray(json)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val tracks = o.optJSONArray("tracks")
        DevicePlaylist(
            id = id,
            title = o.optString("title"),
            tracks = (0 until (tracks?.length() ?: 0))
                .mapNotNull { j -> tracks?.optJSONObject(j)?.let(::trackFromJson) },
        )
    }
}

private fun trackJson(t: TidalTrack): JSONObject =
    JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("artistName", t.artistName)
        .putOpt("artistId", t.artistId)
        .putOpt("albumTitle", t.albumTitle)
        .putOpt("albumId", t.albumId)
        .put("durationSec", t.durationSec)
        .put("trackNumber", t.trackNumber)
        .put("volumeNumber", t.volumeNumber)
        .put("explicit", t.explicit)
        .putOpt("audioQuality", t.audioQuality)
        .put("addedAt", t.addedAt)

private fun trackFromJson(o: JSONObject): TidalTrack? {
    val id = o.optLong("id")
    if (id == 0L) return null
    return TidalTrack(
        id = id,
        title = o.optString("title"),
        artistName = o.optString("artistName"),
        artistId = if (o.has("artistId")) o.optLong("artistId") else null,
        albumTitle = o.optString("albumTitle").takeIf { it.isNotEmpty() },
        albumId = if (o.has("albumId")) o.optLong("albumId") else null,
        durationSec = o.optInt("durationSec"),
        trackNumber = o.optInt("trackNumber"),
        volumeNumber = o.optInt("volumeNumber", 1),
        explicit = o.optBoolean("explicit"),
        audioQuality = o.optString("audioQuality").takeIf { it.isNotEmpty() },
        addedAt = o.optLong("addedAt"),
    )
}

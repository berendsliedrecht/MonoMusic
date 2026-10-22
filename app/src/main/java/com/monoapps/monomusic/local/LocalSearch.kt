package com.monoapps.monomusic.local

import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalTrack

data class LocalSearchResults(
    val tracks: List<TidalTrack>,
    val albums: List<TidalAlbum>,
    val artists: List<TidalArtist>,
    val playlists: List<DevicePlaylist>,
)

/** Case-insensitive substring match over the in-memory local library. */
fun searchLocal(
    query: String,
    tracks: List<TidalTrack>,
    albums: List<TidalAlbum>,
    artists: List<TidalArtist>,
    playlists: List<DevicePlaylist>,
): LocalSearchResults = LocalSearchResults(
    tracks = tracks.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.artistName.contains(query, ignoreCase = true) ||
            it.albumTitle?.contains(query, ignoreCase = true) == true
    },
    albums = albums.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.artistName.contains(query, ignoreCase = true)
    },
    artists = artists.filter { it.name.contains(query, ignoreCase = true) },
    playlists = playlists.filter { it.title.contains(query, ignoreCase = true) },
)

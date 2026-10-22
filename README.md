<img align="left" src="logo.svg" width="100" height="100" alt="MonoMusic Logo">

<br clear="all" />

# MonoMusic

A calm, E-ink-friendly music player for the Mudita Kompakt that plays music files stored on the phone and streams from [Tidal](https://tidal.com). Built with Mudita Mindful Design (MMD): black and white, no animations, no distractions.

You log in with your own Tidal account and MonoMusic becomes a quiet front end for it: your favorite songs, albums, artists and playlists, plus search across the full Tidal catalog. An active Tidal subscription is required for streaming. Local music needs no account at all: pick the folder your music lives in and the app works fully offline.

"Let's make technology useful again."

Originally forked from [davidraywilson/CalmMusic](https://github.com/davidraywilson/CalmMusic). Earlier versions streamed from YouTube Music; since version 3.0.0 the app is a Tidal client instead.

## What you can do

Play your Tidal favorites and playlists, search the catalog, and manage it all from the phone: favorite and unfavorite songs, albums and artists, create playlists, and add or remove songs. Changes sync to your Tidal account, so the library is the same one you see in the official apps. Playback runs as a proper media session with lock screen controls, and streaming quality is selectable in Settings (96 kbps AAC, 320 kbps AAC, or lossless FLAC).

## Local music

Copy music onto the phone (over USB, for example), open Settings, and choose the folder it lives in. The songs, their albums and artists then show up in the normal library tabs next to your Tidal music, marked with a small phone icon. Local songs are read through Android's media index, so even a library of thousands of files loads instantly without a slow file scan. This works without a Tidal account.

## Logging in

MonoMusic uses Tidal's device login, the same flow TVs use. The app shows a short code; you go to link.tidal.com on any device (your computer, for example), enter the code, and approve. Nothing is typed on the phone and MonoMusic never sees your password. The login token is stored only on the phone.

## A note on the API

MonoMusic talks to the same Tidal API the official apps use (the one the [python-tidal](https://github.com/tamland/python-tidal) project documents), because Tidal's official developer platform does not offer full-length playback to third parties. That API is unofficial and changes from time to time, so a Tidal-side change can temporarily break the app until it is updated.

## Install

Download the latest APK from the [releases page](../../releases) and sideload it, or build from source:

    ./gradlew installDebug

## Structure

- `app/src/main/java/com/calmapps/calmmusic/tidal/` - Tidal API client: OAuth device login (`TidalAuth`), session storage (`TidalSession`), endpoints and stream resolution (`TidalClient`), JSON parsing (`TidalJson`)
- `app/src/main/java/com/monoapps/monomusic/local/` - local playback: MediaStore folder listing and library scanning (`LocalLibrary`)
- `MonoMusicViewModel.kt` - app state: login, library, search, details, playback queue
- `PlaybackService.kt` - Media3 playback service; resolves `tidal://track/<id>` to a fresh stream URL when a song starts
- `MainActivity.kt` - navigation, top bar and bottom tab bar
- `ui/` - one file per screen, plus shared list rows and dialogs

## Privacy

No analytics, no trackers, no accounts other than your own Tidal login. The app connects to `tidal.com` services only: `auth.tidal.com` to log in and `api.tidal.com` to browse and stream. Search queries and playback requests go to Tidal, as they must; nothing goes anywhere else, and nothing is stored off the phone. Local playback asks for the phone's music permission and never touches the network.

## Support

If you find this app useful, consider [sponsoring me](https://github.com/sponsors/berendsliedrecht).

## License

[GPL-3.0](LICENSE)

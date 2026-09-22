<img align="left" src="logo.svg" width="100" height="100" alt="MonoMusic Logo">

<br clear="all" />

# MonoMusic

A calm, E‑ink‑friendly music player that puts your attention and privacy first.

MonoMusic brings together your **local files** and **YouTube Music** search/streaming into one quiet, distraction‑free place to listen.

"Let's make technology useful again."

Originally forked from [davidraywilson/CalmMusic](https://github.com/davidraywilson/CalmMusic), but after a major core rework, it is no longer considered a fork.

## Screenshots

<table>
<tr>
  <td><img src="MonoMusic Screens/screen_1.png" alt="MonoMusic screenshot 1"></td>
  <td><img src="MonoMusic Screens/screen_2.png" alt="MonoMusic screenshot 2"></td>
  <td><img src="MonoMusic Screens/screen_3.png" alt="MonoMusic screenshot 3"></td>
  <td><img src="MonoMusic Screens/screen_4.png" alt="MonoMusic screenshot 4"></td>
</tr>
<tr>
  <td><img src="MonoMusic Screens/screen_5.png" alt="MonoMusic screenshot 5"></td>
  <td><img src="MonoMusic Screens/screen_6.png" alt="MonoMusic screenshot 6"></td>
  <td><img src="MonoMusic Screens/screen_7.png" alt="MonoMusic screenshot 7"></td>
  <td><img src="MonoMusic Screens/screen_8.png" alt="MonoMusic screenshot 8"></td>
</tr>
<tr>
  <td><img src="MonoMusic Screens/screen_9.png" alt="MonoMusic screenshot 9"></td>
  <td><img src="MonoMusic Screens/screen_10.png" alt="MonoMusic screenshot 10"></td>
  <td><img src="MonoMusic Screens/screen_11.png" alt="MonoMusic screenshot 11"></td>
  <td><img src="MonoMusic Screens/screen_12.png" alt="MonoMusic screenshot 12"></td>
</tr>
<tr>
  <td><img src="MonoMusic Screens/screen_13.png" alt="MonoMusic screenshot 13"></td>
  <td><img src="MonoMusic Screens/screen_14.png" alt="MonoMusic screenshot 14"></td>
  <td><img src="MonoMusic Screens/screen_15.png" alt="MonoMusic screenshot 15"></td>
  <td><img src="MonoMusic Screens/screen_16.png" alt="MonoMusic screenshot 16"></td>
</tr>
<tr>
  <td><img src="MonoMusic Screens/screen_17.png" alt="MonoMusic screenshot 17"></td>
  <td><img src="MonoMusic Screens/screen_18.png" alt="MonoMusic screenshot 18"></td>
</tr>
</table>

## What makes MonoMusic different?

MonoMusic is for people who want **less noise and more music**—especially on de‑googled phones and E‑ink devices.

- **Built for E‑ink and low‑distraction screens**  
  Large text, high contrast, minimal animations, and layouts that still feel good at slow refresh rates.
- **Mindful by design**  
  No feeds, badges, or engagement tricks—just simple screens that do one job well.
- **Privacy‑respecting**  
  No tracking, no analytics SDKs, no ads. Your listening stays on your device.
- **You stay in control**  
  You choose which folders to scan, which streaming source to use, and what ends up in your library.

## What you can do with MonoMusic

### 1. Listen to your local music

- Choose exactly which folders on your device MonoMusic is allowed to scan.
- The app indexes supported audio files and builds a clean library of **songs, albums, artists, and playlists**.
- Local songs work fully **offline**—perfect for slow or no‑signal moments.

### 2. Stream from YouTube Music (no account required)

When you pick **YouTube Music** as your streaming source:

- Search YouTube Music for songs and albums from inside MonoMusic.
- Add YouTube tracks to the same queue as your local music.
- Optionally let MonoMusic **fill in missing tracks on local albums** using YouTube search results.
- See and manage active and recent **YouTube downloads** in a dedicated Downloads screen.

> Please respect artists’ rights and your local laws when streaming or downloading from YouTube.

### 3. One calm queue for everything

Regardless of where your music comes from:

- Build a single **now‑playing queue** that can mix local files and YouTube tracks.
- Use **shuffle** and **repeat** without losing your place.
- Move naturally between songs with simple previous/next controls.

### 4. Mindful playback

- A quiet **Now Playing** screen with big typography and simple controls—easy on the eyes and on E‑ink.
- Minimal chrome so the artwork, title, and basic actions are all you see.

## Getting started

1. **Install MonoMusic** on an Android device (Android 10 / API 29 or newer).  
2. **Open the app** – you’ll start with an empty library.
3. **Add local music**
   - Go to **Settings → Local music**.
   - Pick the folders that contain your audio files.
   - MonoMusic will scan and build your library of songs, albums, and artists.
4. **Search and stream (optional)**
   - Use the search screen to find songs and albums on YouTube Music.
   - No account is needed.

You can change these choices at any time.

## Privacy & data

MonoMusic is designed to stay out of your business:

- **No accounts required** for local music or YouTube search.
- **No ads, no analytics, no tracking SDKs.**
- Your settings and local library live **only on your device**.
- When you use online features:
  - YouTube‑related features talk only to YouTube/YouTube Music (and supporting streaming APIs) as needed to search and stream audio.

You can always remove folders, clear local data, or turn streaming features off if you prefer a fully offline experience.

## For developers

If you want to hack on MonoMusic or build your own APK:

- **Requirements**
  - Android Studio (Giraffe / Hedgehog or newer)
  - JDK 17
  - Android SDK Platform 35+
  - Device or emulator running Android 10 (API 29) or newer

- **Quick start**
  1. Clone this repository.
  2. Open the root folder in Android Studio.
  3. Let Gradle sync and download dependencies.
  4. Select the `app` configuration and press **Run**.

- **Useful Gradle commands (from repo root)**
  - Assemble debug APK: `./gradlew :app:assembleDebug`
  - Install debug build on a connected device: `./gradlew :app:installDebug`
  - Run unit tests: `./gradlew :app:testDebugUnitTest`
  - Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest`
  - Run Android Lint: `./gradlew :app:lintDebug`

## Contributing

Contributions are welcome—as long as they respect the core principles of **simplicity**, **privacy**, and **focus**.

If you open a pull request:

- Keep UI changes friendly to E‑ink devices (contrast, motion, density).
- Avoid adding tracking, ads, or dark patterns.
- Test on at least one real or virtual device on a supported Android version.

## Support

If you find this app useful, consider [sponsoring me](https://github.com/sponsors/berendsliedrecht).

## License

GPL‑3.0 (see `LICENSE`).

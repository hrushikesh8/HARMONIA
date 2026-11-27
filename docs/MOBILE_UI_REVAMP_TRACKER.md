# Harmonia Android: UI Revamp Tracker

This tracks the Android UI/UX revamp: what is planned, in what order, and a short
"what + why" note for every change that lands. It is meant to be read without
needing anyone to walk you through it.

## Design direction

- **Structure and behaviour (UX): Apple Music.** Simple bottom tabs, a Library that
  opens as a list (Playlists, Artists, Albums, Songs...), big artwork headers on
  album/artist/playlist pages, Now Playing as a full screen you swipe down to close.
- **Look (theme): Spotify-style.** Near-black neutral background, layered grey
  surfaces, and one accent colour used only where it means something (play,
  progress, the active tab, the song that is playing).
- **Accent: Soft Lavender.** `#A99BFF` on dark, deepened to `#5B4BC4` on light so it
  stays readable on white. Picked because the big players already own green
  (Spotify), red/pink (Apple Music, YouTube Music) and saturated purple (Deezer);
  a soft lavender is distinctive, calm and works with any album cover.

Areas: **UI** = how it looks, **UX** = how it behaves / is organised,
**BE** = data, playback engine or Android plumbing needed underneath.

Status: `Done` · `In progress` · `Planned`

## Phase 1: Foundation (everything else builds on this)

| # | Task | Area | Status |
|---|------|------|--------|
| 1.1 | Soft Lavender accent, Spotify-style dark neutrals, properly designed light theme | UI | Done |
| 1.2 | Theme choice: Follow system / Dark / Light | UX, BE | Done |
| 1.3 | Replace developer jargon in screen copy with plain language | UI, UX | Done |
| 1.4 | New font family: Figtree (bundled), no more monospace labels | UI | Done |
| 1.5 | Move to Compose Navigation, Apple-style bottom tabs, back keeps scroll position | UX, BE | Done |
| 1.6 | App icon and splash refreshed to the lavender identity | UI | Done |

## Phase 2: Signature screens

| # | Task | Area | Status |
|---|------|------|--------|
| 2.1 | Now Playing redesign: big artwork, swipe down to close, queue toggle, volume slider (lyrics toggle arrives with 4.3) | UI, UX | Done |
| 2.2 | Mini player restyle to match | UI | Done |
| 2.3 | Library landing list (Playlists, Artists, Albums, Songs, Folders, Favorites); sort is remembered and visible (Genres row arrives with 4.2) | UI, UX, BE | Done |
| 2.4 | Home sections: Recently played, Recently added, Rediscover, Top this month | UI, UX, BE | Done |
| 2.5 | Album page: hero artwork, Play / Shuffle buttons, Disc 1 / Disc 2 headers | UI, UX | Done |
| 2.6 | Artist page: hero header, top songs, albums | UI, BE | Done |
| 2.7 | Playlist page: artwork collage header, drag to reorder | UI, UX | Done |
| 2.8 | Search restyle and recent searches | UI, UX, BE | Done |

## Phase 3: Supporting screens and partly-working features

| # | Task | Area | Status |
|---|------|------|--------|
| 3.1 | Settings regrouped into clear sections, plain language | UI, UX | Done |
| 3.2 | Profile becomes "Your listening": readable stats, rated songs list | UI, UX, BE | Done |
| 3.3 | Equalizer works without music playing; save your own presets | UI, UX, BE | Done |
| 3.4 | Car Mode: progress/seek, favorite, landscape | UI, UX | Planned |
| 3.5 | Format filter chips generated from your library (OGG, Opus, ALAC...) | UI, BE | Planned |
| 3.6 | Album grid adapts to screen width / landscape / tablets | UI | Planned |
| 3.7 | Queue: auto-scroll while dragging; strict "Play next" with shuffle on | UX, BE | Planned |
| 3.10 | Restyle song / album / artist rows in category lists (still the older bordered cards) | UI | Planned |
| 3.9 | Instrumented UI tests (`androidTest`) were already out of date before the revamp (expect a Settings tab, old copy); refresh them | BE | Planned |
| 3.8 | Fix "not responding" on cold start (playback service started during app launch) | BE | Done |
| 3.11 | Faster first launch: Baseline Profile so the first screen appears quicker | BE | Planned |

## Phase 4: New features

| # | Task | Area | Status |
|---|------|------|--------|
| 4.1 | Now Playing background tinted from album art | UI, BE | Planned |
| 4.2 | Browse by genre and year | UI, UX, BE | Planned |
| 4.3 | Lyrics (embedded tags or `.lrc` files, synced scrolling; LRCLIB as an optional online source) | UI, UX, BE | Planned |
| 4.4 | A-Z fast scroll, multi-select, undo after removing | UX, BE | Planned |
| 4.5 | Playback options: crossfade, gapless, speed, volume normalisation | UX, BE | Planned |
| 4.6 | Home-screen widget, app shortcuts, "Open with Harmonia" | UI, BE | Planned |
| 4.7 | Smart playlists, M3U import/export | UI, UX, BE | Planned |
| 4.8 | Move all text to `strings.xml` (translations) | BE | Planned |
| 4.9 | MusicBrainz enrichment (currently hidden) | UI, BE | Planned |

## Change log (what we did and why)

### 1.1 Soft Lavender theme
- **What:** Replaced the "Obsidian & Sonic Amber" palette in `design/theme/Color.kt`.
  Dark: neutral near-black `#121212` with grey surface steps (`#181818`, `#242424`),
  white/grey text, lavender `#A99BFF` accent. Light: white and soft lavender-grey
  surfaces with a deeper `#5B4BC4` lavender so it passes contrast on white.
- **Why:** The old blue-black + amber looked like a technical instrument. Neutral
  greys let album artwork carry the colour, the way Spotify does, and one accent
  makes "this is tappable / playing" obvious. All text/accent pairs are WCAG AA or better.
- Old colour names are kept as aliases, so no screen code had to change for this step.

### 1.2 Follow system theme
- **What:** Settings now offers Follow system / Dark / Light (default: Follow system).
  The old dark-mode switch value is migrated automatically.
- **Why:** Most people expect apps to follow the phone's dark mode; a hard dark/light
  toggle only made sense when the light theme was an afterthought.

### 1.4 Figtree font
- **What:** Bundled Figtree (one 62 KB variable font, SIL Open Font License, licence in
  `harmonia-mobile/third_party/figtree/OFL.txt`) and set it for every text style in
  `design/theme/Typography.kt`, including the styles Material buttons and dialogs use.
  Small labels ("Jump back in", "MP3") were monospace; they are now Figtree bold.
  Numbers use tabular figures so timers don't jitter.
- **Why:** Roboto plus monospace made the app feel like a technical tool. Figtree is a
  friendly geometric font, close in spirit to Spotify's, and bundling it means it looks
  the same on every phone and works offline.

### 1.3 Plain-language copy
- **What:** Rewrote about 95 on-screen strings across Home, Library, Search, Album, Artist,
  Playlists, Profile, Settings and Car Mode. Examples: "Sovereign Offline Audio Vault" became
  "Your music, on your phone"; "telemetry events logged locally" became "plays recorded on
  this phone"; all-caps headings like "LOCAL MEDIA CATALOG" became sentence-style ("Your music").
- **Why:** The old copy read like engineering docs (ADR numbers, SQLite, SAF). A music app
  should talk like one, the way Apple Music and Spotify do.

### 1.5 Compose Navigation
- **What:** Replaced the hand-rolled screen switching in `MainActivity` (a stack of boolean
  flags) with Navigation Compose 2.9.8 and the typed routes in
  `presentation/navigation/HarmoniaNavDestination.kt`. Each bottom tab is its own nested graph.
  Album, artist, playlist, profile, settings and car mode pages are pushed onto the open tab, and
  the tab bar stays visible on them. Re-tapping the open tab returns to its first screen.
- **Why:** Before, going back rebuilt screens from scratch and lost scroll position, and adding
  a screen meant touching a long chain of flags. Now every tab remembers where you were (open an
  album in Library, switch to Search, come back: the album is still open), which is the Apple
  Music behaviour, and new screens are one `composable<Route>` entry.

### 1.6 App icon
- **What:** Kept Hrushikesh's "H" resonance mark (`assets/brand/harmonia-mark.svg`) and recoloured it:
  lavender pillars and nodes, deeper lavender inner bars, white bridge, on an ink (`#121212`)
  tile. The mark is scaled into the adaptive-icon safe zone so no launcher shape clips it.
  Added a single-colour `ic_launcher_monochrome` for Android 13+ themed icons, and deleted
  the old pre-rendered `mipmap-*dpi/*.webp` icons, which are never used with `minSdk 26`.
  The launch splash picks the new icon up automatically.
- **Why:** The old background was Android Studio's default green grid template, and the mark
  was still in the pre-lavender blues. An ink tile matches the dark app and Spotify-style look.

### 2.1 Now Playing
- **What:** Replaced the bottom sheet with a full-screen Now Playing that slides up over the tab
  bar (`presentation/playback/NowPlayingSheet.kt`). Layout follows Apple Music: grabber and
  "Playing from <album>" header, artwork as large as the screen allows (eases back slightly while
  paused), title/artist with a heart, slim scrubber with elapsed / quality badge / remaining time,
  big lavender play button between previous/next with shuffle and repeat at the edges, a volume
  slider, and sleep timer / rating / queue along the bottom. The queue swaps in place of the
  artwork with a compact "now playing" header. Close with the chevron, Back, or a swipe down.
- **Volume:** New `MediaVolume.kt` drives the phone's media volume (the same one the hardware
  keys change) and listens for volume changes so the slider stays in sync. Unit tested.
- **Why:** The old sheet looked like a settings panel: medium artwork, all-caps labels, and
  rating/format crammed next to the title. Music apps make this screen the hero; this gives
  the artwork room and keeps the controls in the places people already know from Apple/Spotify.
- **Not yet:** lyrics (task 4.3) and artwork-tinted background (task 4.1).

### 2.2 Mini player
- **What:** The mini player (`presentation/playback/MiniPlayer.kt`) is now a floating rounded card
  with a small gap above the tab bar, instead of a full-width bar glued to it. Slightly bigger
  artwork, Figtree title, plain white play/pause (no lavender circle), and the progress shown as a
  thin lavender line along the card's bottom edge. Heart, previous, play/pause, next and the swipe
  gestures (left/right to skip, up to open Now Playing) are unchanged. Light mode gets a soft shadow.
- **Why:** Matches the new Now Playing and the floating mini players in Apple Music and Spotify.
  Keeping play/pause neutral leaves the lavender for progress, so the accent isn't overused.

### 2.3 Library landing list
- **What:** The Library tab now opens on a list (new `presentation/library/LibraryHomeScreen.kt`):
  Playlists, Artists, Albums, Songs, Folders and Favorites with counts, then a "Recently added"
  album grid. Each row opens that category as its own page with a back arrow (the existing
  `LibraryScreen` in a single-category mode, route `LibraryCategory`), so no list logic was
  rewritten. Each page shows "Sorted by ..." and tapping it changes the sort.
- **Remembered sort:** Songs, Albums and Artists sort orders are saved in user preferences
  (`getLibrarySort` / `setLibrarySort`) and survive leaving the page and restarting the app.
- **Playlists moved into Library:** the bottom bar is now Home, Search and Library (Apple Music
  style). Home's playlist shortcuts open Playlists inside the Library tab.
- **Why:** Five tabs plus a row of sub-tabs was a lot to scan. Apple Music's list makes each
  category one tap away, and a sort that resets every visit was a common annoyance.

### 3.8 "Isn't responding" on cold start
- **What:** The app used to connect to the playback service inside `HarmoniaApp.onCreate`, so the
  service built its audio player (ExoPlayer) on the main thread at the same moment the first screen
  was being drawn. Android's ANR reports showed the main thread stuck in exactly those two places,
  and the service missed its 20-second start deadline. Now `HarmoniaPlayerController` waits:
  `MainActivity` calls the new `PlaybackPort.ensureConnected()` once the first frame is drawn,
  `play()` / `setQueue()` connect immediately if needed, and a 5-second fallback connects when there's no
  UI at all (Android Auto, headset buttons) so listening history still works there.
- **Result:** 3 out of 3 cold starts on the emulator with no "not responding" (before: almost every
  launch). Playback verified working afterwards.
- **Still slow on the emulator:** the first screen takes ~20 s there because it's a debug build on
  software rendering. Real phones with a release build are much faster; task 3.11 would speed up
  the first launch further.

### 2.4 Home sections
- **What:** Home gains two sections. **Top this month**: your 5 most played songs in the last 30
  days, as a numbered list. **Rediscover**: songs played 3+ times that you haven't played in over
  a month. Order is now Recently played, Top this month, Recently added, Rediscover, Albums,
  Artists, Playlists, with sentence-case headings. Also fixed the "View Library" link, which sat
  above its heading instead of beside it.
- **Backend:** one new grouped SQL query, `ListeningEventDao.observeTrackPlayStats`, counts song
  starts per track (all-time, last 30 days, last played) from existing listening history; no
  schema change or migration. The selection rules live in `presentation/home/HomeSections.kt`
  and are unit tested.
- **Why:** The listening history was already being recorded but only fed "Recently played".
  These are the sections people expect from Apple Music / Spotify Home, built purely from what
  you played on this phone (no recommendations, nothing leaves the device).

### 2.5 Album page
- **What:** Rebuilt `presentation/album/AlbumDetailScreen.kt` in the Apple Music layout: large
  centred artwork with a soft shadow, title, artist (lavender, opens the artist page), year,
  then Play (lavender) and Shuffle (grey) side by side. Songs are a clean numbered list
  (artist shown under a title only when it differs from the album artist, e.g. features), with
  "Disc 1 / Disc 2" headers for multi-disc albums and a summary at the end
  ("12 songs, 48 min", formats, "Released 2014").
- **Behaviour:** The page now follows the library live (`AlbumDetailViewModel`), so a rescan or
  removed file updates it without leaving. A load failure now says "Couldn't load this album" with
  Try again, instead of the misleading "Album not found".
- **Also fixed (app-wide):** button labels ignored their button's colours because the `labelLarge`
  text style had a fixed colour. Removed it, so every Material button gets the right label colour
  (seen as white-on-lavender text in dark mode / dark-on-deep-lavender in light mode).
- **Why:** The album page is where people start most listening; Apple Music's layout puts the
  artwork and Play first and keeps the list quiet.

### 2.6 Artist page
- **What:** Rebuilt `presentation/artist/ArtistDetailScreen.kt` in the Apple Music layout: a large
  artwork header (artist image, or their newest album cover) that fades into the page, the name
  over it and a round lavender Play button; then "1 album · 3 songs" with Shuffle, **Top songs**
  (numbered, with "Show all N songs"), **Albums** (newest first) and **Appears on** (other
  artists' albums where they're featured, e.g. "feat.").
- **Backend:** Top songs reuse the play-count query from Home (`observeTrackPlayStats`), so they're
  your most played songs by that artist; with no plays yet they fall back to album order.
  `ArtistDetailViewModel` now follows the library live and shows a real error with Try again.
- **Why:** The old page was a long flat list. Apple Music leads with the artist, then the songs
  you actually play, then the discography, which makes big artists much easier to browse.

### 2.7 Playlist page
- **What:** Rebuilt `presentation/playlists/PlaylistDetailScreen.kt` in the Apple Music layout: a
  cover that becomes a 2x2 collage once the playlist has four different album covers, the name,
  description and "12 songs, 48 min", then Play / Shuffle. Songs show artwork, artist and length.
  Rename, delete and the new "Add songs" live in the ⋮ menu (plus an "Add songs" link above the list).
- **Reordering:** Drag the ≡ handle to move a song anywhere (saved once you let go, via the new
  `PlaylistDetailViewModel.moveTrack`), swipe a song sideways to remove it. Replaces the old
  up/down arrow buttons. Screen-reader users get "Move up" / "Move down" actions on each song.
- **Why:** Up/down arrows meant many taps to move a song more than one place; drag is what people
  expect from Apple Music, Spotify and our own queue editor.

### 2.8 Search
- **What:** Restyled `presentation/search/SearchScreen.kt`: big "Search" title, a filled rounded
  search box (keyboard Search key runs the search), pill filters (All, Songs, Albums, Artists,
  Playlists) shown only while searching. Before you type: **Recent searches** (tap to search again,
  ✕ to remove, Clear for all) and **Browse your library** tiles for Songs, Albums, Artists, Playlists
  and Favorites that open those pages.
- **Backend:** Up to 8 recent searches are saved in user preferences (`getRecentSearches` /
  `setRecentSearches`); a search is remembered when you press Search or open one of its results.
  Also fixed playlist results always saying "0 songs": the search query now counts songs
  (`PlaylistDao.searchPlaylistsWithCount`).
- **Why:** Apple Music's Search starts with your recent searches and browse categories instead of
  an empty page, which makes it useful even before you type.

### 3.1 Settings
- **What:** Rebuilt `presentation/settings/SettingsScreen.kt` as Apple-style grouped lists:
  **Appearance** (theme, keep screen on), **Playback** (shuffle and repeat defaults as
  Off / All / One song, sleep timer, Car Mode), **Sound** (equalizer), **Music library**
  (song count and size, add a folder, scan for new music, your sources, rescan everything) and
  **About** (version, privacy note). Each group has a short grey title and, where useful, a
  footnote. The developer spec rows (database, Media3, SDK levels) are kept but folded under
  "Technical details". Source types now read "Phone music library", "SD card or USB drive",
  "Folder you added" instead of "Internal MediaStore" / "SAF Folder Tree".
- **Why:** The old screen was five tall cards with all-caps titles and engineering terms; people
  scan settings by section and want short labels with a one-line explanation.

### 3.2 Your listening (Profile)
- **What:** Rebuilt `presentation/profile/ProfileHubScreen.kt` as "Your listening", like Apple
  Music Replay: stat tiles for minutes listened and plays in the last 30 days, songs and favorites;
  **Top artists** (tap to open the artist), **Top songs**, **Recently played** with real song names
  and "45 min ago" style times (the old dialog showed raw track IDs), **Your ratings** as a real
  list with stars (was just a count), and a "More" list for Favorites, Car Mode, Settings, Help
  and About. Removed the placeholder "Harmonia Listener" identity and the jargon-heavy dialogs.
- **Backend:** `ListeningSummary.kt` builds all of this from data already on the phone (the
  play-count query, recent listening events, ratings); minutes are approximate (song length x plays).
  Unit tested.
- **Why:** The listening history was being recorded but shown as a debug log. This turns it into
  something people actually want to look at.

### 3.3 Equalizer
- **What:** Your band levels are now saved (keyed by band frequency) and re-applied every time
  playback starts; before, any adjustment was lost when the audio session restarted. The phone's
  real band layout is remembered the first time music plays, so the bands can be adjusted even
  while nothing is playing (changes apply on the next play). You can **save your own presets**
  ("+ Save", name it, tap to apply, long-press to delete), shown before the phone's presets.
  Restyled the block: plain "Equalizer / On / Off" with a switch, pill presets, slim sliders with
  frequency and dB on one line; no all-caps status badge.
- **Code:** `EqualizerPersistence.kt` (layout, levels, presets + plain-text codec),
  new default methods on `EqualizerUserIntentSource` (stored in
  `SharedPreferencesEqualizerUserIntentSource`), `DefaultAudioEffectManager` records the layout,
  re-applies saved levels on attach and saves levels after band/preset changes,
  `SettingsViewModel` adds offline editing and user presets. Unit tested at all three layers.
- **Note for Hrushikesh (design change):** the intent store previously documented that *only* the
  master switch is persisted and "custom frequency profiles are explicitly excluded". This task
  needed that to change. To keep it safe: levels are matched by centre frequency (a different
  output device with other bands just ignores them), clamped to the device's range, and the engine
  still never invents a band layout — it only shows one the phone actually reported. Easy to revert
  if you prefer the old rule.
- **Why:** An equalizer that forgets your settings, and can only be touched while music plays,
  isn't really usable; saved presets are standard in every music player.

## Decisions and ideas checked

### Telegram music bot as a song source (checked, not adopted)
- **Idea:** Use a Telegram bot (e.g. @Shazam_music_downloader_bot) to fetch songs and lyrics
  instead of YouTube extraction.
- **Finding:** Bots can't talk to bots, so the app would have to log in as the user's own
  Telegram account (TDLib/MTProto) and script chats with an anonymous bot. That risks the
  user's account under Telegram's API terms, breaks whenever the bot changes or disappears,
  and the audio is almost certainly unlicensed, which Google Play treats as a policy violation
  (same problem as YouTube extraction).
- **Decision:** Keep Harmonia playing files the user already has. For lyrics, look at LRCLIB
  (open synced-lyrics database) in task 4.3; for metadata, MusicBrainz (4.9).

## Done before this tracker (PR #1)
Song menu (Play next, Add to queue, Add to playlist, Go to album/artist), queue editor
(drag to reorder, swipe to remove), mini player swipe gestures and Previous button,
faster artwork loading while scrolling, plus fixes for search, folders, artist credits,
profile counts and light-theme text colours.

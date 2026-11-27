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
| 1.3 | Replace developer jargon in screen copy with plain language | UI, UX | Planned |
| 1.4 | New font family: Figtree (bundled), no more monospace labels | UI | Done |
| 1.5 | Move to Compose Navigation, Apple-style bottom tabs, back keeps scroll position | UX, BE | Planned |
| 1.6 | App icon and splash refreshed to the lavender identity (still old green/blue) | UI | Planned |

## Phase 2: Signature screens

| # | Task | Area | Status |
|---|------|------|--------|
| 2.1 | Now Playing redesign: big artwork, swipe down to close, lyrics/queue toggles, volume slider | UI, UX | Planned |
| 2.2 | Mini player restyle to match | UI | Planned |
| 2.3 | Library landing list (Playlists, Artists, Albums, Songs, Genres, Folders, Favorites); sort is remembered and visible | UI, UX, BE | Planned |
| 2.4 | Home sections: Recently played, Recently added, Rediscover, Top this month | UI, UX, BE | Planned |
| 2.5 | Album page: hero artwork, Play / Shuffle buttons, Disc 1 / Disc 2 headers | UI, UX | Planned |
| 2.6 | Artist page: hero header, top songs, albums | UI, BE | Planned |
| 2.7 | Playlist page: artwork collage header, drag to reorder | UI, UX | Planned |
| 2.8 | Search restyle and recent searches | UI, UX, BE | Planned |

## Phase 3: Supporting screens and partly-working features

| # | Task | Area | Status |
|---|------|------|--------|
| 3.1 | Settings regrouped into clear sections, plain language | UI, UX | Planned |
| 3.2 | Profile becomes "Your listening": readable stats, rated songs list | UI, UX, BE | Planned |
| 3.3 | Equalizer works without music playing; save your own presets | UI, UX, BE | Planned |
| 3.4 | Car Mode: progress/seek, favorite, landscape | UI, UX | Planned |
| 3.5 | Format filter chips generated from your library (OGG, Opus, ALAC...) | UI, BE | Planned |
| 3.6 | Album grid adapts to screen width / landscape / tablets | UI | Planned |
| 3.7 | Queue: auto-scroll while dragging; strict "Play next" with shuffle on | UX, BE | Planned |
| 3.8 | Check playback service start-up time on a real phone (emulator showed "not responding" on cold start) | BE | Planned |

## Phase 4: New features

| # | Task | Area | Status |
|---|------|------|--------|
| 4.1 | Now Playing background tinted from album art | UI, BE | Planned |
| 4.2 | Browse by genre and year | UI, UX, BE | Planned |
| 4.3 | Lyrics (embedded tags or `.lrc` files, synced scrolling) | UI, UX, BE | Planned |
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

## Done before this tracker (PR #1)
Song menu (Play next, Add to queue, Add to playlist, Go to album/artist), queue editor
(drag to reorder, swipe to remove), mini player swipe gestures and Previous button,
faster artwork loading while scrolling, plus fixes for search, folders, artist credits,
profile counts and light-theme text colours.

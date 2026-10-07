# Harmonia Visual Identity & Brand Guidelines

**Version:** 1.0  
**Status:** Approved & Official  
**Core Philosophy:** *"Harmonia owns the music. Harmonia understands the person."*

---

## 1. Brand Philosophy & Identity Overview

Harmonia is an independent, local-first personal audio ecosystem built for listeners who prioritize audio fidelity, technical precision, and sovereign ownership of their music libraries.

Unlike cloud-first streaming services or generic media players, Harmonia:
- Treats audio files with archival and scientific respect.
- Performs objective ITU-R BS.1770 signal intelligence.
- Safeguards user privacy and offline permanence.

The visual identity embodies **architectural calm, acoustic precision, and technological elegance**. It deliberately avoids generic music tropes (eighth notes, headphones, equalizer bars, vinyl grooves, or play buttons) in favor of a symbolic visual language derived from the physics of sound and harmonic resonance.

---

## 2. The Core Symbol: "The Resonant Monogram"

The official Harmonia symbol is **The Resonant Monogram** — a geometric synthesis of acoustic standing waves and the architectural letter **"H"**.

### 2.1 Geometric Construction
The symbol consists of:
1. **Dual Acoustic Pillars**:
   - **Left Vertical Pillar**: Represents *The Music* — physical audio files, lossless bitstream integrity, acoustic signal ground truth.
   - **Right Vertical Pillar**: Represents *The Person* — personal listening context, taste, memory, and sovereign ownership.
2. **The Resonant S-Curve Bridge**: A central continuous wave connecting the two pillars at their acoustic node, forming an unmistakable, balanced capital **H**.
3. **Harmonic Propagation Pips**: Symmetrical flank nodes representing acoustic frequency resonance propagating across stereo channels.

### 2.2 Proportions & Geometry
- **Aspect Ratio**: 1:1 square bounding box for the standalone mark.
- **Stem Width**: $w$ (base unit).
- **Inter-pillar Separation**: $2.2w$.
- **Corner Radii**: Smooth, continuous geometric rounding for optimal clarity from 16×16 to 1024×1024.

---

## 3. Logo System & Lockups

The Harmonia identity is provided as a cohesive vector logo system:

```text
assets/
└── brand/
    ├── harmonia-mark.svg
    ├── harmonia-lockup-horizontal.svg
    ├── harmonia-lockup-compact.svg
    ├── harmonia-monochrome-white.svg
    ├── harmonia-monochrome-black.svg
    ├── favicon.svg
    └── icon-512x512.png
```

### 3.1 Primary Logo (Horizontal Lockup)
- **File**: `harmonia-lockup-horizontal.svg`
- **Structure**: Standalone Symbol on the left + `HARMONIA` wordmark on the right.
- **Proportions**: The wordmark height is 65% of the symbol height, optically vertically centered.
- **Usage**: Application header, splash hero, official documentation, web properties.

### 3.2 Symbol-Only Mark
- **File**: `harmonia-mark.svg`
- **Structure**: Standalone Resonant Monogram.
- **Usage**: App launcher icons, macOS dock, Windows taskbar, Android/iOS home screens, window titlebars, media control watermarks.

### 3.3 Compact Lockup (Vertical)
- **File**: `harmonia-lockup-compact.svg`
- **Structure**: Centered Symbol positioned vertically above the `HARMONIA` wordmark.
- **Usage**: Mobile splash screens, square dialogs, vinyl/CD booklet liner notes.

### 3.4 Monochrome Variants
- **Files**: `harmonia-monochrome-white.svg` / `harmonia-monochrome-black.svg`
- **Structure**: 100% solid vector silhouettes (no gradients, no opacities).
- **Usage**: Single-color printing, hardware laser engraving, embossing, high-contrast monochrome displays.

---

## 4. Color Palette

The Harmonia palette draws inspiration from a dark-mode audio mastering console: deep acoustic space illuminated by precise signal frequencies.

| Role | Color Name | HEX Code | RGB | Purpose / Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Accent** | **Acoustic Teal** | `#00D2B4` | `0, 210, 180` | Primary brand mark, active playback state, verified badges |
| **Secondary Accent** | **Harmonic Violet** | `#6E56CF` | `110, 86, 207` | Intelligence layer, harmonic resonance, secondary badges |
| **Background (Deep)** | **Obsidian Space** | `#090B10` | `9, 11, 16` | Main application canvas, low-fatigue dark background |
| **Surface (Card)** | **Acoustic Charcoal** | `#141721` | `20, 23, 33` | Library cards, track detail panels, dock player background |
| **Surface (Elevated)** | **Studio Slate** | `#1E2333` | `30, 35, 51` | Popovers, modal sheets, active track row selection |
| **Text (Primary)** | **Pure Signal White** | `#F0F3F8` | `240, 243, 248` | Primary typography, wordmark, key technical measurements |
| **Text (Secondary)** | **Muted Slate** | `#8E95A5` | `142, 149, 165` | Artist names, album titles, column headers, inactive icons |
| **Text (Tertiary)** | **Dim Bitstream** | `#525866` | `82, 88, 102` | Delimiters, gridlines, inactive audio tracks |

### Light Mode Adaptability
When rendering on white or paper substrates:
- **Background**: `#F8F9FC`
- **Surface**: `#FFFFFF`
- **Wordmark / Dark Mark**: `#090B10`
- **High-Contrast Teal**: `#009E87` (WCAG AAA compliant at 7:1 contrast on white)

---

## 5. Typography

### 5.1 Wordmark
- **Typeface Style**: Geometric Sans-Serif (Geist / Space Grotesk / Söhne archetype).
- **Text**: `HARMONIA`
- **Capitalization**: Strict Uppercase.
- **Weight**: Medium to Semi-Bold (500–600 weight).
- **Letter Spacing (Tracking)**: `+0.18em` (wide, airy, modern cadence).
- **Optical Rules**: Vertically centered crossbar on `A`, baseline-anchored vertex on `M`, circular `O`.

### 5.2 User Interface Typography
- **Primary Interface Font**: **Inter** / **Geist** (`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `sans-serif`).
- **Tabular Figures**: Mandatory for audio metrics (`font-feature-settings: "tnum" 1`): prevents horizontal layout jitter when scrubbing audio timecodes (`03:42`), sample rates (`44,100 Hz`), or loudness meters (`-21.8 LUFS`).
- **Code & Audio Inspector Font**: **JetBrains Mono** for FFprobe metadata, technical specs, and SHA-256 content hashes.

---

## 6. App Icon Specification

- **Master File**: `assets/brand/icon-512x512.png`
- **Favicon Vector**: `assets/brand/favicon.svg`
- **Canvas Geometry**: 512×512 square canvas with smooth squircle curvature.
- **Content Safe Zone**: The Resonant Monogram occupies 62% of the total canvas width/height, guaranteeing no clipping in Android circular adaptive masks or iOS squircle cutouts.
- **Multi-Resolution Scaling**:
  - `512×512`: Master icon with full subtle inner sheen and acoustic radiance.
  - `128×128` / `64×64`: Dock and application launcher scale; crisp solid stems.
  - `32×32` / `16×16`: Favicon scale; optical stem thickening (+15%) and simplified nodes for instant readability.

---

## 7. Clear Space & Minimum Sizing Rules

### 7.1 Clear Space
The minimum exclusion zone around any Harmonia mark or lockup is defined as **`2X`**, where **`X`** is the width of one vertical stem of the "H" mark. No foreign graphics, typography, or UI buttons may enter this zone.

```text
       ┌───────────────────────────────┐
       │             2X                │
       │     ┌───┐                     │
       │ 2X  │ H │  HARMONIA       2X  │
       │     └───┘                     │
       │             2X                │
       └───────────────────────────────┘
```

### 7.2 Minimum Sizes
- **Horizontal Lockup**: Minimum width = `120px` (screen) / `28mm` (print).
- **Standalone Mark**: Minimum width = `16px` (screen) / `5mm` (print).

---

## 8. Incorrect Usage (Brand Violations)

To protect brand integrity, the following practices are strictly prohibited:
1. **Do not stretch or distort**: Never scale non-proportionally.
2. **Do not add literal music clichés**: Never attach headphones, musical notes, or equalizer bars to the mark.
3. **Do not modify lockup hierarchy**: Do not move the wordmark to arbitrary angles or scale it disproportionately.
4. **Do not use unapproved colors**: Never render the logo in uncalibrated saturated colors (e.g. bright red, neon yellow).
5. **Do not apply drop shadows or skeuomorphic bevels**: Harmonia is clean, flat, and architectural.
6. **Do not violate clear space**: Never crowd the mark against edge boundaries.

---

## 9. Brand Architecture & Ecosystem Extensions

The Harmonia identity scales across the three pillars of the ecosystem while preserving the unified core:

```text
                  ┌─────────────────┐
                  │    HARMONIA     │  (Master Ecosystem Mark)
                  └────────┬────────┘
           ┌───────────────┼───────────────┐
           │               │               │
  ┌────────┴────────┐ ┌────┴────────────┐ ┌┴───────────────┐
  │ Harmonia Desktop│ │ Harmonia Mobile │ │  Harmonia AI   │
  └─────────────────┘ └─────────────────┘ └────────────────┘
```

- **Harmonia Desktop**: Primary Acoustic Teal mark + subtle technical subtitle `DESKTOP`.
- **Harmonia Mobile**: Standalone Resonant Monogram inside an optimized platform-native adaptive squircle.
- **Harmonia AI**: The shared "H" mark introduces **Harmonic Violet (`#6E56CF`)** into the resonant bridge, providing a cognitive distinction while maintaining strict family coherence.

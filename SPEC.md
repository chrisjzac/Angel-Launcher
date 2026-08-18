# Spec — exact values

Everything here is lifted from `reference/launcher.jsx`. Where a number looks
arbitrary it was tuned by eye; keep it unless it feels wrong on device, then say so.

## 1 — Frame

Galaxy S23, 19.5:9 display. All vertical positions below are fractions of
**display height**, so they survive a different device.

## 2 — Launcher page

| Element | Value |
|---|---|
| Side padding | 26dp |
| Clock | Bodoni Moda 500, 60sp, tracking −0.012em, line-height 0.9 |
| Date | Mono 500, 10.5sp, tracking 0.2em, uppercase, dim |
| Weather pill | Mono 9.5sp, pill border 1dp at 7% ink, icon 12dp in accent |
| Section label | Mono 700, 9.5sp, tracking 0.22em ("Pinned", or "C — 5 apps") |
| App row | Hanken 500, 19sp, icon 19dp dim, row padding 11dp/12dp, radius 13dp |
| Ghost letter | Bodoni 600, 265sp, ink at 6% opacity, bleeding off the right edge |
| Page dots | 5dp, 22% opacity, active = accent |

Idle shows pinned apps. Touching the rail replaces them with that letter's apps
and the list stays filtered until the clock is tapped.

## 3 — Alphabet rail

Right edge, 46dp wide, spans the list area only. 26 letters, equal flex height.

- Letters with no apps: 45% opacity. With apps: 85%. Active: accent, 100%.
- Hit letter = `floor(localY / (railHeight / 26))`, clamped 0..25.
- **Fisheye**, where `d` = |letter centre − finger Y| in px:
  ```
  f     = exp(-(d / 30)^2)
  scale = 1 + 1.05 * f
  shiftX = -20 * f          // toward the list
  opacity = base + 0.6 * f
  ```
  Transform origin is the letter's **right** edge. Applies only while dragging.
- Honour reduced-motion: skip the fisheye, keep the highlight.
- Keyboard: arrow keys step letters, Escape clears.

## 4 — Page gestures

Three pages; launcher at index 1. Axis is decided once per gesture and never
revisited:

```
if (|dx| < 8 && |dy| < 8) undecided
axis = |dx| > |dy| ? HORIZONTAL : VERTICAL
commit threshold = min(70dp, 0.2 * width)
```

The rail owns its own vertical drags and must be excluded from page swipes —
this is a real conflict, since the rail sits on the edge a page swipe would
naturally start from. Compose: give the pager `nestedScroll` and let the rail
consume first.

## 5 — Quick action button

Bottom right, 48dp circle, inset 56dp from the right edge so it clears the rail.

| Gesture | Action |
|---|---|
| Tap | `Intent.ACTION_DIAL` |
| Drag up > 28dp | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` |
| Drag down > 28dp | `CameraManager.setTorchMode` toggle |

The icon swaps live under the finger — Phone → Camera above the threshold,
Flashlight below — and the button follows the drag at 30%, clamped ±10dp.
Torch on tints the button and its border with the accent.

## 6 — Weather → wallpaper

Six palettes in `kotlin/Theme.kt`. WMO `weather_code` mapping:

| Codes | Sky |
|---|---|
| 0, 1 | sunny |
| 2, 3 | clouds |
| 45, 48 | fog |
| 71–77, 85, 86 | snow |
| ≥ 95 | storm |
| everything else | rain |

Crossfade 900ms. The accent colour changes with the sky and every accented
element must follow it.

## 7 — Biometric gate

Sensor centre at **80% of display height**, horizontally centred — where the
S23's ultrasonic reader physically sits, ~25mm up from the foot of a ~141mm
display. Indicator 72dp; the real sensing area is nearer 8×8mm.

Above it, a single line of prose at 32% height. No heading, no "touch and hold"
label — both were removed on purpose.

Re-lock whenever the page loses focus, not on a timer.

## 8 — Money pane

**Payments:** Out / In cards (Bodoni 25sp) → mini row of Net, Entries, Largest
(Mono 11.5sp) → scan bar showing `N messages · N matched · N skipped` → category
bars sorted descending, width relative to the largest → transaction rows.

The skipped bucket is deliberately visible. A parser that silently drops
messages is worse than one that admits it. Keep it.

**Stocks:** portfolio value (Bodoni 42sp), total P/L, sparkline in the accent,
mini row of Invested / Today / Holdings, then per-holding rows.

## 9 — Home pane

Arranged by the owner, not fixed. The pane is a flat ordered list of widgets
packed two to a row, radius 17dp, 9dp gutter; a widget marked wide takes the
row to itself. Three shapes, chosen by the entity's domain, never stored:
thermostat (±0.5° steppers, Bodoni 46sp target), sensor (label + reading), and
tile (icon, name, state). Active tile: brighter fill and its icon in the accent.

Long-press any widget to arrange. In arrange mode the bar reads ARRANGE in the
accent with add and done beside it, widgets drag to reorder, and a tap opens
wide/narrow and remove. Add lists every entity the house exposes that is not
already placed. Nothing of this shows outside arrange mode.

Until the pane is arranged once, the layout is derived: first climate wide,
then the temperature / humidity / power sensors, then the first eight controls
— which is what the pane showed before it was arrangeable.

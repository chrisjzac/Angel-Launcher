# Rail Launcher — working agreement

An Android home launcher in the spirit of Niagara: a typographic app list
navigated by an alphabet rail, over a wallpaper that reflects local weather.
Three horizontal pages: **Home Assistant ← Launcher → Money**.

`reference/launcher.jsx` is a working prototype of the whole thing. It is the
source of truth for layout, motion and behaviour. Read it before writing UI.
`SPEC.md` holds the exact numbers. `BUILD_ORDER.md` is the sequence to work in.

## Stack

- Kotlin, Jetpack Compose, single Activity, no Fragments
- minSdk 29, targetSdk 35, Compose BOM current
- No DI framework, no Room, until something actually needs one
- ViewModel + `StateFlow` for pane state; keep gesture state local to composables

## Non-negotiables

**Design tokens live in `kotlin/Theme.kt`.** Do not invent colours or sizes.
Three typefaces, each with one job:

| Role | Face | Used for |
|---|---|---|
| Display | Bodoni Moda | clock, ghost letter, money figures, temperatures |
| Body | Hanken Grotesk | app names, device names, prose |
| Utility | JetBrains Mono | all uppercase micro-labels, tickers, metadata |

Load them with `androidx.compose.ui.text.googlefonts.GoogleFont`, not bundled TTFs.

**Restraint is the brief.** Earlier iterations stripped nearly every label
deliberately. Do not add explanatory text, tooltips, onboarding, or empty-state
illustrations unless asked. The launcher screen has no instructional copy at all
and that is intentional.

**The accent colour is derived from the weather**, not fixed. Every accent usage
reads from the active `Sky`.

## Hard constraints — read before planning

1. ~~`READ_SMS` will get the app rejected from Play.~~ **Superseded 2026-08-26:**
   this app is sideload-only (see the debug-signed release config in
   `app/build.gradle.kts`), so Play's restricted-permission review never
   applies to it. The Wealth pane now reads `READ_SMS` for inbox backfill plus
   `RECEIVE_SMS` for the live feed — history can't come from a notification
   listener, which only ever sees what arrives while access is on. Still no
   default-SMS-handler role: no `SMS_DELIVER`, no compose UI, no sending. The
   notification listener stays wired up as a secondary source and dedupes
   against SMS. Original reasoning kept below for context.
   ~~It is a restricted permission granted essentially only to default SMS
   handlers. Build the Money pane against `NotificationListenerService`
   reading bank notifications, or a manual import. `kotlin/SmsParser.kt` works
   on either source — it takes a String. Do not design around `READ_SMS`.~~
2. **Biometrics use `BiometricPrompt` with `BIOMETRIC_STRONG`.** The prototype's
   hold-to-fill ring is a simulation of a sensor the web cannot reach; on device
   the system prompt replaces it. Keep the sensor's screen position anyway (see
   SPEC) so the unlocked layout still reads correctly.
3. **Stock prices are simulated in the prototype.** Live quotes need a keyed API.
   Put the key in `local.properties`, never in source.
4. Torch needs no permission but does need the camera free.

## Working rules

- One slice per session, from `BUILD_ORDER.md`. Finish and verify before moving on.
- Run `./gradlew assembleDebug` after every slice. Do not report a slice done
  until it compiles.
- Prefer deleting code to commenting it out.
- If a prototype behaviour seems wrong, say so before changing it — several
  oddities are deliberate.

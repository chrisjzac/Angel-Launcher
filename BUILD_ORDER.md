# Build order

Vertical slices. Each one installs and runs. Do not start the next until the
current one compiles and works on device.

## 1 — Skeleton that Android will offer as a launcher
Empty Compose activity. Manifest intent filter:
`android.intent.action.MAIN` + categories `HOME`, `DEFAULT`, `LAUNCHER`.
`android:launchMode="singleTask"`, `android:stateNotNeeded="true"`,
`android:excludeFromRecents="true"`.
**Done when:** long-press Home → the app appears in the launcher chooser.

## 2 — App list
`PackageManager.queryIntentActivities` for `ACTION_MAIN` / `CATEGORY_LAUNCHER`.
Sort by label, case-insensitive. Cache in a ViewModel; refresh on
`ACTION_PACKAGE_ADDED` / `REMOVED`. Launch via the resolved component.
**Done when:** every installed app is listed and launches.

## 3 — Alphabet rail (the product)
Fisheye magnification, drag-to-filter, dim letters for empty buckets.
Maths in SPEC §3. This is the slice that matters — get the feel right before
moving on. Compare against the prototype side by side.
**Done when:** a thumb drag down the rail filters the list and letters swell
under the finger.

## 4 — Clock, date, pinned apps
Bodoni numerals. Pinned list persisted in DataStore.

## 5 — Weather wallpaper
`FusedLocationProviderClient` → Open-Meteo `current=weather_code,temperature_2m`.
Map WMO codes per SPEC §6. Crossfade between the six gradients. Accent colour
follows. Cache the last reading; never block first paint on the network.

## 6 — Quick action button
Tap dials, swipe up opens camera, swipe down toggles torch. SPEC §5.

## 7 — Pager and the two side panes
`HorizontalPager`, three pages, launcher at index 1. Gate both side panes with
`BiometricPrompt`; re-lock when the page loses focus.

## 8 — Home Assistant pane
Long-lived access token in EncryptedSharedPreferences. `GET /api/states` to
populate, websocket for live updates, `POST /api/services/...` to toggle.

## 9 — Money pane
`kotlin/SmsParser.kt` is written and its expectations are in
`reference/sms-fixtures.tsv` — wire the unit test first, then the UI.
Source messages from a notification listener, not `READ_SMS`.

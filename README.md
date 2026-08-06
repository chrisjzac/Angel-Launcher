# Angel Launcher

An Android home launcher in the spirit of Niagara: a typographic app list
navigated by an alphabet rail, over a wallpaper that reflects local weather.
Three horizontal pages: **Home Assistant ← Launcher → Money**.

- `CLAUDE.md` — the working agreement
- `SPEC.md` — exact values, lifted from the prototype
- `BUILD_ORDER.md` — the sequence the app was built in
- `reference/launcher.jsx` — the prototype; source of truth for layout, motion and behaviour

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # SmsParser against reference/sms-fixtures.tsv
```

Requires JDK 17 and an Android SDK with platform 35. Every push also builds the
debug APK in GitHub Actions and uploads it as a workflow artifact.

## Install

```
adb install -r angel-launcher-debug.apk
```

Then press Home and pick Angel Launcher. Two things need granting by hand,
both from inside the app:

- **Location** — asked on first run; drives the weather wallpaper and the accent.
- **Notification access** — the Money pane's scan bar opens the settings screen.
  `READ_SMS` is deliberately not used; bank notifications carry the same text and
  `SmsParser` takes a String from either source.

Home Assistant asks for a base URL and a long-lived token the first time the
left page is unlocked; both are kept in `EncryptedSharedPreferences`.

Stock prices are simulated. Put `quotesApiKey=...` in `local.properties` to wire
a real quotes feed — never in source.

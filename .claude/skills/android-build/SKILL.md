---
name: android-build
description: Build a native Android app from a Gradle project - assemble a debug or release APK/AAB, install a missing Android SDK, get the build onto a device, and diagnose why an Android build or its CI run failed. Use this whenever the user asks to build, compile, assemble, run, or ship an Android app or APK, wants a build to test on their phone, needs an Android toolchain set up or repaired, or is staring at a failed Gradle or Actions run - even if they never say "Gradle" or "SDK". Especially important in sandboxed or restricted-network environments, where it detects up front that Google's hosts are unreachable and routes the build to CI instead of failing over and over.
---

# Building native Android apps

An Android build needs three things that a normal JVM build does not: a JDK in
AGP's supported range, an SDK platform on disk, and reachable access to
Google's Maven repository. Miss any one and Gradle fails in a way that looks
like a project problem but is not.

So establish which of those you have **before** running anything. A single
preflight costs seconds and tells you which of three routes you are on; the
alternative is discovering it through a sequence of slow, confusing failures.

## Start here

```bash
bash .claude/skills/android-build/scripts/preflight.sh <project-dir>
```

It prints the evidence and a `ROUTE=` verdict:

| Route | Situation | What to do |
|---|---|---|
| **A** | JDK, SDK platform and hosts all fine | Build locally — below |
| **B** | Toolchain reachable, SDK or JDK not ready | Fix the gap, re-run preflight |
| **C** | Google's hosts blocked, no usable SDK | Build through CI — `references/ci-fallback.md` |

Show the user the preflight output when the route is B or C. "I can't build
here" is a claim they are entitled to see the evidence for, and the reachability
lines are that evidence.

## Route A — build locally

```bash
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signing config must exist, or this fails late
./gradlew testDebugUnitTest      # assemble does NOT run tests
./gradlew installDebug           # build and push to a connected device
```

Use the wrapper, never a system `gradle`: the wrapper pins the version the
project was built against. Prefer `--offline` when the Gradle cache is warm but
network is patchy — it fails fast instead of stalling on an unreachable host.

Report the APK path and its size. If a device is attached
(`adb devices` lists one), offer `adb install -r <apk>`.

## Route B — close the gap

**No SDK, or the wrong platform:**
```bash
bash .claude/skills/android-build/scripts/install-sdk.sh <compileSdk> [install-dir]
echo "sdk.dir=<install-dir>" >> local.properties
```
Keep `local.properties` gitignored. It encodes one machine's filesystem, so
committing it breaks the build for everyone else — and it is also where API
keys belong, which is a second reason it must never be tracked.

**JDK outside 17–21:** point `JAVA_HOME` at a supported JDK rather than
changing the project's `sourceCompatibility`. The project's Java level is a
deliberate choice; your local JDK is an accident of what got installed.

Re-run preflight afterwards to confirm, rather than assuming the fix took.

## Route C — the toolchain is unreachable

This is common in sandboxes. `dl.google.com` serves both the SDK packages and
the Google Maven repository, and **AGP, AndroidX and Compose are published
nowhere else** — no `repositories {}` edit, mirror, or Maven Central fallback
substitutes for it. Treat "Google Maven blocked" as a hard stop for local
builds and stop trying to route around it.

Two honest ways forward, and it is worth naming both:

1. **Build on CI**, then retrieve the artifact. Read
   `references/ci-fallback.md` for driving the run and getting the APK back.
   This works today and needs no permission.
2. **Ask for the sandbox's network policy to be widened** to allow
   `dl.google.com`. This is the only thing that makes local builds possible,
   and it is the user's call, not something to work around silently.

## Diagnosing failures

Read `references/diagnosing.md` when a build or CI run fails. It covers the two
traps that waste the most time: a red CI run whose APK actually built fine
(because the failing step was a later test), and Gradle's 150-frame stack trace
burying the one line that names the real error.

The distinction that matters most: **`assembleDebug` does not run tests.** If
assemble succeeded, the code compiles — a failing test after it is a separate
finding, and often one that predates the current change. Check whether the base
branch was already failing before attributing it to your work, and never
disable a test to turn a badge green.

## Getting it onto a device

When the user wants to *test* rather than just compile, the whole point is a
loop measured in seconds. `./gradlew installDebug` builds and installs over ADB
in one step; wireless debugging makes it cable-free and sidesteps Linux USB
permission problems entirely. Read `references/device-testing.md` for pairing,
the USB fallback, serving the APK over the LAN to a device with no ADB, and the
install errors worth recognising on sight.

## Delivering a build you cannot install directly

A debug APK is usually 40–80 MB, which exceeds most file-delivery limits, so
attaching it to a message often fails outright. Lead with a download link and
attempt the file only when it is comfortably under the cap. If delivery fails,
say so and why — silently omitting the artifact leaves the user waiting for
something that is never coming.

## Reporting honestly

State which check actually ran. A successful `assembleDebug` — local or on CI —
is real evidence the code compiles, and CI's compiler counts. Careful reading
of the source does not. When the only verification available was remote, say
that plainly rather than implying a local build succeeded.

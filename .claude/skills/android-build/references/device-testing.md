# Getting a build onto a real device, fast

The goal is a loop measured in seconds: change code, run one command, see it on
the device. Anything involving "export the APK, upload it somewhere, download
it on the tablet, tap through the installer" is a minute or more of hand-work
per iteration and gets abandoned within an hour.

`./gradlew installDebug` is that one command. It builds and installs over ADB
in a single step, and Gradle skips work that is already up to date, so an
incremental change lands in roughly 20-60 seconds.

## Wireless debugging (Android 11+) — the recommended setup

Wireless is worth preferring over USB even at a desk, because on Linux USB
needs udev permissions that are a classic source of "device shows as
unauthorized / no permissions" dead ends. Wireless sidesteps all of it.

One-time pairing:

1. On the device: **Settings → Developer options → Wireless debugging → on**
2. Tap **Pair device with pairing code**. It shows an IP, a port and a 6-digit
   code. Note that the *pairing* port differs from the *connect* port.
3. On the computer:

```bash
adb pair 192.168.1.50:37419        # port + code from the pairing dialog
adb connect 192.168.1.50:41235  # port shown on the main wireless-debugging screen
adb devices                        # expect: <ip>:<port>   device
```

Needs platform-tools 30+ for `adb pair`. After a reboot the port changes, so
re-run `adb connect`; the pairing itself persists.

Then, from the project root:

```bash
./gradlew installDebug
```

To follow crashes and logs while it runs:

```bash
adb logcat --pid=$(adb shell pidof -s <applicationId>)
```

## USB fallback

Enable **USB debugging** in Developer options, plug in, accept the
authorisation prompt on the device.

On Linux, if `adb devices` reports `no permissions`, the udev rules are
missing. On Debian/Ubuntu the packaged rules fix it properly:

```bash
sudo apt install android-sdk-platform-tools-common
sudo udevadm control --reload-rules && sudo udevadm trigger
```

Prefer that over hand-writing a rule with a hard-coded vendor id — the package
covers every vendor and survives swapping devices.

## No ADB at all (a tablet you would rather not put in developer mode)

Serve the APK on the LAN and download it in the tablet's browser:

```bash
./gradlew assembleDebug
cd app/build/outputs/apk/debug && python3 -m http.server 8000
# on the tablet: http://<computer-lan-ip>:8000/app-debug.apk
```

The device will ask permission to install from an unknown source. Slower than
`installDebug`, but it needs nothing installed on the device.

## Install failures worth recognising

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** — a build signed with a different key
is already installed (often a Play Store copy, or a release build over a debug
one). Uninstall first: `adb uninstall <applicationId>`. This wipes app data,
so it is not something to do silently on a device holding real state.

**`INSTALL_FAILED_INSUFFICIENT_STORAGE`** — debug APKs are large because they
carry every ABI and are not optimised. Nothing is wrong with the build.

**`adb: no devices/emulators found`** after a reboot or sleep — the wireless
port changed. Re-run `adb connect`.

**A launcher app in particular**: installing does not make it active. The user
picks it via Settings → Default apps → Home app, or by pressing Home and
choosing it. Say so rather than letting them conclude the install failed.

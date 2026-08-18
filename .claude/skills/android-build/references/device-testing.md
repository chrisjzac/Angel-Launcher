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

## Remote development: edit from a tablet, install over a VPN

A workable arrangement when the build machine is elsewhere: run a VS Code
tunnel on the build machine and open it from a tablet browser, then put the
build machine and the test device on the same Tailscale tailnet so ADB can
reach the device from outside the LAN.

```bash
# on the build machine, once
code tunnel service install     # survives reboots; `code tunnel` for a one-off
tailscale up
```

Open `vscode.dev/tunnel/<name>` on the tablet. The terminal there is a real
shell on the build machine, so `./gradlew installDebug` runs exactly as it
would locally — the tablet is a thin client, nothing is built on it.

For the agent, prefer the `claude` CLI in that terminal over the VS Code
extension. Extensions in a browser-hosted tunnel run under more constraints
than a desktop VS Code, whereas a terminal is a terminal; the CLI keeps working
when the extension host is the part that breaks.

### ADB over Tailscale

Install Tailscale on the Android device, then connect to its tailnet address
rather than its LAN address:

```bash
tailscale status                      # find the device's 100.x.y.z
adb connect 100.101.102.103:41235
```

Two things to expect:

- **Pair on the LAN first.** The pairing dialog advertises the Wi-Fi address,
  and discovery uses mDNS, which does not cross a tailnet. Pair once on the
  same network; afterwards the tailnet address works from anywhere.
- **The port changes** whenever wireless debugging restarts, so `adb connect`
  is a per-session step even though pairing persists.

This is OEM-dependent — some vendors bind the debug port to the Wi-Fi
interface only, in which case the tailnet route will not reach it. Try it
before designing a workflow around it, and fall back to being on the same
network if it does not take.

### Testing a launcher on the device you are working from

Worth thinking about before setting this up: a launcher becomes the Home app,
so on the device under test every Home press leaves whatever you were doing.
If that device is also your IDE client, you are ejected from the editor each
time you exercise the thing you are testing.

Use two devices where possible — tablet as the editor, phone as the target —
and put both on the tailnet. With one device, keep the launcher installed but
*not* set as default, and open it deliberately rather than making it Home.

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

#!/usr/bin/env bash
# Install a minimal Android SDK: command line tools, one platform, build-tools.
# Only useful when preflight reports the SDK repo as reachable - run it first.
#
# Usage: install-sdk.sh [compileSdk] [install-dir]

set -euo pipefail

API="${1:-35}"
SDK_DIR="${2:-$HOME/Android/Sdk}"
TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
case "$(uname -s)" in
  Darwin) TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" ;;
esac

# Fail here with a clear name rather than midway through with a cryptic error.
for tool in curl unzip java; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "missing: $tool" >&2
    echo "  Debian/Ubuntu: sudo apt install curl unzip openjdk-17-jdk" >&2
    exit 1
  }
done

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Fetching command line tools into $SDK_DIR"
  mkdir -p "$SDK_DIR/cmdline-tools"
  tmp=$(mktemp -d)
  curl -fSL -o "$tmp/tools.zip" "$TOOLS_ZIP_URL"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  # The zip unpacks as cmdline-tools/; sdkmanager insists on living under
  # cmdline-tools/latest/ or it cannot find its own packages.
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

# Licences must be accepted before any package will install.
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" \
  "platform-tools" "platforms;android-$API" "build-tools;$API.0.0"

echo
echo "Installed into $SDK_DIR"
echo "Point the project at it (local.properties is gitignored, which is right -"
echo "the path is specific to this machine):"
echo "    echo \"sdk.dir=$SDK_DIR\" >> local.properties"

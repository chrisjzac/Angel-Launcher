#!/usr/bin/env bash
# Decide whether a native Android build can run here, and say why.
#
# Exits 0 always: the verdict is the output, not the status. Prints a ROUTE
# line the caller acts on, plus the evidence behind it so a human can argue
# with the conclusion rather than having to re-derive it.

set -uo pipefail

PROJECT="${1:-.}"
cd "$PROJECT" 2>/dev/null || { echo "ROUTE=ERROR"; echo "reason: no such directory: $PROJECT"; exit 0; }

say() { printf '%s\n' "$*"; }

# --- JDK ---------------------------------------------------------------
JAVA_OK=no
JAVA_VER=""
if command -v java >/dev/null 2>&1; then
  # Strip the JAVA_TOOL_OPTIONS banner some environments print to stderr.
  JAVA_VER=$(java -version 2>&1 | grep -Eo '"[0-9]+(\.[0-9]+)*' | head -1 | tr -d '"' | cut -d. -f1)
  [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge 17 ] 2>/dev/null && [ "$JAVA_VER" -le 21 ] 2>/dev/null && JAVA_OK=yes
fi

# --- Android SDK -------------------------------------------------------
SDK=""
for candidate in \
  "${ANDROID_HOME:-}" \
  "${ANDROID_SDK_ROOT:-}" \
  "$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -1)" \
  "$HOME/Android/Sdk" \
  "$HOME/Library/Android/sdk" \
  /usr/local/lib/android/sdk \
  /opt/android-sdk
do
  [ -n "$candidate" ] && [ -d "$candidate/platforms" ] && { SDK="$candidate"; break; }
  [ -n "$candidate" ] && [ -d "$candidate" ] && [ -z "$SDK" ] && SDK_PARTIAL="$candidate"
done

# compileSdk the project actually asks for, so we check for the right platform.
COMPILE_SDK=$(sed -n 's/.*compileSdk[[:space:]]*=[[:space:]]*\([0-9]\{2,\}\).*/\1/p' \
  app/build.gradle.kts build.gradle.kts app/build.gradle 2>/dev/null | head -1)
PLATFORM_OK=no
if [ -n "$SDK" ] && [ -n "$COMPILE_SDK" ] && [ -d "$SDK/platforms/android-$COMPILE_SDK" ]; then
  PLATFORM_OK=yes
fi

# --- Network -----------------------------------------------------------
# A blocked CONNECT tunnel yields 000. Any real HTTP status - 200, 403, 429 -
# means the host is reachable, which is the only thing that matters here.
probe() {
  local code
  # curl prints the status via -w even when it fails, emitting 000 for a
  # refused CONNECT. Do NOT add a `|| echo 000` fallback here: curl's own 000
  # would concatenate with it and the result stops comparing equal to 000,
  # which silently turns "blocked" into "reachable".
  code=$(curl -sS -o /dev/null -m 12 -w '%{http_code}' "$1" 2>/dev/null)
  case "$code" in
    ""|000) echo blocked ;;
    *)      echo reachable ;;
  esac
}
SDK_REPO=$(probe https://dl.google.com/android/repository/repository2-3.xml)
GOOGLE_MAVEN=$(probe https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/maven-metadata.xml)
MAVEN_CENTRAL=$(probe https://repo.maven.apache.org/maven2/)

# --- Verdict -----------------------------------------------------------
say "jdk:            ${JAVA_VER:-none} (need 17-21 for AGP 8.x) -> $JAVA_OK"
say "sdk:            ${SDK:-${SDK_PARTIAL:-none}}"
say "compileSdk:     ${COMPILE_SDK:-unknown} platform installed -> $PLATFORM_OK"
say "sdk repo:       $SDK_REPO   (dl.google.com/android/repository)"
say "google maven:   $GOOGLE_MAVEN   (dl.google.com/dl/android/maven2)"
say "maven central:  $MAVEN_CENTRAL"
say ""

if [ "$GOOGLE_MAVEN" = blocked ] && [ "$PLATFORM_OK" != yes ]; then
  say "ROUTE=C"
  say "reason: Google Maven is unreachable and there is no usable SDK on disk."
  say "        AGP and every AndroidX/Compose artifact are published only to"
  say "        Google Maven, and platform jars only to dl.google.com, so no"
  say "        Gradle repository setting can substitute. Build via CI instead."
elif [ "$JAVA_OK" != yes ]; then
  say "ROUTE=B-jdk"
  say "reason: JDK ${JAVA_VER:-missing} is outside AGP's supported 17-21 range."
elif [ "$PLATFORM_OK" != yes ]; then
  if [ "$SDK_REPO" = reachable ]; then
    say "ROUTE=B"
    say "reason: toolchain reachable but android-${COMPILE_SDK:-?} is not installed."
    say "        Run scripts/install-sdk.sh to fetch it, then re-run preflight."
  else
    say "ROUTE=C"
    say "reason: the SDK platform is missing and its download host is blocked."
  fi
else
  say "ROUTE=A"
  say "reason: JDK, SDK platform and dependency hosts all check out."
  if [ "$GOOGLE_MAVEN" = blocked ]; then
    say "        note: Google Maven is blocked, so only an already-warm Gradle"
    say "              cache can resolve dependencies. Add --offline to stop"
    say "              Gradle stalling on a host it will never reach."
  fi
fi

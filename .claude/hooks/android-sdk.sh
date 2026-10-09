#!/bin/bash
# Installs the Android SDK a Gradle build needs, then points the project at it.
#
# Generic: copy it unchanged into any Android project (docs/claude.md). It reads compileSdk from
# the app module, so nothing in it names this project.
#
# Two uses, same file:
#  - SessionStart hook (.claude/settings.json): runs at every session start, cloud or local. It
#    does nothing outside a cloud session, and costs a second when the SDK is already there.
#  - Setup script of the cloud environment (claude.ai/code → environment → Edit → Setup script):
#    paste this file there. The environment is then cached with the SDK already installed, so the
#    hook above only writes local.properties. One environment can serve every Android project.
#
# Always exits 0: a failed download must not stop the session from starting (the build will say
# what is missing, and Claude can retry).

set -u

# Only in cloud sessions: CLAUDE_CODE_REMOTE=true in the hook; the setup script runs before Claude
# Code, without that variable, as root on Linux. Locally the developer's own SDK is used.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ] && { [ "$(uname -s)" != "Linux" ] || [ "$(id -u)" != "0" ]; }; then
  exit 0
fi

SDK="${ANDROID_HOME:-/opt/android-sdk}"
REPO_XML="https://dl.google.com/android/repository/repository2-3.xml"
PROJECT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
log() { echo "[android-sdk] $*" >&2; }

# 1. Command-line tools (sdkmanager), the newest Google lists.
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  zip=$(curl -fsS "$REPO_XML" | grep -o 'commandlinetools-linux-[0-9]*_latest.zip' | sort -uV | tail -1)
  if [ -z "$zip" ]; then
    log "could not read $REPO_XML; skipping"; exit 0
  fi
  log "installing $zip"
  tmp=$(mktemp -d)
  if curl -fsSL "https://dl.google.com/android/repository/$zip" -o "$tmp/tools.zip" \
     && unzip -q "$tmp/tools.zip" -d "$tmp"; then
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  else
    log "download failed; skipping"; rm -rf "$tmp"; exit 0
  fi
  rm -rf "$tmp"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

# 2. Licences, platform-tools and the platform of compileSdk. Build tools are left to AGP: with the
# licences accepted it downloads the version it wants on the first build.
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1
packages=("platform-tools")
gradle_file=""
for f in "$PROJECT/app/build.gradle.kts" "$PROJECT/app/build.gradle"; do
  [ -f "$f" ] && gradle_file="$f" && break
done
if [ -n "$gradle_file" ]; then
  # "compileSdk = 37" or "compileSdk { version = release(37) }".
  api=$(grep -oE 'compileSdk\s*=\s*[0-9]+|release\(\s*[0-9]+' "$gradle_file" | grep -oE '[0-9]+' | head -1)
  if [ -n "$api" ]; then
    # Since API 36.1 the package carries a minor version ("android-37.0"); before, it doesn't.
    available=$(curl -fsS "$REPO_XML" | grep -oE "path=\"platforms;android-$api(\.0)?\"" | head -1 | cut -d'"' -f2)
    packages+=("${available:-platforms;android-$api}")
  fi
fi
missing=()
for p in "${packages[@]}"; do
  [ -d "$SDK/${p//;//}" ] || missing+=("$p")
done
if [ ${#missing[@]} -gt 0 ]; then
  log "installing ${missing[*]}"
  "$SDKMANAGER" --sdk_root="$SDK" "${missing[@]}" >/dev/null 2>&1 || log "sdkmanager failed for ${missing[*]}"
fi

# 3. Point Gradle at the SDK (local.properties is gitignored). Skipped in the setup script, which
# may run before the repository is there.
if [ -f "$PROJECT/settings.gradle.kts" ] || [ -f "$PROJECT/settings.gradle" ]; then
  if ! grep -qs '^sdk.dir=' "$PROJECT/local.properties"; then
    echo "sdk.dir=$SDK" >> "$PROJECT/local.properties"
    log "wrote sdk.dir to local.properties"
  fi
fi

# 4. Cloud container defaults for Gradle (user-level, so no project file changes): few workers,
# since the VM is small; UTF-8, since the default locale is POSIX and breaks non-ASCII sources.
mkdir -p "$HOME/.gradle"
grep -qs '^org.gradle.workers.max=' "$HOME/.gradle/gradle.properties" \
  || echo "org.gradle.workers.max=2" >> "$HOME/.gradle/gradle.properties"

# 5. Expose ANDROID_HOME and the locale to the session's shell commands.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  { echo "export ANDROID_HOME=$SDK"; echo "export LC_ALL=C.UTF-8"; } >> "$CLAUDE_ENV_FILE"
fi
exit 0

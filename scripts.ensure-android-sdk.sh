#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$REPO_ROOT/android"
LOCAL_PROPERTIES="$ANDROID_DIR/local.properties"

if [[ -f "$LOCAL_PROPERTIES" ]] && rg -q '^sdk\.dir=' "$LOCAL_PROPERTIES"; then
  exit 0
fi

candidate_paths=()
if [[ -n "${ANDROID_HOME:-}" ]]; then
  candidate_paths+=("$ANDROID_HOME")
fi
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  candidate_paths+=("$ANDROID_SDK_ROOT")
fi
candidate_paths+=(
  "$HOME/Android/Sdk"
  "/usr/local/lib/android/sdk"
  "/opt/android-sdk"
)

sdk_path=""
for path in "${candidate_paths[@]}"; do
  if [[ -d "$path/platforms" ]]; then
    sdk_path="$path"
    break
  fi
done

if [[ -z "$sdk_path" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT, or create android/local.properties with sdk.dir=<path>." >&2
  exit 1
fi

escaped_path="${sdk_path//\\/\\\\}"
printf 'sdk.dir=%s\n' "$escaped_path" > "$LOCAL_PROPERTIES"
echo "Wrote $LOCAL_PROPERTIES using sdk.dir=$sdk_path"

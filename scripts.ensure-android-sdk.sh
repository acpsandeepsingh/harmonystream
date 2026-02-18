#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$REPO_ROOT/android"
LOCAL_PROPERTIES="$ANDROID_DIR/local.properties"

extract_sdk_dir() {
  local properties_file="$1"
  local line
  line="$(rg '^sdk\.dir=' "$properties_file" | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    return 1
  fi

  local value="${line#sdk.dir=}"
  # local.properties allows escaped backslashes.
  value="${value//\\\\/\\}"
  printf '%s' "$value"
}

existing_sdk_path=""
if [[ -f "$LOCAL_PROPERTIES" ]]; then
  existing_sdk_path="$(extract_sdk_dir "$LOCAL_PROPERTIES" || true)"
fi

if [[ -n "$existing_sdk_path" ]]; then
  if [[ -d "$existing_sdk_path/platforms" ]]; then
    echo "Using Android SDK from existing $LOCAL_PROPERTIES"
    exit 0
  fi

  echo "Found sdk.dir in $LOCAL_PROPERTIES, but SDK is invalid: $existing_sdk_path" >&2
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
  echo "Android SDK not found." >&2
  if [[ -n "$existing_sdk_path" ]]; then
    echo "- Existing android/local.properties sdk.dir points to: $existing_sdk_path" >&2
  fi
  echo "- Checked env vars: ANDROID_HOME, ANDROID_SDK_ROOT" >&2
  echo "- Checked default paths:" >&2
  for path in "${candidate_paths[@]}"; do
    echo "  - $path" >&2
  done
  echo "Next steps: set ANDROID_HOME or ANDROID_SDK_ROOT, or create android/local.properties with sdk.dir=<path>." >&2
  exit 1
fi

escaped_path="${sdk_path//\\/\\\\}"
printf 'sdk.dir=%s\n' "$escaped_path" > "$LOCAL_PROPERTIES"
echo "Wrote $LOCAL_PROPERTIES using sdk.dir=$sdk_path"

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

is_valid_sdk_dir() {
  local sdk_dir="$1"
  [[ -d "$sdk_dir" ]] || return 1
  [[ -d "$sdk_dir/platforms" ]] \
    || [[ -d "$sdk_dir/platform-tools" ]] \
    || [[ -d "$sdk_dir/cmdline-tools" ]] \
    || [[ -d "$sdk_dir/build-tools" ]]
}

append_candidate_path() {
  local path="$1"
  [[ -n "$path" ]] || return 0
  for existing in "${candidate_paths[@]}"; do
    if [[ "$existing" == "$path" ]]; then
      return 0
    fi
  done
  candidate_paths+=("$path")
}

resolve_sdk_from_binary_path() {
  local binary_name="$1"
  local command_path
  command_path="$(command -v "$binary_name" 2>/dev/null || true)"
  [[ -n "$command_path" ]] || return 1

  local binary_dir
  binary_dir="$(cd "$(dirname "$command_path")" && pwd)"

  local inferred_sdk
  case "$binary_name" in
    adb)
      inferred_sdk="$(cd "$binary_dir/.." && pwd)"
      ;;
    sdkmanager)
      inferred_sdk="$(cd "$binary_dir/../.." && pwd)"
      ;;
    *)
      return 1
      ;;
  esac

  append_candidate_path "$inferred_sdk"
}

existing_sdk_path=""
if [[ -f "$LOCAL_PROPERTIES" ]]; then
  existing_sdk_path="$(extract_sdk_dir "$LOCAL_PROPERTIES" || true)"
fi

candidate_paths=()

if [[ -n "$existing_sdk_path" ]]; then
  if is_valid_sdk_dir "$existing_sdk_path"; then
    echo "Using Android SDK from existing $LOCAL_PROPERTIES"
    exit 0
  fi

  echo "Found sdk.dir in $LOCAL_PROPERTIES, but SDK is invalid: $existing_sdk_path" >&2
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
  append_candidate_path "$ANDROID_HOME"
fi
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  append_candidate_path "$ANDROID_SDK_ROOT"
fi

resolve_sdk_from_binary_path "adb" || true
resolve_sdk_from_binary_path "sdkmanager" || true

append_candidate_path "$HOME/Android/Sdk"
append_candidate_path "/usr/local/lib/android/sdk"
append_candidate_path "/usr/lib/android-sdk"
append_candidate_path "/opt/android-sdk"
append_candidate_path "/opt/android-sdk-linux"

sdk_path=""
for path in "${candidate_paths[@]}"; do
  if is_valid_sdk_dir "$path"; then
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
  echo "- Checked SDK hints from installed binaries: adb, sdkmanager" >&2
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

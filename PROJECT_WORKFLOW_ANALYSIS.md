# PROJECT WORKFLOW ANALYSIS

## Executive Summary

HarmonyStream is a hybrid stack:
- **Web app**: Next.js 14 static export (`output: 'export'`) with client-heavy state/context architecture, YouTube playback via `react-youtube`, and Firebase for auth/data. (`src/**`, `next.config.js`)
- **Android app**: Native WebView host + native playback service (ExoPlayer + NewPipe extractor) + JS bridge contract used to synchronize playback state and media commands. (`android/app/src/main/java/com/sansoft/harmonystram/**`)
- **CI/CD**: GitHub Actions builds/deploys website first, then reuses exported artifact to package an Android debug APK. (`.github/workflows/main.yml`)

The repository currently reflects a significant Android pipeline evolution (artifact-first APK assembly, stricter Gradle cache cleanup, service-driven playback), but also contains architectural tension between **native ExoPlayer playback** and **web YouTube iframe playback**, which strongly correlates with the reported “play starts then immediately stops” defect.

---

## 1) High-Level Architecture Overview

## 1.1 Monorepo Layout

- `src/` – Next.js App Router code (UI, API routes, contexts, hooks, firebase wrappers).
- `android/` – Android app module and native runtime (WebView host, playback service, widget, Gradle config).
- `.github/workflows/` – CI workflow for website + APK.
- Root web/tooling config – `package.json`, `next.config.js`, `tailwind.config.ts`, `tsconfig.json`, `capacitor.config.json`, `firestore.rules`.

## 1.2 System Boundaries

### Website app (web runtime)
- Entry point: `src/app/layout.tsx` + `src/app/page.tsx`.
- Main playback UI: `src/components/music-player.tsx`.
- State ownership: context providers in `src/components/providers.tsx` and `src/contexts/*`.
- Data access: Firebase SDK wrappers (`src/firebase/*`) and YouTube API utility (`src/lib/youtube.ts`).

### Android app (native runtime)
- Entry activity: `android/app/src/main/java/com/sansoft/harmonystram/WebAppActivity.java`.
- Native playback engine: `PlaybackService.java`.
- Stream URL extraction: `DownloaderImpl.java` + NewPipe.
- App widget plumbing: `PlaybackWidgetProvider.java` + `res/layout/playback_widget.xml`.

## 1.3 Android ↔ Web Communication Contract

Bridge methods and events:
- JS -> Native: `HarmonyNative` / `AndroidNative` interfaces in `WebAppActivity.NativePlaybackBridge`.
  - Commands: play/pause/seek/mode/queue/updateState/getState.
- Native -> JS:
  - Broadcast from service received by activity, re-dispatched as custom DOM events (`nativePlaybackState`, `nativePlaybackCommand`, `nativeHostResumed`, `nativePictureInPictureChanged`).
  - Direct JS eval callback: `window.updateProgress(pos,dur)` from service.

This forms a bidirectional control loop across:
`music-player.tsx` <-> `WebAppActivity.java` <-> `PlaybackService.java`.

---

## 2) Repository Structure & File Role Mapping

## 2.1 Core Runtime Files

### Web Core
- `src/app/layout.tsx`: Root shell, wraps client providers and toaster.
- `src/components/providers.tsx`: Composes theme/search/playlist/player contexts and layout wrapper.
- `src/components/layout/app-layout.tsx`: Includes persistent `MusicPlayer` when `isPlayerVisible`.
- `src/components/music-player.tsx`: Playback UI, YouTube iframe integration, Android bridge event handling.
- `src/contexts/player-context.tsx`: Queue/history/playback state machine and persistence.

### Android Core
- `android/app/src/main/java/.../WebAppActivity.java`: WebView configuration, asset loading, lifecycle handling, bridge registration, receiver registration.
- `android/app/src/main/java/.../PlaybackService.java`: ExoPlayer setup, actions, stream resolution, notification handling, persistence, state broadcasts.
- `android/app/src/main/AndroidManifest.xml`: permissions, service/activity/widget declarations.

## 2.2 Config and Build Files

### Web
- `package.json`: scripts + dependencies.
- `next.config.js`: static export settings and conditional basePath/assetPrefix strategy for web vs Android bundles.
- `capacitor.config.json`: Capacitor app metadata + Android HTTPS scheme.
- `tsconfig.json`, `tailwind.config.ts`, `postcss.config.mjs`: compile/styling toolchain.

### Android / Gradle
- `android/build.gradle`: top-level AGP plugin classpath + clean task.
- `android/settings.gradle`: plugin/dependency repositories (with mirror env handling + canonical repos).
- `android/app/build.gradle`: application config, secrets/signing resolution, build types, dependencies, asset sync task.
- `android/gradle.properties` + root `gradle.properties`: memory/daemon tuning.
- `android/signing.properties.example`, `android/secrets.properties.example`: local secret templates.

### CI/CD
- `.github/workflows/main.yml`: website build/deploy + APK build job.

### Environment & Secret Inputs
- Web: `NEXT_PUBLIC_YOUTUBE_API_KEY` expected by `src/lib/youtube.ts`.
- Android: `YOUTUBE_API_KEY` and Firebase BuildConfig fields via Gradle properties/env/secrets file.
- Signing: optional `ANDROID_RELEASE_*` values in app Gradle, but CI currently only builds debug APK.

---

## 3) Android Workflow Breakdown

## 3.1 Android App Entry and Bootstrap

1. Launcher starts `WebAppActivity` (Manifest `MAIN` + `LAUNCHER`, plus `LEANBACK_LAUNCHER`).
2. Activity configures WebView:
   - JS enabled, DOM storage enabled, autoplay gesture relaxed.
   - `WebViewAssetLoader` routes `/assets`, `/_next`, `/harmonystream/*` to bundled assets.
3. Activity injects JS interfaces (`HarmonyNative`, `AndroidNative`) and attaches WebView to service static reference (`PlaybackService.attachWebView(webView)`).
4. Activity requests state from service (`ACTION_GET_STATE`) and registers broadcast receivers for state and media actions.

## 3.2 Native Playback Pipeline

1. UI command triggers bridge call (e.g., `AndroidNative.play(id,title)`).
2. `WebAppActivity` starts `PlaybackService` (foreground start for playback-related actions).
3. `PlaybackService.ACTION_PLAY`:
   - resolves YouTube stream URLs via NewPipe (`StreamInfo.getInfo`).
   - chooses audio/video stream URL.
   - loads URL into ExoPlayer and starts playback.
4. Service updates notification, broadcasts state, pushes progress updates to WebView.

## 3.3 Notification + Background

- Notification channel created on service start.
- Media actions (previous/play-pause/next) delivered as service PendingIntents.
- Service enters foreground when `player.isPlaying()`.
- Activity uses PiP behavior + custom pause/stop lifecycle overrides.

## 3.4 Persistence

- Service stores snapshot in SharedPreferences (`playback_service_state`).
- Web player stores last played/queue/history in localStorage.

---

## 4) Website Workflow Breakdown

## 4.1 App Initialization

1. `layout.tsx` initializes Firebase client provider (dynamic import, SSR disabled).
2. `Providers` wraps contexts and renders `AppLayout` outside auth routes.
3. Home route (`src/app/page.tsx`) renders `HomePageClient`.

## 4.2 Data Retrieval & Caching

- `home-client.tsx` fetches songs by genre via `searchYoutube()`.
- Uses Firestore `api_cache` document for cache hits.
- On successful fetch, persists cache + writes song docs (if signed in).

## 4.3 Playback Flow (Web)

1. User interacts with song cards / playlists -> updates `PlayerContext`.
2. `MusicPlayer` loads YouTube iframe track by `videoId`.
3. State transitions (`onStateChange`) update global `isPlaying` and trigger next track when ended.
4. Android runtime mode adds bridge synchronization (`HarmonyNative.updateState`, `setQueue`, command listeners).

## 4.4 API Routes

- `src/app/api/search/route.ts` and `src/app/api/songs/route.ts` intentionally return 404 (“disabled in favor of client-side fetching”).

---

## 5) Build & Pipeline Explanation

## 5.1 Local Build Paths

### Web
- `npm run build` -> `next build` static export to `out/` (as required by `output: 'export'`).

### Android debug APK
- `npm run apk:debug`:
  1. android sdk prep script
  2. build Android-targeted web export (`build:android`)
  3. `cap sync android`
  4. `./android/gradlew assembleDebug`
- `android/app/build.gradle` `preBuild.dependsOn(syncBundledWebAssets)` ensures exported web assets exist and are copied.

## 5.2 CI/CD Sequence (`main.yml`)

1. **website job**:
   - checkout
   - Node 22 + npm ci
   - `npm run build`
   - upload Pages artifact and deploy GitHub Pages
2. **apk job** (depends on website job):
   - download Pages artifact, extract into local `out`
   - Java 17, Gradle 8.2, Android SDK setup
   - copy extracted `out` into `android/app/src/main/assets/public`
   - run clean build: `./gradlew assembleDebug --no-daemon --no-build-cache --refresh-dependencies`
   - upload debug APK artifact

## 5.3 Versioning & Artifact Strategy

- Android app currently fixed at `versionCode 1`, `versionName "1.0"` (no CI auto-versioning).
- CI only uploads **debug APK**, not release APK/AAB.

---

## 6) Dependency Mapping

## 6.1 Web Dependencies (critical)

- `next`, `react`, `react-dom`: frontend runtime.
- `firebase`: auth + firestore data layer.
- `react-youtube`: playback renderer.
- `@radix-ui/*`, `tailwind*`, `lucide-react`: UI system.
- `@dnd-kit/*`: queue reorder drag/drop.

## 6.2 Android Dependencies (critical)

- `androidx.media:media`: media-style notifications.
- `androidx.webkit:webkit`: WebViewAssetLoader.
- `com.google.android.exoplayer:exoplayer`: native playback engine.
- `com.github.TeamNewPipe:NewPipeExtractor`: YouTube stream extraction.

---

## 7) Data Flow: UI → API → Storage → Playback

## 7.1 Catalog/Discovery

1. UI query/genre select in web components.
2. `searchYoutube()` calls YouTube Data API.
3. Results cached to Firestore `api_cache` and optionally persisted to `songs` collection.
4. UI renders `SongCard`s.

## 7.2 Playback (Standard Web)

1. `SongCard` click -> `PlayerContext.playTrack/playPlaylist`.
2. `MusicPlayer` loads YouTube iframe.
3. Iframe events update context and media session.

## 7.3 Playback (Android Hybrid)

1. Same UI action also triggers bridge command (e.g., `AndroidNative.play(videoId,title)`).
2. Native service resolves direct stream URL and starts ExoPlayer.
3. Service broadcasts state/progress back to web UI.
4. UI merges native events with local player state.

This dual-control model is powerful but currently fragile due to race/ownership conflicts.

---

## 8) Major APK Change Audit (Recent Pipeline & Config Evolution)

## 8.1 What Changed (observed from current state + git history)

- CI now reuses website artifact for APK job (instead of rebuilding web assets separately in apk job).
- Aggressive Gradle cache/build cleanup introduced before assemble.
- Release signing steps were removed from workflow, while Gradle release signing config remains available.
- Repository resolution logic hardened in `settings.gradle` (canonical repos retained even when mirror env vars exist).
- Android app has shifted to native ExoPlayer + JS bridge synchronization architecture.

## 8.2 Build Types / Signing / Proguard / Modularization

- Build types: only explicit `release` block; debug uses defaults.
- Signing:
  - release signing config reads from properties/env and is conditionally applied.
  - CI currently does not execute signed release build.
- Minify/R8:
  - `release.minifyEnabled false` => ProGuard/R8 effectively disabled despite proguard file declaration.
- Modularization:
  - single Android app module (no feature modules).
- Architecture:
  - hybrid webview shell + native media service (service-first playback support).

## 8.3 Correctness / Stability Assessment

### Stable/Good
- Artifact reuse between jobs reduces duplicate work and keeps APK web assets aligned with deployed website build.
- `syncBundledWebAssets` prebuild guard catches missing `out/index.html` early.
- Foreground service startup path considered for playback actions.

### Risky / Conflicting
1. **Repository conflict warnings**: `app/build.gradle` still declares repositories despite `RepositoriesMode.PREFER_SETTINGS`; Gradle warns at configuration time.
2. **SDK version mismatch source**: `variables.gradle` (compile/target 35, min 23) is inconsistent with `app/build.gradle` (compile/target 34, min 21), and `variables.gradle` appears unused.
3. **Release pipeline mismatch**: release signing is configured in Gradle but removed in CI, reducing confidence in release readiness.
4. **R8 disabled in release**: hidden issues surface late if/when minification is re-enabled.

## 8.4 Redundant / Obsolete Build Logic

- `android/variables.gradle` appears legacy and not wired into `app/build.gradle`.
- Root `gradle.properties` duplicates concerns handled also by workflow `GRADLE_OPTS` and `android/gradle.properties`.
- `sourceSets { test.java.srcDirs = []; androidTest.java.srcDirs = []; }` disables tests while test classes still exist.

---

## 9) Dead Code & Cleanup Detection Report

> Confidence levels are static-analysis based (no runtime coverage instrumentation in this audit).

| Item | Path | Reason | Safe to Remove? | Priority |
|---|---|---|---|---|
| Disabled API endpoint #1 | `src/app/api/search/route.ts` | Always returns 404 “disabled”. Route still built/deployed. | Yes, if no external clients depend on path. | Medium |
| Disabled API endpoint #2 | `src/app/api/songs/route.ts` | Same as above. | Yes, if no external clients depend on path. | Medium |
| Legacy UI primitives (multiple) | `src/components/ui/accordion.tsx`, `badge.tsx`, `calendar.tsx`, `carousel.tsx`, `chart.tsx`, `checkbox.tsx`, `collapsible.tsx`, `menubar.tsx`, `popover.tsx`, `progress.tsx`, `switch.tsx`, `table.tsx` | No import references from app code detected. Typical scaffold leftovers. | Yes after quick visual regression. | Low |
| Android test stubs effectively ignored | `android/app/src/test/java/com/getcapacitor/myapp/ExampleUnitTest.java`, `android/app/src/androidTest/java/com/getcapacitor/myapp/ExampleInstrumentedTest.java` | Source sets explicitly emptied in Gradle; tests never run. | Yes, or re-enable tests and keep meaningful coverage. | Low |
| Potentially obsolete gradle vars file | `android/variables.gradle` | Version constants not consumed by module build script. | Yes if confirmed unused by external tooling. | Medium |
| Legacy commented/diagnostic markdown accumulation | `android/WHITE_SCREEN_LOG_ANALYSIS.md`, `android/FIREBASE_WEB_PARITY_NOTES.md` | Operational docs may be stale vs current architecture. | Keep but refresh or archive. | Low |

Additional cleanup:
- Remove duplicate repository declarations from `android/app/build.gradle`.
- Consolidate Gradle memory flags to one source of truth.

---

## 10) Audio Playback Critical Bug Investigation

## 10.1 Observed Symptom

“Click Play Song -> starts then immediately stops.”

## 10.2 Full Playback Path Trace

1. User presses play in `MusicPlayer`.
2. `handleTogglePlayPause()` calls both:
   - `window.AndroidNative.play(currentTrack.videoId, currentTrack.title)` (native path)
   - `player.playVideo()` (web iframe path)
3. `WebAppActivity.NativePlaybackBridge.play(id,title)` sends `ACTION_PLAY` intent to `PlaybackService`.
4. `PlaybackService.onStartCommand()` executes `handlePlay()` and **immediately** performs `updateNotification(); persistState(); return` (before async stream resolve completes).
5. In same command cycle, service broadcasts state snapshots where `playing` can still be false.
6. `WebAppActivity.serviceStateReceiver` relays this as `nativePlaybackState` event.
7. `music-player.tsx` `nativeStateListener` sets global play state from event detail.
8. `useEffect([isGlobalPlaying])` then pauses YouTube player if state became false.

## 10.3 Ranked Likely Root Causes

### 1) Race condition between asynchronous native readiness and synchronous state broadcast (**Very High probability**)
- Service broadcasts `playing=false` before ExoPlayer transitions to playing.
- Web listener treats this as authoritative and pauses local player.
- Net effect: brief start then immediate stop.

### 2) Split-brain playback ownership between native ExoPlayer and web YouTube iframe (**High probability**)
- Both engines are commanded in parallel.
- State reconciliation is not source-tagged/debounced.
- Any mismatch can flip global `isPlaying` unexpectedly.

### 3) Missing `ACTION_UPDATE_STATE` handling in `PlaybackService` switch (**Medium probability, architecture bug**)
- Web invokes `HarmonyNative.updateState(...)`, but service lacks explicit case to consume it.
- This makes state sync one-directional/partial and can desynchronize foreground behavior.

### 4) Lifecycle overrides in `WebAppActivity` are unconventional (**Medium probability in edge cases**)
- `onPause` conditionally skips `super.onPause()` on screen-off.
- `onStop` calls `super.onStop()` only in PiP path.
- Could induce state oddities depending on OEM behavior.

### 5) Proguard/obfuscation regression (**Low probability currently**)
- Release minification is disabled, so this is less likely in current builds.

## 10.4 Additional Checks Completed

- Permissions for internet, foreground service, media playback, wake lock, notifications are present in Manifest.
- Service is declared `foregroundServiceType="mediaPlayback"`.
- No clear missing network security config issue in current source.

## 10.5 Recommended Fixes

### Immediate (P0)
1. **Establish single playback authority per runtime**:
   - In Android runtime, prefer native playback authority and avoid directly toggling iframe state until native confirms readiness.
2. **Debounce/qualify native state events**:
   - Ignore `playing=false` transitions during pending `ACTION_PLAY` resolve window.
3. **Add `ACTION_UPDATE_STATE` and `ACTION_CLEAR_PENDING_MEDIA_ACTION` explicit handling in service** for contract completeness.

### Near-term (P1)
4. Include `source` and monotonic event timestamps in native state payload; ignore stale updates in web listener.
5. Separate command vs telemetry channels in bridge protocol.
6. Add integration tests/log assertions for first-play race.

### Medium-term (P2)
7. Decide between:
   - fully native playback with web as UI shell, or
   - fully web playback with native notification proxy.
   Hybrid dual-engine mode should be gated/feature-flagged, not default.

---

## 11) CI/CD & Deployment Validation

## 11.1 Trigger and Job Validation

- Triggered on `push` to `main` and manual dispatch.
- Website job deploys GitHub Pages correctly.
- APK job now explicitly depends on website job (`needs: website`) and uses artifact handoff.

## 11.2 Secrets Usage

- Website build uses `NEXT_PUBLIC_YOUTUBE_API_KEY` from repo secrets.
- APK build maps same value into `YOUTUBE_API_KEY` env.
- Release signing secrets are no longer consumed in workflow (even though Gradle supports them).

## 11.3 Reliability Findings

- Positive: explicit cleanup and no-daemon/no-build-cache reduce nondeterministic runner cache issues.
- Negative: loss of release artifact stage means no continuous validation of release/signing path.

## 11.4 Suggested CI Improvements

1. Reintroduce optional signed release build in a separate workflow/job.
2. Add Gradle lint + unit tests (after re-enabling source sets).
3. Add workflow step to fail on repository configuration warnings.
4. Add versionCode/versionName automation based on git tags/run number.
5. Publish mapping files/ProGuard outputs when release minify enabled.

---

## 12) Risk Areas

## High Risk
- Playback state race between native and web engines.
- Missing/partial command handling in service contract.

## Medium Risk
- Inconsistent Gradle config sources and unused variables file.
- CI validates only debug APK; release path may rot.

## Low Risk
- Large volume of unused UI scaffold files increasing maintenance surface.

---

## 13) Technical Debt Areas

- Dual playback authority without robust arbitration.
- Disabled API routes still packaged.
- Android tests disabled at build config level.
- Documentation drift across operational markdown notes.

---

## 14) Final Recommendations

1. **Fix playback race first** (single authority + event gating).
2. **Complete playback command contract** in `PlaybackService` switch.
3. **Align Gradle config** (remove unused `variables.gradle` or adopt it consistently).
4. **Restore release validation** in CI with secure optional signing.
5. **Run cleanup sprint** for unused UI/API scaffolding and re-enable meaningful Android tests.
6. **Add observability**:
   - structured logs for bridge command/state transitions,
   - unique action IDs for round-trip tracing,
   - debug overlay for playback source (web/native) state.

---

## Appendix: Key File References

- Web runtime and player:
  - `src/components/music-player.tsx`
  - `src/contexts/player-context.tsx`
  - `src/components/layout/app-layout.tsx`
- Android runtime:
  - `android/app/src/main/java/com/sansoft/harmonystram/WebAppActivity.java`
  - `android/app/src/main/java/com/sansoft/harmonystram/PlaybackService.java`
  - `android/app/src/main/AndroidManifest.xml`
- Build/pipeline:
  - `.github/workflows/main.yml`
  - `android/app/build.gradle`
  - `android/settings.gradle`
  - `next.config.js`
  - `package.json`

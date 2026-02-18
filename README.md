# HarmonyStream

This is a Next.js starter app for a music streaming service, built in Firebase Studio.

To get started, take a look at `src/app/page.tsx`.


## Native Android Rewrite Status

### Android YouTube API key setup (native)

The native Android app now reads `YOUTUBE_API_KEY` from one of these sources (highest priority first):

1. Gradle property `-PYOUTUBE_API_KEY=...`
2. `android/secrets.properties` (gitignored)
3. Environment variable `YOUTUBE_API_KEY`

Quick setup:

- Copy `android/secrets.properties.example` to `android/secrets.properties`.
- Add your key: `YOUTUBE_API_KEY=...`.

The Android module now runs as a fully native screen (RecyclerView + Media3/ExoPlayer player) instead of loading the web app through a WebView. The current native build includes:

- Native track list UI and native playback controls (Previous/Play-Pause/Next).
- Native audio/video playback using Media3 ExoPlayer.
- Existing `PlaybackService` notification controls integrated with player state updates.

> Note: The native rewrite currently uses sample media URLs as a baseline player implementation. YouTube-specific catalog, search, and account-connected features still need to be implemented natively in future steps.


## Native Migration Roadmap (Step-by-Step)

### How to update this roadmap after each delivery

Use this lightweight process whenever native Android work lands so the README stays aligned with reality:

1. Verify what shipped in code (UI, repositories, playback lifecycle changes, and build/release scripts).
2. Update the **Current delivery checkpoint** bullets to reflect the latest milestone.
3. Move each affected phase status (`Pending` / `In progress` / `Complete`) and adjust scope notes.
4. Keep each phase's **Exit criteria** outcome-oriented and testable.
5. Add any new risks or environment requirements to the troubleshooting section.

> Suggested cadence: update this section in the same PR that introduces the feature so docs never lag implementation.

### Build issue resolution note (current environment)
- If `npm run apk:debug` fails with `Android SDK not found`, the build scripts are behaving as designed and require a configured local SDK path.
- Fix by setting `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or writing `android/local.properties` with `sdk.dir=/absolute/path/to/Android/Sdk`.
- Then rerun `npm run android:sdk:prepare` followed by `npm run apk:debug`.

### Current delivery checkpoint (where coding stands now)
- **Reached milestone:** **Phase 3 foundation is now delivered** with local playlist/library operations integrated into the native Android flow.
- **Partially present from future phases:** native player controls now include queue-aware playback for native media sources (`previous / play-pause / next`), playback notification sync, local resume-session restore, user-selectable repeat modes (`off / all / one`), a native queue picker dialog for inspecting/jumping within the active queue, and playback session schema-v3 persistence (including `isPlaying`, queue snapshot indexes, and queue cursor) saved on lifecycle stop and item transitions for more reliable resume behavior.
- **Expanded this cycle:** Phase 3B sync foundation is now wired with identity-aware playlist reconciliation primitives (`pullRemoteSnapshot`, `pushLocalChanges`, `resolveConflicts`) using updated-at merge + playlist tombstones, with sync-state surfaced in Library/Profile screens and safe guest/local fallback.
- **Expanded this cycle:** Phase 4A lifecycle hardening now includes a TV focus-navigation pass for main playback controls, key-driven media toggle handling for D-pad/remote workflows, structured native playback event diagnostics, and a dedicated full-screen player surface with queue/state badges.

### Phase-by-phase plan with definition of done

#### Phase 1 — Native data layer (YouTube + models) ✅ Complete
**What was required**
- Android data models (`Song`, `Playlist`, `SearchResult`).
- YouTube API client (search + video details).
- API key through Android build config/secrets (not hardcoded).
- Repository classes/interfaces for UI consumption.
- Replace `seedNativeTrackCatalog()` with fetched data.

**What is implemented now**
- Models and repository interfaces are in place and used by the native app.
- `YouTubeApiClient` supports search and video details endpoints.
- `YOUTUBE_API_KEY` is injected via Gradle property / `android/secrets.properties` / env var.
- Home catalog load is repository-backed using YouTube query results.

#### Phase 2 — Native search screen ✅ Complete
**What is implemented now**
- Native search UI with query input + source selector (`youtube` / `youtube-all`).
- Repository-backed search from native UI, including source-aware query behavior.
- Loading/error/empty/success states rendered in the native screen.
- Search result tap opens playable media flow and syncs playback notification state.

**Exit criteria outcome**
- Core behavior now matches web search intent for source selection and results rendering.

#### Phase 3 — Native playlists & library 🟠 In progress (sync foundation shipped, cloud backend wiring pending)
**What is implemented now**
- Local-first playlist storage layer backed by native SharedPreferences persistence.
- Playlist persistence is now account-scoped locally (guest vs signed-in profile identity) with legacy migration support.
- Native create playlist, add selected track to playlist, remove track, delete playlist, and play-all flow.
- Dedicated native library screen/navigation from the main Android flow with playlist and per-track actions (play, remove, delete).
- Phase 3B sync contract now implemented in native code with deterministic conflict reconciliation (`updatedAt` + deletion tombstones), per-account sync metadata persistence, and sync-state messaging in Library/Profile UIs.

**Remaining scope**
- Replace the current local remote-cache bridge with live Firestore transport + auth-backed cross-device reconciliation.

**Exit criteria**
- Feature parity with the web playlist workflow.

#### Phase 4 — Player parity + TV polish 🟠 In progress (queue lifecycle foundation expanded)
**Already available (foundation)**
- Queue-aware native playback now builds a playable media queue from the active track list, exposes a native queue picker dialog for quick item inspection/selection, and keeps `previous` / `next` navigation aligned with playable native items, including repeat mode control (`off` / `all` / `one`).
- Playback notification controls remain synced with player state updates, and media control actions are now bridged for cold-start resume paths into `MainActivity`.
- Local playback session restore now brings back track list selection, resume position, repeat mode (`off` / `all` / `one`), and schema-v3 queue snapshot state (ordered queue track indexes + active queue cursor) for deterministic queue resume.

**Remaining scope**
- Improved TV remote / D-pad focus navigation.
- Full-screen player + richer artwork handling.
- Additional background playback lifecycle hardening (audio focus edge-cases, duplicate-player guards under rapid recreation, long-session soak validation).

**Exit criteria**
- Stable TV-first UX under long sessions and app lifecycle changes.

#### Phase 5 — Auth/profile/settings parity 🟠 In progress (local native screens now shipped)
**What is implemented now**
- Native profile entry point in the main Android screen with local session state persisted via SharedPreferences.
- Guest/account status indicator in native UI.
- Dedicated native `ProfileActivity` with screen-level sections for login, signup, and settings.
- Local account flows for sign-in, sign-up, display-name update, and sign-out.

**Remaining scope**
- Firebase auth/session integration across native screens.
- User-specific sync rules migration.

**Exit criteria**
- Fully native end-to-end authenticated flow with cloud-backed account state.

#### Phase 6 — Build/release reliability 🟠 In progress
**What is implemented now**
- APK CI workflow now reads optional Android Maven mirror secrets (`ANDROID_GOOGLE_MAVEN_MIRROR`, `ANDROID_MAVEN_CENTRAL_MIRROR`) to support restricted build environments.
- APK CI now maps native `YOUTUBE_API_KEY` from repository secrets and validates generated native BuildConfig wiring during CI.
- Local Android debug builds now run a preflight SDK resolver (`npm run android:sdk:prepare`) that auto-writes `android/local.properties` when a valid SDK is detected.
- CI can now optionally build signed release `APK` and `AAB` artifacts when release-signing secrets are configured, while keeping unsigned debug APK builds always-on.
- Next.js production builds now avoid Firebase App Hosting auto-init warnings during SSR/static generation by using explicit config on server renders.

**Remaining scope**
- Crash reporting, analytics, and regression checklist.

**Exit criteria**
- Production-ready delivery pipeline with repeatable releases.

## Your Development Workflow

Developing and releasing your app is fully automated. Here’s the step-by-step process:

1.  **Make Your Changes:** Edit the code in the `src` directory to add features, fix bugs, or change the style of your app.

2.  **Push to GitHub:** Commit your changes and push them to the `main` branch of your repository.

3.  **Automation Takes Over:** Pushing to `main` automatically triggers two processes in your repository's "Actions" tab:
    *   **Website Deployment:** Your Next.js app is built and deployed to GitHub Pages.
    *   **Android App Build:** A native Android `.apk` file is built from the Android module (no WebView shell).

### Workflow naming clarification (common source of confusion)

If you are looking for CI run status, use these exact workflow/artifact names as currently configured:

- **Workflow name:** `Build Website and Android APK`
- **Debug APK artifact name:** `HarmonyStream-Android-APK`

Older notes/tutorials may still reference "Build Android APK" or `HarmonyStream-Android-App`; those labels are outdated.

4.  **Get Your Updates:**
    *   **Live Website:** Your changes will be live at the URL below within a few minutes.
    *   **Android App:** You can download the updated `.apk` file from the build artifacts, as described in the "Android TV App" section.


## Deployment

This project is automatically deployed to GitHub Pages when changes are pushed to the `main` branch.

**Live URL:** https://acpsandeepsingh.github.io/harmonystream/

## Android TV App

This project includes a native Android app focused on TV-friendly playback with native controls, Media3 player integration, and Android media notifications.

### How to Get the Android App (APK)

This project is set up to automatically build the Android app for you. You do **not** need Android Studio or any other developer tools installed.

1.  **Push Changes:** Make any changes you want to the app and push them to the `main` branch of your GitHub repository. This is the only step you need to do.

2.  **Wait for the Build:** Pushing to `main` will automatically trigger a build process. You can monitor its progress in the **"Actions"** tab of your GitHub repository. Look for the **"Build Website and Android APK"** workflow.

3.  **Download the APK:** Once the workflow is complete (it will have a green checkmark), click on it. On the summary page, you will find an "Artifact" named **HarmonyStream-Android-APK**. Click it to download the `app-debug.apk` file.

4.  **Install the App:** Transfer the downloaded `app-debug.apk` file to your Android TV and install it. You may need to enable "Install from unknown sources" in your TV's settings.

### APK Troubleshooting ("App opens, but content is blank/not loading")

If the Android app shell opens but the actual app content is blank or never loads, the cause is usually one of these:

1. **Android SDK is not configured in the local shell**
   - Run `npm run android:sdk:prepare` to auto-detect a local SDK and write `android/local.properties` for you.
   - If auto-detection fails, set Android SDK path in either `ANDROID_HOME` / `ANDROID_SDK_ROOT` or `android/local.properties` manually (`sdk.dir=/path/to/Android/Sdk`).
   - Then rerun: `npm run apk:debug`.

2. **Missing YouTube API key at build time**
   - Native Android runtime expects `YOUTUBE_API_KEY` (injected from Gradle property / `android/secrets.properties` / env var).
   - In GitHub Actions, this is currently mapped from repository secret `NEXT_PUBLIC_YOUTUBE_API_KEY` into native `YOUTUBE_API_KEY` for the Android build job.
   - If the key is missing/invalid or quota is exhausted, dynamic song content may fail to load.

3. **No internet connectivity on device**
   - The APK bundles UI files, but song discovery/streaming still requires network access.


4. **Gradle dependency download fails with `403 Forbidden` in CI/dev shell**
   - Some environments block direct Maven/Google repository access and require internal mirrors.
   - Set these environment variables before building Android:
     - `ANDROID_GOOGLE_MAVEN_MIRROR` (mirror URL for Google Maven artifacts)
     - `ANDROID_MAVEN_CENTRAL_MIRROR` (mirror URL for Maven Central artifacts)
   - Then rerun: `./android/gradlew -p android assembleDebug`


5. **Release signing artifacts are missing in CI**
   - The workflow builds signed release artifacts only when all signing secrets are configured:
     - `ANDROID_RELEASE_STORE_FILE_BASE64`
     - `ANDROID_RELEASE_STORE_PASSWORD`
     - `ANDROID_RELEASE_KEY_ALIAS`
     - `ANDROID_RELEASE_KEY_PASSWORD`
   - For local signed builds, copy `android/signing.properties.example` to `android/signing.properties` and set keystore path + credentials, then run: `./android/gradlew -p android assembleRelease bundleRelease`.


## Next phase implementation plan (from current roadmap)

The next roadmap milestone is to complete **Phase 3 remaining scope**: Firestore-backed playlist sync with cross-device reconciliation.

### Phase 3B implementation outline (Firestore sync)

1. **Data contract + repository extension**
   - Add a Firestore playlist schema (`users/{uid}/playlists/{playlistId}` + track subcollection/array strategy).
   - Extend native playlist repository interfaces with explicit sync methods (`pushLocalChanges`, `pullRemoteSnapshot`, `resolveConflicts`).

2. **Identity-aware sync bootstrap**
   - On profile/session change, load identity-scoped local cache and attempt remote pull.
   - Keep current guest/local behavior as fallback when auth or network is unavailable.

3. **Conflict resolution strategy (first pass)**
   - Use deterministic `updatedAt` merge with tombstones for deleted playlists/tracks.
   - Persist reconciliation metadata locally (last sync timestamp + per-playlist sync hash/version).

4. **UI/UX parity updates**
   - Add sync-state UI in library/profile surfaces (`syncing`, `offline`, `conflict-resolved`, `error`).
   - Keep play/remove/delete actions optimistic, then reconcile in background.

5. **Validation + rollout guardrails**
   - Add repository-level tests for merge rules.
   - Add instrumentation checks for sign-in/out transitions and offline/online resume sync.
   - ✅ Rollout is now gated behind `PLAYLIST_SYNC_ENABLED` (Gradle/env feature flag) so local-only fallback remains safe.

### Definition of done for this next phase

- A signed-in user sees the same playlist library on two devices after sync.
- Playlist create/edit/delete operations converge correctly after offline edits.
- Guest users continue to use local-only storage without regressions.
- Sync failures are visible and recover automatically when connectivity returns.

## Further next phase implementation (after Phase 3B)

Once Firestore playlist sync (Phase 3B) is stable, the immediate follow-up should target the highest-risk gaps in **Phase 4 (Player parity + TV polish)**.

### Phase 4A implementation outline (playback lifecycle hardening)

**Implementation update (latest):**
- ✅ Queue snapshot persistence has been implemented with schema-v3 playback session state (`queue_track_indexes`, `current_queue_index`) and deterministic queue restore fallback behavior.
- ✅ Notification media controls now route through a cold-start-safe activity handoff path so `previous / play-pause / next` actions still execute after process recreation.
- ✅ Dedicated native full-screen player surface is now available with large artwork, queue position context, and visible playback/repeat/buffering/source badges.
- ✅ Added in-app playback diagnostics + soak-gate evaluator summary (event checkpoints for background/resume, queue navigation, session persistence, and notification-resume path).
- 🟠 Remaining: extended long-duration soak execution coverage across physical-device matrix.

1. **Queue persistence beyond single-session restore**
   - Persist full queue snapshot (ordered ids, current index, repeat mode, play state).
   - Restore queue deterministically after process death and app relaunch.
   - Add schema versioning for queue state to allow safe future migrations.

2. **Background/foreground lifecycle correctness**
   - Ensure service/player survive screen transitions and activity recreation without duplicate player instances.
   - Harden audio focus handling (duck, transient loss, permanent loss) with explicit test scenarios.
   - Normalize notification action behavior when app process is cold-started from media controls.

3. **TV remote and D-pad focus navigation pass**
   - Define predictable focus order across home/search/library/player controls.
   - Add fallback focus anchors to prevent focus loss after list updates and dialog close.
   - Validate navigation behavior on long lists, nested dialogs, and resume from background.

4. **Full-screen player UX parity**
   - Introduce dedicated full-screen player surface with large artwork and queue context.
   - Add visible state badges (repeat, buffering, source type) and consistent seek affordances.
   - Keep controls accessible for both D-pad and touch emulator workflows.

5. **Observability + stability gates**
   - Add structured playback event logging for lifecycle transitions and queue operations.
   - Add regression checklist for long-session soak (e.g., 2+ hour playback with background transitions).
   - Define release gate: no crashes/ANRs in smoke tests + deterministic queue resume behavior.

### Definition of done for Phase 4A

- Playback continues correctly across app background/foreground and activity recreation.
- Queue and repeat state restore reliably after process death for native-playable items.
- TV remote navigation remains predictable across all primary screens.
- Media notification actions function correctly even on cold-start resume paths.

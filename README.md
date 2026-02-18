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

### Current delivery checkpoint (where coding stands now)
- **Reached milestone:** **Phase 3 foundation is now delivered** with local playlist/library operations integrated into the native Android flow.
- **Partially present from future phases:** native player controls now include queue-aware playback for native media sources (`previous / play-pause / next`), playback notification sync, local resume-session restore, and user-selectable repeat modes (`off / all / one`).
- **Expanded this cycle:** auth/profile/settings parity now includes native profile navigation with dedicated local login, signup, and settings screens (still local-only; Firebase auth wiring remains pending).

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

#### Phase 3 — Native playlists & library 🟠 In progress (account-aware local foundation shipped)
**What is implemented now**
- Local-first playlist storage layer backed by native SharedPreferences persistence.
- Playlist persistence is now account-scoped locally (guest vs signed-in profile identity) with legacy migration support.
- Native create playlist, add selected track to playlist, remove track, delete playlist, and play-all flow.
- Dedicated native library screen/navigation from the main Android flow with playlist and per-track actions (play, remove, delete).

**Remaining scope**
- Firestore-backed sync and cross-device playlist reconciliation.

**Exit criteria**
- Feature parity with the web playlist workflow.

#### Phase 4 — Player parity + TV polish 🟠 In progress (queue foundation expanded)
**Already available (foundation)**
- Queue-aware native playback now builds a playable media queue from the active track list and keeps `previous` / `next` navigation aligned with playable native items, including repeat mode control (`off` / `all` / `one`).
- Playback notification controls remain synced with player state updates.
- Local playback session restore now brings back track list selection, resume position, and repeat mode (`off` / `all` / `one`) for native-playable items.

**Remaining scope**
- Robust queue management (beyond single active session restore).
- Improved TV remote / D-pad focus navigation.
- Full-screen player + richer artwork handling.
- Background playback lifecycle hardening.

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
- Next.js production builds now avoid Firebase App Hosting auto-init warnings during SSR/static generation by using explicit config on server renders.

**Remaining scope**
- CI for signed APK/AAB release builds.
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

2.  **Wait for the Build:** Pushing to `main` will automatically trigger a build process. You can monitor its progress in the **"Actions"** tab of your GitHub repository. Look for the "Build Android APK" workflow.

3.  **Download the APK:** Once the workflow is complete (it will have a green checkmark), click on it. On the summary page, you will find an "Artifact" named **HarmonyStream-Android-App**. Click it to download the `app-debug.apk` file.

4.  **Install the App:** Transfer the downloaded `app-debug.apk` file to your Android TV and install it. You may need to enable "Install from unknown sources" in your TV's settings.

### APK Troubleshooting ("App opens, but content is blank/not loading")

If the Android app shell opens but the actual app content is blank or never loads, the cause is usually one of these:

1. **Android SDK is not configured in the local shell**
   - Run `npm run android:sdk:prepare` to auto-detect a local SDK and write `android/local.properties` for you.
   - If auto-detection fails, set Android SDK path in either `ANDROID_HOME` / `ANDROID_SDK_ROOT` or `android/local.properties` manually (`sdk.dir=/path/to/Android/Sdk`).
   - Then rerun: `npm run apk:debug`.

2. **Missing YouTube API key at build time**
   - The app fetches songs from YouTube Data API and expects `NEXT_PUBLIC_YOUTUBE_API_KEY` during build/runtime.
   - If the key is missing/invalid or quota is exhausted, dynamic song content may fail to load.

3. **No internet connectivity on device**
   - The APK bundles UI files, but song discovery/streaming still requires network access.


4. **Gradle dependency download fails with `403 Forbidden` in CI/dev shell**
   - Some environments block direct Maven/Google repository access and require internal mirrors.
   - Set these environment variables before building Android:
     - `ANDROID_GOOGLE_MAVEN_MIRROR` (mirror URL for Google Maven artifacts)
     - `ANDROID_MAVEN_CENTRAL_MIRROR` (mirror URL for Maven Central artifacts)
   - Then rerun: `./android/gradlew -p android assembleDebug`

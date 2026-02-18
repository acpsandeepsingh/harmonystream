# HarmonyStream

This is a NextJS starter app for a music streaming service, built in Firebase Studio.

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

### Current delivery checkpoint (where coding stands now)
- **Reached milestone:** **Phase 1 is complete** and integrated into the native Android flow.
- **Partially present from future phases:** basic native player controls (`previous / play-pause / next`) and playback notification sync are already wired.
- **Not started yet:** full native search page, playlist/library parity, auth/profile/settings parity, and release hardening pipeline tasks.

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

#### Phase 2 — Native search screen 🟡 Pending
**Scope to implement**
- Native search UI: query input + source selector (`youtube` / `youtube-all`).
- Call repository search methods from UI.
- Render loading/error/empty/success states.
- On search result tap: play through ExoPlayer and sync playback notification.

**Exit criteria**
- Behavior matches current web search flow first, even if visual polish comes later.

#### Phase 3 — Native playlists & library 🟡 Pending
**Scope to implement**
- Playlist storage layer (Firestore or local-first with sync).
- Add to playlist, create/delete playlist, play all.
- Native library page with playlist details + track operations.

**Exit criteria**
- Feature parity with the web playlist workflow.

#### Phase 4 — Player parity + TV polish 🟠 In progress (foundation only)
**Already available (foundation)**
- Basic queue stepping (`previous`/`next`) and native playback notification controls are available.

**Remaining scope**
- Robust queue management and resume position.
- Improved TV remote / D-pad focus navigation.
- Full-screen player + richer artwork handling.
- Background playback lifecycle hardening.

**Exit criteria**
- Stable TV-first UX under long sessions and app lifecycle changes.

#### Phase 5 — Auth/profile/settings parity 🟡 Pending
**Scope to implement**
- Native login/signup/profile/settings screens.
- Firebase auth/session integration.
- User-specific sync rules migration.

**Exit criteria**
- Fully native end-to-end authenticated flow.

#### Phase 6 — Build/release reliability 🟡 Pending
**Scope to implement**
- Final Gradle/repository mirror setup for target environment.
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
    *   **Android App Build:** A native Android `.apk` file is built, bundling your website inside it.

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

1. **Wrong build command for Android assets**
   - For APK builds you must use the Android build path (`npm run apk:debug` or at minimum `npm run build:android` + `npm run cap:sync:android`).
   - Running a regular web build (`npm run build`) can generate asset paths for GitHub Pages (`/harmonystream/...`), which are not valid inside the APK webview.

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

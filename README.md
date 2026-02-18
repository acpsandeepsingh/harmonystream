# HarmonyStream

This is a NextJS starter app for a music streaming service, built in Firebase Studio.

To get started, take a look at `src/app/page.tsx`.


## Native Android Rewrite Status

The Android module now runs as a fully native screen (RecyclerView + Media3/ExoPlayer player) instead of loading the web app through a WebView. The current native build includes:

- Native track list UI and native playback controls (Previous/Play-Pause/Next).
- Native audio/video playback using Media3 ExoPlayer.
- Existing `PlaybackService` notification controls integrated with player state updates.

> Note: The native rewrite currently uses sample media URLs as a baseline player implementation. YouTube-specific catalog, search, and account-connected features still need to be implemented natively in future steps.


## Native Migration Roadmap (Step-by-Step)

### Phase 1 (Implemented)
- Added Android-native `Song` model and `YouTubeRepository` data layer for YouTube search API fetches.
- Added Android `YOUTUBE_API_KEY` configuration via Gradle `buildConfigField` from `YOUTUBE_API_KEY` env/property.
- Main screen now tries loading a YouTube song list first; if it fails, app safely falls back to local demo streams.

### Next phases
- **Phase 2:** Native search screen + filters + loading/error states.
- **Phase 3:** Native playlist/library management with Firebase sync.
- **Phase 4:** Full native YouTube playback/session handling and TV polish.

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

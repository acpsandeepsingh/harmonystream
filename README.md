# HarmonyStream

This is a NextJS starter app for a music streaming service, built in Firebase Studio.

To get started, take a look at `src/app/page.tsx`.

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

This project includes a native Android wrapper app that provides a better experience on Smart TVs by enabling smooth D-pad navigation. This app is self-contained and does not require an internet connection to run, as it bundles the website files directly into the APK.

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

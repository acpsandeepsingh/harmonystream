# Firebase Studio

This is a NextJS starter in Firebase Studio.

To get started, take a look at src/app/page.tsx.

## APK build help

This repository is currently a **Next.js web app** and does not include an Android project folder (`./android`) yet.

If your CI step fails with:

```bash
chmod +x ./android/gradlew
# chmod: cannot access './android/gradlew': No such file or directory
```

it means the Android project has not been generated.

### Create Android project (Capacitor)

1. Install Capacitor dependencies:

```bash
npm install @capacitor/core @capacitor/cli @capacitor/android
```

2. Initialize Capacitor (first time only):

```bash
npx cap init harmonystream com.harmonystream.app --web-dir=out
```

3. Build web assets:

```bash
npm run build
```

4. Add Android platform (creates `./android/gradlew`):

```bash
npx cap add android
```

5. Sync updates to Android:

```bash
npx cap sync android
```

6. Build APK:

```bash
cd android
chmod +x ./gradlew
./gradlew assembleDebug
```

APK output (debug):

`android/app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions APK workflow

This repository now includes `.github/workflows/android-apk.yml`.

- If `android.zip` exists in the repo root, the workflow unzips it first.
- Then it checks for `android/gradlew`, runs `chmod +x android/gradlew`, and builds the debug APK.
- The APK is uploaded as an artifact named `app-debug-apk`.

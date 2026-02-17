# Firebase Studio

This is a NextJS starter in Firebase Studio.

To get started, take a look at src/app/page.tsx.

## APK build help

This repository now includes Capacitor configuration so CI can generate the Android project automatically and build an APK.

### Local APK build

```bash
npm run apk:debug
```

That command will:

1. Build the static Next.js output (`out/`)
2. Create the Android project if missing
3. Sync web assets to Android
4. Build a debug APK

APK output (debug):

`android/app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions APK workflow

This repository includes `.github/workflows/android-apk.yml`.

- Installs Node + Java + Android SDK dependencies
- Accepts Android SDK licenses in CI
- Installs Capacitor Android toolchain packages for CI
- Builds static web assets
- Auto-generates `android/` via Capacitor when missing
- Syncs assets (`npx cap sync android`)
- Normalizes and executes `android/gradlew`
- Writes `android/local.properties` with CI SDK path when needed
- Runs `./android/gradlew -p android assembleDebug`
- Uploads the APK as `app-debug-apk`

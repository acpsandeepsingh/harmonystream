# HarmonyStream

This is a Next.js starter app for a music streaming service, built in Firebase Studio.

To get started, take a look at `src/app/page.tsx`.

## Android-Only Agent Remaining Work (from `agent.md`)

The following items are still required to fully satisfy the Android-only execution plan:

### 1) Baseline validation still required per delivery
- Verify clean local git/project state before each run.
- Compile Android module successfully in an SDK-configured environment.
- Confirm playback service + notification channel behavior on device/emulator.
- Confirm media action broadcast path end-to-end from notification to active controller.

### 2) Playback contract completeness checks
- Re-verify the command contract execution path for: `play`, `pause`, `next`, `previous`, `seek`, `setQueue`, `getState`.
- Ensure all commands are routed through the foreground playback service and reflected back to UI sync events.

### 3) Lifecycle/background reliability validation
- Verify active playback survives app minimization and resumes cleanly after activity recreation.
- Verify persisted session restore covers queue, track index, playback state, and position after process death/reopen.

### 4) Notification UX/device validation
- Validate compact controls (`Previous`, `Play/Pause`, `Next`) on physical device/emulator.
- Validate progress bar updates while playing and state remains consistent after reopening via notification tap.

### 5) Android TV D-pad deterministic flow verification
- Validate section model transitions in runtime:
  - Menu -> Songs (`Right`/`Down`)
  - Songs left edge -> Menu
  - Songs bottom/last -> Player
  - Player `Up` -> Songs
- Validate first focus lands on Menu.
- Validate back behavior: Songs/Player -> Menu first, then exit on subsequent back.
- Validate focus context recovery after selecting song and returning.

### 6) Full validation checklist still pending in this container
The `agent.md` delivery checklist requires running these each delivery:
- `npm run android:sdk:prepare`
- `npm run apk:debug`
- On-device behavioral checks (background playback, notification actions/progress, reopen sync, force-stop restore)
- Android TV D-pad checks (all 12 navigation expectations)

> Current blocker in this container: Android SDK path is not configured, so APK build and runtime/device validations cannot be completed here until SDK variables/path are set.

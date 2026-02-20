# Android-Only Execution Agent (No Website Changes)

This file defines an automation workflow for implementing APK behavior changes while keeping the web app code and hosted website behavior unchanged.

## Goal
- Keep the existing web player UI/UX unchanged.
- Apply required behavior changes only in the Android APK layer.
- Ensure playback continues when app is minimized.
- Ensure notification controls can change track (Previous / Play-Pause / Next).
- Ensure progress is reflected in notification.
- Ensure Android TV D-pad navigation is deterministic across **Menu / Songs / Player** sections.

## Scope Guardrails
- **Allowed edits:** `android/**`, Android build config, native bridge files.
- **Do not modify website behavior/UI:** avoid changing `src/app/**` and web feature logic unless a native bridge hook is strictly required.
- **No hardcoded secrets:** use Gradle/env/secrets properties.
- **No remote-host dependency for app shell:** APK should run with bundled assets.

## Architecture Rules
1. Web UI can remain the primary visual player surface.
2. Background playback + notification transport controls must be owned by Android native service.
3. Web layer sends playback intents/commands to native layer (bridge/plugin).
4. Native layer returns playback state to web layer for UI sync.
5. Android TV focus must be section-driven (Menu, Songs grid/list, Player controls), not ad-hoc per widget.

## Implementation Workflow (Automatable)

### Phase 1: Baseline Validation
1. Verify local project state is clean.
2. Verify Android module compiles with current setup.
3. Confirm playback service and notification channel are present.
4. Confirm media action broadcast path exists.

### Phase 2: Android-Only Playback Contract
1. Define command contract (play, pause, next, previous, seek, setQueue, getState).
2. Implement/extend native bridge adapter in Android layer only.
3. Route all playback control commands to foreground playback service.
4. Route notification action callbacks back to active playback controller.

### Phase 3: Lifecycle and Background Reliability
1. Start/maintain foreground service while playback is active.
2. Persist playback session (track index, position, queue, play state).
3. Restore session on process/activity recreation.
4. Keep notification progress updated on interval and state transitions.

### Phase 4: Notification UX
1. Notification shows title/artist/art.
2. Compact actions: Previous, Play/Pause, Next.
3. Progress bar reflects current position/duration.
4. Tapping notification reopens playback surface.

### Phase 5: Offline APK Shell Behavior
1. Ensure web assets are bundled into APK.
2. Ensure startup path uses bundled content path for app shell.
3. Avoid requiring hosted site connection for shell rendering.

### Phase 6: Android TV D-pad Focus Model
1. Define three focus anchors in main screen layout:
   - `menu_section` (left/navigation/actions)
   - `song_section` (song cards/list)
   - `player_section` (transport/player controls)
2. Ensure song cards are focusable/clickable and can receive D-pad events.
3. Implement deterministic movement rules:
   - **From Menu**: Right/Down → Songs
   - **From Songs**:
     - Left edge → Menu
     - Down on last visible/last item → Player
   - **From Player**: Up → Songs
4. Initial focus behavior on page load:
   - First focus lands on **Menu**.
   - User can go Down to Songs, then Down to Player.
5. Back behavior:
   - If focus is in Songs/Player, Back returns focus to Menu first.
   - Only subsequent Back exits/navigates out.
6. After selecting a song card and returning, restore three-way navigation context (Menu/Songs/Player) rather than trapping focus.

## Definition of Done
- App launches with bundled UI from APK.
- Audio continues with app minimized.
- Notification shows and controls playback reliably.
- Next/Previous from notification updates the active track.
- Progress bar updates while playing.
- Android TV D-pad flow behaves as specified:
  - Menu ↔ Songs ↔ Player section transitions are consistent.
  - Songs left-edge returns to Menu.
  - Songs last-row/down returns to Player.
  - Back from Songs/Player returns to Menu first.
- No required changes to website UI logic for this delivery.

## Validation Checklist (Run Every Delivery)
- Build checks:
  - `npm run android:sdk:prepare`
  - `npm run apk:debug`
- Behavior checks on device/emulator:
  1. Start playback, minimize app, verify audio continues.
  2. Use notification Play/Pause/Next/Previous.
  3. Verify notification progress moves while playing.
  4. Reopen app from notification and confirm state sync.
  5. Force-stop/reopen and verify session restore.
- Android TV D-pad checks:
  6. First load focus starts in Menu.
  7. Menu Right/Down moves to Songs.
  8. Songs Left from first/left edge moves to Menu.
  9. Songs Down from last item moves to Player.
  10. Player Up moves back to Songs.
  11. Back from Songs/Player returns focus to Menu first.
  12. After song selection and return, 3-section navigation still works.

## Change Control
- Keep commits focused to Android runtime/playback files.
- Include concise changelog in commit body:
  - What changed
  - Why needed for background/notification reliability
  - How validated

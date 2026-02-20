# HarmonyStream

This is a Next.js starter app for a music streaming service, built in Firebase Studio.

To get started, take a look at `src/app/page.tsx`.

## Android-Only Agent Implementation Status (from `agent.md`)

This status tracks the Android-only delivery plan and has been updated to reflect what is implemented in the Android layer vs what still requires runtime/device validation.

### Phase 1: Baseline Validation
- [x] Playback service and notification channel exist in Android runtime.
- [x] Media action broadcast path exists between notification actions and active controller bridge.
- [ ] Per-delivery clean-state and full compile verification in a correctly configured Android SDK environment.

### Phase 2: Android-Only Playback Contract
- [x] Command contract is implemented in Android bridge/service path for `play`, `pause`, `next`, `previous`, `seek`, `setQueue`, `getState`.
- [x] Web bridge methods route commands to Android playback service.
- [x] Service emits playback state updates back to web layer via broadcast + JS event.
- [x] Notification action callbacks are routed back to the active playback controller bridge.

### Phase 3: Lifecycle and Background Reliability
- [x] Foreground playback service is used while active playback is running.
- [x] Playback session persistence exists (queue, selected index, position, play state).
- [x] Session restore path exists on activity/process recreation.
- [x] Notification progress refresh loop exists and updates state on interval.
- [ ] Device/emulator verification still required for minimize/restore and force-stop/reopen scenarios.

### Phase 4: Notification UX
- [x] Notification includes title/artist/artwork (when provided).
- [x] Notification compact actions include Previous / Play-Pause / Next.
- [x] Notification progress reflects current position/duration.
- [x] Notification tap reopens playback surface and can deliver pending action.
- [ ] Device-level behavioral verification still required each delivery.

### Phase 5: Offline APK Shell Behavior
- [x] App shell loads bundled assets via `WebViewAssetLoader` path.
- [x] Startup defaults to bundled app assets and falls back to bundled offline shell.
- [x] Runtime shell does not require hosted site for initial rendering.

### Phase 6: Android TV D-pad Focus Model
- [x] Three focus sections exist in native layout (`menu_section`, `song_section`, `player_section`).
- [x] Song rows are focusable/clickable and handle directional edge transitions.
- [x] Deterministic section movement rules are implemented:
  - Menu Right/Down -> Songs
  - Songs Left edge -> Menu
  - Songs last-item Down -> Player
  - Player Up -> Songs
- [x] Initial focus targets Menu on screen load.
- [x] Back from Songs/Player returns to Menu before exit.
- [x] Focus context restoration after song interaction is handled by section focus helpers.
- [ ] End-to-end Android TV remote validation is still required on emulator/device.

## Definition of Done Status
- [x] App launches with bundled UI from APK.
- [x] Android-side background playback + notification contract is implemented.
- [x] Notification controls are wired to active playback control path.
- [x] Notification progress update path is implemented.
- [x] Android TV section-navigation logic is implemented natively.
- [ ] Remaining done criteria that require hardware/emulator runtime validation are pending per-delivery execution.

## Validation Checklist (Run Every Delivery)

### Build checks
- [ ] `npm run android:sdk:prepare`
- [ ] `npm run apk:debug`

### Behavior checks on device/emulator
1. [ ] Start playback, minimize app, verify audio continues.
2. [ ] Use notification Play/Pause/Next/Previous.
3. [ ] Verify notification progress moves while playing.
4. [ ] Reopen app from notification and confirm state sync.
5. [ ] Force-stop/reopen and verify session restore.

### Android TV D-pad checks
6. [ ] First load focus starts in Menu.
7. [ ] Menu Right/Down moves to Songs.
8. [ ] Songs Left from first/left edge moves to Menu.
9. [ ] Songs Down from last item moves to Player.
10. [ ] Player Up moves back to Songs.
11. [ ] Back from Songs/Player returns focus to Menu first.
12. [ ] After song selection and return, 3-section navigation still works.

> Environment note: this container may not have an Android SDK/emulator attached, so device-runtime checks can remain pending until executed in a configured Android environment.

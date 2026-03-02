# Android WebView + Player Regression Playbook

Use this when bundled web assets stop loading or native player state/UI regresses.

## 1) Do not remove fallback path handlers
When changing WebView loading, keep support for all of these request path families:
- `/_next/*`
- `/next/*`
- `/harmonystream/_next/*`
- `/harmonystream/next/*`
- route fallback under `/` to `public/...`

And keep fallback probing order:
1. `public/...`
2. root-level asset path

## 2) Keep startup mode safe
On app startup, homepage must remain visible. Do **not** hide WebView by default.
- Audio mode: show homepage + mini player.
- Video mode: fullscreen WebView, hide mini player.
- If playback is not active, force video mode off in UI.

## 3) Player state contract (must stay in sync)
`PlaybackService` state broadcasts and snapshots must include:
- `title`
- `artist`
- `thumbnailUrl`
- `playing`
- `position_ms`
- `duration_ms`
- `video_mode`

If any field is removed, native UI and web UI can desync.

## 4) Control behavior rules
- Play button must toggle icon based on `playing` state.
- Mode button must toggle icon (`video` when currently audio mode, `audio` when currently video mode).
- Seek must trigger a state broadcast after `ACTION_SEEK` so UI/progress updates immediately.
- Toggling video/audio mode while playing should re-resolve stream for active `videoId`.

## 5) Quick validation checklist before merge
1. Launch app first time -> homepage visible (not black).
2. Start a track -> title/artist/thumbnail update in mini player.
3. Seek bar drag -> playback position updates immediately.
4. Play/Pause icon toggles correctly.
5. Mode icon toggles audio/video icon correctly.
6. Switching mode while playing keeps playback running.

## 6) Guardrail for future edits
If refactoring `WebViewManager`, `WebAppActivity`, `PlaybackService`, or `PlayerUiController`:
- Preserve this playbook behavior.
- Add/update regression tests where possible.
- Mention this file in PR description under "Regression safety".

# Native Android parity with website (UI + functionality)

This app can match the website experience in a **pure native Android implementation**, but it must be delivered as a feature-by-feature parity roadmap.

## What is already native and working

- Native launcher + playback flow starts in `MainActivity` and keeps playback in-app via `PlaybackService`.
- Native transport actions are wired (`previous / play-pause / next`) and notification controls are integrated with player state sync.
- Native search, local playlists, library, and profile screens exist and can run without WebView.
- Native playlist sync now targets Firestore REST (`native_sync/{accountKey}`) with local fallback cache.

## What "same as website" means in practice

To look and behave like the website, native Android needs these equivalents:

1. **Home feed parity**
   - Web: genre chips, top music cards, play-all, liked songs affordances.
   - Native target: same sections, typography scale, spacing rhythm, and card interactions.

2. **Navigation parity**
   - Web routes (`/`, `/search`, `/library`, `/history`, `/profile`, `/settings`) should map to native destinations with persistent nav (bottom bar / rail for TV).

3. **Data parity**
   - Web uses Firestore collections (`songs`, `api_cache`, user playlists).
   - Native must use same query semantics and caching policy so results/ordering feel identical.

4. **State parity**
   - Queue, repeat mode, liked songs, and playlist membership must behave the same across sessions and sign-in states.

5. **Visual parity system**
   - Define shared design tokens (spacing, corner radius, color roles, motion) and apply them in Android XML/theme components.

## Recommended implementation sequence

1. **Lock design tokens** from the website into Android theme resources (colors, dimensions, type scale).
2. **Build a native shell navigation** matching website IA (home/search/library/history/profile/settings).
3. **Recreate home cards + genre filters** using RecyclerView sections.
4. **Align search ranking + filters** with the web query behavior.
5. **Finalize library/profile/settings parity** including account/sync indicators.
6. **Parity QA checklist**: compare every website screen against native screenshots and interaction scripts.

## Short answer

Yes — this can be pure native Android and still match the website. The current app already has native playback and core screens; the remaining work is mostly **UI parity polish + feature completion per route**, not a WebView dependency.

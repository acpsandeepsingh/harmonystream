# Firebase web parity notes (search + song listing)

This document maps how the **web app** currently talks to Firestore for song listing and search, and how the **native Android app** should mirror that behavior and schema.

## 1) Firebase project wiring

Use the same Firebase project values across web and native:

- `projectId`: `studio-8258640677-40b59`
- `apiKey`: `AIzaSyAbHGZu-NdtvQhV_IkWh0htVgsSoIHam-s`
- `appId`: `1:312831088099:web:e9129a94070e9cff033cd6`
- `authDomain`: `studio-8258640677-40b59.firebaseapp.com`

In this repo:

- Web config is in `src/firebase/config.ts`.
- Android injects matching values into `BuildConfig` from `android/app/build.gradle`.

## 2) Firestore collections used for songs

### `songs/{songId}` (global catalog)

Used by both web home/search flows and Android read/write adapters.

Expected fields (web shape):

- `id` (string)
- `videoId` (string)
- `title` (string)
- `artist` (string)
- `album` (string)
- `duration` (number, seconds)
- `genre` (string)
- `year` (number)
- `thumbnailUrl` (string)
- `title_lowercase` (string)
- `title_keywords` (string[])
- `search_keywords` (string[])

Android currently also writes compatibility aliases (`songId`, `audioUrl`, `durationMs`, `mediaUrl`), which is fine because Android readers already support fallback keys.

### `api_cache/{cacheId}` (genre/home cache)

Web home page checks/uses cache docs keyed as:

- `genre-${selectedGenre.toLowerCase().replace(/\s+/g, '-')}`
  - examples: `genre-new-songs`, `genre-bollywood`, `genre-indi-pop`

Cache document fields:

- `query` (string)
- `timestamp` (ISO string)
- `songs` (array of song objects in the same shape above)

Android home loader attempts multiple genre cache key variants (`genre-kebab`, `genre-compact`, etc.) then falls back to reading `songs` directly.

## 3) Web flow: song listing (home)

1. Build query from selected genre:
   - `latest indian songs` for New Songs
   - `latest {Genre} songs` for other genres
2. Read `api_cache/{genre-...}`.
3. If cache exists, return cached `songs`.
4. Else fetch from YouTube API, map to app song model, then:
   - Save cache doc in `api_cache/{genre-...}`
   - Upsert each song into `songs/{song.id}` (`setDoc(..., { merge: true })`)

## 4) Web flow: search

Search source switch:

- `database`: Firestore `songs` collection search
- `youtube` / `youtube-all`: YouTube live search, then backfill `songs`

### Database search behavior (important parity)

- Split user query into lowercase terms.
- Firestore query: `where('search_keywords', 'array-contains-any', terms.slice(0, 10))` and `limit(50)`.
- Client computes relevance score by counting matched keywords and sorts descending.

### YouTube search behavior

- Calls YouTube APIs and maps responses into the song model.
- Generates keyword fields (`title_lowercase`, `title_keywords`, `search_keywords`) using normalized words + prefix tokens.
- Writes each fetched song into `songs/{id}` with merge.

## 5) Android current behavior vs web

### Already aligned

- Uses same Firebase project/api key values from `BuildConfig`.
- Reads home songs from `api_cache` by genre-doc IDs, then falls back to `songs` listing.
- Reads `songs` via Firestore REST and maps tolerant field aliases.
- Writes songs with metadata including `title_lowercase`, `title_keywords`, `search_keywords`.

### Gap to close for strict web parity

Android `FirebaseSongRepository.search()` currently:

- fetches a page of `songs` ordered by `title_lowercase`
- applies local substring matching on title/artist/id

Web `database` search currently uses Firestore keyword matching (`array-contains-any`) over `search_keywords` + scoring.

To match web exactly in Android, implement Firestore REST `runQuery` (or equivalent SDK query) for `search_keywords array-contains-any` and keep the same relevance sorting logic.

## 6) Rules assumptions

Current Firestore rules allow:

- Public read/list on `songs` and `api_cache`
- Authenticated create/update on `songs` and `api_cache`

So Android writes to `songs` should include user auth token when available (already supported), while reads can work without auth in default policy.

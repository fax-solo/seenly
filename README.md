# Seenly

[![Android CI](https://github.com/fax-solo/seenly/actions/workflows/android.yml/badge.svg)](https://github.com/fax-solo/seenly/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/fax-solo/seenly?display_name=tag&sort=semver)](https://github.com/fax-solo/seenly/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF)
![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4)

Seenly is a local-first Android entertainment tracker for anime, cartoons, TV
series, movies, and documentaries. It is built with Kotlin and Jetpack Compose,
stores everything in a local Room database, and never requires an account,
backend, analytics service, or cloud sync.

[Download the latest build](https://github.com/fax-solo/seenly/releases/latest) ·
[Build from source](#build-and-install) ·
[Report a bug](https://github.com/fax-solo/seenly/issues)

> [!NOTE]
> GitHub Releases currently provide a debug-signed APK
> (`com.seenly.app.debug`) for sideloading and testing. Production release
> signing is not configured yet.

**Version:** 1.1.0  
**Package:** `com.seenly.app` (debug builds use `com.seenly.app.debug`)  
**Minimum Android:** 8.0 / API 26  
**Compile and target SDK:** 36

---

## What is included

### Library and tracking

- Home dashboard with seven configurable sections: continue watching, upcoming
  episodes, recently watched, watchlist, recommendations, quick stats, and an
  activity calendar. The "For you" section is a swipeable recommendation deck:
  artwork stays centred, swiping right (or tapping Add) imports the title, and
  swiping left dismisses it permanently. A "View all" screen lists every
  candidate with the same actions.
- Library grid with search, type/category/status/genre filters, minimum rating,
  release-year filters, and six sort orders.
- Detail screens for titles, seasons, and episodes, with progress, next-episode
  calculation, quick actions, related titles, provider metadata, and external
  ratings (IMDb and Rotten Tomatoes for films/series; MyAnimeList as well for
  anime).
- Watch status: plan to watch, watching, on hold, dropped, completed; plus
  paused dates. Status can be changed directly from a list row or from the
  detail screen without waiting for an episode to air.
- Upcoming anime with no announced episode count keep a selectable season
  placeholder, so they can be planned now and moved to watching when episodes
  are released.
- Append-only watch history. Rewatches add viewings without inflating the number
  of unique completed episodes.
- Ratings from 0 to 10 in 0.5 steps; reviews with spoiler flags, notes, and
  personal timestamps.
- Idempotent completion actions: repeated taps never create duplicate watch
  events. Season completion and "watched up to here" are idempotent too. Lists
  have a bulk "mark all as completed" action with a confirmation dialog.
- Manual title entry for anything missing from a provider. Manual series with a
  known episode count get real, evenly distributed season/episode rows so
  progress can be tracked offline.

### Organization and insight

- Built-in Favorites list and computed Plan to Watch list.
- Custom lists with duplicate-safe membership, reordering, rename, and delete
  without deleting titles.
- Calendar of upcoming and recently aired episodes.
- Weekly, monthly, and yearly goals for episodes, movies, titles, or minutes,
  with Monday-start weeks.
- Statistics: unique completed titles by category, distinct episodes versus
  viewings, known-runtime-only hours with an explicit unknown count, streak
  calculations, monthly trends, genre/category breakdowns, ratings, and
  recently completed titles.
- Deterministic local recommendations based on rated/completed/favorite genres;
  no server profiling. The remote recommendation deck extends this with sequels
  and genre-matched titles, never suggesting library titles back.
- Reviews hub and a More hub for lists, calendar, goals, reviews, backup, and
  settings.

### Discovery

- AniList GraphQL adapter (no key required) for anime search, trending,
  seasonal titles, details, episode schedules, relations, and recommendations.
  TV-format prequel/sequel chains are grouped into numbered seasons, so a
  multi-cour anime imports as one title instead of one entry per cour.
- Cinemeta (Stremio's IMDb mirror, no key required) for movie and series search,
  trending, genre browse, details, IMDb ratings, and real episode titles.
- TVmaze (no key required) as a series companion/fallback with full season and
  episode listings plus IMDb cross-references.
- Jikan (no key required) as the secondary anime search/seasonal fallback and
  the source of episode titles and MyAnimeList scores.
- External ratings: IMDb from Cinemeta, MyAnimeList from Jikan with a public
  MyAnimeList page fallback, and a best-effort Rotten Tomatoes Tomatometer
  scrape from public pages. Lookups run concurrently, are rate-limited and
  cached, and never fail an import. A missing rating stays unknown rather than
  being guessed.
- Discover has category chips only; no provider/source picker and no API-key
  configuration is required. Changing category or search cancels older
  in-flight requests so a stale response can never overwrite the active feed;
  empty filters show a clear empty state instead of leftover results.
- Provider-ID deduplication through a unique `(provider, externalId)` index.
- Exact-title matching is used only for explicit local-link decisions; there is
  no fuzzy or automatic destructive merging.
- Coil 3 image loading with a 128 MB disk cache. Remote images are never
  required for the app to open or for personal data to be readable.

### Interface

- Jetpack Compose and Material 3 with a cinematic dark-first palette and amber
  accent; light, dark, and system modes are persisted.
- Full English and Arabic resource sets (411 keys each), RTL support, and an
  in-app language override persisted in DataStore. Dates, numbers, weekday and
  month names, notification channel names, and notification bodies follow the
  app language configuration rather than the raw system default locale.
- Edge-to-edge layout, light/dark system-bar styling, responsive poster grids,
  empty/error states, and accessible content descriptions.
- Local notifications via WorkManager for upcoming episodes, stale-progress
  reminders, and a Monday weekly summary. Finishing a title posts an immediate
  "finished" notification (event-driven, with its own toggle) and an in-app
  confirmation snackbar. No push service or account is used.

### Backup and privacy

- Versioned JSON backup through the Storage Access Framework.
- Merge and replace import modes. Both validate the complete file before one
  database transaction; a failed import leaves the existing library intact.
- Merge is idempotent: provider links, exact title/type signatures, seasons,
  episodes, list memberships, and watch events are deduplicated.
- Replace mode preserves backup IDs so relationships remain intact, and only
  removes data in the app's own database.
- No backend, Firebase, Supabase, ads, analytics, crash reporting, accounts, or
  cloud sync. No network logging or analytics SDK is included.
- Automatic Android backup and device-to-device transfer are explicitly disabled.
  Users move their own data with the app's export/import flow.

---

## Build and install

### Install from GitHub Releases

1. Open [Releases](https://github.com/fax-solo/seenly/releases/latest).
2. Download `seenly-<version>-debug.apk`.
3. Install it on a device running Android 8.0 or newer. You may need to allow
   installation from your browser or file manager.
4. Optionally verify the download against `SHA256SUMS`.

Or install over ADB:

```bash
adb install -r seenly-1.1.0-debug.apk
```

The release APK is debug-signed and uses the `com.seenly.app.debug` package id.
It can be installed alongside a production-signed build later.

### Requirements

- JDK 17
- Android SDK Platform 36 and Build Tools compatible with AGP 8.13.2
- The included Gradle wrapper (8.14.3)

Create `local.properties` if it does not already exist:

```properties
sdk.dir=/path/to/Android/Sdk
```

No API keys are required. Every metadata provider used by Discover (AniList,
Cinemeta, TVmaze, Jikan) is credential-free; Rotten Tomatoes ratings are read
best-effort from public pages.

### Commands

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is debug-signed. Configure a release keystore and release signing before
distribution; no release-signed artifact is included. App bundles disable
language splitting so English and Arabic always travel together and the in-app
language switch works offline without Play Core downloads.

### Continuous integration and releases

The workflow at
[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every
push to `main`, every pull request, manual dispatches, and `v*` tags. It:

1. Sets up JDK 17 and Android SDK 36.
2. Runs the JVM/Robolectric test suite.
3. Runs Android lint.
4. Builds the debug APK.
5. Uploads the APK and `SHA256SUMS` as a workflow artifact.
6. For a `v*` tag, publishes a GitHub Release with both files attached.

Create a release by pushing a matching tag:

```bash
git tag v1.1.0
git push origin v1.1.0
```

The existing workflow publishes the debug-signed APK. Add a private release
keystore and signing configuration before distributing a production-signed
build.

### Build memory note

The project is configured with conservative Gradle/Kotlin heaps for constrained
machines (`gradle.properties`). On systems with less than roughly 8 GB of free
memory, stop other Gradle/Kotlin daemons before running tests or lint.

---

## Provider setup

| Provider | Key | Used for |
| --- | --- | --- |
| AniList | Not required | Anime search, trending, seasonal, details, airing dates, relations, franchise grouping |
| Cinemeta | Not required | Movie/series search, trending, genre browse, details, IMDb ratings, episode titles |
| TVmaze | Not required | Series search/details fallback, full season/episode listings, IMDb ids |
| Jikan | Not required | Secondary anime source, episode titles, MyAnimeList scores |
| MyAnimeList | Not required | Public-page score fallback when Jikan is unavailable |
| Rotten Tomatoes | Not required | Best-effort public Tomatometer scrape for films and series |

- Server-side API keys do not exist in this app and no credential UI is shown.
- Provider calls use HTTPS only. Cleartext HTTP is blocked by
  `network_security_config.xml`.
- Discover requires network access. Library, lists, goals, stats, reviews,
  backup, and all tracking actions work offline.
- A failed metadata refresh is non-fatal. Existing metadata and all personal data
  remain available. External ratings are best-effort and may be absent.
- Metadata refresh never clears ratings, reviews, notes, watch history, or
  progress. User-edited core fields are marked `locallyEdited` and are protected
  from provider overwrites. Watched episodes are retained even if a provider
  later omits them from its payload.

---

## Architecture

```text
MainActivity
  -> SeenlyRoot (Navigation Compose)
     -> screen composables + per-screen ViewModels
        -> repository interfaces (domain/repository)
           -> Room-backed implementations (data/repository)
           -> Room database (SQLite, schema v2)
           -> provider adapters (AniList / Cinemeta / TVmaze / Jikan)
           -> Rotten Tomatoes rating scraper
           -> DataStore settings
           -> SAF backup/restore
           -> Coil image loader
           -> WorkManager local notifications
```

Key decisions:

- **Single `:app` module.** Small project, clear boundaries, no Hilt or annotation
  processor overhead beyond Room/KSP.
- **Manual dependency injection.** `AppContainer` owns the database, repositories,
  settings, HTTP clients, image loader, and notification scheduling.
- **Room is the source of truth.** Provider metadata is cached in the same local
  database; personal fields are separate from provider fields.
- **Append-only viewing history.** Progress is derived from completed watch events
  except where the user explicitly sets a status. This makes duplicate taps and
  rewatches deterministic.
- **Provider-ID identity.** A title is linked to an external title by
  `(provider, externalId)`. Exact title/type matching is a separate, explicit
  operation and never uses fuzzy matching.
- **No destructive migrations.** Schema v2 adds external-rating columns and is
  reached through an explicit 1→2 Room migration. Schemas are committed under
  `app/schemas`. `fallbackToDestructiveMigration` is not used.
- **Transactions.** Imports, season completion, progress refresh, and other
  multi-row operations run in Room transactions.

### Important data rules

- Ratings are validated to the 0.5 grid from 0 through 10, including in backup
  validation.
- Watch events count only when completed; future-dated events are excluded from
  statistics and goals.
- Local dates are stored as ISO local dates in the device time zone at the time
  of the event, avoiding UTC day-boundary errors.
- Unique provider pairs, unique season numbers per title, unique episode numbers
  per season, unique title/type lookup, and foreign keys are enforced by SQLite.
- Deleting a title cascades through its seasons, episodes, progress, provider
  links, list memberships, and watch history. Backup replace mode only deletes
  data inside Seenly's own database; it never touches other files.

---

## Backup format

`schemaVersion` currently is `2`. A backup contains:

- media metadata and personal fields (rating, review, spoiler flag, notes,
  timestamps, locally-edited marker)
- external rating fields (IMDb id/rating, Rotten Tomatoes, MyAnimeList) when known
- genres and media/genre links
- provider IDs
- seasons, episodes, progress, and full watch-event history
- lists and list items
- goals

Excluded on purpose:

- app settings and credentials (none are required)
- image cache and remote-response cache
- WorkManager state
- recommendation-deck dismissals (device-local preferences)

The file is plain JSON, not encrypted. Store exports somewhere you trust. Import
validates schema version, IDs, ratings, enum values, dates, relationships,
duplicate pairs, and size limits before writing. Merge is safe to repeat; replace
is destructive only to Seenly's database and should be used with care.

---

## Verification

The project was verified locally with JDK 17 and installed/launched on a
physical Samsung SM-A055F:

```text
./gradlew :app:testDebugUnitTest   -> 109 tests, 0 failures
./gradlew :app:lintDebug           -> no lint errors (46 advisory warnings)
./gradlew :app:assembleDebug       -> build successful
```

Test coverage includes:

- pure logic: ratings, statistics, streaks, goals, recommendations
- backup validation, JSON round-trip, malformed/newer files, merge idempotence,
  replace relationships, rating-grid validation
- Room integrity: foreign keys, provider uniqueness, schema version, and the
  automated 1→2 ratings migration
- watch repository: duplicate taps, season watch, watch-up-to, rewatch, reset,
  movie completion, manual status changes (including upcoming titles with no
  episodes), bulk list completion, and completion marking every known episode
- library repository: manual entries, generated episode structures, duplicates,
  personal-field preservation, local-edit protection, cascade deletion
- lists: built-in seeding, uniqueness, membership, reorder, favorites, computed
  plan-to-watch behavior, safe deletion
- provider import: metadata refresh preserving personal fields, watch history,
  and locally edited core fields; repeated refresh idempotence
- AniList mapper (including franchise season grouping and announced seasons
  without episode counts), Cinemeta and TVmaze mappers, and the Rotten Tomatoes
  public-page parser with fixtures
- DataStore settings persistence (theme, language, home sections, notification
  toggles, recommendation-deck dismissals)

Robolectric is used for database and repository tests. Tests do not use live
network calls.

---

## Known limitations

1. **No automated on-device UI suite.** The APK installs and launches on a
   physical Samsung SM-A055F, and the repository/workflow logic is covered by
   unit and Robolectric tests. An exhaustive manual smoke test of notifications,
   SAF pickers, image loading, RTL layout, and WorkManager scheduling, plus
   instrumented Compose tests, is still recommended.
2. **No instrumented Compose UI tests.** Screens are compiled and their
   repositories/workflow logic are tested under Robolectric, but end-to-end UI
   automation was not run.
3. **Schema v2 has one migration.** The 1→2 migration adds five nullable rating
   columns and is covered by an automated `MigrationTestHelper` test using the
   exported schemas. Destructive fallback is disabled.
4. **WorkManager merges normal internal permissions.** Seenly's own manifest
   declares only `INTERNET` and `POST_NOTIFICATIONS`. WorkManager's library
   manifest adds normal permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
   `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`) plus an AndroidX signature
   permission. They are not dangerous/runtime permissions, but they do appear in
   the merged manifest. Removing them would reduce notification reliability.
5. **Notifications are best effort.** WorkManager timing is subject to Android
   battery optimization and Doze. Exact delivery is not promised.
6. **Backup is unencrypted JSON.** It intentionally excludes settings,
   credentials, and image caches. The user is responsible for storing exports
   securely.
7. **Provider availability is external.** AniList, Cinemeta, TVmaze, Jikan,
   Rotten Tomatoes, and image CDNs can change or rate-limit. Metadata is optional;
   personal data remains local.
8. **No streaming links or downloads.** Seenly tracks viewing; it does not host,
   stream, download, or scrape media.
9. **Debug artifact only.** The included APK is debug-signed and intended for
   local testing.
10. **Count strings use single plural forms.** English/Arabic translations are
    complete, but Android quantity (`plurals`) resources are not used for every
    count string; lint reports `PluralsCandidate` advisories.

---

## Attribution

Metadata and images are provided by AniList, Cinemeta (IMDb), TVmaze, and Jikan.
Rotten Tomatoes Tomatometer values are read best-effort from public pages;
MyAnimeList scores come from Jikan when available and a public MyAnimeList page
fallback otherwise. This product is not endorsed or certified by any of these
services. All metadata remains subject to the respective provider's terms and
availability.

Seenly does not upload personal data anywhere.

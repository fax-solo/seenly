# Seenly — delivery checklist

Living implementation record. The app is functionally complete and verified with
unit/Robolectric tests, lint, and a debug build. Items marked with a caveat remain
environment limitations rather than unfinished code.

## 1. Project and toolchain

- [x] Inspect repository and available SDK/JDK toolchain
- [x] Gradle 8.14.3 wrapper
- [x] AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5, JDK 17
- [x] Compile SDK 36, target SDK 36, min SDK 26
- [x] Version catalog and reproducible dependency versions
- [x] Application ID `com.seenly.app`; debug suffix `.debug`
- [x] Debug build and lint configuration
- [x] AAB language splits disabled so offline in-app locale switching is reliable
- [x] Release build intentionally unsigned; no debug-signed release artifact
- [x] Room schema export enabled

## 2. Product and design

- [x] Seenly branding, launcher icons, adaptive icon with monochrome layer
- [x] Material 3 theme: dark-first cinematic palette, light/dark/system
- [x] Edge-to-edge system bars and dynamic bar appearance
- [x] Shared components: cards, posters, rating stars, status pills, empty/error
      states, dialogs, date picker, section headers
- [x] Full English and Arabic resources (411 keys each, placeholder parity)
- [x] RTL support, auto-mirrored directional icons, persisted in-app language
- [x] Locale-aware dates, numbers, calendar labels, and notification content
- [x] No hardcoded user-facing UI strings

## 3. Data layer

- [x] Domain models and enums
- [x] Room entities, DAOs, converters, foreign keys, indices
- [x] Append-only watch events with derived progress
- [x] Unique provider `(provider, externalId)` links
- [x] Unique title/type lookup; exact-title linking only
- [x] Room schema v2 with explicit 1→2 migration (external ratings)
- [x] Schema exports committed to `app/schemas` (v1 and v2)
- [x] Non-destructive database builder; no destructive fallback
- [x] Repositories: library, watch, lists, goals, statistics, catalog, settings,
      backup
- [x] Built-in Favorites list and computed Plan to Watch list
- [x] DataStore-backed settings, including deck dismissals
- [x] Provider adapters: AniList, Cinemeta (IMDb), TVmaze, Jikan fallback
- [x] Coil 3 image cache
- [x] WorkManager notification scheduling
- [x] Single kept `AppContainer` for manual dependency injection

## 4. Business logic

- [x] Idempotent episode/movie/season/status transitions
- [x] Rewatch history without double-counting unique episodes
- [x] Next-episode and continue-watching calculations
- [x] Statistics: categories, unique episodes, viewings, known/unknown runtime,
      local dates, future exclusion, streak, monthly trends, genres, ratings
- [x] Goals: weekly/monthly/yearly, episodes/movies/titles/minutes
- [x] Deterministic recommendation engine
- [x] Taste-driven remote recommendation deck (genres, sequels, dismissals)
- [x] Anime franchise grouping into numbered seasons
- [x] External ratings enrichment (IMDb, Rotten Tomatoes, MyAnimeList)
- [x] Completion notices for episode, season, movie, and bulk actions
- [x] Bulk "mark all as completed" for lists
- [x] Manual series episode-structure generation with count validation
- [x] Provider metadata refresh preserves personal fields and watched episodes
- [x] User-edited core fields protected by `locallyEdited`
- [x] Backup validation, merge/replace, ID remapping, transactions, duplicate
      guards

## 5. UI

- [x] Navigation shell: Home / Library / Discover / Stats / More
- [x] Home with seven configurable sections and a swipeable recommendation deck
- [x] Library: filters, search, sort, manual add
- [x] Discover: merged keyless search/trending with category chips only
- [x] Details: seasons, episodes, progress actions, history, review, rating,
      external ratings, completion snackbar
- [x] Lists and list detail with reorder/favorites/read-only Plan to Watch and
      bulk completion
- [x] Recommendations "View all" screen
- [x] Calendar
- [x] Goals
- [x] Statistics
- [x] Reviews
- [x] Backup screen with SAF export/import and merge/replace confirmation
- [x] Settings: theme, language, notifications (including finished titles), home
      sections, default sort, backup link, version
- [x] Manual add screen
- [x] More hub and About/attribution
- [x] Error mapping and localized messages

## 6. Privacy, security, and manifest

- [x] App manifest declares only `INTERNET` and `POST_NOTIFICATIONS`
- [x] Runtime notification permission flow
- [x] HTTPS-only network security config; cleartext HTTP blocked
- [x] Automatic backup and device-transfer rules exclude all app data
- [x] No backend, accounts, ads, analytics, crash reporting, or cloud sync
- [x] No network logging interceptor or raw exception exposure
- [x] Backup excludes settings, credentials, and caches
- [x] Backup size limit and complete validation before writes
- [x] Single transaction import/restore
- [x] Release/debug signing separation documented

## 7. Tests

- [x] Rating validation tests
- [x] Statistics and streak tests
- [x] Goal progress tests
- [x] Recommendation tests
- [x] Backup validator and JSON round-trip tests
- [x] Backup export/merge/replace/invalid-file tests
- [x] Room integrity tests: version, FKs, unique provider pairs, case-insensitive
      title lookup
- [x] Automated Room 1→2 migration test against the exported schema
- [x] Episode-row watched mapping and series-completion notice tests
- [x] Watch repository idempotence/history/reset and bulk-completion tests
- [x] Library repository manual-entry/personal-data/cascade tests
- [x] Lists repository tests
- [x] Provider import refresh/idempotence/personal-data tests
- [x] AniList, Cinemeta, and TVmaze mapper tests with fixtures (including
      franchise season grouping)
- [x] Rotten Tomatoes public-page parser tests
- [x] DataStore settings persistence tests (including deck dismissals and the
      completion-notification toggle)
- [x] Full suite: 109 tests, 0 failures

## 8. Verification and delivery

- [x] `:app:compileDebugKotlin`
- [x] `:app:testDebugUnitTest` (109 tests)
- [x] `:app:lintDebug` (no errors; advisory warnings only)
- [x] `:app:assembleDebug`
- [x] Debug APK at `app/build/outputs/apk/debug/app-debug.apk`
- [x] String/resource audit: 411/411 keys, placeholder parity, no unused
      string resources
- [x] Permission and merged-manifest audit
- [x] Dependency audit: no analytics, ads, Firebase, Play Services, or backend SDKs
- [x] Offline behavior audited: local data/screens do not require a provider
- [x] README with architecture, setup, privacy, providers, backup, tests,
      installation, and limitations
- [x] GitHub Actions CI: tests, lint, debug APK artifact, and tagged GitHub
      Releases with APK + SHA256SUMS

## 9. Environment caveats

- [x] On-device installation and launch (Samsung SM-A055F)
- [ ] Exhaustive manual smoke test of notifications, SAF, RTL, image loading, and
      WorkManager scheduling
- [ ] Instrumented Compose UI workflow tests (Robolectric repository/workflow
      coverage used instead)

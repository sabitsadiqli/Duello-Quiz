# android/

Owner: `android-dev`. Gradle multi-module Android app (Kotlin, Jetpack Compose, Material 3,
Hilt, type-safe Navigation Compose, DataStore, Coroutines/Flow).

```
cd android && ./gradlew lint test assembleDebug
```

## Modules

```
:app ──► :feature:auth ─────┐
     ├─► :feature:home ─────┤
     ├─► :feature:quiz ─────┤
     ├─► :feature:duel ─────┤
     ├─► :feature:exams ────┤
     ├─► :feature:profile ──┤
     ├─► :feature:settings ─┤
     │                      ▼
     ├─► :core:notifications ─► :core:data (FCM, channels, phone notification state)
     └─► :core:data ─► :core:network ─► :core:auth
         │            └─► :core:database (Room)
         :core:duel ─┘ └─► :core:datastore
         :core:designsystem  :core:common  :core:model
:core:testing (test-only: MainDispatcherRule, test repositories, fake duel server, Turbine, MockK)
```

| Module | Content |
| --- | --- |
| `:app` | Application (Coil image loader, sign-out cleanup), MainActivity (splash, edge-to-edge, theme), v3 launcher icon (adaptive + themed layers, legacy WebP mipmaps for API 24–25) and splash icon (`drawable-night` variant) from `design/brand/`, NavHost driven by the auth state with the three-tab scaffold and bottom bar (Gündəlik badge, reminder taps), network security config |
| `:feature:auth` | Welcome, sign-in, sign-up (sends the name with `PATCH /v1/me`), password reset |
| `:feature:home` | The Oyna tab (Baku date, streak chip, avatar, rotating greeting, duel card, category list with loading/empty/error/offline states, quiz start, verification banner) and the Gündəlik tab (`daily/`: challenge card with countdown and share, streak section with the week row and messages, daily reminder switch) |
| `:feature:quiz` | Quiz loading and question screen, result, wrong-answer review; the daily challenge (play, result with share card, review) on the shared `QuestionRunner`; XP/streak card and notification permission card on results |
| `:feature:duel` | The whole duel flow as one destination: search, intro, question → Əminlik → answer → reveal, round 7 and sudden death, disconnect overlays, leave dialog, result with the Əminlik kartı, upgrade/unavailable/connection states |
| `:feature:exams` | Mock exams "Sınaqlar" (behind `EXAMS_ENABLED`): catalogue + history, info and package download, the attempt (timer, pager, Tapşırıq/Mətn and split view, written field, listening player), self-assessment, result, review with the review player; `Media3ExamAudioEngine` + `ExamPlaybackService` (ADR 0003) |
| `:core:database` | Room `DuelloDatabase` (exam attempts, answers with revisions, listening state, passage offsets, marks, response cache); exported schemas in `core/database/schemas` |
| `:feature:profile` | The Profil tab: name, avatar, rating, Qoşulub, level and XP, streak row (→ Gündəlik), category levels, duel record, Əminlik statistikası, last 20 matches, name editing, the settings entry |
| `:feature:settings` | Theme, language, notification reminders, account (sign-out, delete via `DELETE /v1/me`), about, open-source licences (list + detail) |
| `:core:notifications` | FCM messaging service (token refresh, reminders shown in the foreground), reminder channels `daily_reminder` / `streak_reminder`, phone notification state, push token registration, reminder taps → Gündəlik tab, `ReminderSettingsController` (reminder switches shared by settings and the Gündəlik tab) |
| `:core:network` | Ktor client (OkHttp) for `docs/api/openapi.yaml`: Bearer ID token with one refresh on 401, `Accept-Language`, error `code` mapping, DTOs (internal) → `:core:model`; the duel WebSocket (`/v1/duel?protocolVersion=1`) with its internal wire types and codec |
| `:core:duel` | `DuelClient` for `docs/api/duel-protocol.md`: ping, 15 s silence watchdog, clock offset on the monotonic clock, `seq` ordering with `match.rejoin` on gaps, `hello`/`match.state` resync, close codes (no reconnect after 4000/4001), reconnect with backoff in a match, saved running match and "left while offline" marker |
| `:core:data` | Repositories (profile incl. name editing, categories, quiz sessions and daily attempts with the shared offline answer queue, progress, notification settings, push tokens, duel stats and history, account), per-account cache, `NetworkMonitor`, preferences |
| `:core:auth` | `AuthRepository` (Firebase or in-memory fake), Credential Manager Google client, validation rules |
| `:core:datastore` | Preferences DataStore (theme mode, notification card counter, shown streak messages, daily question show times, push registration) and the per-account cache store |
| `:core:designsystem` | Theme from `design/tokens.json` 3.1.0 (scheme, game colours, typography, shapes, motion), Fraunces + Plus Jakarta Sans, icons, components (navigation bar, buttons, option card, quiz timer, category row, duel card, sticker, streak chip, greeting with squiggle, daily card, answer boxes, week row, Əminlik tiles, state views, `DuelloLogoMark` coloured from the scheme), **all UI strings** |
| `:core:common` | `AppConfig`, `WallClock`, application scope, navigation result keys, external links |
| `:core:model` | Plain models (`AuthUser`, `Profile`, `Category`, quiz types, `DataResult`/`DataError`) |

Features never depend on each other. Navigation between graphs happens in `:app`.

## Navigation (v3, HOME-09)

Signed in, the root is a `Scaffold` with `DuelloNavigationBar` over three tabs, in this order:
**Oyna** (`PlayDestination`, `:feature:home`), **Gündəlik** (`DailyTabDestination`,
`:feature:home`) and **Profil** (`ProfileDestination`, `:feature:profile`). All destinations share
one back stack in `DuelloNavHost`:

- Switching tabs is `navigate(tab) { popUpTo<PlayDestination> { saveState = true };
  launchSingleTop = true; restoreState = true }` (`navigateToTab`), so each tab keeps its scroll
  and state. System back on Gündəlik or Profil lands on Oyna; back on Oyna finishes the activity.
- Re-selecting the shown tab scrolls it to the top (a conflated channel per tab, passed to the
  route as `scrollToTop: Flow<Unit>`); nothing reloads.
- Full-screen flows (quiz, daily play/result/review, duel, settings, licences, delete account,
  auth) are pushed above the tab they start from and hide the bar. Quiz and duel return to Oyna
  (`popBackToPlay`), the daily flow to Gündəlik (`popBackToDailyTab`); one-off messages travel as
  `NavResults.HOME_NOTICE`. Whenever the back stack goes from a flow back to a tab, `:app` sets
  `NavResults.TAB_REFRESH` on that tab's entry and its ViewModel refreshes once.
- Reminder pushes (`daily_reminder`, `streak_reminder`, `ReminderOpens`) open Gündəlik scrolled
  to the top; while a full-screen flow or the auth graph is shown the tap is ignored.
- The Gündəlik badge dot (`MainTabsViewModel`) reads only the cached `GET /v1/daily` status:
  shown for `available` / `in_progress` of today, hidden otherwise and after Baku midnight. Home
  refreshes the status with its own refresh; the Gündəlik tab on return from the daily flow.
- Settings is reached from the Profil tab only.
- Screen transitions (`motion.game.screenTransition`) are set once on the `NavHost`; feature
  graphs declare none. `screenTransition(from, to)` (`ScreenTransitions.kt`) picks one from the
  areas of the two entries (tab, flow, auth): tab ↔ tab is a 200 ms fade-through; crossing the
  auth boundary (sign-in success, sign-out) a 300 ms fade-through; everything else (tab → flow,
  inside a flow or the auth graph, flow → tab) shared axis X, forward on navigate and backward on
  pop and predictive back, mirrored in RTL. The specs live in
  `:core:designsystem` `motion/NavTransitions.kt`; with "Remove animations" on every transition is
  a plain 150 ms cross-fade. The duel's search → intro → match → retry are states of one
  destination and keep their in-screen motion.

The pure rules (tab selection, reminder taps, return refresh, badge) are unit-tested in
`app/src/test/.../navigation/TabNavigationTest.kt`, the transition choice in
`ScreenTransitionsTest.kt` and the motion specs in `core/designsystem/.../motion/NavTransitionsTest.kt`.

## Theme and fonts (v3)

`DuelloTheme` maps `design/tokens.json` 3.0.0 1:1: `color.light.scheme` / `color.dark.scheme`
(every Material role, `surfaceTint` = surface) plus `GameColors` (streak fill, nav indicator,
card border, banners …), the type scale (`typography.scale.*.family`), shapes and motion. The
v3 look is flat: no depth edges, gradients or per-category colours; presses are ripple plus the
`motion.press` scale. Status and navigation bar icons follow the app theme (dark icons on paper,
light icons on ink). Theme mode (System / Light / Dark) is stored in DataStore.

Two font families, bundled as static instances because minSdk 24 has no variable-font axes:
**Fraunces** (display and numerals: `fraunces_regular`, `fraunces_semibold`) and **Plus Jakarta
Sans** (brand/body: medium, semibold, bold, extrabold), each subset to Latin + Latin Extended +
the Azerbaijani letters and the listed punctuation. `DuelloTypography` adds `overline`,
`numeral` and `numeralLarge`. The instances are produced by
`core/designsystem/third_party/build_fonts.py` (fontTools; axis values, subset ranges and the
cmap check are in `core/designsystem/third_party/README.md`); re-run the glyph test string from
`design/README.md` on a device in every weight after regenerating them.

## Conventions

- Convention plugins in `build-logic/` (`duelloquiz.android.application`, `.library`,
  `.feature`, `.compose`, `duelloquiz.hilt`); versions only in `gradle/libs.versions.toml`.
  AGP 9 built-in Kotlin; the Kotlin Gradle plugin version is pinned in the root build file.
- UDF per screen: `ViewModel` exposes one `StateFlow<UiState>`, takes `UiAction`s and sends
  one-off `UiEvent`s through a `Channel`. Composables are stateless (`XxxScreen`) and previewed
  in light and dark; `XxxRoute` wires the ViewModel.
- Strings: `core/designsystem/src/main/res/values/strings.xml`, keys 1:1 from
  `design/copy/az.md` (default locale `az`, see `app/src/main/res/resources.properties`).
  A new language is a new `values-xx/strings.xml` next to it.
- Colours, type, shapes, spacing and motion come only from `:core:designsystem`;
  `ColorTokensTest` fails when the Kotlin theme drifts from `design/tokens.json`.

## Firebase

No Firebase project is committed. Without `app/google-services.json` (git-ignored) the app
binds `InMemoryAuthRepository` and `FakeGoogleCredentialClient`: accounts live in memory and
Google sign-in returns a test user. With the file present, the Google services plugin is
applied and the Firebase implementations are used. CI release builds decode the file from the
`GOOGLE_SERVICES_JSON_BASE64` secret (`infra/README.md`).

Crashlytics (OQ-101) follows the same rule: the `com.google.firebase.crashlytics` plugin is
applied only together with the Google services plugin, and without a Firebase app the
`Telemetry` binding in `:app` is a no-op. Collection is off in debug builds
(`firebase_crashlytics_collection_enabled` from a manifest placeholder per build type). The
Analytics and Crashlytics user ID is the backend `users.id` (`TelemetryUserSync`, auth.md §5),
cleared on sign-out; no custom keys, never email, name or Firebase UID. Analytics never collects
the Advertising ID: `AD_ID` and the Privacy Sandbox ad permissions are removed from the merged
manifest (OQ-100).

Firebase project setup needed: Email/Password and Google providers enabled, password policy
minimum 8 characters (OQ-018), email enumeration protection on, SHA-1/SHA-256 of the debug and
release (Play App Signing) keys registered for Google sign-in.

## Backend

The base URL is `BuildConfig.API_BASE_URL` of `:core:network`: debug defaults to
`http://10.0.2.2:8080` (the host machine from the emulator, `infra/docker-compose.yml`), release
defaults to the dev server `https://api-dev.duelloquiz.app` while internal and closed tests run
(Sabit, 2026-10-29). The release workflow passes the repository variable `ANDROID_API_BASE_URL`
as `-PapiBaseUrl`; set it to `https://api.duelloquiz.app` once the prod VM exists (ADR 0001,
`infra/README.md` "Production environment"). Override for
every build type with `-PapiBaseUrl=https://...`; against the dev server (Hetzner, `infra/README.md`):

```
./gradlew assembleDebug -PapiBaseUrl=https://api-dev.duelloquiz.app
```

A debug build without `google-services.json` still uses the in-process fakes below, whatever the
URL. Cleartext `http` is allowed only
to `10.0.2.2`, `localhost` and `127.0.0.1` in debug (`app/src/debug/res/xml/network_security_config.xml`);
on a physical device use `adb reverse tcp:8080 tcp:8080` and `-PapiBaseUrl=http://localhost:8080`.

Without Firebase (no `google-services.json`) the backend cannot verify the fake tokens, so a
**debug** build uses `FakeDuelloNetworkDataSource` (`core/network/src/debug`) with sample
categories and placeholder questions that name their right answer. Release builds never contain
it. With Firebase configured, debug builds talk to the real backend.

The fake also serves a daily challenge, XP/levels/streak (a new fake account starts with a 6-day
streak and some XP) and notification switches, so every retention screen can be tried without a
backend; push needs Firebase.

Quiz timing follows the server: each question runs 20 s from the moment it is shown, capped by
the session `deadlineEpochMs` converted with the clock offset measured from `serverNowEpochMs`
at start; the client only renders `correct` / `correctOptionId`.

## Duel

The duel is behind the build flag `BuildConfig.DUEL_ENABLED` of `:app` (OQ-020): on in debug, off
in release. Override for every build type with `-PduelEnabled=true|false`. With the flag off there
is no duel card on Oyna (and no duel greeting lines), no duel sections on the profile and no duel route at all.

The socket belongs to the duel screen (`DuelViewModel`): it survives rotation, is closed when the
user leaves the flow, keeps running in the background during a match and sends `queue.leave` when
the app is backgrounded while searching. Countdowns are server deadlines minus the clock offset,
read from `SystemClock.elapsedRealtime`; the client never computes points or correctness. After a
process death the Oyna tab reopens the duel on the saved running match (`resume = true`). The result
screen takes names and avatars from `match.result.you/opponent` (a deleted opponent shows as
"Silinmiş oyunçu") and names the forfeit cause (`forfeitCause`); a rejoin snapshot keeps the
opponent's reconnect countdown from `match.state.opponent.reconnectDeadlineEpochMs`.

Bot opponent (protocol v1.2, OQ-106 … OQ-111): `queue.join` always carries
`capabilities: ["bot"]`. With `queue.joined.botAtEpochMs` the search counts down to the bot moment
(15 s) with the bot hint; if no `match.found` arrives 3 s after it, the countdown falls back to the
queue deadline. The app never starts a bot match itself and recognises the bot only by
`opponentType` (protocol) / `MatchSummary.opponentType` (REST), never by its name: Bot badge, app
mark avatar, "—" instead of a rating, the two-line rating card and the half-XP note (the XP number is
the server's). The profile record counts real players only; `Stats.botRecord` is the "Bota qarşı"
line.

Without Firebase a debug build plays against `FakeDuelSocketFactory` (`core/network/src/debug`): an
in-process server with scripted opponents that pick Əminlik and answer at random times (about 70 %
right); every fake question names its right answer. Searches alternate: the first gets the Duello
Bot at `botAtEpochMs` (15 s, `opponentType = bot`, null ratings, no Elo, half XP), the next a
scripted "real player" after a few seconds. Finished matches feed the fake profile stats (real and
bot record), history and XP.

## Retention (daily challenge, XP, streak, reminders)

Rules: `docs/mvp-spec.md` §9–§11; the app never computes XP, levels or the streak, it shows the
server's values (`progress` on quiz/daily `finish` and `match.result`, `GET /v1/me/progress`).
Countdowns use `nextResetEpochMs` with the clock offset from `serverNowEpochMs`, never the device
clock. The share text is built from string resources and server data only (`DailyShareText`).

Push: `firebase-messaging` from the Firebase BoM. Without `google-services.json` a no-op token
source is bound and nothing is registered. The permission (Android 13+) is never asked at start;
the in-app card appears on the first quiz/daily result and once more 7 days after "Sonra".

## Open-source licences

"Açıq mənbə lisenziyaları" (`design/screens/licenses.md`) lists three manual entries (Fraunces
and Plus Jakarta Sans with the full OFL texts, Material Symbols) and the libraries generated by the AboutLibraries Gradle
plugin in `:app` from each variant's runtime classpath into `res/raw/aboutlibraries.json`, so the
release list always matches the shipped dependencies. `:core:designsystem` copies
`third_party/fraunces/OFL.txt` and `third_party/plusjakartasans/OFL.txt` into the assets
`licenses/fraunces/OFL.txt` and `licenses/plusjakartasans/OFL.txt` at build time.
`LicenseRepository` (`:core:data`) parses both; unreadable metadata is a Crashlytics non-fatal and
the screen shows only the manual section.

## Legal pages

`BuildConfig.PRIVACY_POLICY_URL` defaults to `https://duelloquiz.app/legal/privacy-policy.html`
(`docs/legal/` on GitHub Pages under the custom domain, `app/build.gradle.kts`); override with
`-PprivacyPolicyUrl=...`. It must be the same URL as in Play Console. The account deletion page
`https://duelloquiz.app/legal/account-deletion.html` is entered only in Play Console (Data safety);
inside the app, deletion is the in-app flow in settings.

## Mock exams "Sınaqlar"

Behind the build flag `BuildConfig.EXAMS_ENABLED` of `:app` (on in debug and release since 2026-11-23, override
with `-PexamsEnabled=true|false`); with the flag off no route, Oyna row, banner, settings row,
licence section or reminder exists (EXAM-01). Rules: `docs/requirements/mock-exams.md`, ADR 0002
(data, scoring, timer) and ADR 0003 (audio).

- The countdown is `endsAtEpochMs` minus the clock offset from `serverNowEpochMs`; at 0 every input
  stops and the client submits (`trigger = deadline`). The client never derives correctness.
- Every change is a new revision in Room (`:core:database`) first, then uploaded in batches of 100
  in save order (`ExamRepository.sync`); offline, `ExamSyncWorker` (WorkManager, network
  constraint) uploads answers, listening state, submits and marks later (24 h late window).
- Packages download with `ExamDownloadWorker`: free-space check (size × 1.1), resumable media with
  size + SHA-256 verification and one automatic retry, into `filesDir/exams` (kept on sign-out).
- Listening: `ListeningPlayState` (2 plays, counted after 10 s of played time, resume paused at the
  saved position after a kill); the media session offers no seek in an attempt.
- EXAM-27: `ExamReminderController` (`:core:notifications`) arms an inexact alarm for
  `endsAt − 10 min` while the app is in the background and posts once on the `exam` channel.
- A debug build without Firebase serves one variant from `FakeExamNetworkDataSource`
  (`core/network/src/debug`, right option always "A", a generated WAV tone as listening audio).

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
     ├─► :feature:profile ──┤
     ├─► :feature:settings ─┤
     │                      ▼
     ├─► :core:notifications ─► :core:data (FCM, channels, phone notification state)
     └─► :core:data ─► :core:network ─► :core:auth
         :core:duel ─┘ └─► :core:datastore
         :core:designsystem  :core:common  :core:model
:core:testing (test-only: MainDispatcherRule, test repositories, fake duel server, Turbine, MockK)
```

| Module | Content |
| --- | --- |
| `:app` | Application (Coil image loader, sign-out cleanup), MainActivity (splash, edge-to-edge, theme), NavHost driven by the auth state, network security config |
| `:feature:auth` | Welcome, sign-in, sign-up (sends the name with `PATCH /v1/me`), password reset |
| `:feature:home` | Profile header with level and streak, streak messages, daily challenge card (countdown, share), category list with loading/empty/error/offline states, quiz start, verification banner |
| `:feature:quiz` | Quiz loading and question screen, result, wrong-answer review; the daily challenge (play, result with share card, review) on the shared `QuestionRunner`; XP/streak card and notification permission card on results |
| `:feature:duel` | The whole duel flow as one destination: search, intro, question → Əminlik → answer → reveal, round 7 and sudden death, disconnect overlays, leave dialog, result with the Əminlik kartı, upgrade/unavailable/connection states |
| `:feature:profile` | Name, avatar, rating, Qoşulub, level, streak and category levels, duel record, Əminlik statistikası, last 20 matches, name editing |
| `:feature:settings` | Theme, language, notification reminders, account (sign-out, delete via `DELETE /v1/me`), about |
| `:core:notifications` | FCM messaging service (token refresh, reminders shown in the foreground), reminder channels `daily_reminder` / `streak_reminder`, phone notification state, push token registration, reminder taps → home |
| `:core:network` | Ktor client (OkHttp) for `docs/api/openapi.yaml`: Bearer ID token with one refresh on 401, `Accept-Language`, error `code` mapping, DTOs (internal) → `:core:model`; the duel WebSocket (`/v1/duel?protocolVersion=1`) with its internal wire types and codec |
| `:core:duel` | `DuelClient` for `docs/api/duel-protocol.md`: ping, 15 s silence watchdog, clock offset on the monotonic clock, `seq` ordering with `match.rejoin` on gaps, `hello`/`match.state` resync, close codes (no reconnect after 4000/4001), reconnect with backoff in a match, saved running match and "left while offline" marker |
| `:core:data` | Repositories (profile incl. name editing, categories, quiz sessions and daily attempts with the shared offline answer queue, progress, notification settings, push tokens, duel stats and history, account), per-account cache, `NetworkMonitor`, preferences |
| `:core:auth` | `AuthRepository` (Firebase or in-memory fake), Credential Manager Google client, validation rules |
| `:core:datastore` | Preferences DataStore (theme mode, notification card counter, shown streak messages, daily question show times, push registration) and the per-account cache store |
| `:core:designsystem` | Theme from `design/tokens.json` (v2: scheme, game, category accents, gradients, depth), Nunito font, icons, components (pressable surface, buttons, option card, quiz timer, category tile grid, duel hero, Əminlik tiles, state views), **all UI strings** |
| `:core:common` | `AppConfig`, `WallClock`, application scope, navigation result keys, external links |
| `:core:model` | Plain models (`AuthUser`, `Profile`, `Category`, quiz types, `DataResult`/`DataError`) |

Features never depend on each other. Navigation between graphs happens in `:app`.

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

Firebase project setup needed: Email/Password and Google providers enabled, password policy
minimum 8 characters (OQ-018), email enumeration protection on, SHA-1/SHA-256 of the debug and
release (Play App Signing) keys registered for Google sign-in.

## Backend

The base URL is `BuildConfig.API_BASE_URL` of `:core:network`: debug defaults to
`http://10.0.2.2:8080` (the host machine from the emulator, `infra/docker-compose.yml`), release
has the placeholder `https://api.duelloquiz.invalid` until the domain is decided (OQ-006).
Override for every build type with `-PapiBaseUrl=https://...`. Cleartext `http` is allowed only
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
is no duel card on home, no duel sections on the profile and no duel route at all.

The socket belongs to the duel screen (`DuelViewModel`): it survives rotation, is closed when the
user leaves the flow, keeps running in the background during a match and sends `queue.leave` when
the app is backgrounded while searching. Countdowns are server deadlines minus the clock offset,
read from `SystemClock.elapsedRealtime`; the client never computes points or correctness. After a
process death home reopens the duel on the saved running match (`resume = true`).

Without Firebase a debug build plays against `FakeDuelSocketFactory` (`core/network/src/debug`): an
in-process server with a bot that picks Əminlik and answers at random times (about 70 % right); every
fake question names its right answer. Finished bot matches feed the fake profile stats and history.

## Retention (daily challenge, XP, streak, reminders)

Rules: `docs/mvp-spec.md` §9–§11; the app never computes XP, levels or the streak, it shows the
server's values (`progress` on quiz/daily `finish` and `match.result`, `GET /v1/me/progress`).
Countdowns use `nextResetEpochMs` with the clock offset from `serverNowEpochMs`, never the device
clock. The share text is built from string resources and server data only (`DailyShareText`).

Push: `firebase-messaging` from the Firebase BoM. Without `google-services.json` a no-op token
source is bound and nothing is registered. The permission (Android 13+) is never asked at start;
the in-app card appears on the first quiz/daily result and once more 7 days after "Sonra".

## Privacy policy URL

`BuildConfig.PRIVACY_POLICY_URL` defaults to the GitHub Pages URL in `app/build.gradle.kts`;
override with `-PprivacyPolicyUrl=...`.

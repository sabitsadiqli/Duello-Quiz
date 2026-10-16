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
     ├─► :feature:settings ─┤
     │                      ▼
     └─► :core:data ─► :core:network ─► :core:auth
                   └─► :core:datastore
         :core:designsystem  :core:common  :core:model
:core:testing (test-only: MainDispatcherRule, test repositories, Turbine, MockK)
```

| Module | Content |
| --- | --- |
| `:app` | Application (Coil image loader, sign-out cleanup), MainActivity (splash, edge-to-edge, theme), NavHost driven by the auth state, network security config |
| `:feature:auth` | Welcome, sign-in, sign-up (sends the name with `PATCH /v1/me`), password reset |
| `:feature:home` | Profile header, category list with loading/empty/error/offline states, quiz start, verification banner |
| `:feature:quiz` | Quiz loading and question screen, result, wrong-answer review |
| `:feature:settings` | Theme, language, account (sign-out, delete via `DELETE /v1/me`), about |
| `:core:network` | Ktor client (OkHttp) for `docs/api/openapi.yaml`: Bearer ID token with one refresh on 401, `Accept-Language`, error `code` mapping, DTOs (internal) → `:core:model` |
| `:core:data` | Repositories (profile, categories, quiz sessions with the offline answer queue, account), per-account cache, `NetworkMonitor`, preferences |
| `:core:auth` | `AuthRepository` (Firebase or in-memory fake), Credential Manager Google client, validation rules |
| `:core:datastore` | Preferences DataStore (theme mode) and the per-account cache store |
| `:core:designsystem` | Theme from `design/tokens.json`, Inter font, icons, components (option card, quiz timer, category card, state views), **all UI strings** |
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

Quiz timing follows the server: each question runs 20 s from the moment it is shown, capped by
the session `deadlineEpochMs` converted with the clock offset measured from `serverNowEpochMs`
at start; the client only renders `correct` / `correctOptionId`.

## Privacy policy URL

`BuildConfig.PRIVACY_POLICY_URL` defaults to the GitHub Pages URL in `app/build.gradle.kts`;
override with `-PprivacyPolicyUrl=...`.

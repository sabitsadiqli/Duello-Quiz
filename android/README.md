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
     ├─► :feature:settings ─┤
     │                      ▼
     └─► :core:auth  :core:data ─► :core:datastore
         :core:designsystem  :core:common  :core:model
:core:testing (test-only: MainDispatcherRule, test repositories, Turbine, MockK)
```

| Module | Content |
| --- | --- |
| `:app` | Application, MainActivity (splash, edge-to-edge, theme), NavHost driven by the auth state |
| `:feature:auth` | Welcome, sign-in, sign-up, password reset |
| `:feature:home` | Placeholder home: name + email-verification banner |
| `:feature:settings` | Theme, language, account (sign-out, delete), about |
| `:core:auth` | `AuthRepository` (Firebase or in-memory fake), Credential Manager Google client, validation rules |
| `:core:data` | `UserPreferencesRepository`, `VerificationBannerRepository` |
| `:core:datastore` | Preferences DataStore (theme mode) |
| `:core:designsystem` | Theme from `design/tokens.json`, Inter font, icons, components, **all UI strings** |
| `:core:common` | `AppConfig`, external links |
| `:core:model` | Plain models (`AuthUser`, `ThemeMode`) |

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

## Privacy policy URL

`BuildConfig.PRIVACY_POLICY_URL` defaults to the GitHub Pages URL in `app/build.gradle.kts`;
override with `-PprivacyPolicyUrl=...`.

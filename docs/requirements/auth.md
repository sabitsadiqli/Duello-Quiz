# Auth — requirements

Spec: `docs/mvp-spec.md` §3. Terms: `docs/requirements/glossary.md`.
Quoted UI text is a proposal in Azerbaijani; the designer owns final copy and tone.

> UI tone is "sən" (OQ-041). Quoted Azerbaijani text here is indicative; the final
> copy and string keys are in `design/copy/az.md`.

## 1. Goal

Every player needs an account so that the duel can match real people, store an Elo
rating and keep quiz history. Signing in must be fast and familiar for Azerbaijani
Android users, many of whom already have a Google account on the phone, while
email/password remains available for users who do not want to use Google. The
backend trusts only Firebase ID tokens and creates the player profile on the first
authenticated call, so there is no separate "create profile" step for the user.

## 2. User stories

| ID | Story | Decisions |
| --- | --- | --- |
| AUTH-01 | As a new or returning user, I want the app to open on the right screen at launch, so that I do not sign in again when I already have. | — |
| AUTH-02 | As a new user, I want to create an account with email and password, so that I can play without a Google account. | OQ-011, OQ-018 (rules only) |
| AUTH-03 | As a returning user, I want to sign in with email and password, so that I get my rating and history back. | — |
| AUTH-04 | As a user who forgot the password, I want to request a reset link, so that I can regain access. | — |
| AUTH-05 | As a user, I want to sign in with my Google account through the system account picker, so that I can start in one tap. | OQ-019 (edge case only) |
| AUTH-06 | As a signed-in user, I want my profile to be created automatically, so that I can play right after signing in. | OQ-011 |
| AUTH-07 | As a signed-in user, I want to sign out, so that someone else can use the device or I can switch accounts. | — |
| AUTH-08 | As the product owner, I want email/password accounts to verify their email (if decided), so that we can reach users and reduce throw-away accounts. | OQ-010 |
| AUTH-09 | As a signed-in user, I want an expired or revoked sign-in to be handled gracefully, so that I am not stuck on errors. | — |

### AUTH-01 Launch routing

- Given no Firebase user is present on the device, When the app launches, Then the
  welcome screen is shown with "Google ilə daxil ol", "E-poçtla daxil ol" and
  "Qeydiyyatdan keç".
- Given a Firebase user is present, When the app launches, Then the home screen is
  shown directly, without showing the welcome screen even for one frame (use the
  splash screen until the sign-in state is known).
- Given a Firebase user is present and the device is offline, When the app launches,
  Then the home screen is shown in its offline state (HOME-05); the user is not
  signed out because of missing network.
- Given the app is launched for the very first time after install, When the welcome
  screen appears, Then the theme follows the system setting (SET-01 default) and no
  permission dialog is shown.
- Given the sign-in state is still loading for more than 2 s (slow device), When the
  splash is visible, Then the app keeps the splash; it never shows a blank screen.
- Given AUTH-08 is decided as "required" and the user is signed in with an
  unverified email/password account, When the app launches, Then the verification
  screen (AUTH-08) is shown instead of home.

### AUTH-02 Email sign-up

Fields: "E-poçt", "Şifrə" (with show/hide toggle; no "repeat password" field),
"Ad" only if OQ-011 decides that email users enter a name at sign-up.

- Given valid email and a password that meets the policy (OQ-018), When the user taps
  "Qeydiyyatdan keç", Then a Firebase account is created, the user is signed in,
  AUTH-06 runs and the user lands on home (or on the verification screen, per OQ-010).
- Given the email format is invalid, When the field loses focus or the user submits,
  Then "E-poçt ünvanı düzgün deyil." is shown under the field and no request is sent.
- Given the password is shorter than the policy minimum, When the user submits, Then
  "Şifrə ən azı N simvol olmalıdır." is shown (N from OQ-018) and no request is sent.
- Given an account with this email already exists (any provider), When the user
  submits, Then "Bu e-poçtla artıq hesab var. Daxil ol və ya şifrəni bərpa et."
  is shown with a link to sign-in, and the typed email is kept.
- Given the device is offline, When the user submits, Then "İnternet bağlantısı
  yoxdur. Yenidən cəhd et." is shown and the form keeps its values.
- Given the network is slow, When the request runs, Then the button shows a progress
  indicator, the form is disabled, and a second tap does nothing (no double account,
  no double request).
- Given the request takes more than 15 s, When it times out, Then a generic error
  "Xəta baş verdi. Yenidən cəhd et." is shown and the form is enabled again.
- Given the app is backgrounded while the request runs, When the user returns, Then
  the outcome (success → home, failure → message) is shown; the form is not reset.
- Given Firebase answers "too many requests", When the user submits, Then "Çox sayda
  cəhd edildi. Bir az sonra yenidən cəhd et." is shown.
- Given leading/trailing spaces in the email, When the user submits, Then they are
  trimmed before validation and sending. The password is never trimmed.

### AUTH-03 Email sign-in

- Given correct email and password, When the user taps "Daxil ol", Then the user is
  signed in, AUTH-06 runs and home is shown.
- Given a wrong password or an unknown email, When the user submits, Then the same
  message "E-poçt və ya şifrə yanlışdır." is shown for both cases (Firebase email
  enumeration protection; do not reveal whether the email exists).
- Given the account is disabled in Firebase, When the user submits, Then "Bu hesab
  deaktiv edilib." is shown.
- Given repeated wrong attempts, When Firebase returns "too many requests", Then
  "Çox sayda uğursuz cəhd. Bir az sonra yenidən cəhd et." is shown; the app adds no
  own lockout.
- Given the email belongs to a Google-only account, When the user tries a password,
  Then the generic wrong-credentials message is shown (behaviour per OQ-019).
- Offline, slow network, double tap, timeout and backgrounding: same as AUTH-02.
- Given the sign-in screen, When the user taps "Şifrəni unutmusan?", Then the
  reset screen (AUTH-04) opens with the typed email pre-filled.

### AUTH-04 Password reset

- Given a syntactically valid email, When the user taps "Link göndər", Then Firebase
  sends a reset email (Azerbaijani template if Firebase supports it; otherwise
  default) and the app shows "Əgər bu e-poçtla hesab varsa, şifrəni bərpa etmək
  üçün link göndərdik." — the same text whether or not the account exists.
- Given an invalid email format, When the user submits, Then the field error is
  shown and no request is sent.
- Given the user taps "Link göndər" again within 60 s, When the button is pressed,
  Then it is disabled with a countdown; this limits abuse of the reset email.
- Given the device is offline, When the user submits, Then the offline message is
  shown.
- Given the user completes the reset in the browser, When they return to the app,
  Then they sign in with the new password on the sign-in screen (the app does not
  handle the reset link itself in the MVP).

### AUTH-05 Google sign-in (Credential Manager)

- Given the device has one or more Google accounts, When the user taps "Google ilə
  daxil ol", Then the Credential Manager bottom sheet opens; after the user picks an
  account, the Google ID token is exchanged for a Firebase sign-in, AUTH-06 runs and
  home is shown.
- Given the user dismisses the sheet or presses back, When the flow returns
  "cancelled", Then the welcome screen stays as it was, with no error message.
- Given the device has no Google account, When the user taps the button, Then the
  system flow to add an account is offered; if that is not available, "Bu cihazda
  Google hesabı tapılmadı." is shown.
- Given Google Play services are missing or outdated, When the user taps the button,
  Then "Google ilə daxil olmaq mümkün olmadı. E-poçtla daxil ol." is shown and the
  email options stay usable.
- Given the device is offline, When the user taps the button, Then the offline
  message is shown.
- Given any other Credential Manager or Firebase error, When it occurs, Then "Google
  ilə daxil olmaq alınmadı. Yenidən cəhd et." is shown and the error code is
  logged to Crashlytics as a non-fatal (no email, no token).
- Given a sign-in is already running, When the user taps the button again, Then
  nothing happens.
- Given the user signs in with Google for the first time, When the profile is
  created, Then display name and avatar URL come from the Google account.

### AUTH-06 Profile creation on first API call (backend + client)

- Given a valid Firebase ID token for a `firebase_uid` without a `users` row, When
  the client calls `GET /v1/me`, Then the backend creates the row (`firebase_uid`,
  display name, avatar URL, `rating = 1000`, `created_at`) and returns it.
- Given two first calls arrive at the same time (e.g. home loads categories and
  profile in parallel), When both try to create the row, Then exactly one row exists
  (`firebase_uid UNIQUE` + upsert) and both calls succeed.
- Given the row already exists, When `GET /v1/me` is called, Then it is returned
  unchanged; display name and avatar are not overwritten by later token claims.
- Given an invalid, expired or wrongly signed token, When any `/v1` endpoint is
  called, Then the backend returns 401 and creates nothing.
- Given the client signed in successfully but `GET /v1/me` fails (offline, 5xx),
  When home opens, Then home shows its error state with "Yenidən cəhd et"; the user
  stays signed in.
- Given an email/password user with no display name, When the row is created, Then
  the display name follows OQ-011 (never empty, never the full email address).

### AUTH-07 Sign-out

Entry point: settings (SET-04).

- Given a signed-in user, When they confirm "Çıx" in the dialog "Hesabdan çıxmaq
  istəyirsən?", Then Firebase signs out, the Credential Manager state is cleared
  (`clearCredentialState`), user-specific local data (Room cache, profile, in-progress
  quiz) is deleted, and the welcome screen is shown with the back stack cleared.
- Given the device is offline, When the user signs out, Then sign-out still works
  locally.
- Given sign-out finished, When the user presses back, Then the app does not return
  to any signed-in screen.
- Given sign-out, When the user signs in with another account, Then no data of the
  previous account (rating, categories cache tied to user, quiz results) is visible.
- Theme and language settings are device settings and survive sign-out.

### AUTH-08 Email verification (blocked by OQ-010)

Written for the recommended option (send a verification email, do not block play).

- Given a new email/password account, When sign-up succeeds, Then Firebase sends a
  verification email and the user goes to home.
- Given an unverified account, When home is shown, Then a dismissible banner
  "E-poçtunu təsdiqlə" with "Yenidən göndər" is shown; play is not blocked.
- Given the user taps "Yenidən göndər", When less than 60 s passed since the last
  send, Then the button is disabled with a countdown.
- Given the user verified in the browser, When the app returns to the foreground,
  Then the user is reloaded and the banner disappears.
- Google accounts are treated as verified and never see the banner.

### AUTH-09 Expired or revoked sign-in

- Given the ID token expired, When an API call returns 401, Then the client forces a
  token refresh once and retries the call once.
- Given the refresh fails because the account was deleted or disabled, When the
  retry fails, Then the client signs out locally (AUTH-07 cleanup) and shows the
  welcome screen with "Giriş müddəti bitib. Yenidən daxil ol.".
- Given the refresh fails because of the network, When it fails, Then the user is
  not signed out; the screen shows its offline state.

## 3. Business rules

- Providers: email/password and Google only (`docs/mvp-spec.md` §3).
- The backend verifies Firebase ID tokens with the Firebase Admin SDK on every call;
  it never accepts a `firebase_uid` from the request body.
- Profile row fields and default rating: `docs/mvp-spec.md` §3 and §6 (rating 1000).
- Password policy: OQ-018. Email verification: OQ-010. Display name: OQ-011.
- No personal data (email, name, token) in analytics, logs or crash reports.

## 4. Out of scope

Phone/SMS sign-in, Apple/Facebook sign-in, anonymous play, multi-factor auth,
in-app handling of reset links, changing email or password inside the app, editing
the display name (profile feature, later), account deletion (SET-05, SET-06).

## 5. Analytics events

| Event | Properties | When |
| --- | --- | --- |
| `sign_up` (Firebase recommended) | `method`: `password` \| `google` | Account created |
| `login` (Firebase recommended) | `method` | Existing account signed in |
| `auth_error` | `method`, `step`: `sign_up` \| `sign_in` \| `reset`, `error_code` (Firebase code, no PII) | Any auth failure except user cancel |
| `google_sign_in_cancelled` | — | User dismissed Credential Manager |
| `password_reset_requested` | — | Reset email requested |
| `email_verification_sent` | `trigger`: `sign_up` \| `resend` | If AUTH-08 is built |
| `sign_out` | — | User signed out |

Set the Firebase Analytics user ID to the backend `users.id` (never email or
`firebase_uid` shown to others); clear it on sign-out.

## 6. Decisions

All questions below were decided by Sabit on 2026-10-14 as recommended; the
final wording is in `open-questions.md` (Decided).

OQ-010 (email verification), OQ-011 (display name for email users), OQ-018
(password policy), OQ-019 (same email with Google and password).

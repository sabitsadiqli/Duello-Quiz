# Splash and welcome (first launch)

Stories: AUTH-01, AUTH-05 (Google button), AUTH-07/AUTH-09/SET-05 (arrival
messages), SET-01 (theme from the first frame).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Open questions

- None blocking. Assumption: the welcome screen is the root of the signed-out graph;
  back on welcome exits the app.

## Purpose

Splash covers the time until the sign-in state is known, so the user never sees a
blank frame or a flash of the wrong screen. Welcome is the single signed-out entry:
Google in one tap, email sign-in, or sign-up.

## Entry points

- App launch (cold or warm) → splash.
- Splash → welcome when no Firebase user is present.
- Splash → home when a user is present (also offline, AUTH-01).
- Sign-out (AUTH-07), forced sign-out (AUTH-09), account deleted (SET-05) → welcome
  with the back stack cleared.

## Layout

### Splash (Android 12+ SplashScreen API, also via the compat library)

- Background: `scheme.surface` of the theme that will be used (SET-01: the stored
  theme choice is applied before the first frame; "Sistem" follows the device).
- Centre: app icon (adaptive icon foreground) `size.splashLogo`, no text, no
  animation beyond the system default.
- Kept on screen (`setKeepOnScreenCondition`) until the sign-in state is resolved;
  no upper time limit (AUTH-01: never a blank screen).

### Welcome (top to bottom, 360dp)

1. Status bar inset, edge-to-edge, background `scheme.surface`.
2. Flexible top space (weight 1).
3. Logo mark 96dp (`size.splashLogo`), centred.
4. `space.lg`, `app_name` in `type.headlineMedium` `scheme.onSurface`, centred.
5. `space.sm`, `auth_welcome_tagline` `type.bodyLarge` `scheme.onSurfaceVariant`,
   centred, max 2 lines.
6. Flexible space (weight 1).
7. Button column, side gutters `space.layout.screenGutter`, gap `space.md`:
   - Google sign-in button (`components.md` §1) `auth_google_sign_in`.
   - Tonal button `auth_email_sign_in`.
   - Outlined button `auth_sign_up_cta`.
8. `space.xl`, text button `auth_privacy_link` (`type.labelLarge`, centred),
   opens the privacy policy URL (OQ-015) in a Custom Tab.
9. `space.lg` + navigation bar inset.

At font scale 2.0 the whole column scrolls; buttons keep full width.
At ≥ 600dp width: content centred, max width `space.layout.maxContentWidth`.

## Components

Google sign-in button, tonal button, outlined button, text button, snackbar host.

## States

| State | What the user sees |
| --- | --- |
| Splash, sign-in state loading | Splash only (any duration). |
| Welcome, default | Layout above, all buttons enabled. |
| Google sign-in running | Google button in loading state; the other two buttons and the privacy link are disabled; a second tap does nothing (AUTH-05). |
| Google cancelled by user | Back to default, **no** message (AUTH-05). |
| Google: no account on device | Snackbar `auth_error_google_no_account` (if the system add-account flow is not offered). |
| Google: Play services missing/outdated | Snackbar `auth_error_google_unavailable`; email buttons stay enabled. |
| Google: other error | Snackbar `auth_error_google_failed` (long, no action). |
| Offline on Google tap | Snackbar `error_offline`. |
| Success | Navigate to home (fade-through `motion.game.screenTransition`), welcome removed from the back stack. |
| Arrived after sign-out | Default; no message. |
| Arrived after forced sign-out (AUTH-09) | Snackbar `error_sign_in_expired` (long). |
| Arrived after account deletion | Snackbar `auth_account_deleted` (short). |
| Empty / error (data) | Not applicable: no data loaded on this screen. |

## Interactions and animations

- Splash exit: system icon fade/scale (default `SplashScreen` exit), then the target
  screen appears; no extra custom animation.
- Welcome first appearance: content fades in `motion.duration.medium2`
  (`standardDecelerate`); none with reduced motion.
- "E-poçtla daxil ol" → sign-in (shared axis X forward). "Qeydiyyatdan keç" →
  sign-up.
- Back on welcome → leaves the app.
- No permission dialogs on first launch (AUTH-01).

## Accessibility

- Logo is decorative. `app_name` is the screen heading (`semantics { heading() }`).
- Focus order: heading, tagline, Google, email, sign-up, privacy link.

## String keys

`app_name`, `auth_welcome_tagline`, `auth_google_sign_in`, `auth_email_sign_in`,
`auth_sign_up_cta`, `auth_privacy_link`, `auth_error_google_no_account`,
`auth_error_google_unavailable`, `auth_error_google_failed`, `error_offline`,
`error_sign_in_expired`, `auth_account_deleted`, `common_loading`.

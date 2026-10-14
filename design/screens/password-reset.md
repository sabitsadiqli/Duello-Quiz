# Password reset

Story: AUTH-04.
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- Assumption: the 60 s resend countdown is kept only while this screen is alive
  (in the ViewModel, survives rotation). Leaving and re-entering resets it; Firebase's
  own throttling (`auth_error_too_many_requests`) covers abuse beyond that.

## Purpose

Request a Firebase reset link without revealing whether an account exists.

## Entry points

- Sign-in → `auth_forgot_password` (email pre-filled from sign-in).

## Layout (top to bottom)

1. Top app bar: back icon only.
2. Scrollable column, gutters `space.layout.screenGutter`, `imePadding`:
   1. `auth_reset_title` `type.headlineSmall` (heading; in content, not in the
      app bar — see truncation risks in `az.md`).
   2. `space.sm`, `auth_reset_body` `type.bodyMedium` `scheme.onSurfaceVariant`.
   3. `space.xl`, form error slot (hidden by default).
   4. `space.lg`, text field `auth_email_label` (Done = submit).
   5. `space.xl`, filled button `auth_reset_send`, full width.
   6. Confirmation block (hidden until sent): info banner style container
      (`game.bannerInfoContainer` / `game.onBannerInfoContainer`, `shape.medium`,
      padding `space.lg`, `mail` icon) with `auth_reset_sent`.
   7. `space.lg`, text button `auth_reset_back_to_sign_in`, centred.

## Components

Top app bar, outlined text field, filled button (with countdown), info block, text
button.

## States

| State | What the user sees |
| --- | --- |
| Default | Email pre-filled or empty; button enabled. |
| Field error | `auth_error_email_required` / `auth_error_email_invalid`; no request. |
| Loading | Button loading, field disabled. |
| Sent (also for unknown emails) | Confirmation block appears; button becomes disabled `auth_reset_send_countdown` counting 60 → 1 (`type.labelLarge`, `scheme.onSurfaceVariant` text so it stays readable), then back to `auth_reset_send`. Field stays editable so a typo can be fixed and resent after the countdown. |
| Too many requests | Form error `auth_error_too_many_requests`. |
| Offline | Form error `error_offline`. |
| Timeout / unknown | Form error `error_generic`. |
| Empty (data) | Not applicable. |

## Interactions and animations

- Confirmation block expands in (`motion.duration.medium1`, `emphasizedDecelerate`)
  and is announced (polite live region).
- `auth_reset_back_to_sign_in` → back to sign-in with the current email.
- The reset link itself opens in the browser; the app does not handle it (AUTH-04).

## Accessibility

- Countdown changes are not announced every second; only the start
  ("sent" message) is announced. The button's content description includes the
  remaining seconds when focused.

## String keys

`common_back`, `auth_reset_title`, `auth_reset_body`, `auth_email_label`,
`auth_reset_send`, `auth_reset_send_countdown`, `auth_reset_sent`,
`auth_reset_back_to_sign_in`, `auth_error_email_required`,
`auth_error_email_invalid`, `auth_error_too_many_requests`, `error_offline`,
`error_generic`, `common_loading`.

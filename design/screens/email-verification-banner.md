# Email verification banner

Story: AUTH-08. Decision: OQ-010 (send a verification email, show a banner, never
block play). Placement: top of home (`home.md`).
Tokens: `design/tokens.json`. Components: `components.md` §8. Copy: `copy/az.md`.

## Open questions

- **OQ-D2 (needs Sabit): how long does "dismiss" last?** AUTH-08 says the banner is
  dismissible but not for how long. Proposal: dismissed until the next cold start of
  the app (in-memory flag), so it comes back as a gentle reminder but never nags
  within one session. Alternative: dismissed for 7 days (DataStore timestamp).
- Assumption: the banner shows the account email (`%1$s`), because catching typos
  is the main reason for OQ-010 option B.

## Purpose

Remind email/password users to verify their address without blocking play.

## Entry points

- Home, for a signed-in email/password user whose Firebase `isEmailVerified` is
  false. Never for Google accounts.

## Layout

Info banner (`components.md` §8, variant Info), directly under the home top app
bar, above the profile header, full width minus gutters
(`space.layout.screenGutter`):

1. Row: `mail` icon 24dp `game.onBannerInfoContainer`, `space.md`, column:
   - `auth_verify_banner_title` `type.titleSmall`.
   - `space.xs`, `auth_verify_banner_body` `type.bodyMedium` (max 3 lines; the email
     breaks anywhere if it is longer than a line).
   - Trailing close icon button (48dp target, 20dp icon) `auth_verify_dismiss_cd`,
     top-aligned.
2. `space.sm`, actions row, end-aligned: text button `auth_verify_resend` in
   `game.onBannerInfoContainer`.

## Components

Banner (info), text button, icon button, snackbar host (home's).

## States

| State | What the user sees |
| --- | --- |
| Hidden | Verified user, Google user, or dismissed (per OQ-D2). |
| Visible | Layout above. |
| Resend running | Text button replaced by a 20dp progress indicator; no second request. |
| Resend sent | Snackbar `auth_verify_sent`; button becomes disabled `auth_verify_resend_countdown` 60 → 1 (text in `game.onBannerInfoContainer` at full opacity, readable). |
| Resend too often (Firebase) | Snackbar `auth_error_too_many_requests`. |
| Resend offline | Snackbar `error_offline`. |
| Resend other error | Snackbar `error_generic`. |
| Verified in browser | On app return to foreground the user is reloaded; banner collapses and snackbar `auth_verify_done` is shown once. |
| Offline (home offline) | Banner still visible (state is local); offline banner (HOME-05) is shown **above** it. Max two banners stacked. |
| Loading / empty | Not applicable (banner depends only on the local Firebase user). |

## Interactions and animations

- Expand/collapse per `components.md` §8.
- Close icon hides the banner immediately; no confirmation.
- The banner never covers the category list; it pushes content down and scrolls
  with it (not sticky).

## String keys

`auth_verify_banner_title`, `auth_verify_banner_body`, `auth_verify_resend`,
`auth_verify_resend_countdown`, `auth_verify_sent`, `auth_verify_done`,
`auth_verify_dismiss_cd`, `auth_error_too_many_requests`, `error_offline`,
`error_generic`.

# Sign-in (email and password)

Stories: AUTH-03, AUTH-06 (runs after success), AUTH-09 (arrival).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- None blocking. Assumption: Google sign-in is offered only on welcome, not repeated
  here (keeps this screen focused; welcome is one back press away).

## Purpose

Let a returning email user sign in fast, recover a forgotten password, or switch to
sign-up.

## Entry points

- Welcome → "E-poçtla daxil ol".
- Sign-up → "Artıq hesabın var? Daxil ol" (email carried over).
- Sign-up error "email in use" → `auth_go_to_sign_in` (email carried over).
- Password reset → `auth_reset_back_to_sign_in` (email carried over).

## Layout (top to bottom)

1. Top app bar: navigation icon only (back, `common_back`), no title.
2. Scrollable column, gutters `space.layout.screenGutter`:
   1. `auth_sign_in_title` in `type.headlineSmall` `scheme.onSurface` (heading).
   2. `space.xl`, form error slot (hidden by default): inline error text
      `type.bodyMedium` `scheme.error` with a 20dp error icon, live region.
   3. `space.lg`, text field `auth_email_label` (email keyboard, Next).
   4. `space.lg`, password field `auth_password_label` (toggle, Done = submit).
   5. `space.sm`, text button `auth_forgot_password`, end-aligned.
   6. `space.xl`, primary button `auth_sign_in_button`, full width.
   7. `space.xl`, row centred: `auth_no_account_prompt` (`type.bodyMedium`
      `scheme.onSurfaceVariant`) + text button `auth_sign_up_link`. Wraps to two
      centred lines if it does not fit.
3. Content is placed above the keyboard (`imePadding`); the submit button stays
   reachable by scrolling.

## Components

Top app bar, outlined text fields (email, password), text buttons, primary button,
inline form error, snackbar host.

## States

| State | What the user sees |
| --- | --- |
| Default | Empty fields (or pre-filled email), button enabled. Validation happens on submit/focus loss, not while typing. |
| Field errors | Email empty → `auth_error_email_required`; bad format → `auth_error_email_invalid`; password empty → `auth_error_password_required`. No request sent. Email is trimmed first; password never. |
| Loading | Button in loading state, both fields disabled (values kept), back still works and cancels nothing on the server; second tap ignored. |
| Wrong credentials / unknown email / Google-only email | Form error `auth_error_wrong_credentials`; password field cleared, focus to password. |
| Account disabled | Form error `auth_error_user_disabled`. |
| Too many attempts | Form error `auth_error_too_many_attempts`. |
| Offline | Form error `error_offline`; values kept. |
| Timeout (15 s) / unknown error | Form error `error_generic`; form enabled again. |
| Backgrounded during request | On return the outcome is shown (home or error); form not reset. |
| Success | Navigate to home (fade-through), auth graph cleared from the back stack. Home then loads the profile (AUTH-06). |
| Empty (data) | Not applicable. |

Form errors are cleared as soon as the user edits any field.

## Interactions and animations

- `auth_forgot_password` → password reset, email pre-filled.
- `auth_sign_up_link` → sign-up (replaces this screen, email carried over), so
  back from sign-up returns to welcome, not in a loop.
- Form error slot expands with `motion.duration.short4` `standard`.
- Keyboard Done on the password field submits.

## Accessibility

- Heading semantics on the title. Fields have labels (no placeholder-only fields).
- Form error is a polite live region and gets focus after a failed submit.
- Autofill: email = `emailAddress`, password = `password`.

## String keys

`common_back`, `auth_sign_in_title`, `auth_email_label`, `auth_password_label`,
`common_password_show_cd`, `common_password_hide_cd`, `auth_forgot_password`,
`auth_sign_in_button`, `auth_no_account_prompt`, `auth_sign_up_link`,
`auth_error_email_required`, `auth_error_email_invalid`,
`auth_error_password_required`, `auth_error_wrong_credentials`,
`auth_error_user_disabled`, `auth_error_too_many_attempts`, `error_offline`,
`error_generic`, `common_loading`.

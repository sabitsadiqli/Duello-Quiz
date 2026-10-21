# Sign-up (email, password, name)

Stories: AUTH-02, AUTH-06, AUTH-08 (verification email after success). Decisions:
OQ-011 (name 2–20 chars), OQ-018 (password ≥ 8), OQ-019 (one account per email).
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- **OQ-D1 — decided as proposed (OQ-042): which characters are allowed in "Ad"?**
  OQ-011 fixes only the length. Proposal: letters of any script (incl. Ə ə Ğ ğ I ı
  İ i Ö ö Ü ü Ç ç Ş ş), digits, space, hyphen, apostrophe and dot; trimmed; inner
  runs of spaces collapsed to one; no emoji. The length counts Unicode code points
  after trimming. The server must apply the same rule (it is the authority).
- Assumption: field order is Ad → E-poçt → Şifrə (name first feels personal and
  matches "who are you, how do you sign in").

## Purpose

Create an email account with a public display name in one screen.

## Entry points

- Welcome → "Qeydiyyatdan keç".
- Sign-in → `auth_sign_up_link` (email carried over).

## Layout (top to bottom)

1. Top app bar: back icon only.
2. Scrollable column, gutters `space.layout.screenGutter`, `imePadding`:
   1. `auth_sign_up_title` `type.headlineSmall` (heading).
   2. `space.xl`, form error slot (hidden by default), as in `sign-in.md`.
   3. `space.lg`, text field `auth_name_label`; supporting `auth_name_supporting`;
      counter `auth_name_counter` ("0/20"); keyboard capitalisation Words; Next.
   4. `space.lg`, text field `auth_email_label`; Next.
   5. `space.lg`, password field `auth_password_label`; supporting
      `auth_password_supporting` ("Ən azı 8 simvol."); toggle; Done = submit.
      No "repeat password" field (AUTH-02); the show toggle replaces it.
   6. `space.xl`, primary button `auth_sign_up_cta`, full width.
   7. `space.xl`, centred row `auth_have_account_prompt` + text button
      `auth_sign_in_link`.

## Components

Top app bar, outlined text fields (with counter and supporting text), password
field, primary button, text button, inline form error.

## States

| State | What the user sees |
| --- | --- |
| Default | Empty fields (email pre-filled if carried over). |
| Name errors | Empty → `auth_error_name_required`; < 2 or > 20 after trim → `auth_error_name_length`. The counter turns `scheme.error` above 20 (input is not cut off, so pasting is visible). |
| Email errors | `auth_error_email_required` / `auth_error_email_invalid` (trimmed first). |
| Password error | Shorter than 8 → `auth_error_password_short`. Never trimmed. |
| Loading | Button loading, fields disabled, values kept; no second request (no double account). |
| Email already in use (any provider, OQ-019) | Form error `auth_error_email_in_use` with an inline text button `auth_go_to_sign_in` (opens sign-in with the email). Email kept, password cleared. |
| Too many requests | Form error `auth_error_too_many_requests`. |
| Offline | Form error `error_offline`; values kept. |
| Timeout (15 s) / unknown | Form error `error_generic`; form enabled. |
| Backgrounded during request | Outcome shown on return; form not reset. |
| Success | Account created, verification email sent (AUTH-08), user lands on home with the verification banner (`email-verification-banner.md`). |
| Empty (data) | Not applicable. |

Validation: on focus loss per field and on submit for all; the first invalid field
gets focus on submit.

## Interactions and animations

- Counter updates live; supporting text never jumps (error replaces supporting text
  in the same slot).
- `auth_sign_in_link` replaces this screen with sign-in (no loop in the back stack).

## Accessibility

- Name field autofill hint `personName`, email `emailAddress`, password
  `newPassword` (lets password managers offer a strong password).
- The counter is read as part of the supporting text only on focus.

## String keys

`common_back`, `auth_sign_up_title`, `auth_name_label`, `auth_name_supporting`,
`auth_name_counter`, `auth_error_name_required`, `auth_error_name_length`,
`auth_email_label`, `auth_error_email_required`, `auth_error_email_invalid`,
`auth_password_label`, `auth_password_supporting`, `auth_error_password_short`,
`common_password_show_cd`, `common_password_hide_cd`, `auth_sign_up_cta`,
`auth_have_account_prompt`, `auth_sign_in_link`, `auth_error_email_in_use`,
`auth_go_to_sign_in`, `auth_error_too_many_requests`, `error_offline`,
`error_generic`, `common_loading`.

# Settings (incl. account deletion and licences)

Stories: SET-01 … SET-05, SET-07 (notifications, added 2026-10-22), AUTH-07
(sign-out), NOTIF-06. Decisions: OQ-014, OQ-015, OQ-090, OQ-091.
SET-06 (web deletion page) is a document, not a screen; out of this spec.
Tokens: `design/tokens.json`. Components: `components.md`. Copy: `copy/az.md`.

## Questions and decisions

- Assumption: the account section shows who is signed in (email for email accounts,
  Google display name + email for Google) as supporting text. Useful before
  "Hesabdan çıx" / "Hesabı sil". It is the user's own data on their own device.
- Decided: the licences screen has its own spec, `licenses.md` (generated library
  list rendered with our components, plus manual entries for Nunito and Material
  Symbols).

## Purpose

Theme, language info, notification reminders, account actions (sign-out,
delete), app information.

## Entry points

- Home top app bar settings icon.

## Layout — settings screen (top to bottom)

1. Top app bar: back icon, title `settings_title`.
2. `LazyColumn` of list rows (`components.md` §14), full width (rows have their own
   `space.lg` padding):
   1. Section header `settings_section_theme`.
   2. Radio group (`selectableGroup`):
      - `settings_theme_system` + supporting `settings_theme_system_supporting`.
      - `settings_theme_light`.
      - `settings_theme_dark`.
   3. Section header `settings_section_language`.
   4. Row `settings_language_value`, leading `language` icon, **not clickable**, no
      chevron (SET-02). Trailing check icon in `scheme.primary` to show it is the
      selected language.
   4a. Section header `settings_section_notifications` ("Bildirişlər").
   4b. Optional blocked banner `settings_notif_blocked` + action
       `settings_notif_open_system` (only when the phone blocks the app's
       notifications), inset `space.lg`.
   4c. Switch row (`components.md` §31) `settings_notif_daily` + supporting
       `settings_notif_daily_supporting`, leading `today` icon.
   4d. Switch row `settings_notif_streak` + supporting
       `settings_notif_streak_supporting`, leading `local_fire_department`
       (outlined) icon.
       States, permission flow and API: `notifications.md` B.
   5. Section header `settings_section_account`.
   6. Info row (not clickable): leading `account_circle` icon, headline = display
      name, supporting `settings_signed_in_as` (email; 1 line, ellipsis).
   7. Row `settings_sign_out`, leading `logout` icon.
   8. Row `settings_delete_account`, leading `delete` icon, text + icon in
      `scheme.error`, trailing chevron.
   9. Section header `settings_section_about`.
   10. Row `settings_version` ("Versiya 1.0.0 (12)"), leading `info` icon, not
       clickable, no hidden actions on tap/long-press (SET-03).
   11. Row `settings_privacy_policy`, leading `policy` icon, trailing
       `open_in_new` icon.
   12. Row `settings_licenses`, leading `description` icon, trailing chevron.
   13. Bottom `space.xl` + navigation bar inset.

Section order follows `settings.md` §3: Tema, Dil, Bildirişlər, Hesab, Haqqında.

## Layout — delete account screen (SET-05)

1. Top app bar: back icon, no title.
2. Scrollable column, gutters `space.layout.screenGutter`:
   1. `warning` icon 48dp `scheme.error`.
   2. `space.lg`, `settings_delete_title` `type.headlineSmall` (heading).
   3. `space.md`, `settings_delete_body` `type.bodyLarge` `scheme.onSurface`.
   4. `space.md`, bullet-free paragraphs `settings_delete_body_matches` and
      `settings_delete_body_backups`, `type.bodyMedium` `scheme.onSurfaceVariant`.
   5. Inline error slot (hidden by default), `scheme.error`.
   6. Flexible space, then full-width buttons, gap `space.md`:
      - Filled destructive `settings_delete_confirm_button`.
      - Outlined `common_cancel`.
   7. Navigation bar inset.

Second confirmation: dialog (`components.md` §9) title
`settings_delete_dialog_title`, body `settings_delete_dialog_body`, actions
`common_cancel` and `settings_delete_dialog_confirm` (in `scheme.error`).

## Layout — licences screen

See `licenses.md`.

## Components

Top app bar, section headers, radio rows, list rows, destructive row, dialogs,
filled destructive button, neutral button, snackbar.

## States

### Settings

| State | What the user sees |
| --- | --- |
| Default | Stored theme selected ("Sistem" on fresh install or unreadable DataStore). |
| Theme changed | Whole app recolours immediately with a cross-fade of `motion.duration.medium2` (none with reduced motion); scroll position and screen state kept. |
| Loading / empty / error | Theme and language are local (no states). The notification section loads from the server: loading, load error and offline states in `notifications.md` B. |
| Offline | Theme, language and sign-out work offline (AUTH-07); privacy policy opens and the browser shows its own offline page; notification switches show cached values and revert with `settings_notif_offline` when toggled. |
| Return from system notification settings | Notification section re-reads permission and channel state (blocked banner appears / disappears). |
| Privacy link cannot open (no browser) | Snackbar `settings_link_open_failed`. |

### Sign-out dialog (AUTH-07)

Title `settings_sign_out_title`, body `settings_sign_out_body`, actions
`common_cancel` and `settings_sign_out_confirm`. Confirm → local cleanup → welcome,
back stack cleared. No loading state (local, fast).

### Delete account

| State | What the user sees |
| --- | --- |
| Default | Explanation + buttons. |
| Confirm dialog | As above. Cancel / back / scrim → closes, nothing happens. |
| Deleting | Dialog closes; destructive button in loading state, cancel button and back disabled (the request cannot be cancelled), status text `settings_delete_in_progress` under the buttons. |
| Offline | Inline error `settings_delete_offline`; nothing deleted. |
| Server error / 15 s timeout | Inline error `settings_delete_failed`; user stays signed in; button enabled for retry. |
| Success (incl. 404 = already deleted) | Local cleanup → welcome with snackbar `auth_account_deleted`. |

## Interactions and animations

- Radio row: whole row clickable, ripple; selection change animates the radio.
- "Hesabı sil" row → delete screen (shared axis X). Analytics
  `account_deletion_started` when it opens.
- Delete is two steps on purpose (screen + dialog); no typing confirmation.
- The delete entry is not reachable from a running quiz or duel (settings is only
  reachable from home).

## Accessibility

- Theme options are a radio group with selected state (SET-01).
- Language row is announced as "Azərbaycan dili, seçilib", not as a button.
- Notification rows are switches (role Switch, whole row toggles); a disabled row
  reads its reason (`notifications.md` Accessibility).
- Destructive actions use both colour and wording ("həmişəlik", "Sil").

## String keys

`settings_title`, `common_back`, `settings_section_theme`,
`settings_theme_system`, `settings_theme_system_supporting`,
`settings_theme_light`, `settings_theme_dark`, `settings_section_language`,
`settings_language_value`, `settings_section_account`, `settings_signed_in_as`,
`settings_sign_out`, `settings_sign_out_title`, `settings_sign_out_body`,
`settings_sign_out_confirm`, `common_cancel`, `settings_delete_account`,
`settings_section_about`, `settings_version`, `settings_privacy_policy`,
`settings_licenses`, `settings_licenses_title`, `settings_link_open_failed`,
`settings_delete_title`, `settings_delete_body`, `settings_delete_body_matches`,
`settings_delete_body_backups`, `settings_delete_confirm_button`,
`settings_delete_dialog_title`, `settings_delete_dialog_body`,
`settings_delete_dialog_confirm`, `settings_delete_in_progress`,
`settings_delete_offline`, `settings_delete_failed`, `auth_account_deleted`;
notification keys in `notifications.md` (`settings_section_notifications`,
`settings_notif_*`).

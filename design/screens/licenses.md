# Open-source licences

Stories: SET-03 ("Açıq mənbə lisenziyaları" shows the list of third-party licences).
Entry from `settings.md`. Tokens: `design/tokens.json`. Components: `components.md`
§11, §12, §14. Copy: `copy/az.md` (`settings_licenses_*`).

## Questions and decisions

- Assumption (android-dev may choose otherwise): the library list is **generated at
  build time** (e.g. the AboutLibraries Gradle plugin) into a bundled resource, and
  rendered with **our own Compose list** below, not with a ready-made licence
  Activity: `play-services-oss-licenses` opens an unthemed AppCompat screen that
  ignores dark mode and our type scale. The generator must run for release builds,
  so the list always matches the shipped dependencies.
- Decided: two **manual entries** that no Gradle scan can find are always added on
  top: **Nunito** (fonts in `core/designsystem/src/main/res/font`, SIL Open Font
  License 1.1, full text from `android/core/designsystem/third_party/nunito/OFL.txt`;
  replaces Inter since tokens 2.0.0 — drop the Inter entry when the Inter files are
  removed)
  and **Material Symbols** (icons copied as vector drawables, Apache License 2.0).
  The OFL requires the licence to travel with the font; showing the full text here
  satisfies that.
- Decided: the Google "G" logo on the sign-in button is a trademark used under
  Google's branding guidelines, not an open-source asset; it is not listed.
- Decided: library names, authors and licence names come from the metadata in
  English (proper names, not translated). Only the screen chrome is Azerbaijani.
- No open questions for Sabit.

## Purpose

Give credit and meet licence obligations: list every open-source component shipped
in the app with its licence, and show the full licence text on demand. Works
offline.

## Entry points

- Settings → "Haqqında" → row `settings_licenses` (shared axis X forward).
- Back → settings (scroll position kept).

## Layout — list (top to bottom, 360dp)

1. Top app bar (`components.md` §12): back icon (`common_back`), title
   `settings_licenses_title` ("Lisenziyalar"; the long form truncates in a bar,
   see `az.md` risks).
2. `LazyColumn`, full width (rows carry their own `space.lg` padding):
   1. Intro, padding `space.lg` horizontal, `space.sm` top, `space.md` bottom:
      `settings_licenses_intro` `type.bodyMedium` `scheme.onSurfaceVariant`, wraps.
   2. Section header (`components.md` §14) `settings_licenses_section_assets`.
   3. Manual rows in this order: Nunito, Material Symbols.
   4. Section header `settings_licenses_section_libraries`.
   5. Generated rows, sorted by name (case-insensitive, Azerbaijani collation not
      needed: names are Latin/English). One row per library as delivered by the
      generator; no grouping.
   6. Bottom `space.xl` + navigation bar inset.

**Licence row** (`components.md` §14 list row, two-line):
- Headline: library name `type.bodyLarge` `scheme.onSurface`, max 2 lines, then
  ellipsis (names like "Kotlinx Serialization JSON" fit on 1–2 lines at 1.3).
- Supporting: `settings_licenses_item_supporting` = version · licence
  ("Versiya 3.6.0 · Apache License 2.0") `type.bodySmall`
  `scheme.onSurfaceVariant`, max 2 lines. Without a version: licence name only.
  Several licences: names joined with ", ". No licence in the metadata:
  `settings_licenses_unknown`.
- Trailing: `chevron_right` 24dp `scheme.onSurfaceVariant`.
- No leading icon (keeps the full width for long names).
- Whole row clickable → detail.

At ≥ 600dp: content centred, max width `space.layout.maxContentWidth`.

## Layout — detail (top to bottom)

1. Top app bar: back icon, **no title** (library names are often longer than the
   ~16-character bar limit, `components.md` §12).
2. Scrollable column (`LazyColumn`, so a long licence text is not one huge text
   node), gutters `space.layout.screenGutter`:
   1. Library name `type.headlineSmall` `scheme.onSurface`, wraps, heading.
   2. `space.xs`, author / organisation (if present) `type.bodyMedium`
      `scheme.onSurfaceVariant`.
   3. `space.xxs`, `settings_licenses_version` ("Versiya 3.6.0") `type.bodyMedium`
      `scheme.onSurfaceVariant`, tabular. Hidden without a version.
   4. `space.md`, row of text buttons (wrap to a second line at 1.3 if needed):
      `settings_licenses_open_website` (trailing `open_in_new` 18dp) when a
      website URL exists; `settings_licenses_open_license` when a licence URL
      exists. Both open a Custom Tab.
   5. `space.lg`, for each licence: `type.titleSmall` `scheme.onSurface` licence
      name, `space.sm`, licence text card: `scheme.surfaceContainerLow`,
      `shape.medium`, padding `space.lg`, text `type.bodySmall` `scheme.onSurface`
      (not monospace: wraps better on 360dp), selectable, paragraphs split on blank
      lines into separate list items.
   6. Bottom `space.xl` + navigation bar inset.

## Components

Top app bar, section headers, two-line list rows, text buttons, card with
selectable text, full-screen state views, snackbar.

## States

### List

| State | What the user sees |
| --- | --- |
| Loading (reading the bundled metadata, > 300 ms) | Loading state view (`components.md` §11), caption `common_loading`. Usually never visible (local file). |
| Success | Intro, manual section, library section. |
| Empty | Not reachable: the two manual entries are always there. If the generated part is empty (generator not run), show only the manual section; a debug-build log warns android-dev. |
| Error (metadata missing or unreadable) | Manual section still shown; in place of the library section an inline message `settings_licenses_error` `type.bodyMedium` `scheme.onSurfaceVariant` with no retry (retrying cannot fix a bundled file). Logged as a non-fatal error (Crashlytics once it is integrated). |
| Offline | Same as success: everything is bundled. |

### Detail

| State | What the user sees |
| --- | --- |
| Success | As laid out. |
| Licence text missing in the metadata | Card replaced by `settings_licenses_text_missing` `type.bodyMedium` `scheme.onSurfaceVariant`; the `settings_licenses_open_license` button stays if a URL exists. |
| No URL at all | No button row. |
| Link tapped, no browser | Snackbar `settings_link_open_failed`. |
| Link tapped offline | The Custom Tab shows its own offline page (as for the privacy policy). |
| Loading / error | Not applicable (data is passed from the list). |

## Interactions and animations

- Settings → list and list → detail: shared axis X (`motion.game.screenTransition`);
  back reverses. Reduced motion: fade.
- Rows: ripple; no long-press actions.
- Licence text is selectable (copy allowed).
- Scroll position of the list is kept when returning from a detail.

## Accessibility

- List rows: one element each, `settings_licenses_item_cd` ("Coil, versiya 3.6.3,
  lisenziya Apache License 2.0"), role button.
- Section headers and the detail name have heading semantics.
- Licence text is reachable paragraph by paragraph with TalkBack.
- Contrast: only existing pairs (`onSurface` / `onSurfaceVariant` on `surface` and
  `surfaceContainerLow`, `primary` text buttons on `surface`), both themes.

## String keys

`settings_licenses_title`, `common_back`, `settings_licenses_intro`,
`settings_licenses_section_assets`, `settings_licenses_section_libraries`,
`settings_licenses_item_supporting`, `settings_licenses_version`,
`settings_licenses_unknown`, `settings_licenses_item_cd`,
`settings_licenses_open_website`, `settings_licenses_open_license`,
`settings_licenses_text_missing`, `settings_licenses_error`, `common_loading`,
`settings_link_open_failed`.

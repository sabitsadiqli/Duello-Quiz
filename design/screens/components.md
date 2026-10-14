# Shared components

Used by every screen spec. Token names refer to `design/tokens.json`
(`scheme.*` = `color.<theme>.scheme.*`, a Material 3 `ColorScheme` role;
`game.*` = `color.<theme>.game.*`, a custom game colour; `type.*` =
`typography.scale.*`; `space.*` = `spacing.*`; `shape.*`, `size.*`,
`elevation.*`, `motion.*` as in the file). Every colour token resolves to the
light or dark value automatically. String keys refer to `design/copy/az.md`. Build each component once in
the Android `core:designsystem` module (name is a suggestion for `android-dev`).

General rules for all components:
- Touch target ≥ `size.minTouchTarget` (48dp) even when the visual is smaller.
- Disabled: content `scheme.onSurface` at `stateLayer.disabledContentOpacity`,
  container `scheme.onSurface` at `stateLayer.disabledContainerOpacity`.
- Pressed/hover/focus: Material 3 state layers (ripple) in the content colour.
- Every icon-only control has a content description from `az.md`.
- Works at font scale 1.3 without truncation and stays usable at 2.0 (text wraps,
  containers grow in height, never clip).

---

## 1. Buttons

All buttons: height ≥ `size.buttonHeight`, shape `shape.component.button` (full),
label `type.labelLarge`, horizontal padding `space.xl`, icon `size.iconSmall` with
`space.sm` gap. Single-line labels (checked in `az.md`).

| Variant | Container | Label | Use |
| --- | --- | --- | --- |
| Filled (primary) | `scheme.primary` | `scheme.onPrimary` | The one main action per screen: "Daxil ol", "Yenidən oyna" |
| Filled destructive | `scheme.error` | `scheme.onError` | "Hesabı həmişəlik sil" only |
| Tonal | `scheme.secondaryContainer` | `scheme.onSecondaryContainer` | Secondary action of equal weight: "E-poçtla daxil ol" |
| Outlined | transparent, 1dp `scheme.outline` | `scheme.primary` | Alternative action: "Əsas səhifə", "Geri" |
| Text | transparent | `scheme.primary` (destructive: `scheme.error`) | Low-emphasis: links, dialog actions, banner actions |
| Google sign-in | `scheme.surfaceContainerLowest`, 1dp `scheme.outline` | `scheme.onSurface` + multicolour Google "G" (official asset) | Welcome only. Follows Google branding: the "G" logo is never recoloured. |

Full-width buttons on phones: width = content width (360 − 2 × `space.layout.screenGutter`).

States:
- **Default / pressed / focused**: as above with state layer.
- **Loading**: label replaced by a 20dp circular progress indicator in the label
  colour; button keeps its width (no layout jump); not clickable; content
  description `common_loading`. Used for every network submit (double-tap guard).
- **Disabled**: M3 disabled colours. Also used for countdowns
  ("Yenidən göndər (42 san.)") — the countdown text stays readable because it uses
  `scheme.onSurfaceVariant` instead of the 38 % disabled colour (exception, since
  the user must read it).

## 2. Text field

Material 3 **outlined** text field. Height ≥ `size.textFieldHeight`, shape
`shape.component.textField`, label `type.bodyLarge` → floating `type.bodySmall`,
input `type.bodyLarge` `scheme.onSurface`, border `scheme.outline` (focused 2dp
`scheme.primary`), supporting text `type.bodySmall` `scheme.onSurfaceVariant`.

- **Error**: border and label `scheme.error`, supporting text replaced by the error
  string in `scheme.error`, trailing error icon (24dp) unless the field has a
  password toggle. Error appears on focus loss or submit, disappears as soon as the
  user edits the field.
- **Password**: trailing icon button (48dp) eye / eye-off, content description
  `common_password_show_cd` / `common_password_hide_cd`. Default hidden.
- **Disabled** (while submitting): M3 disabled colours; values are kept.
- **Counter** (name field): `auth_name_counter` right-aligned in supporting row.
- Keyboard: email → `KeyboardType.Email`, `ImeAction.Next`; last field →
  `ImeAction.Done` triggers submit. Autofill hints: email, password / newPassword,
  personName.

## 3. Option card (quiz answer)

Full-width card, min height `size.optionCardMinHeight`, shape
`shape.component.optionCard`, padding `space.lg` horizontal / `space.md` vertical,
gap between cards `space.md`.

Content, left to right: letter badge (A, B, C, D — 32dp circle, `type.labelLarge`),
`space.md`, option text `type.bodyLarge` (wraps, **never truncated**, up to 3+
lines), trailing status icon slot 24dp (empty unless revealed).

Letters A–D are used (not numbers) so options never look like Əminlik 1/2/3 in the
duel. TalkBack still reads "1-ci variant: …" (`quiz_option_N_cd`).

| State | Container | Border | Text | Badge | Icon | When |
| --- | --- | --- | --- | --- | --- | --- |
| Default | `scheme.surfaceContainerLow` | 1dp `scheme.outline` | `scheme.onSurface` | `scheme.secondaryContainer` / `scheme.onSecondaryContainer` | — | Question open, timer running |
| Pressed | Default + pressed state layer | — | — | — | — | Finger down |
| Selected (pending) | `game.pendingContainer` | 2dp `scheme.primary` | `game.onPendingContainer` | `scheme.primary` / `scheme.onPrimary` | 20dp progress indicator in `scheme.primary` | Tapped, waiting for server |
| Correct | `game.correctContainer` | 2dp `game.correct` | `game.onCorrectContainer` | `game.correct` / `game.onCorrect` | check, `game.correct` | Server says correct (tapped or the right one after a wrong tap/timeout) |
| Wrong | `game.wrongContainer` | 2dp `game.wrong` | `game.onWrongContainer` | `game.wrong` / `game.onWrong` | cross, `game.wrong` | The user's tapped option when wrong |
| Disabled (other options) | `scheme.surfaceContainerLow` | 1dp `scheme.outlineVariant` | `scheme.onSurfaceVariant` | unchanged | — | After a tap or timeout, for options that are neither correct nor wrong. Text stays readable (not 38 % alpha) so the user can still read them during the reveal. |
| Queued (offline) | `scheme.surfaceContainerHigh` | 2dp `scheme.outline` | `scheme.onSurface` | `scheme.secondary` / `scheme.onSecondary` | cloud-upload icon, `scheme.onSurfaceVariant` | Answer saved locally, result unknown (QUIZ-08) |

Motion: colour change `motion.game.optionRevealColour`; wrong card shakes once
horizontally `motion.game.optionWrongShake`; correct card's check icon scales in
(0 → 1, `motion.duration.short4`, `emphasizedDecelerate`). No shake/scale with
reduced motion.

Accessibility: each card is one focusable element with role Button, label
`quiz_option_N_cd`, plus state description (`quiz_option_state_*_cd`) after reveal.
Colour is never the only signal (icon + state description).

## 4. Question timer

Linear bar + numeric countdown in the quiz top bar (single-player). The duel later
reuses the same tokens with a ring variant.

- **Bar**: full content width, height `size.progressBarHeight`, track
  `game.timerTrack`, indicator `game.timerNormal`, shape `shape.full`. Shrinks from
  full to empty over 20 s, driven by wall-clock remaining time (not by an animation
  that could drift or pause).
- **Digits**: "17" in `type.titleMedium` with tabular figures, colour
  `game.timerNormal`, right-aligned in the top bar, with a 20dp timer icon.
- **Warning (≤ `motion.game.timerWarningThresholdMs`, 5 s)**: bar and digits switch
  to `game.timerWarning`; digits pulse once per second
  (`motion.game.timerWarningPulse`); TalkBack announces `quiz_timer_cd` at 5 s.
  Reduced motion: no pulse, colour change only.
- **Stopped** (after a tap): bar and digits freeze at the remaining value in
  `scheme.onSurfaceVariant`.
- **Expired**: bar empty, digits "0" in `game.timerWarning`, then the reveal shows
  `quiz_timeout`.
- **Ring variant (duel, later)**: circle `size.timerRing`, stroke
  `size.timerStroke`, same colours and warning rule; digits inside.

## 5. Category card (home)

Full-width card (not a grid — see truncation risks in `az.md`), min height 72dp,
shape `shape.component.categoryTile`, container `scheme.surfaceContainerLow`,
padding `space.lg`, gap `space.layout.listItemGap` between cards.

Left: icon container `size.categoryIconContainer` circle, `scheme.primaryContainer`
with 24dp icon in `scheme.onPrimaryContainer`. Then `space.lg`, a text column: name
`type.titleMedium` `scheme.onSurface` (max 2 lines, ellipsis), supporting
`home_category_question_count` `type.bodyMedium` `scheme.onSurfaceVariant`. Right:
chevron 24dp `scheme.onSurfaceVariant`.

Icons (Material Symbols Rounded, Apache 2.0, bundled as vector drawables), mapped by
`icon_key`. The key values are a **proposal** until `backend-dev` fixes them in
`docs/api/` (open question in `home.md`):

| icon_key | Symbol |
| --- | --- |
| `geography` | `public` |
| `history` | `history_edu` |
| `biology` | `eco` |
| `physics` | `science` |
| `sports` | `sports_soccer` |
| unknown | `category` (generic fallback, HOME-01) |

States: default; pressed (state layer); **loading** (the chevron is replaced by a
20dp progress indicator, the card is not clickable and all other cards ignore taps
— HOME-02 double-tap rule); **skeleton** (icon circle and two text bars in
`scheme.surfaceContainerHighest`, shimmer alpha 0.5 ↔ 1.0 over 1 s, no shimmer with
reduced motion).

## 6. Duel entry card (home, behind build flag)

Hidden until the duel ships (OQ-020) — the layout slot simply does not exist.
When enabled: full-width card, shape `shape.large`, container
`scheme.primaryContainer`, title `home_duel_title` `type.titleLarge`
`scheme.onPrimaryContainer`, hint `home_duel_hint` `type.bodyMedium`
`scheme.onPrimaryContainer`, trailing filled icon button (`bolt` icon,
`scheme.primary` / `scheme.onPrimary`). No coins, dice, cards or casino imagery.

## 7. Əminlik chips (defined now, used by the duel)

Three square chips side by side, each `size.confidenceChip` (88dp), shape
`shape.component.confidenceChip`, gap `space.md`. Big numeral `type.displaySmall`
plus three small pips under it (1, 2 or 3 filled dots) so the level is not
communicated by colour alone.

| Level | Container | Content |
| --- | --- | --- |
| 1 | `game.confidence1Container` | `game.onConfidence1Container` |
| 2 | `game.confidence2Container` | `game.onConfidence2Container` |
| 3 | `game.confidence3Container` | `game.onConfidence3Container` |

- Intensity grows with the level (calm → strong) in both themes.
- **Selected**: 3dp ring `game.confidenceSelectedOutline` with 2dp gap, chip scales
  to 1.05 (`motion.duration.short4`). Others keep their colour.
- **Disabled** (Əminlik 3 used twice): M3 disabled colours, small "0" uses-left
  counter stays readable in `scheme.onSurfaceVariant`.
- Uses-left counter under chip 3: "2" / "1" / "0" in `type.labelMedium`.
- Copy for chips and uses-left is defined with the duel spec, not now.

## 8. Banners

Inline, full-width, placed directly under the top app bar (pushes content down,
never overlays it). Shape `shape.component.banner`, padding `space.lg`, min height
`size.bannerMinHeight`, leading 24dp icon, text `type.bodyMedium`, optional title
`type.titleSmall`, actions as text buttons on their own row, right-aligned, under
the text (so long Azerbaijani labels never squeeze the message).

| Variant | Container | Content | Icon | Use |
| --- | --- | --- | --- | --- |
| Info | `game.bannerInfoContainer` | `game.onBannerInfoContainer` | `mail` / `info` | Email verification (AUTH-08), result not synced |
| Warning / offline | `game.bannerWarningContainer` | `game.onBannerWarningContainer` | `cloud_off` | Offline over cached data (HOME-05), quiz offline (QUIZ-08) |

Enter: expand + fade `motion.duration.medium1` `emphasizedDecelerate`; exit:
collapse + fade `motion.duration.short4` `emphasizedAccelerate`. Text actions in a
banner use the banner's content colour, not `scheme.primary` (contrast).
Accessibility: live region polite; announced once when it appears.

## 9. Dialogs

Material 3 `AlertDialog`: container `scheme.surfaceContainerHigh`, shape
`shape.component.dialog`, elevation `elevation.component.dialog`, scrim
`scheme.scrim` at 0.32, title `type.headlineSmall` `scheme.onSurface`, body
`type.bodyMedium` `scheme.onSurfaceVariant`, actions text buttons right-aligned
(confirm on the right). Destructive confirm uses `scheme.error`. Buttons stack
vertically automatically if they do not fit side by side.

- Dismiss on scrim tap and back = the safe action (cancel / continue).
- No icon in confirmation dialogs (keeps the title width).
- Used: quiz exit (QUIZ-07), sign-out (AUTH-07), delete account second confirmation
  (SET-05).

## 10. Snackbars

`SnackbarHost` at the bottom, above the system navigation bar inset. Container
`scheme.inverseSurface`, text `scheme.inverseOnSurface` `type.bodyMedium` (max 2
lines, then wraps — never truncated mid-sentence in Azerbaijani; keep strings short),
action `scheme.inversePrimary`, shape `shape.component.snackbar`. Duration: short
(4 s) for confirmations, long (10 s) for errors with an action. One at a time; a
new one replaces the old one. Use for transient errors that do not belong to a
field (offline on tap, Google errors, rate limit).

## 11. Full-screen state views (empty / error / offline / loading)

Centred column inside the content area, max width `space.layout.maxContentWidth`,
padding `space.xl`:

- Illustration slot: 72dp icon in `scheme.onSurfaceVariant` on a 120dp circle of
  `scheme.surfaceContainerHigh` (icons: `inbox` empty, `error` error, `cloud_off`
  offline). No raster illustrations in the MVP.
- `space.lg`, title `type.titleMedium` `scheme.onSurface`, centred, wraps.
- `space.sm`, body `type.bodyMedium` `scheme.onSurfaceVariant`, centred, optional.
- `space.xl`, primary action: tonal button (`common_retry` / `common_refresh`);
  optional secondary text button under it.
- **Loading**: circular indicator 48dp `scheme.primary`, optional caption
  `type.bodyMedium` `scheme.onSurfaceVariant` (e.g. `quiz_loading`). Shown only after
  300 ms to avoid flashing on fast networks.

## 12. Top app bar

`TopAppBar` (small), height `size.topAppBarHeight`, container `scheme.surface`
(scrolled: `scheme.surfaceContainer`), title `type.titleLarge` `scheme.onSurface`
single line, icons `scheme.onSurfaceVariant`. Titles longer than ~16 characters are
not allowed here (see `az.md` risks); auth screens put their title in the content
as `type.headlineSmall` instead.

## 13. Avatar

Circle `size.avatar` (home header). Image via Coil, crop centre. Fallback (no URL or
load failed): `scheme.tertiaryContainer` with the first letter of the display name
(uppercase, Azerbaijani-aware: "i" → "İ", "ı" → "I") in `type.titleMedium`
`scheme.onTertiaryContainer`. Decorative for TalkBack (the header is merged).

## 14. List rows and radio rows (settings)

Material 3 `ListItem`: min height 56dp (72dp with supporting text), headline
`type.bodyLarge` `scheme.onSurface` (max 2 lines), supporting `type.bodyMedium`
`scheme.onSurfaceVariant`, leading icon 24dp `scheme.onSurfaceVariant`, padding
`space.lg`. Section headers `type.titleSmall` `scheme.primary`, padding
`space.lg` start, `space.xl` top. Radio rows: whole row is the touch target
(`Modifier.selectable`, role RadioButton), radio on the leading side, group
semantics `selectableGroup` (SET-01 TalkBack). Destructive row ("Hesabı sil"):
icon and text `scheme.error`.

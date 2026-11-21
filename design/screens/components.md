# Shared components

Used by every screen spec. **v3 (tokens 3.0.0, 2026-11-17):** flat "dəftər"
look — paper/ink, one nar accent, pill buttons, hairline cards, no gradients, no
depth edges. Sections 0, 1, 3, 4, 5, 6, 8, 12, 24, 25, 28, 29 were rewritten or
retired; §33 … §38 are new; the rest keep their behaviour and get the v3 look
through tokens plus the restyle notes under "v3 restyle of the remaining
components" below. **Mock exams (tokens 3.1.0, 2026-11-21):** §3a, §39 … §46 and
"Exam rows" at the end of this file. Per-screen impact: `v3-migration.md`. Token names refer to `design/tokens.json`
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
- Pressed (v3, §0): Material 3 ripple in the content colour everywhere; game
  inputs and the big home entries also scale to 0.98. No depth edges. Focus: 2dp
  `scheme.onSurface` ring outside the shape (keyboard / switch access).
- Errors are never colour-only (accent and error are both reds): icon + sentence.
- Every icon-only control has a content description from `az.md`.
- Works at font scale 1.3 without truncation and stays usable at 2.0 (text wraps,
  containers grow in height, never clip).

---

## 0. Press feedback (v3) — replaces the v2 `PressableSurface`

**Removed in v3:** the chunky depth edge (solid bottom edge, face moving down on
press), `depth.*`, all `game.*Depth` roles and `motion.pressable`. Nothing in v3
has a depth edge. android-dev deletes the depth drawing from `PressableSurface`
(or the whole composable) instead of keeping it behind a flag.

v3 press feedback (`motion.press`):

| Component | Ripple (state layer) | Scale | Haptic |
| --- | --- | --- | --- |
| Option card, Əminlik tile | content colour at `stateLayer.pressedOpacity` | 1.0 → `motion.press.scaleTo` (0.98) in `pressDownMs`, back with `spring.gentle` | light tick |
| Category row, duel card | yes | yes | none |
| Buttons, list rows, icon buttons, nav items | yes | no | none |
| Answer boxes, week dots, sticker, info cards | not interactive | — | — |

The click fires on release as usual and never waits for the animation (DUEL-16).
Disabled: flat, M3 disabled colours, no ripple. Reduced motion: no scale, ripple
stays. "White game cards" of v2 are now **flat cards**: face `scheme.surface`
(or a stated container), 1dp `game.cardBorder` hairline, `shape.component.card`,
no shadow. Wherever an older spec says "white game card (§0 without press)",
read "flat card".

## 1. Buttons

v3: **pills** (`shape.component.button` = full), flat, height `size.buttonHeight`
(48dp; `buttonHeightLarge` 52dp only for a single hero action), label
`type.labelLarge` (15sp Bold, sentence case), horizontal padding `space.xl`, icon
18–20dp with `space.sm` gap. Single-line labels (checked in `az.md`). Ripple, no
scale (§0).

| Variant | Face | Label | Border | Use |
| --- | --- | --- | --- | --- |
| Primary | `scheme.primary` | `scheme.onPrimary` | — | The one main action per screen: "Daxil ol", "Başla", "Yenidən oyna" |
| Secondary | transparent | `scheme.onSurface` | 1dp `scheme.outline` | Secondary action of equal weight: "Əsas səhifə", "Paylaş", "Yenidən cəhd et" in state views, "E-poçtla daxil ol". Replaces v2 Neutral (and v1 Tonal / Outlined) |
| Destructive | `scheme.error` | `scheme.onError` | — | "Hesabı həmişəlik sil" only |
| Text | transparent | `scheme.primary` (destructive: `scheme.error`) | — | Links, dialog actions, banner actions |
| On ink | `scheme.surface` | `scheme.onSurface` | — | Only on an `inverseSurface` block (duel card CTA, §6) |
| Google sign-in | Secondary | `scheme.onSurface` + official multicolour "G" | 1dp `scheme.outline` | Welcome / sign-in; the "G" is never recoloured |

Naming in older specs: "primary button" / "pressable primary" = Primary;
"neutral button" / "neutral pressable" = Secondary; "duel button" / "Duel
pressable" = **Primary** (v3 has no separate duel colour); "destructive
button" = Destructive.

Full width on phones = content width. Two stacked: gap `space.md`, primary on top.

States: **Loading** — label replaced by a 20dp circular indicator in the label
colour, width kept, not clickable, `common_loading`. **Disabled** — M3 disabled
colours (countdown labels such as "Yenidən göndər (42 san.)" stay
`scheme.onSurfaceVariant` so they remain readable).

## 2. Text field

Outlined text field with a **label above the field** (not floating — keeps long
Azerbaijani labels fully visible). Label `type.labelMedium`
`scheme.onSurfaceVariant`, `space.xs` gap, field: height ≥ `size.textFieldHeight`,
shape `shape.component.textField` (16dp), container `scheme.surfaceContainerLowest`,
2dp border `scheme.outline` (focused: 2dp `scheme.primary` + 3dp focus ring
`scheme.primary` at 20 % outside), input `type.bodyLarge` `scheme.onSurface`,
placeholder `scheme.onSurfaceVariant`, supporting text `type.bodySmall`
`scheme.onSurfaceVariant`. No depth edge (it is not a button).

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

v3: flat, `shape.component.optionCard` (12dp), min height
`size.optionCardMinHeight` (56dp), face `scheme.surface`, 1dp `scheme.outline`
border, padding `space.md` start / `space.lg` end / `space.md` vertical, gap
`space.sm` between cards. Press: §0 (ripple + 0.98 scale + light tick).

Content: **letter badge** (A–D, `size.optionLetterBadge` 32dp **circle**,
`type.labelLarge`), `space.md`, option text `type.bodyLarge` (wraps, never
truncated), trailing outcome word `type.labelMedium` after reveal.

| State | Face | Border | Text | Badge (container / glyph) | Trailing |
| --- | --- | --- | --- | --- | --- |
| Default | `scheme.surface` | 1dp `scheme.outline` | `scheme.onSurface` | `scheme.surfaceContainerHigh` / letter `scheme.onSurface` | — |
| Selected (pending) | `game.pendingContainer` | **2dp `scheme.onSurface` (ink)** | `game.onPendingContainer` | `scheme.inverseSurface` / letter `scheme.inverseOnSurface` | 20dp progress in `scheme.onSurface` |
| Correct | `game.correctContainer` | 2dp `game.correct` | `game.onCorrectContainer` | `game.correct` / **check** `game.onCorrect` | `quiz_option_outcome_correct` in `game.correct` |
| Wrong | `game.wrongContainer` | 2dp `game.wrong` | `game.onWrongContainer` | `game.wrong` / **cross** `game.onWrong` | `quiz_option_outcome_wrong` in `game.wrong` |
| Dimmed | `scheme.surface` | 1dp `game.cardBorder` | `scheme.onSurfaceVariant` | `scheme.surfaceContainerHigh` / `scheme.onSurfaceVariant` | — |
| Queued (offline) | `scheme.surfaceContainerHigh` | 1dp dashed `scheme.outline` | `scheme.onSurface` | `scheme.secondary` / letter `scheme.onSecondary` | `cloud_upload` `scheme.onSurfaceVariant` |

The selected state uses **ink**, not the accent, so red on the question screen
only ever means "wrong" (the accent is a red too). Colour-blind safety unchanged:
glyph, outcome word, motion (pop vs shake) and luminance. Motion:
`motion.game.optionRevealColour`, `optionCorrectPop` (now 1.02),
`optionWrongShake` (now 4dp); none with reduced motion. Accessibility and the
duel picker markers: unchanged (self ring `scheme.primary`, opponent
`scheme.tertiary`).

## 4. Question timer

v3 restyle, same behaviour:
- **Bar**: thin line `size.progressBarHeight` (4dp), track `game.timerTrack`,
  indicator `game.timerNormal` (ink), `shape.full`, **no highlight stripe**.
  Wall-clock driven.
- **Digits**: timer pill, `shape.full`, face `scheme.surface`, 1dp
  `scheme.outline`, 18dp `timer` icon + digits `type.titleMedium` tabular,
  `game.timerNormal`.
- **Warning** (≤ 5 s): bar, pill border, icon, digits `game.timerWarning`
  (burnt saffron-orange); digits pulse (`timerWarningPulse`, now 1.05); TalkBack
  at 5 s. Reduced motion: colour only.
- **Stopped**: freeze in `scheme.onSurfaceVariant`. **Expired**: "0" in
  `game.timerWarning`.
- **Ring variant (duel)**: `size.timerRing`, stroke `size.timerStroke` (4dp,
  round caps), track `game.timerTrack`, arc `game.timerNormal`, digits
  `type.titleSmall` tabular. Rules for fraction, per-phase warning, frozen and
  expired states unchanged (DUEL-12).

## 5. Category list row (home) — replaces the v2 category tile grid

**Removed in v3:** the 2-column tile grid, the 8 category accent slots, the
single-column fallback and `accentKey` (OQ-D27 superseded). One colour for all
categories.

Row, full content width, min height `size.categoryRowMinHeight` (52dp, grows),
no container (`shape.component.categoryRow` = none), 1dp `game.cardBorder`
divider between rows (inset 0). Padding `space.sm` vertical. Content, vertically
centred:
1. **Numeral** "01"… (index + 1, two digits) `typography.custom.numeral`
   (Fraunces Regular 20sp) `scheme.primary`, fixed width
   `size.categoryNumeralWidth` (32dp), start-aligned. From 10 on: "10", "11".
2. 24dp **line icon** (Material Symbols **Outlined**, weight 300) in
   `scheme.onSurfaceVariant`, mapped by `icon_key` (table below), `space.md` after.
3. Name `type.titleMedium` `scheme.onSurface`, weight 1, max 2 lines, wraps at
   word boundaries, never ellipsized or hyphenated.
4. 24dp `chevron_right` `scheme.onSurfaceVariant`, `space.sm` before.

| icon_key | Symbol (Outlined) |
| --- | --- |
| `geography` | `public` |
| `history` | `history_edu` |
| `biology` | `eco` |
| `physics` | `science` |
| `sports` | `sports_soccer` |
| unknown | `category` (HOME-01 fallback) |

States: rest; pressed (§0: ripple + scale); **loading** (chevron → 20dp progress
in `scheme.onSurface`; all rows and the duel card ignore taps, HOME-02);
**skeleton** (numeral, icon and name as `scheme.surfaceContainerHighest` bars;
shimmer alpha 0.5 ↔ 1.0 over 1 s; none with reduced motion). First appearance:
`motion.game.categoryListEnter`.
Accessibility: one node per row, role Button, label "name, 10 sual"; the numeral
is not announced.

## 6. Duel card v3 (home, behind build flag) — replaces the gradient duel hero card

Hidden when the duel build flag is off (OQ-020): no slot, no placeholder.

Full-width solid block — the "inverted page": face `scheme.inverseSurface` (ink in
light theme, paper in dark theme), `shape.component.duelCard` (18dp), padding
`space.lg`, min height `size.duelCardMinHeight`, no border, no shadow, no
gradient. The whole card is one touch target (§0: ripple in
`scheme.inverseOnSurface` + scale).

- **Sticker** (§34) on the top edge, end inset `space.lg`, vertically centred on
  the edge (half outside the card), rotated `size.stickerRotationDeg`.
- Title `home_duel_title` "Duel" `type.headlineLarge` (Fraunces)
  `scheme.inverseOnSurface`.
- `space.xs`, hint `home_duel_hint` `type.bodyMedium` `scheme.inverseOnSurface`.
- `space.lg`, row (space between): duration 16dp `schedule` +
  `home_duel_duration` `type.labelLarge` `game.inverseOnSurfaceVariant`; CTA pill
  ("On ink" button, §1): `home_duel_cta` + 18dp `arrow_forward` (mirrored in RTL).
  If both do not fit, the pill goes under the duration at full width.

No crossed-swords tile any more (the swords icon now lives in the bottom bar).
Never coins, dice, cards or casino imagery.

## 7. Əminlik tiles (duel)

> v3: no depth edge (`depth.confidenceChip` and `game.confidenceNDepth` removed),
> press = §0 (ripple + scale + tick), shape 16dp (`shape.component.confidenceChip`
> = large). Levels are ink density: 1 paper, 2 sand, 3 solid ink
> (`game.confidenceNContainer`), plus the signal bars and the numeral (now Fraunces
> `type.displaySmall`). Selected ring = 3dp `game.confidenceSelectedOutline`
> (the accent). Tile 1 keeps a 1dp `scheme.outline` border. Everything else below
> is unchanged.

Three pressable tiles (§0) side by side in the confidence panel (§18). Width =
(content width − 2 × `space.md`) / 3 (~101dp on 360dp), face min height
`size.confidenceChip`, depth `depth.confidenceChip` (5dp) in
`game.confidenceNDepth`, shape `shape.component.confidenceChip` (20dp — a rounded
rectangle, **never a circle**, so it cannot read as a chip/coin), gap `space.md`,
padding `space.md` vertical / `space.sm` horizontal. Tile 1 also gets a 2dp border
in `game.onConfidence1Container` at 30 % (it is pale in light theme). Content,
centred, top to bottom:

1. Row: numeral "1" / "2" / "3" in `type.displaySmall` (Black), tabular figures,
   `space.sm`, **signal bars**: three rounded bars 6dp wide, heights 8/13/18dp,
   gap 3dp; the first N bars are solid in the content colour, the rest at 30 %
   (level 1 = one bar, 3 = three bars). The bars are a strength meter — a second,
   non-colour cue for the level.
2. `space.xs`, stake on correct `duel_delta_positive` ("+300") in `type.labelLarge`.
3. Stake on wrong `duel_delta_negative` ("−200", U+2212) or `duel_delta_zero` ("0")
   in `type.labelLarge`.

All text uses `game.onConfidenceNContainer` (never green/red on the chip: the sign
carries the meaning and every pair stays checked). Round 7 shows doubled stakes
(DUEL-10). The numeral is the level, so colour is never the only signal (the old
"pips" are replaced by the signal bars).

| Level | Container | Content | Stakes (regular / round 7) |
| --- | --- | --- | --- |
| 1 | `game.confidence1Container` | `game.onConfidence1Container` | +100 / 0 · +200 / 0 |
| 2 | `game.confidence2Container` | `game.onConfidence2Container` | +200 / −100 · +400 / −200 |
| 3 | `game.confidence3Container` | `game.onConfidence3Container` | +300 / −200 · +600 / −400 |

**Uses-left badge on chip 3**: 24dp circle at the chip's top-end corner (inset
`space.xs`), `scheme.inverseSurface` with the number of remaining uses ("2", "1")
in `type.labelMedium` `scheme.inverseOnSurface`. Hidden at 0 (the chip is disabled
and the caption explains it). Always paired with the caption under the chip row
(`duel_confidence_three_left` / `duel_confidence_three_none`), so the count is
visible as text, not only as a badge.

| State | Visual | When |
| --- | --- | --- |
| Enabled | As above, ripple in content colour | Confidence phase open, nothing sent |
| Pressed | §0 press (`motion.duel.chipPress`) | Finger down. `round.bet` is sent on the tap itself, never after the animation |
| Selected / sending | 3dp ring `game.confidenceSelectedOutline` outside the shape, scale `motion.duel.chipLock` (spring `bouncy`); other tiles fade to `othersFadeToAlpha`; 24dp corner badge at the top-start (inverse of the ring colour) with a 16dp progress indicator | Tapped, waiting for `round.betLocked {self}` |
| Locked | Ring stays, the corner badge shows a 14dp `lock` icon | `round.betLocked {self}` arrived |
| Defaulted | Chip 1 gets the same ring and the lock icon; others faded; status line `duel_confidence_defaulted` | Confidence deadline passed without a valid pick (server default 1) |
| Disabled (limit) | Tile 3 only: flat (§0), M3 disabled container/content colours, no badge, not focusable as a button (TalkBack reads `duel_confidence_three_none`) | Əminlik 3 used twice |
| Disabled (others) | M3 disabled colours | After any chip was tapped, and after the phase deadline |

Accessibility: each chip is one element, role Button, label
`duel_confidence_chip_1_cd` / `duel_confidence_chip_cd`; state description
"seçildi" via `duel_confidence_selected_cd`. Haptic: one light tick on tap.

## 8. Banners

v3: flat, `shape.component.banner` (12dp), no border. Two sizes.

**Compact** (home, Gündəlik, Profil — tab roots): height
`size.bannerCompactHeight` (40dp, grows with font scale), padding `space.md`
horizontal, row: 20dp icon, `space.sm`, one sentence `type.bodyMedium` (wraps to
2 lines, never truncated), optional trailing text button or chevron. Tapping a
compact banner with a chevron expands it into the full banner in place.

**Full** (all other screens, and an expanded compact one): min height
`size.bannerMinHeight`, padding `space.lg`, leading 24dp icon, optional title
`type.titleSmall`, text `type.bodyMedium`, actions as text buttons on their own row.

| Variant | Container | Content | Icon | Use |
| --- | --- | --- | --- | --- |
| Info | `game.bannerInfoContainer` | `game.onBannerInfoContainer` | `mail` / `info` | Email verification (AUTH-08), result not synced |
| Warning / offline | `game.bannerWarningContainer` | `game.onBannerWarningContainer` | `cloud_off` | Offline over cached data (HOME-05), quiz offline (QUIZ-08), notifications blocked |

Enter: expand + fade `motion.duration.medium1`; exit: collapse + fade
`short4`. Banner text actions use the banner content colour. Polite live region,
announced once.

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
- `space.xl`, action: secondary pill button (§1) (`common_retry` / `common_refresh`);
  optional text button under it.
- v3: the icon sits without the 120dp circle (just the 48dp line icon in
  `scheme.onSurfaceVariant`); title in serif `type.headlineSmall`.
- **Compact** variant (inside a tab root list, e.g. the home category area): no
  max-height centring, padding `space.lg`, 32dp icon, title `type.titleMedium`,
  button inline under it; keeps the rest of the screen visible.
- **Loading**: circular indicator 48dp `scheme.primary`, optional caption
  `type.bodyMedium` `scheme.onSurfaceVariant` (e.g. `quiz_loading`). Shown only after
  300 ms to avoid flashing on fast networks.

## 12. Top app bar

v3: `TopAppBar` (small), height `size.topAppBarHeight`, container
`scheme.surface`; when content is scrolled under it a 1dp `game.cardBorder`
hairline appears (no colour change). Title `type.titleLarge` (sans 20sp Bold)
`scheme.onSurface`, single line, ≤ ~16 characters; navigation icon
`scheme.onSurface`, actions `scheme.onSurfaceVariant`. Screen headlines inside
content (auth, result screens, empty states) use the serif `type.headline*`.

Tab roots (Gündəlik, Profil) have no navigation icon; the Oyna tab has no top app
bar at all (its top row, `home.md`, replaces it). Full-screen flows keep a close
or back icon.

## 13. Avatar

Circle `size.avatarSmall` (home top row, v3), `size.avatar` ( duel panels, match history), `size.avatarLarge`
(result screen), `size.avatarXLarge` (profile header, match intro, search). Image
via Coil, crop centre. Fallback (no URL or load failed): `scheme.tertiaryContainer`
with the first letter of the display name (uppercase, Azerbaijani-aware: "i" → "İ",
"ı" → "I") in `type.titleMedium` (`type.headlineMedium` at `avatarXLarge`)
`scheme.onTertiaryContainer`. If the first character is not a letter, use the first
visible character; if there is none, the `person` icon (PROF-02). Decorative for
TalkBack (the surrounding element carries the name).

- **Deleted player**: `scheme.surfaceContainerHighest` circle with the `person_off`
  icon in `scheme.onSurfaceVariant`; name `common_deleted_player`.
- **Bot** (OQ-107): circle in `scheme.surface` with the app mark centred
  (`brand/logo-mark-light.svg` / `logo-mark-dark.svg` as vector drawables by theme,
  the same `DuelloLogoMark` as on welcome), mark size = 70 % of the circle
  (`size.avatar` 40dp → 28dp, above the 24dp brand minimum; 20dp picker markers
  (`duel-reveal.md`) → the circle alone in `scheme.tertiaryContainer` with a 14dp
  `smart_toy` icon in `scheme.onTertiaryContainer`, because the mark is not legible
  below 24dp). Always a local resource: never loaded from a URL, never a photo,
  never the letter fallback. Chosen by `opponentType = bot` (protocol) / the bot
  flag in `MatchSummary`, never by comparing the display name. Decorative like the
  other variants; it still gets the opponent accent ring in the duel.
- **Accent ring (duel only)**: 2dp ring outside the avatar (avatarXLarge: 3dp) —
  self `scheme.primary`, opponent `scheme.tertiary`. The same two accents mark self
  and opponent everywhere in the duel, so "who is who" never depends on position
  alone. The labels "Sən" / the opponent's name are always present too.
- The duel never waits for an avatar image (DUEL-05).

## 14. List rows and radio rows (settings)

Material 3 `ListItem`: min height 56dp (72dp with supporting text), headline
`type.bodyLarge` `scheme.onSurface` (max 2 lines), supporting `type.bodyMedium`
`scheme.onSurfaceVariant`, leading icon 24dp `scheme.onSurfaceVariant`, padding
`space.lg`. Section headers `type.titleSmall` `scheme.primary`, padding
`space.lg` start, `space.xl` top. Radio rows: whole row is the touch target
(`Modifier.selectable`, role RadioButton), radio on the leading side, group
semantics `selectableGroup` (SET-01 TalkBack). Destructive row ("Hesabı sil"):
icon and text `scheme.error`.

---

Components 15–23 are used by the duel (`duel-*.md`) and the profile (`profile.md`).
They reuse existing colour roles; every new colour combination is listed in
`tools/contrast.py`. Since tokens 2.0.0 every card-like container is a white game
card with a depth edge (§0) unless stated otherwise.

## 15. Duel top bar and phase stepper

Pinned at the top of the match screen (never scrolls). Kept as low as possible so
the body keeps room for the question and four options on a 360 × 640dp phone.

**Top bar** (custom, `scheme.surface`, height `size.topAppBarHeight`):
- Leading: close icon button 48dp, content description `duel_leave_cd` (opens the
  leave dialog, `duel-interruptions.md`).
- Centre-start, two lines:
  - Round label `duel_round_label` ("Raund 3 / 7") or
    `duel_round_label_sudden_death` ("Əlavə sual 1 / 3") in `type.titleMedium`
    `scheme.onSurface`, tabular figures, 1 line; content description
    `duel_round_label_cd` / `duel_round_label_sudden_death_cd`. In round 7 an
    All-in pill follows the label with `space.sm` gap: `shape.full`,
    `scheme.tertiaryContainer` / `scheme.onTertiaryContainer`, padding `space.sm` ×
    `space.xxs`, text `duel_all_in_pill` ("×2") `type.labelLarge`, content
    description `duel_all_in_pill_cd`.
  - Phase name `duel_phase_question` / `duel_phase_confidence` /
    `duel_phase_answer` / `duel_phase_result` in `type.labelMedium`
    `scheme.onSurfaceVariant`, cross-fades on change.
- Trailing: timer ring (§4 ring variant), 48dp, `space.xs` end padding.

**Phase stepper** directly below: gutters `space.layout.screenGutter`, padding
`space.xxs` top / `space.sm` bottom; 4 equal segments (3 in sudden death: no
Əminlik), gap `space.xs`, each a bar of `size.phaseSegmentHeight`, `shape.full`:
done and current `scheme.primary`, upcoming `game.timerTrack`. The current
segment fills from start to end over `motion.duration.short4` when its phase
begins. Decorative for TalkBack; the phase change is announced once as
`duel_phase_cd` (polite).

## 16. Duel player panel

Two panels side by side under the stepper (self at the start, opponent at the end),
gap `space.sm`, gutters `space.layout.screenGutter`, always the same height
(`IntrinsicSize.Max`), min height `size.duelPlayerPanelMinHeight`. Pinned with the
top bar. White game card (§0 without press): face `scheme.surfaceContainerLowest`,
depth `game.cardDepth` 4dp, shape `shape.component.duelPlayerPanel`, padding
10dp, **2dp border in the player's accent** — self `scheme.primary`, opponent
`scheme.tertiary` (plus the avatar accent ring, §13, and the name, so "who is who"
never depends on colour alone).

Content, top to bottom:
1. Row: avatar `size.avatar` with accent ring, `space.sm`, column:
   - name `type.labelLarge` `scheme.onSurface`, 1 line, ellipsis. Self shows
     `duel_you` ("Sən"); the opponent shows the display name (truncation accepted,
     DUEL-05; the full name is on the intro and the result screen). **Bot
     opponent**: the name slot shows the Bot badge (§32, panel placement) instead
     of the text "Duello Bot" — the 84dp name slot cannot hold "Duello Bot" plus a
     badge at font scale 1.3 (≈ 123dp + 64dp), and a truncated "Duello B…" next to
     "Bot" reads worse than "Bot" alone, mirroring "Sən" on the own panel. The full
     name is on the intro and the result screen. The panel is one TalkBack element
     starting with `duel_opponent_bot_cd` ("Rəqib: Duello Bot, bot");
   - score `type.titleLarge` `scheme.onSurface`, tabular figures, U+2212 for
     negatives. Content description `duel_player_score_cd`.
2. `space.sm`, **status slot** (min 28dp): a status pill (§17) or, in the result
   phase, the reveal face (below). In the own panel, from the moment the own
   Əminlik is locked (or defaulted) until the reveal, the slot starts with the mini
   Əminlik badge of that level (`size.duelBadge`, see below; content description
   `duel_answer_your_confidence`) followed by the pill — so the player always sees
   what is at stake while answering. The opponent slot never shows a level before
   the reveal.

**Reveal face** (result phase, after the flip, `motion.duel.revealFlip`): the panel
switches face, depth edge (`game.correctDepth` / `game.wrongDepth` /
`game.cardDepth`) and gets a 3dp border:

| Outcome | Container / content | Border | Icon |
| --- | --- | --- | --- |
| Correct | `game.correctContainer` / `game.onCorrectContainer` | `game.correct` | check |
| Wrong | `game.wrongContainer` / `game.onWrongContainer` | `game.wrong` | cross |
| Timeout ("Vaxt bitdi") | `scheme.surfaceContainerHighest` / `scheme.onSurface` | `scheme.outline` | `timer_off` |
| Missed round | `scheme.surfaceContainerHighest` / `scheme.onSurface` | `scheme.outline` | `cloud_off` |

Reveal face content (replaces the status slot; rows 1–2 stay, the score counts up):
- Row: mini Əminlik badge (`size.duelBadge`, `shape.component.duelBadge`,
  `game.confidenceNContainer` / `game.onConfidenceNContainer`, numeral
  `type.titleMedium`) — `space.sm` — answer letter badge (`size.duelBadge` circle,
  `game.correct` / `game.onCorrect` or `game.wrong` / `game.onWrong`; none for
  timeout/missed) — `space.xs` — outcome icon 18dp in `game.correct` / `game.wrong`
  / `scheme.onSurfaceVariant`.
- Row: round points `type.titleMedium` tabular (`duel_delta_positive` /
  `duel_delta_negative` / `duel_delta_zero`) in `game.correct` on correct,
  `game.wrong` on wrong, the content colour when 0; then `space.sm`, speed bonus
  (only if > 0): 16dp `bolt` icon + "+37" (`duel_delta_positive`) in
  `type.labelMedium` in the content colour, content description
  `duel_reveal_bonus_cd`.
- Outcome word under the points, `type.labelMedium` content colour:
  `duel_reveal_correct` / `duel_reveal_wrong` / `quiz_timeout` / `duel_reveal_missed`.

Before the flip, the opponent's slot shows a 20dp `lock` icon next to its status
pill; the own slot shows the own locked level (mini badge). Both panels flip in the
same frame (never staggered).

## 17. Status pill

`shape.component.statusPill`, height ≥ 28dp, padding `space.sm` × `space.xs`,
16dp leading icon, `space.xs`, text `type.labelMedium`, wraps to max 2 lines (never
truncates), cross-fades on change (`motion.duel.statusPillChange`). Polite live
region on the opponent panel only.

| Variant | Container / content | Icon | Text |
| --- | --- | --- | --- |
| Waiting (opponent) | transparent / `scheme.onSurfaceVariant` | 14dp indeterminate progress | `duel_status_choosing`, `duel_status_answering` |
| Sending (self) | transparent / `scheme.onSurfaceVariant` | 14dp progress | `duel_status_sending` |
| Done | `scheme.secondaryContainer` / `scheme.onSecondaryContainer` | `lock` (Əminlik) / `check_circle` (answer) | `duel_status_locked_opponent`, `duel_status_locked_self`, `duel_status_answered_opponent`, `duel_status_answered_self` |
| Timeout | `scheme.surfaceContainerHighest` / `scheme.onSurfaceVariant` | `timer_off` | `quiz_timeout` |
| Disconnected | `scheme.errorContainer` / `scheme.onErrorContainer` | `cloud_off` | `duel_status_disconnected` |
| Returned | `scheme.secondaryContainer` / `scheme.onSecondaryContainer` | `wifi` | `duel_status_returned` (shown for `motion.duel.opponentReturnedHoldMs`) |

The opponent pill never contains a value (OQ-024, DUEL-18).

## 18. Confidence panel

Slides up from the bottom in the Əminlik phase (`motion.duel.confidencePanelEnter`)
and is replaced by the options in the answer phase. Full width, container
`scheme.surfaceContainerHigh`, shape `shape.component.confidencePanel` (top
corners `shape.extraLarge`), padding `space.lg` vertical and
`space.layout.screenGutter` horizontal, bottom padding + navigation bar inset.

1. Title `duel_confidence_title` `type.titleLarge` `scheme.onSurface`, centred.
2. `space.lg`, chip row (§7).
3. `space.md`, caption (centred, `type.bodyMedium`, wraps) in
   `scheme.onSurfaceVariant`: `duel_confidence_three_left` /
   `duel_confidence_three_none`.
4. `space.sm`, status line (height reserved 24dp, `type.bodyMedium`
   `scheme.onSurface`, 18dp icon): `duel_status_sending` → `duel_confidence_locked`
   (`lock`) → `duel_confidence_waiting_opponent`; or `duel_confidence_defaulted`
   (`timer_off`); or `duel_confidence_too_late` (`timer_off`).

## 19. Round banner (All-in, sudden death)

Inline banner (§8 geometry) between the pinned header and the question; scrolls
with the body. Icon 24dp, title `type.titleSmall`, body `type.bodyMedium`.

| Variant | Container / content | Icon | Text |
| --- | --- | --- | --- |
| All-in (round 7) | `scheme.tertiaryContainer` / `scheme.onTertiaryContainer` | `keyboard_double_arrow_up` | `duel_all_in_title`, `duel_all_in_body` |
| Sudden death | `scheme.inverseSurface` / `scheme.inverseOnSurface` | `bolt` | `duel_sudden_death_title`, `duel_sudden_death_body` |

Expanded in the question phase; from the next phase on it collapses
(`motion.duration.short4`) and only the top-bar pill (All-in) or the round label
(sudden death) remains. Announced once (polite).

## 20. Calibration rows and card

Used by the match result (Əminlik kartı, DUEL-21) and the profile (PROF-05).
White game card (§0 without press): `scheme.surfaceContainerLowest`, 2dp
`game.cardBorder`, depth `game.cardDepth`, `shape.component.calibrationCard`,
padding `space.lg`. Title `type.titleMedium`, optional subtitle `type.bodyMedium`
`scheme.onSurfaceVariant`, then 3 rows (level 1, 2, 3) with gap `space.md`, then a
hint and a footnote.

Row (stacked so nothing truncates at 1.3):
- Line 1: mini Əminlik badge (§16) — `space.md` — column: level label
  `duel_calibration_level` `type.titleSmall` `scheme.onSurface`; value
  `type.bodyMedium` `scheme.onSurfaceVariant` (card: `duel_calibration_value` "2 / 3
  düzgün" or `duel_calibration_unused` "—"; profile: `profile_calibration_value`,
  `profile_calibration_few`, `profile_calibration_never`).
- Line 2 (aligned with the text column): accuracy bar, height
  `size.calibrationBarHeight`, `shape.full`, track `scheme.surfaceContainerHighest`,
  fill `scheme.primary` = correct / count. Track only for "—", "Az məlumat" and
  "Hələ seçilməyib".
- One element for TalkBack: `duel_calibration_row_cd` / `profile_calibration_row_cd`
  (`duel_calibration_unused_cd`, `profile_calibration_row_few_cd`).

Hint (`space.lg` above): 20dp `lightbulb` icon + `type.bodyMedium` `scheme.onSurface`,
one of `duel_calibration_hint_*` chosen by the rule in `duel-result.md`. Footnote:
`type.bodySmall` `scheme.onSurfaceVariant`.

## 21. Match history row (profile)

Material 3 `ListItem` (§14), three-line, not clickable (PROF-04):
- Leading: avatar `size.avatar` (deleted-player and bot variants, §13).
- Headline: opponent name `type.bodyLarge`, 1 line, ellipsis. Bot match: the name
  from the API (fallback `duel_bot_name`), `space.sm`, Bot badge (§32, inline);
  the name gives way (ellipsis), the badge never does.
- Supporting line 1: `profile_match_supporting` = result word · score
  ("Qələbə · 640 : 410"), `type.bodyMedium`; the result word is coloured
  `game.correct` (win), `game.wrong` (loss), `scheme.onSurfaceVariant` (draw,
  abandoned); the rest `scheme.onSurfaceVariant`.
- Supporting line 2 (optional): `type.labelMedium` `scheme.onSurfaceVariant`:
  `profile_match_label_opponent_left`, `profile_match_label_opponent_missed_rounds`,
  `profile_match_label_self_left`, `profile_match_label_self_missed_rounds`
  (fallbacks without a cause: `profile_match_label_opponent_forfeit`,
  `profile_match_label_self_forfeit`), `profile_match_label_sudden_death`.
- Trailing column, end-aligned: rating change `type.titleSmall` tabular
  (`game.correct` +, `game.wrong` −, `scheme.onSurfaceVariant` 0), date
  `type.labelSmall` `scheme.onSurfaceVariant`. Bot match: `profile_match_rating_none`
  ("—") in `scheme.onSurfaceVariant`, never "0" or "+0" (DUEL-28).
- Merged for TalkBack: `profile_match_cd` (bot match: `profile_match_bot_cd`).

## 22. Duel overlay card (self reconnecting)

Scrim `scheme.scrim` at 0.32 over the match **body** only (the pinned top bar and
player panels stay visible and keep updating). Centred card: container
`scheme.surfaceContainerHigh`, `shape.extraLarge`, elevation
`elevation.component.duelOverlayCard`, padding `space.xl`, max width
`space.layout.maxContentWidth`. Content: 48dp progress indicator or 48dp
`cloud_off` icon in `scheme.onSurfaceVariant`, `space.lg`, title `type.titleMedium`
`scheme.onSurface`, `space.sm`, body `type.bodyMedium` `scheme.onSurfaceVariant`,
`space.xl`, buttons full width (gap `space.sm`). Not dismissible by scrim tap; back
opens the leave dialog. Focus moves into the card; announced once (assertive).

## 23. Stat rows (profile record)

White game card (§0 without press), `shape.large`, padding `space.lg`. Header row:
title `type.titleMedium` + trailing total `type.labelLarge`
`scheme.onSurfaceVariant`. Then 3 rows (min height 40dp): 20dp icon (win
`emoji_events` `game.correct`, loss `close` `game.wrong`, draw `drag_handle`
`scheme.onSurfaceVariant`), `space.md`, label `type.bodyLarge` `scheme.onSurface`
(full width, never truncated), trailing count `type.titleMedium` tabular. The card
is one TalkBack element (`profile_record_cd`). No win rate in the MVP (PROF-03).
Rows and total count **real-player matches only** (OQ-110).

**Bot line** (only when the player has at least one counted bot match): `space.md`,
1dp divider `scheme.outlineVariant`, `space.md`, row: 20dp `smart_toy` icon
`scheme.onSurfaceVariant`, `space.md`, `profile_record_bot` ("Bota qarşı: 3 qələbə,
2 məğlubiyyət, 0 heç-heçə", built from the plurals `profile_record_bot_wins` /
`_losses` / `_draws`) `type.bodyMedium` `scheme.onSurfaceVariant`, wraps (2 lines at
1.3 are expected). Not a fourth stat row on purpose: it must not read as part of the
record. Its text is appended to the card's TalkBack label.

**Real record empty, bot line present** (played only against the bot, DUEL-28): the
three rows and the total are replaced by `profile_record_empty_real` ("Hələ
oyunçulara qarşı oynamamısan.") `type.bodyMedium` `scheme.onSurfaceVariant`; the bot
line follows without the divider. TalkBack: the two texts in order.

---

Components 24–31 were added with tokens 2.1.0 for the retention features: daily
challenge (`daily-challenge.md`), streak / XP / levels (`progression.md`) and
notifications (`notifications.md`). They reuse §0 and existing roles; the only new
colour roles are `game.streak`, `game.streakContainer`, `game.onStreakContainer`
and `gradient.dailyHero`. Visual rules for all of them: playful, never casino —
no coins, chips, gems, gold, treasure chests, slot reels, "jackpot" counters or
falling confetti. The flame is the only streak symbol; XP is a plain number and a
bar.

## 24. Streak badge (home header) — removed in v3

Replaced by the streak chip in the home top row (§35) and the streak section of
the Gündəlik tab (`daily-tab.md` B). The state rules (active / not yet today /
zero / unconfirmed cache / hidden / skeleton) carry over to §35 unchanged.

## 25. Level chip and mini XP bar — moved to the Profil header in v3

Same content, now under the name in the Profil header (`profile.md`). v3 look:
chip `shape.component.levelChip`, face `scheme.surfaceContainerHigh`, text
`progress_level` `type.labelMedium` `scheme.onSurface`; `space.sm`, bar
`size.xpBarMiniHeight` (4dp), track `scheme.surfaceContainerHighest`, fill
`scheme.primary`. If < 48dp is left for the bar, it moves under the chip at full
width. Decorative bar; the level is read in text.

## 26. Progress card (XP, level, streak on result screens)

Shared by the quiz result, the daily result and the duel result (PROG-02, PROG-06).
White game card (§0 without press): `scheme.surfaceContainerLowest`, 2dp
`game.cardBorder`, depth `game.cardDepth`, `shape.component.progressCard`, padding
`space.lg`. Content top to bottom:

1. **XP row** (only when `xpGained > 0`): 24dp `star` icon (filled) in
   `scheme.primary`, `space.sm`, `progress_xp_gained` ("+70 XP")
   `type.headlineSmall` `scheme.primary`, tabular. Pops in
   (`motion.progression.xpGainPop`).
2. `space.md`, **level row**: `progress_level` `type.titleSmall` `scheme.onSurface`
   at the start; at the end `progress_xp_into_level` ("270 / 500 XP")
   `type.labelMedium` `scheme.onSurfaceVariant`, tabular. If both do not fit on one
   line (font scale), the XP text wraps under the level label.
3. `space.sm`, **XP bar**: full width, `size.xpBarHeight`, `shape.full`, track
   `scheme.surfaceContainerHighest`, fill `scheme.primary` with the 3dp highlight
   stripe of §4. Animates from the old to the new fraction
   (`motion.progression.xpBarFill`).
4. **Level-up strip** (only when `levelAfter > levelBefore`), `space.md` above:
   `scheme.primaryContainer`, `shape.medium`, padding `space.md`, row: 24dp
   `arrow_circle_up` in `scheme.onPrimaryContainer`, `space.sm`,
   `progress_level_up` ("Yeni səviyyə: 5!") `type.titleMedium`
   `scheme.onPrimaryContainer`, wraps. Enters per `motion.progression.levelUp`.
5. **Category level-ups** (only for entries in `categoryLevelUps[]`), `space.sm`
   gap: row with a 24dp rounded square in `category.<slot>.container` holding the
   16dp category icon in `category.<slot>.onContainer`, `space.sm`,
   `progress_category_level_up` ("Coğrafiya: səviyyə 3") `type.bodyMedium`
   `scheme.onSurface`, wraps.
5a. **Bot note** (duel result of a bot match, only when the XP row is shown),
   `space.xs` under the XP row: `progress_xp_bot_note` ("Bota qarşı oyunda yarım XP
   qazanırsan.") `type.bodyMedium` `scheme.onSurfaceVariant`, wraps. The number in
   the XP row is the server's value ("+25 XP"); the client never halves anything
   (DUEL-27). Appended to `progress_card_cd`.
6. **Cap note** (only when the server says XP was capped for this game type today,
   PROG-09), replaces the XP row: 20dp `info` icon + `progress_xp_capped`
   `type.bodyMedium` `scheme.onSurfaceVariant`, wraps. Shown once per Baku day per
   game type (client remembers the day key).
7. `space.md`, divider `scheme.outlineVariant`, `space.md`, **streak row**: 24dp
   flame (`game.streak` filled if `streakExtendedToday` or the day is already
   active, else outlined `scheme.onSurfaceVariant`), `space.sm`, `progress_streak_days`
   ("Seriya: 8 gün") `type.titleSmall` `scheme.onSurface`; when
   `streakExtendedToday`, a trailing chip `progress_streak_extended` ("+1 gün")
   `type.labelMedium` `game.onStreakContainer` on `game.streakContainer`,
   `shape.full`, and the flame ignites once (`motion.progression.streakIgnite`).
   Hidden when `streakCurrent` is not in the response.

| State | Card |
| --- | --- |
| `progress` not yet received (finish / `match.result` running, < 2 s) | Not rendered. Nothing is reserved; when it arrives the card expands in (`motion.duration.medium2`, `emphasizedDecelerate`). The client never computes XP. |
| Offline / `finish` failed | Not rendered (the screen shows its "not synced" banner). Rendered later if a retry succeeds while the screen is open. |
| XP 0, not capped (forfeit, 0 correct quiz) | XP row hidden, level row + bar static, streak row as usual (PROG-02: never "+0 XP"). |
| XP 0, capped | Cap note instead of the XP row. |
| Level-up | Strip shown once; a reopened result (duplicate `match.result`, retried `finish`) shows the final state without the animation and without repeating analytics. |
| Duplicate result | Final state, no animation. |
| Reduced motion | Final values, strip without scale. |

Accessibility: the card is one element with `progress_card_cd` ("70 XP qazandın.
Səviyyə 5, növbəti səviyyəyə 230 XP qalıb."), followed by `progress_level_up_cd` and
`progress_streak_extended_cd` when present (polite live region, announced once).

## 27. Result squares (daily challenge)

One square per daily question, in order. Used large on the daily result share card
(`size.resultSquare`, 40dp, glyph 20dp, gap `space.sm`) and compact on the home
daily card (`size.resultSquareCompact`, 24dp, glyph 14dp, gap `space.xs`). Shape
`shape.component.resultSquare`.

| Result | Face | Glyph | Border |
| --- | --- | --- | --- |
| Correct | `game.correct` | `check` in `game.onCorrect` | — |
| Wrong | `game.wrong` | `close` in `game.onWrong` | — |
| No answer (timeout, deadline) | `scheme.surfaceContainerHighest` | `remove` (dash) in `scheme.onSurfaceVariant` | 2dp `scheme.outline` |
| Not confirmed (answered offline, result unknown) | `scheme.surfaceContainerHigh` | `cloud_upload` in `scheme.onSurfaceVariant` | 2dp dashed `scheme.outline` |

The large variant shows the question number ("1"…"5", `type.labelSmall`
`scheme.onSurfaceVariant`) under each square. The row is one TalkBack element:
`daily_squares_cd` ("Sual 1 düzgün, sual 2 səhv, …", built from
`daily_square_correct_cd` / `_wrong_cd` / `_none_cd` / `_pending_cd`). Enter:
`motion.progression.resultSquaresEnter`. Glyphs are never removed: colour is never
the only signal (the share text uses 🟩 🟥 ⬜, which is outside the app).

## 28. Daily challenge card (home) — removed in v3

The card moved to the Gündəlik tab as the flat challenge card (§37,
`daily-tab.md` A). No gradient face, no "white face" variant: one flat card with
all states. The state list of v2 is carried over 1:1 in `daily-tab.md`.

## 29. Streak message (v3, Gündəlik tab)

Moved from under the home header to the streak section of the Gündəlik tab
(`daily-tab.md` B). Same variants, priority (streak ended → freeze used → hint),
dismiss rules and live-region behaviour; geometry of the full §8 banner with a
trailing 48dp close icon (`common_close`):

| Variant | Container / content | Icon |
| --- | --- | --- |
| Streak hint | `game.streakContainer` / `game.onStreakContainer` | outlined `local_fire_department` in `game.streak` |
| Freeze used | `scheme.secondaryContainer` / `scheme.onSecondaryContainer` | `shield` |
| Streak ended | `scheme.surfaceContainerHigh` / `scheme.onSurface` | `restart_alt` |

No red, no warning triangle, no sad faces.

## 30. Notification permission card (result screens)

In-app explanation before the Android 13+ system dialog (NOTIF-01, OQ-090). White
game card (§0 without press) placed **below** the score and progress cards on the
quiz or daily result, never above or over them. Padding `space.lg`:
1. Row: 48dp circle `scheme.primaryContainer` with 24dp `notifications_active` in
   `scheme.onPrimaryContainer`, `space.md`, column: title `notif_prompt_title`
   `type.titleMedium` `scheme.onSurface`; `space.xxs`, body `notif_prompt_body`
   `type.bodyMedium` `scheme.onSurfaceVariant` (wraps).
2. `space.lg`, row of two buttons, end-aligned: text button `notif_prompt_later`
   ("Sonra"), `space.sm`, primary pressable button with compact height (face 44dp
   + depth 4dp = 48dp touch target) `notif_prompt_allow` ("İcazə ver"). If both do
   not fit side by side (font scale), they stack: primary full width on top, text
   button under it.

States: default → "İcazə ver" opens the system dialog → granted: the card collapses
(`motion.duration.short4`) and the snackbar `notif_enabled_snackbar` appears;
denied: the card collapses silently (NOTIF-01). Permanently denied (known before the
tap): the primary button reads `notif_open_settings` ("Ayarları aç") and opens the
app's system notification settings; on return the card re-checks and collapses if
granted. "Sonra" or back: the card collapses, nothing else. Not dismissible by
swiping. Accessibility: one traversal group, title as heading, buttons separate.

## 31. Switch row (settings)

Material 3 `ListItem` (§14) with a trailing `Switch`; the **whole row** is the toggle
(`Modifier.toggleable`, role Switch), so the 48dp target is the full row. Headline
`type.bodyLarge` (max 2 lines), supporting `type.bodyMedium`
`scheme.onSurfaceVariant` (wraps). Switch colours: Material 3 defaults from the
scheme (checked track `scheme.primary`, thumb `scheme.onPrimary`; unchecked track
`scheme.surfaceContainerHighest`, border/thumb `scheme.outline`). The switch is
vertically centred on the headline block and never pushed out by long text.

| State | Visual |
| --- | --- |
| On / Off | Standard |
| Saving | Switch shows the new value at once; no spinner (optimistic, NOTIF-06) |
| Save failed / offline | Switch animates back; snackbar with the reason |
| Disabled (blocked by the phone, channel off, loading) | M3 disabled colours; supporting text explains why (`settings_notif_channel_off`) — the reason is always written, never only greyed out |
| Loading (first load, no cache) | Switch replaced by a 20dp progress indicator, row not toggleable |

---

Component 32 was added with the bot opponent (2026-10-30, OQ-106 … OQ-111,
DUEL-23 … DUEL-29). It reuses existing roles; no new colour token.

## 32. Bot badge — `BotBadge`

Marks the bot opponent wherever its name or panel appears, so a bot is never taken
for a person (DUEL-24). Not interactive, not focusable on its own (the surrounding
element reads `duel_opponent_bot_cd` / `profile_match_bot_cd`).

**Visual**: pill, `shape.full`, min height 20dp (grows with font scale), padding
`space.sm` horizontal × `space.xxs` vertical, content centred: 14dp `smart_toy`
icon (Material Symbols Rounded, filled), `space.xs`, `duel_bot_badge` ("Bot")
`type.labelMedium`. Container `scheme.tertiaryContainer`, icon and text
`scheme.onTertiaryContainer` (the pair is in `tools/contrast.py`, both themes pass
4.5:1). Tertiary because it belongs to the opponent side (opponent accent =
`scheme.tertiary`, §13); it is a type label, so it has no depth edge and no press
state. Never truncated, never shrunk by a `weight`: neighbouring text ellipsizes
first. Width ≈ 57dp at font scale 1.0, ≈ 64dp at 1.3 (the icon does not scale).

Rules: plain robot-head symbol and the word "Bot" only — no chips, coins, dice,
cards, crowns, stars or "AI" sparkle; no animation (it appears with its parent).
The badge keeps its colours on every background it sits on (white game card, reveal
face `game.correctContainer` / `game.wrongContainer`, `gradient.resultWin`): the
pill is opaque and its text is checked against its own container only.

| Placement | Where | Layout |
| --- | --- | --- |
| Inline | Intro opponent row (`duel-intro.md`), history row headline (§21) | Same line as the name, `space.sm` after it, vertically centred on the first text line; the name ellipsizes, the badge stays. |
| Stacked | Result score column (`duel-result.md`) | Own line `space.xxs` under the name, centred (the ~130dp column cannot hold both at 1.3). |
| Panel | Opponent player panel in every phase (§16, `duel-match.md`, `duel-reveal.md`) | Replaces the name text in the name slot, start-aligned. |

Width check (360dp, font scale 1.3): intro name column 240dp — "Duello Bot"
`type.titleMedium` ≈ 130dp + 8 + 64 = 202dp, one line. History headline 264dp —
≈ 124dp + 8 + 64 = 196dp, one line. Panel name slot 84dp — badge 64dp, fits.
Result column — stacked, fits.

---

## v3 restyle of the remaining components

These keep their anatomy, states and behaviour; only the look changes:

| § | Component | v3 change |
| --- | --- | --- |
| 2 | Text field | `shape.small` (8dp), face `scheme.surfaceContainerLowest`, 1dp `scheme.outline` (focused 2dp `scheme.onSurface`, no glow ring); error unchanged |
| 9 | Dialogs | Container `scheme.surface`, `shape.extraLarge` (18dp), title serif `type.headlineSmall` |
| 10 | Snackbars | `shape.small`; ink in light / paper in dark (`inverseSurface`); action `scheme.inversePrimary` |
| 13 | Avatar | Letter fallback on `scheme.surfaceContainerHigh` with the letter in serif `type.titleMedium` `scheme.onSurface` (was tertiary). Bot avatar unchanged. Duel accent rings: self `scheme.primary`, opponent `scheme.tertiary` (plum) |
| 14 | List rows | Unchanged; section headers become the overline (§35a) |
| 15 | Duel top bar / stepper | Segments 4dp, done/current `scheme.onSurface` (ink), upcoming `game.timerTrack` |
| 16 | Duel player panel | Flat: face `scheme.surface`, 1dp border in the player accent, no depth; reveal faces as before without depth |
| 17–19 | Status pill, confidence panel, round banner | Tokens only |
| 20, 23, 26, 30 | Calibration card, stat rows, progress card, permission card | Flat cards (§0); category icon squares in the progress card use `scheme.surfaceContainerHigh` / `scheme.onSurfaceVariant` (category colours removed); XP bars thin (`size.xpBarHeight` 6dp, no highlight stripe); "+70 XP" stays `scheme.primary` |
| 21 | Match history row | Tokens only |
| 22 | Duel overlay card | `scheme.surface`, `shape.extraLarge` |
| 27 | Result squares | Kept for the daily result share card; same glyph rules; `shape.extraSmall` |
| 31 | Switch row | M3 switch from the scheme: checked track `scheme.primary` |
| 32 | Bot badge | Tokens only (plum `tertiaryContainer`) |

## 33. Bottom navigation bar — `DuelloNavigationBar`

Three destinations, shown only on the three tab roots (Oyna, Gündəlik, Profil);
every full-screen flow (quiz, daily play / result, duel, settings, licences,
delete account, auth) hides it.

- Container `scheme.surfaceContainer`, height `size.bottomBarHeight` (64dp) +
  system navigation inset below, 1dp `game.cardBorder` hairline on top, no
  shadow.
- Three equal items (≥ 120dp wide on 360dp). Item, stacked and centred: indicator
  pill `size.navIndicatorWidth` × `size.navIndicatorHeight` (56 × 28dp,
  `shape.full`) holding the 24dp icon; `space.xxs`; label `type.labelMedium`, 1
  line (all labels fit at 1.3: "Gündəlik" 74dp of 104dp).

| Item | Label | Icon unselected / selected | Content description extra |
| --- | --- | --- | --- |
| Oyna | `nav_play` | `swords` outlined / filled | — |
| Gündəlik | `nav_daily` | `calendar_today` outlined / filled | `nav_daily_badge_cd` when the dot shows |
| Profil | `nav_profile` | `person` outlined / filled | — |

| State | Indicator | Icon + label |
| --- | --- | --- |
| Selected | `game.navIndicator` | `scheme.onSurface`, filled icon, label Bold 700 |
| Unselected | none | `scheme.onSurfaceVariant`, outlined icon |
| Pressed | ripple on the indicator area | — |

**Badge dot** (Gündəlik only): `size.navBadgeDot` (8dp) circle `scheme.primary`
at the icon's top-end corner (offset 2dp), with a 2dp ring of the bar colour.
Shown when today's daily is `available` or `in_progress` (`daily-tab.md`);
appears / disappears with a 150ms fade. Not a count.

Behaviour: tabs keep their own back stack and scroll position (Navigation Compose
`saveState` / `restoreState`); re-selecting the current tab scrolls it to the
top; switching uses fade-through 200ms (`motion.game.screenTransition`). System
back on Gündəlik / Profil → Oyna; on Oyna → exit. Accessibility: M3
`NavigationBarItem` semantics (role Tab, selected state); the badge is read as
part of the item label.

## 34. Sticker pill — `Sticker`

The only rotated element in the app; used **only** for the rating on the home
duel card. Pill `shape.component.sticker`, min height `size.stickerMinHeight`
(28dp), padding `space.md` × `space.xs`, face `scheme.primary`, text
`home_duel_sticker_rating` `type.labelMedium` `scheme.onPrimary` tabular,
rotation `size.stickerRotationDeg` (−4°) around its centre, no shadow. Settles
once (`motion.game.stickerSettle`). Not interactive, not focusable (its text is
part of the duel card's content description). Never truncated: it grows.
Never reused for prices, rewards or counts of anything collectible (no casino
"badge" look).

## 35. Streak chip (home top row)

Row: 20dp flame (`local_fire_department`) + `space.xs` + number
`type.titleSmall` tabular, both `game.streak`; pill `shape.component.streakBadge`
with no fill; min width `size.streakBadgeMinWidth`, touch target 48 × 48dp,
ripple. States (from v2 §24): active today = filled flame; not yet today / zero /
unconfirmed cache = outlined flame in `game.streak`, number `game.streak`;
freeze held = 12dp `shield` in `scheme.secondary` after the number; hidden
without progress and cache; skeleton 48 × 24dp bar. Tap → Gündəlik tab. Ignite
once per Baku day (`motion.progression.streakIgnite`).

## 35a. Section overline

`typography.custom.overline` (11sp, ExtraBold, letter-spacing 1.4sp)
`scheme.onSurfaceVariant`, 1 line at 1.0 (wraps if needed, never truncated),
heading semantics. Strings are authored in capitals in `az.md` (never uppercased
at runtime). Used: home category section, Gündəlik sections, Profil sections,
settings section headers.

## 36. Greeting with squiggle underline

Two lines in `type.headlineSmall` (Fraunces SemiBold) `scheme.onSurface`
(`home.md`). Line 2 comes from `home_greeting_N`; its string contains one
`<annotation squiggle="1">…</annotation>` span (Android `getText()` keeps it as
an `Annotation` span; convert to an `AnnotatedString` range).

**Squiggle**: drawn under that range with `TextLayoutResult.getPathForRange` /
the range's bounding box: a stroked path, colour `scheme.primary`, width
`size.squiggleStroke` (2.5dp, round caps), baseline offset
`size.squiggleOffsetBelowBaseline` (4dp), a sine-like wave with wavelength
`size.squiggleWavelength` (11dp) and amplitude `size.squiggleAmplitude` (2.5dp),
started and ended 2dp outside the word, with a fixed small irregularity (±0.4dp
per crest from a seed = dayKey) so it looks hand-drawn but is stable for the
day. Reference shape: `design/brand/squiggle-underline.svg`. Draws in once per
app start (`motion.game.squiggleDraw`). Decorative (not in semantics).

Where hand-drawn touches are allowed (the full list; do not add more without a
spec change):

| Touch | Allowed only on |
| --- | --- |
| Squiggle underline | Home greeting (one word per day) |
| Rotated sticker | Rating on the home duel card |
| Dashed boxes / rings | Gündəlik answer boxes before playing, today's week dot, queued (offline) answer states |
| Serif numerals | Home category numbers, "#12" and the score on the Gündəlik tab, big scores on result screens, the Əminlik tile numerals |

Outside the app UI, the squiggle is also used once on the Play feature graphic
(under one tagline word, `design/brand.md` do / don't); the brand mark itself has
no hand-drawn touches.

## 37. Daily challenge card v3 (Gündəlik tab)

Flat card (§0): face `scheme.surface`, 1dp `game.cardBorder`,
`shape.component.dailyCard` (16dp), padding `space.lg`. Content and all states:
`daily-tab.md` A. Primary pill "Başla" / "Davam et"; finished: countdown row +
secondary pill "Paylaş". Same minimum height in every state. The card itself is
not a touch target in the finished state (only its button).

## 38. Answer boxes (Gündəlik)

Row of 5 rounded squares `size.resultBox` (44dp, `shape.component.resultBox`),
gap `space.sm`; compact `size.resultBoxCompact` where needed.

| State | Face | Border | Glyph |
| --- | --- | --- | --- |
| Not played yet | none | 1.5dp **dashed** `scheme.outline` (dash 4dp, gap 3dp) | — |
| Correct | `game.correct` | — | 20dp `check` `game.onCorrect` |
| Wrong | `game.wrong` | — | 20dp `close` `game.onWrong` |
| No answer | `scheme.surfaceContainerHighest` | 1dp `scheme.outline` | 20dp `remove` `scheme.onSurfaceVariant` |
| Not confirmed (offline) | none | 1.5dp dashed `scheme.outline` | 18dp `cloud_upload` `scheme.onSurfaceVariant` |
| Result unknown (OQ-D28 missing) | `scheme.surfaceContainerHighest` | — | — (decorative; score is in text) |

Fill left to right after a result (`motion.progression.resultSquaresEnter`).
Not interactive. TalkBack: part of the card node, or `daily_squares_cd`.

### 38a. Week row (Gündəlik streak section)

7 equal cells (Mon → Sun of the current Baku week), each ≥ 40dp; dot
`size.weekDot` (28dp, `shape.full`) over the day abbreviation
(`common_weekdays_short`, `type.labelSmall`). Dot states and glyphs:
`daily-tab.md` B (active = saffron fill + ring + check, today pending = dashed
saffron ring, freeze = blue-ink fill + ring + shield, missed = outline ring +
dash, future = hairline ring). Data: each dot is the server `week[].state`
(`GET /v1/me/progress`, API 0.4.0) 1:1 — `active`, `today_pending`, `freeze`,
`missed`, `none`; the client never infers a state. Fallback for an old cached
response without `week` only: active / today pending / none derived from
`streakCurrent` + `todayActive`, never `freeze` or `missed` (not from the
`freeze_used` / `streak_lost` events either; `daily-tab.md` OQ-D35, PROG-11). One
TalkBack element (`daily_tab_week_cd`). Not interactive.


---

## Mock exams "Sınaqlar" (tokens 3.1.0, 2026-11-21)

Components §39 … §46 serve `exam-catalogue.md`, `exam-info.md`,
`exam-attempt.md`, `exam-self-assessment.md`, `exam-result.md` and
`exam-review.md` (EXAM-01 … EXAM-25, Phase 1). They add **no colour roles**;
every new pair is in `design/tools/contrast.py` (section "Mock exams"). Reused
as they are: buttons §1, text field §2 (as the base of §43), option card §3
(with the exam addendum below), timer bar §4, banners §8, dialogs §9,
snackbars §10, state views §11, top app bar §12, list / switch rows §14 / §31,
section overline §35a, progress card streak row §26. Copy keys: `copy/az.md`
"exam_".

### 3a. Option card — exam addendum

- Letters come from the task data: **A–E**, 2–5 options (OQ-113); TalkBack
  `exam_option_cd` ("A variantı: …"), not the quiz ordinal keys.
- **Chosen** state (exam only; no server round-trip): exactly the visual of
  "Selected (pending)" (ink 2dp border, `game.pendingContainer`, ink letter
  badge) **without** the trailing progress indicator; state description
  `exam_option_selected_cd`. Tapping another option moves the choice at once
  (last tap wins, EXAM-06); tapping the chosen option again does nothing (clearing
  is the explicit `exam_clear_answer` text button, so a stray double tap never
  blanks an answer).
- Correct / Wrong / Dimmed states are used only in the review (`exam-review.md`),
  never during an attempt (EXAM-06: no feedback before submit).
- Read-only (after the deadline or submit): Chosen / Default look kept, ripple
  and clicks off, content not dimmed (still readable).

## 39. Exam timer pill — `ExamTimerPill`

Same anatomy as the quiz timer pill (§4): `shape.full`, face `scheme.surface`
(on the navigator sheet: transparent on `scheme.surfaceContainerLow`), 1dp
`scheme.outline`, padding `space.md` horizontal, height 32dp inside a 48dp touch
target, 18dp `timer` icon + `space.xs` + digits `type.titleMedium` **tabular**
in `game.timerNormal`.

- Format: `m:ss` below 1 hour ("59:59", "4:07"), `h:mm:ss` from 1 hour (Phase 2,
  180 min). Width reserved for the widest value of the attempt (`59:59` →
  91dp at 1.0 / 105dp at 1.3) so the header never shifts.
- Source: `endsAtEpochMs` − server-offset clock (DUEL-12 method), refreshed
  every second by the wall clock, never by animation frames; the 2-min grace is
  **not** displayed (OQ-D41).
- Warning at ≤ `motion.exam.timerWarningThresholdMs` (10 min): border, icon and
  digits `game.timerWarning`. **No pulse** (a 10-minute pulse is stress, not
  information). Expired: "0:00" in `game.timerWarning`.
- Tap: announces `exam_timer_cd` ("Qalan vaxt: 42 dəqiqə") once; visual
  unchanged. Not a live region (EXAM-22: announced on demand; the 30/10/1-min
  notices are separate, `exam-attempt.md`).
- Thin **time line** under the exam header: §4 bar, fraction = remaining /
  duration, warning colour like the pill.

## 40. Task navigator — `ExamTaskNavigator`

Opened from the header grid button as an M3 **modal bottom sheet** expanded to
full height (`shape.component.bottomSheet`, container
`scheme.surfaceContainerLow`, drag handle). Layout:

1. Sheet header row (48dp): title `exam_grid_title` `type.titleLarge`, weight 1;
   the **timer pill §39** (the timer stays on screen while the sheet covers the
   header); close icon button `common_close`.
2. `space.sm`, summary `exam_grid_summary` ("Cavablanıb: 12 · Cavabsız: 15 ·
   İşarələnib: 3") `type.bodyMedium` `scheme.onSurfaceVariant`, wraps.
3. `space.md`, legend row (wraps): four 16dp mini cells + `type.bodySmall`
   labels (`exam_grid_legend_*`). Decorative for TalkBack (each cell says its
   state).
4. Per section, in exam order: section name `type.titleSmall` `scheme.onSurface`
   + `space.xs` + task range ("7–22") `type.bodySmall` `scheme.onSurfaceVariant`
   (sentence case from the server — never an overline, which would need runtime
   uppercasing); `space.sm`; a flow grid of cells, `size.examGridColumns` (6)
   per row, `size.examTaskCell` (48dp) squares, gap `space.sm`; `space.lg`
   before the next section. A task group (reading / listening) is not a separate
   heading: its section is the group.
5. Sticky bottom: primary pill `exam_finish` ("Sınağı bitir"), full width,
   `space.lg` padding + navigation inset.

Cell (`shape.component.examTaskCell`), number `type.titleSmall` tabular, centred:

| State | Face | Border | Number | Extra glyph |
| --- | --- | --- | --- | --- |
| Not answered | `scheme.surface` | 1dp `scheme.outline` | `scheme.onSurface` | — |
| Answered (closed option chosen) | `scheme.inverseSurface` | — | `scheme.inverseOnSurface` | — |
| Written, text entered | `scheme.inverseSurface` | — | `scheme.inverseOnSurface` | 12dp `edit` under the number, `scheme.inverseOnSurface` (cell content stacks; still 48dp) |
| Written, empty | as Not answered | | | — (an empty written task is legal, EXAM-11) |
| Flagged (any of the above) | unchanged | unchanged | unchanged | **Flag badge** at the top-end corner, centred on the corner: `size.examFlagBadge` circle in the sheet colour with a 12dp filled `flag` in `scheme.primary` |
| Current task | unchanged | + 2dp `scheme.primary` ring, 2dp outside the cell | Bold | — |
| Skipped listening (OQ-119) | not rendered: the section shows `exam_grid_skipped` text instead of cells | | | |
| Read-only (after the deadline) | unchanged | | | taps still open the task read-only |

Never colour alone: answered vs not = filled vs outlined; flag = glyph; current
= ring + bold + TalkBack "hazırda açıqdır". Tap a cell → sheet closes →
that task opens (`motion.exam.taskTransition`, fade-through). Accessibility: each
cell one node, role Button, label `exam_grid_cell_cd` ("Tapşırıq 14, cavabsız,
işarələnib"); section name is a heading; traversal follows the visual order.

## 41. Passage panel — `ExamPassage`

Page text, no container (`shape.component.passagePanel`), inside the task body
scroll (reading groups, `exam-attempt.md` B3; review and self-assessment).

- Optional overline `exam_passage_overline` ("OXU MƏTNİ"), then title from the
  data `type.titleMedium`, then paragraphs `type.bodyLarge` `scheme.onSurface`,
  `space.md` between paragraphs, max width `space.layout.maxContentWidth`.
- **Paragraph numbers** (when tasks refer to them): in a start gutter of
  `size.examPassageGutter`, `type.labelMedium` tabular `scheme.onSurfaceVariant`,
  top-aligned with the paragraph's first line. Line numbers are not used in
  Phase 1 (they break with font scaling; tasks refer to paragraphs).
- **Restricted markup** (OQ-116): bold, italic, underline, sub/superscript.
  Marked words (a task refers to them) = **underline 1.5dp** in
  `scheme.onSurface`, 2dp below the baseline **plus** Bold — two cues, readable in
  both themes, no colour. TalkBack reads them with `exam_marked_word_cd`
  ("altından xətt çəkilmiş söz: …", via a `TtsSpan`/custom semantics on the span).
- **Gap** token in a task stem (`{gap}` in the data, e.g. "She ___ to school"):
  rendered as a 40dp-wide 1.5dp underline in `scheme.onSurface`; TalkBack
  `exam_gap_cd` ("boşluq").
- 3 000 characters at font scale 1.3 on 360dp ≈ 95 lines ≈ 2 950dp: scrolls,
  never truncates; the scroll position is stored per group (survives task
  changes, rotation and process death — save the first visible character
  offset, not pixels, so a font-scale change still lands on the same text).
- Text selection: off (no copy-out during an attempt); on in the review.
- ≥ 600dp: shown beside the task (split view, `exam-attempt.md` B3).

## 42. Listening player — `ExamAudioPlayer`

Flat card (§0): face `scheme.surface`, 1dp `game.cardBorder`,
`shape.component.audioPlayer`, padding `space.lg`, full content width. Pinned at
the top of the body of **every task of a listening group** (one shared player
state per group). Plays the downloaded local file only (never streams,
EXAM-10).

Anatomy (360dp):

```
[ ▶ ]  Dinləmə mətni                      ● ●
56dp   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
       1:12 / 3:05            2 dəfə qalıb
       (caption line, optional)
```

1. Row: round button `size.examAudioPlayButton` (56dp, `shape.full`, face
   `scheme.primary`, 28dp icon `play_arrow` / `pause` in `scheme.onPrimary`) —
   `space.md` — column (weight 1): title `exam_audio_title` `type.titleSmall`
   `scheme.onSurface` + at the end the **play dots**: 2 × `size.examPlayDot`
   (filled `scheme.onSurface` = play still available, 1dp `scheme.outline` ring
   = used), decorative.
2. `space.sm`, **progress line** `size.examAudioProgressHeight` (4dp),
   `shape.full`, track `game.timerTrack`, fill `scheme.onSurface`. **Not
   interactive**: no thumb, no seek, no drag, no speed control (OQ-119);
   updated every 250ms (`motion.exam.audioProgress`).
3. `space.xs`, row: elapsed / total `exam_audio_elapsed` ("1:12 / 3:05")
   `type.bodyMedium` tabular `scheme.onSurfaceVariant`; end: plays text
   `type.bodyMedium` `scheme.onSurfaceVariant` (state table).
4. Optional caption line `type.bodySmall` `scheme.onSurfaceVariant` (state
   table), or an inline warning banner (§8 full, `bannerWarningContainer`) for
   failures.

A **play** = from the start to the end of the file; pause / resume inside a play
is allowed; there is no "restart". A play is **counted** once 10 s of it have
played (server rule OQ-119; the client reports position and count with the
answers).

| State | Button | Plays text | Caption / banner |
| --- | --- | --- | --- |
| Ready, 2 left | `play_arrow`, label `exam_audio_play` | `exam_audio_plays_left` "2 dəfə qalıb" | — |
| Playing, first 10 s of a play | `pause` | `exam_audio_play_n` "Dinləmə 1 / 2" | `exam_audio_counts_after` "10 saniyədən sonra sayılacaq" |
| Playing, counted | `pause` | "Dinləmə 1 / 2" | — |
| Paused (user, phone call, audio focus loss, headphones unplugged — `ACTION_AUDIO_BECOMING_NOISY`) | `play_arrow`, label `exam_audio_resume` | "Dinləmə 1 / 2" | after an automatic pause: `exam_audio_interrupted` |
| Play ended, 1 left | `play_arrow` | "1 dəfə qalıb" | — |
| No plays left | disabled (M3 disabled colours), `play_arrow` | `exam_audio_no_plays` "Dinləmə imkanı bitdi" | — |
| Preparing (decoder start, > 300ms) | 24dp progress in the button | unchanged | `exam_audio_preparing` |
| Local file missing / corrupt → automatic re-download once | progress in the button | unchanged | `exam_audio_redownloading` |
| Re-download failed | `refresh`, label `common_retry` | unchanged (**plays not consumed**) | warning banner `exam_audio_failed` (+ `exam_audio_failed_offline` when offline) |
| Restored after app kill mid-play | `play_arrow` (paused at the saved position, OQ-D43) | "Dinləmə 1 / 2" | `exam_audio_restored` |
| Volume 0 / muted media stream on tap | plays normally | — | inline hint `exam_audio_volume_hint` (non-blocking, hides when volume > 0) |
| Listening skipped (OQ-119 choice) | player replaced by a note (§46) `exam_audio_skipped` | | |
| Attempt read-only (deadline) | disabled | unchanged | — |
| Review (after submit, OQ-D44) | free play **with** a seekable slider (M3 `Slider`) and transcript below | no counter | — |

Behaviour: only one player plays at a time; leaving the listening group does
not stop playback (the audio is the exam, the user may read tasks of the group
while listening — all six tasks show the same player). Backgrounding the app
**keeps playing** (Media3 session, media notification with play/pause only, no
seek bar: `COMMAND_SEEK_*` removed from the session's available commands).
Lock-screen / notification controls follow the same rules.

Accessibility: the button is one node: label `exam_audio_play_cd` ("Dinlə.
İstifadə olunub: 1 / 2") / `exam_audio_pause_cd`; the progress line is not
focusable; elapsed time and plays text form one node read on focus. State
changes (counted, ended, failed) are polite announcements.

## 43. Written answer field — `ExamWrittenField`

Based on the text field §2 (v3 look): label above `exam_written_label`
("Cavabın") `type.labelMedium` `scheme.onSurfaceVariant`; multiline outlined
field, `shape.component.writtenField`, min height
`size.examWrittenFieldMinHeight`, **grows with the text** (no inner scroll; the
page scrolls, IME padding keeps the caret visible), input `type.bodyLarge`,
placeholder `exam_written_placeholder`; supporting row: start
`exam_written_supporting` (wraps), end counter `exam_written_counter`
("0 / 1000") tabular.

- Max 1 000 characters (EXAM-11): input stops at the limit; the counter turns
  `scheme.error` with `exam_written_limit` as supporting text (icon + text, §2
  error rules) — the typed text is never cut.
- Keyboard: `KeyboardType.Text`, `ImeAction.Default` (new line),
  capitalization Sentences, **autoCorrect off** (exam realism; the keyboard's
  own suggestions strip may still show — not controllable), `hintLocales =
  en` when the task language is English (Android 7+ keyboards switch layout).
  Stored as typed (EXAM-11).
- Saving: locally on every change (debounced 500ms, Room) and queued for sync;
  no save button, no "saved" toast.
- Read-only after the deadline: text kept, field disabled-looking border but
  text stays `scheme.onSurface` (must stay readable).
- Phase 2 `solution` tasks: the hint `exam_written_solution_hint` above the
  field.

## 44. Mark picker — `ExamMarkPicker` (self-assessment)

Row of 5 tiles `0`, `⅓`, `½`, `⅔`, `1` (OQ-112), equal width ((content −
4 × `space.sm`) / 5 ≈ 59dp on 360dp), min height `size.examMarkTileMinHeight`,
`shape.component.markTile`, gap `space.sm`. Label `type.titleLarge` (sans — the
serif has no ⅓ ⅔ glyphs; the sans subset must include U+2153 / U+2154,
`tokens.json` 3.1.0).

| State | Face | Border | Label |
| --- | --- | --- | --- |
| Unselected | `scheme.surface` | 1dp `scheme.outline` | `scheme.onSurface` |
| Selected | `scheme.inverseSurface` (ink) | — | `scheme.inverseOnSurface` + 16dp `check` above the label |
| Disabled (saving failed hard, read-only history) | M3 disabled | | |

Single choice, `selectableGroup`; press §0 (ripple + scale + light tick);
`motion.exam.markSelect`. Under the row: `exam_assess_points`
("1,33 / 2 xal") `type.bodyMedium` `scheme.onSurfaceVariant` once a mark is
chosen. TalkBack: role RadioButton, labels `exam_mark_0_cd` … `exam_mark_1_cd`
("üçdə iki" etc.), group label `exam_assess_pick`.

At font scale 1.3 the tile stays 59dp wide (label ≤ 21dp); at ≥ 1.8 the row
wraps to 3 + 2 tiles (never shrinks below 48dp).

## 45. Score split bar — `ScoreSplitBar` (exam result)

Shows how the estimate is made (OQ-112, A2 split always visible).

- Bar `size.scoreSplitBarHeight` (12dp), `shape.component.scoreSplitBar`, full
  content width = the maximum score (100). Track `game.timerTrack`.
- Segment 1 from the start: **auto-graded** points, `scheme.onSurface` (ink).
- 2dp gap in the page colour, then segment 2: **self-assessed** points,
  `scheme.secondary` (blue ink). Only drawn when > 0.
- The rest is track: written points not assessed (or wrong).
- Legend under the bar, one row per part (full width, label start, value end,
  `type.bodyMedium` / `type.titleSmall` tabular), with a 12dp square in the
  segment colour: `exam_split_auto` "Avtomatik yoxlanılıb — 46,9 / 62,2",
  `exam_split_self` "Özün qiymətləndirdin — 20,8 / 37,8"; when written tasks are
  unassessed the second row reads `exam_split_unassessed_count`
  ("Qiymətləndirilməyib: 7 tapşırıq"). The values carry the meaning, the colours
  are supporting (and the segments differ in luminance and position).
- Motion `motion.exam.scoreSplitFill`; reduced motion: final widths.
- TalkBack: the bar and legend are one node `exam_split_cd`.

## 46. Exam note — `ExamNote`

Small static box for the disclaimer and rule notes: `shape.component.examNote`,
face `scheme.surfaceContainerLow`, no border, padding `space.md`, 20dp `info`
icon `scheme.onSurfaceVariant` + `space.sm` + text `type.bodySmall`
`scheme.onSurfaceVariant` (wraps, never truncated). Not interactive, read as
text. Used for `exam_disclaimer` (catalogue, info, result, settings
"Haqqında"), `exam_audio_skipped`, excluded-listening notes. For anything the
user must act on use a banner (§8), not this note.

### Exam rows (catalogue, history, Oyna)

Two-line rows of `size.examRowMinHeight` (64dp, grows): optional 24dp line icon
`scheme.onSurfaceVariant`, `space.md`, column (title `type.titleMedium`
`scheme.onSurface`, supporting `type.bodyMedium` `scheme.onSurfaceVariant`, both
wrap, never ellipsized), trailing chevron or status. Ripple, no scale.
Variant cards in the catalogue are flat cards (§0) with the same column inside
(`exam-catalogue.md`).

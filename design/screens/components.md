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
- Pressed: **pressable** components (§0) move down onto their depth edge, no
  ripple. Other components (list rows, icon buttons, text buttons): Material 3
  state layers (ripple) in the content colour. Focus: 3dp `scheme.primary` ring
  outside the shape (keyboard / switch access).
- Every icon-only control has a content description from `az.md`.
- Works at font scale 1.3 without truncation and stays usable at 2.0 (text wraps,
  containers grow in height, never clip).

---

## 0. Pressable surface (v2 "chunky" look) — `PressableSurface`

The base of every game control: buttons (§1), option cards (§3), category tiles
(§5), Əminlik tiles (§7), the duel hero card (§6) and white game cards.

Anatomy (bottom to top):
1. **Depth edge**: same shape as the face, colour = the component's depth role
   (`game.pressable*Depth`, `game.cardDepth`, `game.correctDepth`, …,
   `category.<slot>.depth`), offset down by `depth.<component>` (4dp buttons,
   cards, option cards; 5dp category and Əminlik tiles). Not a shadow, no blur.
2. **Face**: the container colour, optional 2dp border (`depth.border`) inside the
   shape (neutral buttons and white cards use `game.pressableNeutralBorder` /
   `game.cardBorder`).
3. Content.

Build it as one composable: a `Box` with the depth shape drawn behind
(`drawBehind`) and the face translated by `-depth + pressOffset`. The layout height
= face + depth, so nothing jumps when pressed.

| State | Face offset | Edge | Notes |
| --- | --- | --- | --- |
| Rest | 0 | visible, `depth` dp | |
| Pressed (finger down) | +`depth` dp (face covers the edge) | hidden | `motion.pressable.pressDownMs` (60 ms, `standardAccelerate`) |
| Released | springs back to 0 | visible | `motion.spring.snappy`; the click fires on release as usual and never waits for the animation |
| Disabled | +`depth` dp (flat) | none | face `scheme.onSurface` 12 %, content 38 % (M3); no border |
| Reduced motion | 0 | visible | no translation; the face shows the pressed state layer instead |

Haptics: a light tick on press-down for game inputs (option, Əminlik tile,
category tile), none for buttons (`motion.pressable.haptic`).
Accessibility: the whole surface is one node with role Button; the depth edge is
inside the touch target (≥ 48dp total height).

## 1. Buttons

All buttons: face height `size.buttonHeight` (52dp) + depth `depth.pressable`
(4dp) = 56dp, shape `shape.component.button` (medium, 16dp — a rounded
rectangle, not a pill, so the edge reads as a solid block), label
`type.labelLarge` (16sp ExtraBold), horizontal padding `space.xl`, icon
`size.iconSmall`… `size.icon` with `space.sm` gap. Single-line labels (checked in
`az.md`). Never all caps.

| Variant | Face | Label | Depth edge | Use |
| --- | --- | --- | --- | --- |
| Primary (pressable) | `game.pressablePrimary` | `game.onPressablePrimary` | `game.pressablePrimaryDepth` | The one main action per screen: "Daxil ol", "Yenidən oyna" (quiz) |
| Duel (pressable) | `game.pressableDuel` | `game.onPressableDuel` | `game.pressableDuelDepth` | Duel entry points only: "Yenidən oyna" on the duel result, "Yenidən axtar", "Duelə başla" (profile empty state) |
| Neutral (pressable) | `game.pressableNeutral` + 2dp `game.pressableNeutralBorder` | `game.onPressableNeutral` | `game.pressableNeutralDepth` | Secondary action of equal weight: "Əsas səhifə", "E-poçtla daxil ol", "Ləğv et" (search). Replaces v1 Tonal and Outlined |
| Destructive (pressable) | `game.pressableDestructive` | `game.onPressableDestructive` | `game.pressableDestructiveDepth` | "Hesabı həmişəlik sil" only |
| Text | transparent, no edge | `scheme.primary` (destructive: `scheme.error`), `type.labelLarge` | — | Low emphasis: links, dialog actions, banner actions. Ripple, not pressable |
| Google sign-in | Neutral pressable | `scheme.onSurface` + multicolour Google "G" (official asset) | `game.pressableNeutralDepth` | Welcome / sign-in. Google branding: the "G" logo is never recoloured; the label stays `scheme.onSurface` (not violet) per Google's guidelines |

Naming in screen specs: "primary button" = Primary pressable, "neutral button" =
Neutral pressable (v1 "tonal" and "outlined" both map here), "destructive
button" = Destructive pressable, "duel button" = Duel pressable. Where a duel
screen spec says "primary button" for the main duel action (`duel_result_play_again`,
`duel_search_retry`, `profile_empty_cta`), use the **Duel** variant.

Full-width buttons on phones: width = content width (360 − 2 × `space.layout.screenGutter`).
Two buttons stacked: gap `space.md`, primary on top.

States (on top of §0):
- **Loading**: label replaced by a 20dp circular progress indicator in the label
  colour; button keeps its width and stays in the rest position (no layout jump);
  not clickable; content description `common_loading`. Used for every network
  submit (double-tap guard).
- **Disabled**: flat (§0). Countdowns ("Yenidən göndər (42 san.)") keep the label
  in `scheme.onSurfaceVariant` instead of the 38 % colour (exception, the user must
  read it).

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

Pressable surface (§0): full width, face min height `size.optionCardMinHeight`
(60dp) + `depth.optionCard`, shape `shape.component.optionCard` (16dp), padding
`space.md` start / `space.lg` end / `space.md` vertical, gap between cards
`space.md`.

Content, left to right: **letter badge** (A, B, C, D — `size.optionLetterBadge`
36dp rounded square `shape.component.optionLetterBadge`, letter `type.labelLarge`),
`space.md`, option text `type.bodyLarge` (wraps, **never truncated**, up to 3+
lines), trailing **outcome word** slot (`type.labelMedium`, only after reveal).

Letters A–D are used (not numbers) so options never look like Əminlik 1/2/3 in the
duel. TalkBack still reads "1-ci variant: …" (`quiz_option_N_cd`).

| State | Face | Border | Depth edge | Text | Badge (container / glyph) | Trailing | When |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Default | `game.pressableNeutral` | 2dp `game.cardBorder` | `game.cardDepth` | `scheme.onSurface` | `scheme.primaryContainer` / letter `scheme.onPrimaryContainer` | — | Question open, timer running |
| Pressed | §0 pressed | — | hidden | — | — | — | Finger down |
| Selected (pending) | `game.pendingContainer` | 3dp `scheme.primary` | `game.pendingDepth` | `game.onPendingContainer` | `scheme.primary` / letter `scheme.onPrimary` | 20dp progress in `scheme.primary` | Tapped, waiting for server |
| Correct | `game.correctContainer` | 3dp `game.correct` | `game.correctDepth` | `game.onCorrectContainer` | `game.correct` / **check glyph** `game.onCorrect` | `quiz_option_outcome_correct` ("Düzgün") in `game.correct` | Server says correct (tapped or the right one after a wrong tap/timeout) |
| Wrong | `game.wrongContainer` | 3dp `game.wrong` | `game.wrongDepth` | `game.onWrongContainer` | `game.wrong` / **cross glyph** `game.onWrong` | `quiz_option_outcome_wrong` ("Səhv") in `game.wrong` | The user's tapped option when wrong |
| Dimmed (other options) | `game.pressableNeutral` | 2dp `game.cardBorder` | none (flat) | `scheme.onSurfaceVariant` | `scheme.surfaceContainerHigh` / letter `scheme.onSurfaceVariant` | — | After a tap or timeout, for options that are neither correct nor wrong. Text stays readable (not 38 % alpha) |
| Queued (offline) | `scheme.surfaceContainerHigh` | 2dp `scheme.outline` | `game.cardDepth` | `scheme.onSurface` | `scheme.secondary` / letter `scheme.onSecondary` | cloud-upload icon, `scheme.onSurfaceVariant` | Answer saved locally, result unknown (QUIZ-08) |

**Colour-blind safety**: correct and wrong differ in four ways besides hue — the
badge glyph (check vs cross instead of the letter), the outcome word, the motion
(pop vs shake) and luminance (crimson `game.wrong` is clearly darker/redder than the
teal-leaning `game.correct` under deuteranopia and protanopia simulation). Never
remove the glyph or the word to "clean up" the card.

Motion: colour change `motion.game.optionRevealColour`; correct card pops once
(`motion.game.optionCorrectPop`, spring `bouncy`) and its check glyph scales in;
wrong card shakes horizontally `motion.game.optionWrongShake`. Reduced motion: no
pop or shake.

Accessibility: each card is one focusable element with role Button, label
`quiz_option_N_cd`, plus state description (`quiz_option_state_*_cd`) after reveal.

**Duel use** (`duel-match.md`, `duel-reveal.md`): same card and states, plus
**picker markers** in the result phase: small avatars (20dp, accent ring 2dp, §13)
in the trailing slot, before the outcome word, showing who picked that option —
self (`scheme.primary` ring) and/or opponent (`scheme.tertiary` ring). The
opponent's option is shown in the **Wrong** state only if it is wrong and the own
choice is a different option; otherwise it only gets its marker. State
description adds `duel_option_picked_by_you_cd` / `duel_option_picked_by_opponent_cd`.
In the duel the "Selected (pending)" state never turns into correct/wrong before
the result phase (DUEL-08).

## 4. Question timer

Linear bar + numeric countdown in the quiz top bar (single-player). The duel later
reuses the same tokens with a ring variant.

- **Bar**: full content width, height `size.progressBarHeight` (12dp, chunky),
  track `game.timerTrack`, indicator `game.timerNormal`, shape `shape.full`, plus a
  decorative highlight stripe on the indicator (3dp, white at 30 %, inset 8dp from
  the start, `space.xxs` from the top) for the glossy game look. Shrinks from
  full to empty over 20 s, driven by wall-clock remaining time (not by an animation
  that could drift or pause).
- **Digits**: a **timer pill** right-aligned in the top bar: `shape.full`,
  container `scheme.surfaceContainerLowest`, 2dp border `game.cardBorder`, padding
  `space.sm` + 2dp × 6dp, 20dp `timer` icon + "17" in `type.titleMedium` with
  tabular figures, both `game.timerNormal`.
- **Warning (≤ `motion.game.timerWarningThresholdMs`, 5 s)**: bar, pill border,
  icon and digits switch to `game.timerWarning`; digits pulse once per second
  (`motion.game.timerWarningPulse`); TalkBack announces `quiz_timer_cd` at 5 s.
  Reduced motion: no pulse, colour change only.
- **Stopped** (after a tap): bar and digits freeze at the remaining value in
  `scheme.onSurfaceVariant`.
- **Expired**: bar empty, digits "0" in `game.timerWarning`, then the reveal shows
  `quiz_timeout`.
- **Ring variant (duel)**: circle `size.timerRing`, stroke `size.timerStroke` (5dp, round caps),
  track `game.timerTrack`, arc `game.timerNormal`, digits inside in `type.titleSmall`
  tabular figures. The arc drains clockwise from 12 o'clock; fraction =
  remaining / `motion.duel.phaseNominalMs.<phase>` (clamped), remaining computed
  from the server deadline and the clock offset (DUEL-12), never from an animation.
  Warning per phase: `motion.duel.timerWarningThresholdMs` (Əminlik ≤ 2 s, answer
  ≤ 3 s, none in question and result) → arc and digits `game.timerWarning` + pulse.
  **Frozen** (own answer sent; result phase): arc and digits
  `scheme.onSurfaceVariant` on `game.timerTrack`; in the result phase the arc keeps
  draining (it shows the time to the next round) but never turns orange. **Expired**:
  digits "0", arc empty; the ring waits for the next server message (no negative
  numbers, DUEL-12). Content description `quiz_timer_cd`, announced at most at the
  warning threshold, polite.

## 5. Category tile (home grid)

Pressable surface (§0) in the category's own accent colour. **2-column grid**,
gap `space.layout.gridGap`, cell width = (content width − gap) / 2 (158dp on a
360dp phone), face min height `size.categoryTileMinHeight` (132dp) + 
`depth.categoryTile`, shape `shape.component.categoryTile` (20dp). All tiles in a
row take the height of the tallest one. An odd last tile stays one cell wide
(start-aligned).

Colours: `category.<slot>.face` / `.onFace` / `.depth`, where `<slot>` comes from
the category's `accentKey` (one of `color.categoryAccentOrder`: violet, blue, teal,
green, orange, rose, pink, ocean). Unknown/missing key → slot by index
(`categoryAccentOrder[index % 8]`, server order), so a new category always gets a
colour without an app release. Proposed defaults for the seed categories:
`color.categoryAccentDefaults` (Coğrafiya ocean, Tarix orange, Biologiya green,
Fizika violet, İdman rose).

Content, top to bottom, padding `space.lg`, gap `space.md`:
1. Icon box: `size.categoryIconContainer` (48dp) rounded square
   (`shape.component.categoryIcon`), `category.<slot>.onFace` at 20 %; icon 28dp
   `category.<slot>.onFace`.
2. Name `type.titleMedium` `category.<slot>.onFace`, **max 3 lines**, wraps at word
   boundaries, never hyphenated, never ellipsized.
3. `space.xxs`, `home_category_question_count` `type.bodyMedium`
   `category.<slot>.onFace`.

**Single-column fallback** (no truncation at 360dp): when font scale > 1.15 **or**
the window is narrower than 360dp **or** any category name has a single word wider
than the cell's text width (measure with `TextMeasurer`; Azerbaijani words such as
"Astronomiya" at 1.3 do not fit 126dp), switch to one column: full-width tiles,
face min height 88dp, icon box at the start and the text column beside it.

Icons (Material Symbols Rounded, Apache 2.0, bundled as vector drawables), mapped by
`icon_key`. The key values are decided (OQ-044):

| icon_key | Symbol |
| --- | --- |
| `geography` | `public` |
| `history` | `history_edu` |
| `biology` | `eco` |
| `physics` | `science` |
| `sports` | `sports_soccer` |
| unknown | `category` (generic fallback, HOME-01) |

States: rest / pressed (§0); **loading** (the count line is replaced by a 20dp
progress indicator in `onFace`, the tile is not clickable and all other tiles
ignore taps — HOME-02 double-tap rule); **skeleton** (tile face
`scheme.surfaceContainerHighest`, no depth, icon box and two text bars in
`scheme.surfaceContainerHigh`, shimmer alpha 0.5 ↔ 1.0 over 1 s, no shimmer with
reduced motion). First appearance: `motion.game.categoryGridEnter` (stagger).

## 6. Duel hero card (home, behind build flag)

Hidden until the duel ships (OQ-020) — the layout slot simply does not exist.
When enabled: full-width pressable card (§0), shape `shape.extraLarge` − 4 (24dp),
face = `gradient.duelHero` (violet → pink, 135°), depth `game.pressableDuelDepth`
5dp, padding `space.xl` × 18dp, gap `space.md`:
- Row: column (title `home_duel_title` `type.headlineMedium`, hint
  `home_duel_hint` `type.bodyMedium`, both `gradient.duelHero.on`) + decorative
  56dp rounded square (white 18 %) with the 32dp `swords` symbol (two crossed
  swords = a duel; no coins, dice, cards or casino imagery).
- Full-width white pressable button (face #FFFFFF = `game.pressableNeutral` light
  value in both themes, label `game.onPressableNeutral` light value `#4426D6`,
  depth `scheme.primaryContainer` light value): `home_duel_cta` ("Duelə başla").
The whole card is one touch target; the button is its visual anchor.

## 7. Əminlik tiles (duel)

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
- `space.xl`, primary action: neutral pressable button (`common_retry` / `common_refresh`);
  optional secondary text button under it.
- **Loading**: circular indicator 48dp `scheme.primary`, optional caption
  `type.bodyMedium` `scheme.onSurfaceVariant` (e.g. `quiz_loading`). Shown only after
  300 ms to avoid flashing on fast networks.

## 12. Top app bar

`TopAppBar` (small), height `size.topAppBarHeight`, container `scheme.surface`
(scrolled: same colour plus a 2dp `game.cardBorder` line at the bottom, no tonal
change), title `type.titleLarge` (20sp ExtraBold) `scheme.onSurface` single line,
navigation icon `scheme.onSurface`, action icons `scheme.onSurfaceVariant`. Titles longer than ~16 characters are
not allowed here (see `az.md` risks); auth screens put their title in the content
as `type.headlineSmall` instead.

## 13. Avatar

Circle `size.avatar` (home header, duel panels, match history), `size.avatarLarge`
(result screen), `size.avatarXLarge` (profile header, match intro, search). Image
via Coil, crop centre. Fallback (no URL or load failed): `scheme.tertiaryContainer`
with the first letter of the display name (uppercase, Azerbaijani-aware: "i" → "İ",
"ı" → "I") in `type.titleMedium` (`type.headlineMedium` at `avatarXLarge`)
`scheme.onTertiaryContainer`. If the first character is not a letter, use the first
visible character; if there is none, the `person` icon (PROF-02). Decorative for
TalkBack (the surrounding element carries the name).

- **Deleted player**: `scheme.surfaceContainerHighest` circle with the `person_off`
  icon in `scheme.onSurfaceVariant`; name `common_deleted_player`.
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
     DUEL-05; the full name is on the intro and the result screen);
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
- Leading: avatar `size.avatar` (deleted-player variant, §13).
- Headline: opponent name `type.bodyLarge`, 1 line, ellipsis.
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
  `type.labelSmall` `scheme.onSurfaceVariant`.
- Merged for TalkBack: `profile_match_cd`.

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

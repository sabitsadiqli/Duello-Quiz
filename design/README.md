# design/

Owner: `designer`. Source of truth for how the app looks, moves and talks. No
Figma file is authoritative; if one exists later, it mirrors these files.

## What is where

| Path | Content |
| --- | --- |
| `tokens.json` | Colours (light + dark), typography, spacing, sizes, shapes, elevation, motion |
| `contrast-report.md` | WCAG contrast of every colour pair used, both themes (generated) |
| `tools/contrast.py` | Generates the report; `--check` fails on any pair below its minimum |
| `tools/text_width.py` | Measures strings with the shipped font files (stdlib only); `--glyphs` checks subset coverage |
| `copy/az.md` | Every UI string in Azerbaijani with its `strings.xml` key, tone rules, width check |
| `screens/components.md` | Shared components and their states (buttons, option card, timer, …) |
| `screens/splash-welcome.md` | Splash and welcome (AUTH-01, AUTH-05) |
| `screens/sign-in.md` | Email sign-in (AUTH-03) |
| `screens/sign-up.md` | Email sign-up with "Ad" (AUTH-02, OQ-011) |
| `screens/password-reset.md` | Password reset (AUTH-04) |
| `screens/email-verification-banner.md` | Verification banner on home (AUTH-08, OQ-010) |
| `screens/v3-migration.md` | **v3 minimal**: per-screen impact, Android notes (3 tabs, fonts, removed components), font verification, impact on requirements |
| `screens/home.md` | Home = "Oyna" tab (v3): top row with date, streak, avatar; greeting; duel card; category list (HOME-01 … HOME-06) |
| `screens/daily-tab.md` | "Gündəlik" tab (v3): today's challenge, streak + week row + messages, daily reminder switch |
| `screens/daily-challenge.md` | Daily challenge: loading, play, result, share text (DAILY-02 … DAILY-10; card states moved to `daily-tab.md` in v3) |
| `screens/progression.md` | XP gain and level-up on results; level / category levels on Profil; streak elements (placed on home top row and Gündəlik in v3) (PROG-01 … PROG-10, PROF-09 … PROF-11) |
| `screens/notifications.md` | Permission card, settings switches, push texts (NOTIF-01 … NOTIF-08, SET-07) |
| `screens/quiz-question.md` | Quiz loading + question screen + exit dialog (QUIZ-01 … QUIZ-04, QUIZ-07, QUIZ-08) |
| `screens/quiz-result.md` | Result + wrong-answer review, XP card, permission card (QUIZ-05, QUIZ-06) |
| `screens/settings.md` | Settings (opened from Profil in v3), notifications, delete account (SET-01 … SET-05, SET-07, AUTH-07) |
| `screens/licenses.md` | Open-source licences list and detail (SET-03) |
| `screens/duel-search.md` | Duel search / queue, bot countdown, timeout, retry, leave (DUEL-02 … DUEL-04, DUEL-23) |
| `screens/duel-intro.md` | Match found + 3 s intro (DUEL-05) |
| `screens/duel-match.md` | Match screen frame and the question, Əminlik and answer phases (DUEL-06 … DUEL-08, DUEL-12, DUEL-17) |
| `screens/duel-reveal.md` | Result phase: simultaneous reveal (DUEL-09) |
| `screens/duel-special-rounds.md` | Round 7 All-in and sudden death (DUEL-10, DUEL-11) |
| `screens/duel-interruptions.md` | Disconnect overlays, rejoin, leave dialog, session replaced, match lost (DUEL-13 … DUEL-16) |
| `screens/duel-result.md` | Match result + calibration card (DUEL-20 … DUEL-22) |
| `screens/duel-availability.md` | Upgrade required, duel unavailable, connection failed (DUEL-01) |
| `screens/profile.md` | "Profil" tab (v3): profile, level chip + XP, category levels, match history, calibration stats, edit name, settings entry (PROF-01 … PROF-11) |
| `brand.md`, `brand/*.svg` | v3 logo mark, adaptive icon layers, splash and notification icons (OQ-D37), `brand/squiggle-underline.svg` (v3 greeting underline); review PNGs in `brand/previews/` |
| `tools/brand_assets.py`, `tools/brand_previews.py` | Generate the brand / store / favicon SVGs and the inline web marks from one geometry (fontTools); render the review PNGs (macOS) |
| `screens/web-legal.md` | Public GitHub Pages: app intro, privacy policy, account deletion (`docs/legal/`, SET-06) |
| `screens/exam-catalogue.md` | Mock exams (post-MVP): Oyna entry decision (OQ-D40), catalogue "Sınaqlar", history "Sınaqlarım" (EXAM-01, -13, -19, -20, -25) |
| `screens/exam-info.md` | Sınaq info, download package, start confirm, skip listening (EXAM-02 … -04, -19, -20) |
| `screens/exam-attempt.md` | Attempt: header timer, pager, reading / listening groups, written field, navigator, leave / submit / time-up (EXAM-04 … -14) |
| `screens/exam-self-assessment.md` | Self-assessment of written tasks with model answer, criteria, 0 / ⅓ / ½ / ⅔ / 1 (EXAM-16) |
| `screens/exam-result.md` | "Təxmini bal", auto vs self-assessed split, sections (EXAM-15, -17) |
| `screens/exam-review.md` | Review of every task, transcript after submit, filters (EXAM-18) |
| `store/` | Google Play listing text, screenshot plan, feature graphic |

Duel colours reuse existing roles (`game.confidence*`, `game.correct/wrong`,
`scheme.primary` for self, `scheme.tertiary` for the opponent); duel timing lives
in `motion.duel`.

Retention (tokens 2.1.0, 2026-10-22): streak colours `game.streak*` (burnt-orange
flame), `gradient.dailyHero` (sky blue → teal daily card), XP and levels on
`scheme.primary*`, streak freeze on `scheme.secondary*`; sizes `size.resultSquare*`,
`size.levelBadge`, `size.streakBadgeMinWidth`, `size.xpBar*`; motion
`motion.progression`. Components §24–§31 in `screens/components.md`.

Bot opponent (tokens 2.2.0, 2026-10-30, OQ-106 … OQ-111): "Bot" badge
(`components.md` §32) on `scheme.tertiaryContainer` / `onTertiaryContainer`, bot
avatar = app mark (§13), no rating ("—"); search countdown to the bot moment over
`motion.duel.phaseNominalMs.searchBot`. No new colour role. Bot variants live in
`duel-search.md`, `duel-intro.md`, `duel-match.md`, `duel-reveal.md`,
`duel-interruptions.md`, `duel-result.md` and `profile.md`.

## Design direction (v3 minimal, tokens 3.0.0) — current

Approved by Sabit 2026-11-16 (mockup), specified 2026-11-17. Replaces v2 below
(v2 notes are kept as history; where they conflict, v3 wins). Full impact:
`screens/v3-migration.md`.

- **"Dəftər" (notebook)**: warm paper `#F5EFE3` / ink `#1E1B16` in light, ink
  `#17150F` / paper `#EFE7D6` in dark. One accent, **nar** red (`#A82D25` /
  `#EC7B6F`, tuned from the mockup for AA). **Saffron** only for the streak
  (`game.streak`, `game.streakFill`). Blue ink secondary (freeze, info), plum
  tertiary (opponent, Bot badge).
- **Navigation**: bottom bar with Oyna / Gündəlik (badge dot) / Profil; flows
  (quiz, daily play, duel, settings) are full screen without the bar.
- **Type**: Fraunces (display/headline serif, numerals) + **Plus Jakarta Sans**
  (UI) — Manrope from the mockup lacks capital Ə (OQ-D34). Both bundled as static
  instances; coverage verified from the font files.
- **Shapes**: flat, hairline cards 16dp, duel card 18dp, pill buttons. No
  gradients, no depth edges, no category colours. Press = ripple + 0.98 scale.
- **Human touches**, a fixed list (`components.md` §36): squiggle under one
  greeting word, the rotated rating sticker, dashed boxes / rings for "not yet",
  serif numerals.
- Unchanged rules: no casino visuals, never "bet", colour never the only signal,
  AA contrast, no truncation at 360dp / 1.3.

## Design direction (v2, tokens 2.0.0) — superseded by v3

Vivid game style in the spirit of Duolingo / Trivia Crack (decided by Sabit,
2026-10-21; full before/after table in `brand.md`).

- **Primary: electric violet** (`#5334E6` light; `#C0B3FF` as the dark-theme text
  accent, `#6A4DFF` as the dark-theme button face). **Tertiary: hot pink**
  (`#C2187A` / `#FFADD6`) = the opponent's accent in the duel (self = primary).
  **Secondary: sky blue** (`#0068C9` / `#9ECAFF`). Light surfaces are a pale
  lavender with white "game cards"; dark surfaces a deep ink-violet.
- **Chunky pressable controls**: buttons, option cards, category tiles and Əminlik
  tiles sit on a solid bottom depth edge (`depth.*`, `game.*Depth`) that disappears
  when pressed (`motion.pressable`). No blurred shadows except dialogs/snackbars.
- **Category colours**: 8 named accent slots (`color.<theme>.category.<slot>`),
  assigned per category row by an `accentKey`, never per category name in code.
- Game colours stay separate roles: correct = green, wrong = crimson, timer warning
  = burnt orange, Əminlik 1/2/3 = increasing violet intensity with 1–3 signal bars.
  Correct/wrong always add a glyph (check / cross replaces the letter badge) and an
  outcome word, so colour-blind players never rely on hue. No gold, no coins, no
  casino visuals.
- **Font: Nunito** (OFL, Google Fonts), bundled, weights 600/700/800/900 (500
  optional). Rounded, friendly, full Azerbaijani Latin coverage. Fallback: system
  `sans-serif`. Never all-caps.
- Figma mirror (not authoritative): "Duello Quiz — UI refresh",
  https://www.figma.com/design/7ik6IU2tYVByPYP2jBRcLB — foundations, components and
  360 × 800 mockups in light and dark.

Azerbaijani glyph test string (v3: must render with Fraunces and Plus Jakarta Sans, no fallback glyphs, in every
bundled instance): `Əə Ğğ Iı İi Öö Üü Çç Şş — Coğrafiya, Şəki, İsmayıllı, Gəncə, Ağdaş`.
Also check that `"i".uppercase(Locale("az"))` gives "İ" for the avatar letter.

## How android-dev consumes this

### Colours
- `color.light.scheme` → `lightColorScheme(...)`, `color.dark.scheme` →
  `darkColorScheme(...)`. Keys equal the Compose parameter names; map every key,
  do not rely on defaults. Dynamic colour (Material You) is **off**: brand and game
  colours must stay consistent and contrast-checked.
- `color.<theme>.game` → an immutable `GameColors` data class exposed through a
  `CompositionLocal` (e.g. `MaterialTheme.gameColors`), one instance per theme,
  same property names as the JSON keys.
- v3 removed `color.<theme>.category`, `color.<theme>.gradient`, `depth.*` and the
  pressable/depth game roles; `color.deprecated` names the replacement token for
  any old reference. Do not keep aliases in Kotlin.
- `color.stateLayer` → Material 3 defaults already match; only use the values for
  custom components.
- Theme choice (Sistem/İşıqlı/Qaranlıq) is read before the first frame so the
  splash already has the right background (SET-01).

### Typography, shapes, spacing, sizes, motion
- `typography.scale` → `Typography(...)`; each style's `family` picks the display
  (Fraunces) or brand (Plus Jakarta Sans) `FontFamily`; `typography.custom` →
  extra styles (overline, numeral, numeralLarge). Names match.
  Use `fontFeatureSettings = "tnum"` where `tokens.json` says so.
- `shape` → `Shapes(extraSmall…extraLarge)`; `shape.component` says which one each
  component uses.
- `spacing`, `size`, `elevation`, `motion` → Kotlin constants (e.g. `object
  Spacing { val lg = 16.dp }`, `object Motion { const val QuizRevealHoldMs = 1500 }`).
  Never type raw dp/ms values in screens.
- Respect "Remove animations" (`motion.reducedMotion`).

### Screen specs
- Each spec lists: open questions (top), purpose, entry points, layout top to
  bottom with token names, components, every state, interactions/animations,
  accessibility, string keys. Build every state in the table — they map to the
  acceptance criteria in `docs/requirements/`.
- If a spec and a requirement disagree, the requirement wins on behaviour, the
  spec wins on visuals and copy; raise it with the designer / business-analyst.
- Open questions marked **OQ-Dn** are design-side questions; until they are
  answered, build the stated proposal.

### Strings
- Copy every key from `copy/az.md` into `values/strings.xml` (`plurals` where
  noted). Keys are final; text may still be polished by the designer, so never
  copy Azerbaijani text into Kotlin.
- Server error codes map to `error_*` keys (table in `copy/az.md`).

## Naming rules

- Colour tokens: Material 3 role names in lowerCamelCase (`surfaceContainerHigh`);
  custom roles follow the same pattern: `<role>`, `on<Role>`, `<role>Container`,
  `on<Role>Container`.
- Every colour exists in **both** themes with the same key. Adding a colour means
  adding it to light and dark and adding its pair to `tools/contrast.py`, then
  regenerating `contrast-report.md`.
- String keys: `snake_case`, prefix by screen/area: `app_`, `common_`, `error_`,
  `auth_`, `home_`, `quiz_`, `settings_`, `duel_`, `profile_`, `daily_`, `progress_`,
`notif_`; `push_` = backend i18n only (never in `strings.xml`). Keys never contain
  "bet" even when the protocol code does (`bet_limit_reached` →
  `error_confidence_limit_reached`). Suffix `_cd` for content
  descriptions, `_title`, `_body`, `_label`, `_supporting`, `_button`/`_confirm`
  for roles in a component.
- Screen spec files: `screens/<screen>.md`, kebab-case.
- Never use "bet", "mərc", "stavka" or casino imagery in UI text or visuals; the
  mechanic is "Əminlik".

## Checks before handing off a change

1. `python3 design/tools/contrast.py --check` passes; regenerate the report with
   `python3 design/tools/contrast.py --report > design/contrast-report.md`.
2. Every key used in `screens/*.md` exists in `copy/az.md`.
3. New single-line labels are estimated against the budgets in `copy/az.md` at
   font scale 1.3.

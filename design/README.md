# design/

Owner: `designer`. Source of truth for how the app looks, moves and talks. No
Figma file is authoritative; if one exists later, it mirrors these files.

## What is where

| Path | Content |
| --- | --- |
| `tokens.json` | Colours (light + dark), typography, spacing, sizes, shapes, elevation, motion |
| `contrast-report.md` | WCAG contrast of every colour pair used, both themes (generated) |
| `tools/contrast.py` | Generates the report; `--check` fails on any pair below its minimum |
| `copy/az.md` | Every UI string in Azerbaijani with its `strings.xml` key, tone rules, width check |
| `screens/components.md` | Shared components and their states (buttons, option card, timer, …) |
| `screens/splash-welcome.md` | Splash and welcome (AUTH-01, AUTH-05) |
| `screens/sign-in.md` | Email sign-in (AUTH-03) |
| `screens/sign-up.md` | Email sign-up with "Ad" (AUTH-02, OQ-011) |
| `screens/password-reset.md` | Password reset (AUTH-04) |
| `screens/email-verification-banner.md` | Verification banner on home (AUTH-08, OQ-010) |
| `screens/home.md` | Home (HOME-01 … HOME-06) |
| `screens/quiz-question.md` | Quiz loading + question screen + exit dialog (QUIZ-01 … QUIZ-04, QUIZ-07, QUIZ-08) |
| `screens/quiz-result.md` | Result + wrong-answer review (QUIZ-05, QUIZ-06) |
| `screens/settings.md` | Settings, delete account, licences (SET-01 … SET-05, AUTH-07) |

Duel screens come later (build-order step 5). Their shared pieces already exist:
`game.confidence*` colours, the Əminlik chip and the timer ring in `components.md`.

## Design direction

- **Primary: indigo blue** (`#4355B9` light / `#BAC3FF` dark): calm, "knowledge",
  readable, and far from the green/red used for correct/wrong. Secondary is a
  neutral indigo-grey, tertiary a muted plum used only for the avatar fallback and
  small accents. Neutrals carry a slight indigo tint.
- Game colours are separate roles so they never collide with brand colour:
  correct = green, wrong = red, timer warning = burnt orange, Əminlik 1/2/3 =
  increasing indigo intensity. No gold, no coins, no casino visuals.
- **Font: Inter** (OFL, Google Fonts), bundled, weights 400/500/600/700. Chosen for
  full Azerbaijani Latin coverage, very good legibility at small sizes, tabular
  figures for timers and scores, and a neutral modern look. Fallback: system
  `sans-serif` (Roboto/Noto), which also covers Azerbaijani.
- Material 3 tonal surfaces instead of shadows. Rounded but not toy-like shapes.

Azerbaijani glyph test string (must render with Inter, no fallback glyphs, in every
weight): `Əə Ğğ Iı İi Öö Üü Çç Şş — Coğrafiya, Şəki, İsmayıllı, Gəncə, Ağdaş`.
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
- `color.stateLayer` → Material 3 defaults already match; only use the values for
  custom components.
- Theme choice (Sistem/İşıqlı/Qaranlıq) is read before the first frame so the
  splash already has the right background (SET-01).

### Typography, shapes, spacing, sizes, motion
- `typography.scale` → `Typography(...)` with the Inter `FontFamily`; names match.
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
  `auth_`, `home_`, `quiz_`, `settings_` (later `duel_`). Suffix `_cd` for content
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

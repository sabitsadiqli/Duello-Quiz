# Brand: logo mark and app icon

Owner: `designer`. Files in `design/brand/`. Colours come from `tokens.json`; no new
brand colours.

## Questions and decisions

- **OQ-D15 — approved by Sabit (2026-10-16).** Proposal below ("two speech bubbles":
  a question between two players). It is a placeholder-quality geometric mark,
  good enough for the closed test and the Play listing; a professional logo can
  replace it later without code changes (same file names, same layers).
- Decided: no wordmark file. "Duello Quiz" is set in Nunito ExtraBold
  (`type.headlineMedium` on welcome) next to or under the mark; text is never
  baked into the icon.

## Visual direction v2 — vivid game style (tokens 2.0.0, 2026-10-21)

Sabit found v1 (stock Material 3 baseline: indigo `#4355B9`, grey-lavender
surfaces, Inter, flat tonal cards) too old-fashioned and chose a vivid game look in
the spirit of Duolingo / Trivia Crack. What changed:

| Area | v1 | v2 |
| --- | --- | --- |
| Brand colour | Indigo `#4355B9` / `#BAC3FF` | Electric violet `#5334E6` (light) / `#C0B3FF` (dark text accent), pressable face `#6A4DFF` in dark |
| Opponent / accent | Plum `#7A5368` | Hot pink `#C2187A` / `#FFADD6` |
| Secondary | Indigo-grey | Sky blue `#0068C9` / `#9ECAFF` |
| Surfaces | Grey-lavender, tonal elevation | Light lavender `#F7F5FF` with white game cards; deep ink-violet `#121027` in dark |
| Depth | Tonal surface colours, no edges | Solid 4–5dp bottom "depth edge" on buttons, cards, tiles (`tokens.depth`) that compresses on press |
| Categories | One indigo icon circle for all | 8 named accent slots, each category tile in its own saturated colour |
| Type | Inter 400–700 | **Nunito** 600–900, rounded, ExtraBold titles and labels |
| Shapes | 4/8/12/16/28, pill buttons | 8/12/16/20/28, rounded-rectangle buttons |
| Motion | Tweens only | Springs for press release and pops (`motion.spring`) |

Rules that did not change: no casino imagery (no coins, chips, dice, slot shapes,
gold), no "bet" wording, AA contrast for every text pair, colour never the only
signal.

### Font: Nunito

- Source: Google Fonts, `ofl/nunito` (designers Vernon Adams, Cyreal, Jacques Le
  Bailly). Licence: **SIL Open Font License 1.1** — free to bundle in the APK; the
  licence text ships with the app and Nunito is listed on the licences screen
  (`screens/licenses.md`).
- Why Nunito: rounded terminals give the friendly game feel without being a
  novelty display face; it stays readable at 12–14sp in body text; the variable
  font covers weights 200–1000 so one family gives SemiBold body text and Black
  display numbers.
- Alternatives checked: **Baloo 2** (very playful but heavy at body sizes and
  tall line height, which costs vertical space with long Azerbaijani words),
  **Rubik** (good, but less "round"; a fine fallback choice), **Lexend** (designed
  for readability, but its wide letterforms make Azerbaijani labels longer),
  **Fredoka** (too childish for an Elo-rated duel).
- Azerbaijani coverage: Nunito's latin-ext set includes Ə ə Ğ ğ I ı İ i Ö ö Ü ü
  Ç ç Ş ş. Checked on 2026-10-21 by requesting the Google Fonts subset for exactly
  those glyphs (returns a non-empty glyph kit, unlike unsupported characters) and
  by rendering the test string in the Figma file in every weight used. android-dev
  repeats the on-device check with the test string in `README.md`.

## The mark

Two overlapping rounded speech bubbles, one in front of the other, tails pointing
away from each other — two players in a conversation of questions. The front
bubble carries a question mark. Built only from rounded rectangles, short straight
tails, a stroked arc and a dot: readable at 24dp, no gradients, no shadows, no
text.

- Front bubble = self/primary violet; back bubble = opponent/tertiary pink — the
  same two accents the duel uses for "you" and "the opponent".
- A gap ring in the background colour separates the bubbles (drawn as a path
  filled and stroked in the background colour under the front bubble, so it works
  in Android `VectorDrawable`, which has no masks).
- Never: coins, chips, dice, cards, crowns, trophies, slot shapes, lightning
  "VS" badges, sparkles.

| File | Use | Colours |
| --- | --- | --- |
| `brand/logo-mark-light.svg` | In-app mark on `scheme.surface`, light theme (welcome, `size.splashLogo`) | back `tertiary` #C2187A, front `primary` #5334E6, glyph `onPrimary` #FFFFFF, gap `surface` #F7F5FF |
| `brand/logo-mark-dark.svg` | Same, dark theme | back `tertiary` #FFADD6, front `primary` #C0B3FF, glyph `onPrimary` #23107A, gap `surface` #121027 |
| `brand/ic-launcher-background.svg` | Adaptive icon background layer (108 × 108) | solid `primary` light #5334E6 |
| `brand/ic-launcher-foreground.svg` | Adaptive icon foreground layer (108 × 108) | back `tertiaryContainer` #FFD9EC, front #FFFFFF, glyph and gap #5334E6 |
| `brand/ic-launcher-monochrome.svg` | Themed icon layer (Android 13+); outlines only, single colour | #000000 (the system tints it; only alpha matters) |
| `brand/app-icon-full.svg` | Full-bleed square for the Play Store 512 × 512 PNG and other stores | background + foreground composited |

The mark files use a square viewBox around the shapes; the launcher files use the
108 × 108 adaptive-icon canvas.

## Geometry (108 × 108 canvas)

- Front bubble: rounded rectangle x 28–62, y 30–62, corner radius 10, tail to
  (31, 70). Back bubble: x 46–80, y 38–70, radius 10, tail to (75, 77).
- Gap ring: 3 units outside the front bubble.
- Question mark: arc radius 5.8 centred at (45, 40.5), stroke 4.2, round caps,
  hook ending at (45, 49); dot radius 2.5 at (45, 56).
- Launcher layers: the whole mark is scaled to 95 % around the centre
  (`translate(2.7 2.7) scale(0.95)`): the furthest point is ~30 units from the
  centre, inside the 33-unit (66dp) safe zone, so circle, squircle and rounded-square
  masks never cut it.
- Clear space around the in-app mark: at least 1/4 of its width. Minimum size: 24dp.

## Hand-off to android-dev

1. Import `ic-launcher-foreground.svg`, `ic-launcher-background.svg` and
   `ic-launcher-monochrome.svg` with Android Studio → Vector Asset (local SVG) into
   `app/src/main/res/drawable/ic_launcher_foreground.xml`,
   `ic_launcher_background.xml` and a new `ic_launcher_monochrome.xml`
   (`<group android:pivotX="54" android:pivotY="54" android:scaleX="0.95"
   android:scaleY="0.95">` if the importer drops the group transform). Point
   `<monochrome>` in `mipmap-anydpi-v26/ic_launcher*.xml` to the new monochrome
   drawable (today it reuses the foreground, which would render as a white blob).
2. Regenerate the legacy `mipmap-*/ic_launcher*.webp` (API < 26) from
   `app-icon-full.svg` with Image Asset Studio (shape: circle for `_round`,
   square/none for the default).
3. In-app mark: replace the placeholder `DuelloLogoMark` (`core/designsystem`)
   with vector drawables from `logo-mark-light.svg` / `logo-mark-dark.svg`
   (`drawable/` and `drawable-night/`, or one drawable with colour attributes bound
   to `primary`, `tertiary`, `onPrimary`, `surface`). Size `size.splashLogo`;
   decorative (no content description).
4. Splash (Android 12+): `windowSplashScreenAnimatedIcon` = the adaptive icon
   foreground; background `scheme.surface` of the active theme (unchanged rule in
   `screens/splash-welcome.md`).
5. Play Store: export `app-icon-full.svg` as a 512 × 512 PNG (32-bit, sRGB), no
   rounded corners or shadow (Play applies them).

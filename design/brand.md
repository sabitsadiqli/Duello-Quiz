# Brand: logo mark and app icon

Owner: `designer`. Files in `design/brand/`. Colours come from `tokens.json`; no new
brand colours.

## Questions and decisions

- **OQ-D15 — for Sabit: approve the mark.** Proposal below ("two speech bubbles":
  a question between two players). It is a placeholder-quality geometric mark,
  good enough for the closed test and the Play listing; a professional logo can
  replace it later without code changes (same file names, same layers).
- Decided: no wordmark file. "Duello Quiz" is set in Inter SemiBold
  (`type.headlineMedium` on welcome) next to or under the mark; text is never
  baked into the icon.

## The mark

Two overlapping rounded speech bubbles, one in front of the other, tails pointing
away from each other — two players in a conversation of questions. The front
bubble carries a question mark. Built only from rounded rectangles, short straight
tails, a stroked arc and a dot: readable at 24dp, no gradients, no shadows, no
text.

- Front bubble = self/primary indigo; back bubble = opponent/tertiary plum — the
  same two accents the duel uses for "you" and "the opponent".
- A gap ring in the background colour separates the bubbles (drawn as a path
  filled and stroked in the background colour under the front bubble, so it works
  in Android `VectorDrawable`, which has no masks).
- Never: coins, chips, dice, cards, crowns, trophies, slot shapes, lightning
  "VS" badges, sparkles.

| File | Use | Colours |
| --- | --- | --- |
| `brand/logo-mark-light.svg` | In-app mark on `scheme.surface`, light theme (welcome, `size.splashLogo`) | back `tertiary` #7A5368, front `primary` #4355B9, glyph `onPrimary` #FFFFFF, gap `surface` #FBF8FF |
| `brand/logo-mark-dark.svg` | Same, dark theme | back `tertiary` #EAB9D2, front `primary` #BAC3FF, glyph `onPrimary` #08218A, gap `surface` #131318 |
| `brand/ic-launcher-background.svg` | Adaptive icon background layer (108 × 108) | solid `primary` light #4355B9 |
| `brand/ic-launcher-foreground.svg` | Adaptive icon foreground layer (108 × 108) | back `tertiaryContainer` #FFD8EA, front #FFFFFF, glyph and gap #4355B9 |
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

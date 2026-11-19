# Google Play phone screenshots

Owner: `designer`. Which screens to capture, in which theme and state, and the
caption for each. Text: `listing-az.md`. Screens: `design/screens/*.md`.

## Open questions

- **OQ-D26 — decided by Sabit (2026-10-17): captioned composites.** Proposal: captioned
  composites (layout below): they explain the Əminlik idea, which a raw duel
  screenshot does not. Raw screenshots are faster to make and are allowed too.
  Decides: Sabit.
- Duel screenshots need two signed-in test accounts playing each other against
  the dev backend. The debug bot server in `android/README.md` is not usable for
  store screenshots: its fake questions name their own right answer. No real
  people's names or photos.

## Capture setup

- Device: emulator with a **1080 × 1920 px, 480 dpi** custom profile (= 360 × 640dp,
  the phone width every spec is designed for). Play allows at most 2:1 between the
  long and the short side, so 20:9 phones (1080 × 2400) cannot be used as they are.
- Font scale 1.0, display size default, app language Azerbaijani.
- Status bar in demo mode: `adb shell settings put global sysui_demo_allowed 1`,
  then `adb shell am broadcast -a com.android.systemui.demo -e command clock -e
  hhmm 1000`, battery full, Wi-Fi full, no notifications.
- Accounts: test accounts with neutral display names ("Leyla" for self, "Murad"
  for the opponent), letter avatars (no photos), ratings around 1000–1100.
- Questions: only from `content/samples/` or reviewed questions that may appear
  publicly; never the email of the account (settings and profile screenshots do
  not show it; do not capture the settings screen).
- Capture with `adb exec-out screencap -p > NN-name.png`; PNG, sRGB, no alpha.

## Screens (8; Play needs 2–8, the first 3 matter most)

| # | Screen and state | Theme | Spec | Caption (≤ 30) |
| --- | --- | --- | --- | --- |
| 1 | Duel, **Əminlik phase**: question visible, chips 1 / 2 / 3, caption "Əminlik 3: 2 dəfə qalıb", ring at ~3 s, opponent pill "Seçir…" | Light | `duel-match.md` | Əvvəlcə Əminliyini seç |
| 2 | Duel, **result phase** right after the flip: both panels revealed (self correct +237 with speed bonus, opponent wrong −100), correct option green | Light | `duel-reveal.md` | Nəticə eyni anda açılır |
| 3 | Home (Oyna tab, v3): top row with streak, greeting with squiggle, ink duel card with the rating sticker, the five category rows | Light | `home.md` | 5 kateqoriya, canlı duel |
| 4 | Quiz question with an image, progress "4 / 10", ring at ~12 s, no option chosen | Light | `quiz-question.md` | Hər suala 20 saniyə |
| 5 | Duel round 7: "×2" pill and the "Həlledici raund: xallar ikiqat!" banner in the question phase | Dark | `duel-special-rounds.md` | 7-ci raundda xallar ikiqat |
| 6 | Duel match result: win hero (v3: solid paper block on ink), score card, rating "+16", Əminlik kartı with all three levels used and a hint | Dark | `duel-result.md` | Nə qədər əmin idin? |
| 7 | Quiz result with the wrong-answer review entry, then (optional second capture) one review card with explanation | Dark | `quiz-result.md` | Səhvlərini izahla öyrən |
| 8 | Profil tab: header with level chip, record card, Əminlik statistikası, first history rows | Dark | `profile.md` | Statistikanı izlə |

Order rationale: the Əminlik mechanic is what makes the app different, so it
opens the gallery (1–2); then breadth (3–4), the duel's highlights (5–6), learning
(7) and progress (8). Light first, dark from 5 on, so the gallery shows both
themes.

Caption lengths (checked): 22, 23, 24, 19, 26, 19, 23, 17 characters.

## Captioned composite layout (if OQ-D26 = composites) — v3 (OQ-D37)

Canvas 1080 × 1920 px, the same paper frame for every screenshot (also for the
dark ones, so the gallery looks like one printed series):

1. Background `scheme.surface` light `#F5EFE3` (paper). No gradient, no texture.
2. Caption: top 96 px margin, centred, max 2 lines, **Fraunces SemiBold** 72 px
   (opsz 72, SOFT 50, the bundled `fraunces_semibold.ttf`), line height 84 px,
   letter-spacing −0.5 px, `scheme.onSurface` `#1E1B16` (ink on paper 14.99:1).
   Side margins 72 px. One-line captions sit vertically centred in a 352 px
   caption band. Convert to outlines or render with the bundled font installed.
3. Screenshot: scaled to 82 % (886 × 1574 px), centred horizontally, top at 400 px,
   corner radius 40 px, **2 px border `game.cardBorder` `#E2D9C8`** (light
   screenshots are paper on paper and need the edge); the bottom part may run
   off the canvas (the bottom 54 px are cut; keep nothing important there:
   navigation bar area).
4. No device frame, no shadow, no squiggle, no nar (the accent belongs to the
   screenshots themselves), no extra text, no badges, no "#1", no prices.

Files: `NN-<name>.png` (e.g. `01-duel-confidence.png`); keep the raw captures
next to the composites so they can be rebuilt when the UI changes. They are
release assets, not source: store them outside the repo or in a release folder
Sabit chooses (not committed here).

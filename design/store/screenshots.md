# Google Play phone screenshots

Owner: `designer`. Which screens to capture, in which theme and state, and the
caption for each. Text: `listing-az.md`. Screens: `design/screens/*.md`.

## Open questions

- **OQ-D26 — captioned composites or raw screenshots?** Proposal: captioned
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
| 3 | Home: header with rating, duel card, the five category cards | Light | `home.md` | 5 kateqoriya, canlı duel |
| 4 | Quiz question with an image, progress "4 / 10", ring at ~12 s, no option chosen | Light | `quiz-question.md` | Hər suala 20 saniyə |
| 5 | Duel round 7: "×2" pill and the "Həlledici raund: xallar ikiqat!" banner in the question phase | Dark | `duel-special-rounds.md` | 7-ci raundda xallar ikiqat |
| 6 | Duel match result: win hero, score card, rating "+16", Əminlik kartı with all three levels used and a hint | Dark | `duel-result.md` | Nə qədər əmin idin? |
| 7 | Quiz result with the wrong-answer review entry, then (optional second capture) one review card with explanation | Dark | `quiz-result.md` | Səhvlərini izahla öyrən |
| 8 | Profile: header, record card, Əminlik statistikası, first history rows | Dark | `profile.md` | Statistikanı izlə |

Order rationale: the Əminlik mechanic is what makes the app different, so it
opens the gallery (1–2); then breadth (3–4), the duel's highlights (5–6), learning
(7) and progress (8). Light first, dark from 5 on, so the gallery shows both
themes.

Caption lengths (checked): 22, 23, 24, 19, 26, 19, 23, 17 characters.

## Captioned composite layout (if OQ-D26 = composites)

Canvas 1080 × 1920 px, light colours for every frame (also for dark screenshots,
so the gallery looks consistent):

1. Background `scheme.primary` light `#4355B9`.
2. Caption: top 96 px margin, centred, max 2 lines, Nunito ExtraBold 72 px, line
   height 88 px, `scheme.onPrimary` `#FFFFFF` (pair `onPrimary` / `primary`,
   checked). Side margins 72 px. One-line captions sit vertically centred in a
   352 px caption band.
3. Screenshot: scaled to 82 % (886 × 1574 px), centred horizontally, top at 400 px,
   corner radius 40 px, bottom part may run off the canvas (the bottom 54 px are
   cut; keep nothing important there: navigation bar area).
4. No device frame, no extra text, no badges, no "#1", no prices.

Files: `NN-<name>.png` (e.g. `01-duel-confidence.png`); keep the raw captures
next to the composites so they can be rebuilt when the UI changes. They are
release assets, not source: store them outside the repo or in a release folder
Sabit chooses (not committed here).

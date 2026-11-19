# v3 minimal — migration (design → android-dev, business-analyst)

Tokens 3.0.0 (2026-11-17). Direction approved by Sabit on 2026-11-16 (mockup):
"dəftər" (notebook) — warm paper and ink, one nar (pomegranate) accent, saffron
only for the streak, Fraunces display serif + a sans UI face, flat hairline cards,
pill buttons, three bottom tabs (Oyna / Gündəlik / Profil), daily and streak
content off the home dashboard.

## Open questions (all in the specs too)

| ID | Question | Proposal | Decides |
| --- | --- | --- | --- |
| **OQ-D34** | UI sans font: Manrope (approved in the mockup) has **no capital Ə (U+018F)** — verified in the font's cmap (google/fonts `ofl/manrope`, updated 2026-09-14) and by the Google Fonts subset response (empty kit). | **Plus Jakarta Sans** (verified full coverage incl. Ə ə, tnum). Verified alternatives: Onest, Hanken Grotesk, Work Sans, Public Sans, DM Sans, Inter. | **Decided 2026-11-17: Plus Jakarta Sans** (Sabit) |
| **OQ-D35** | The Gündəlik week row needs per-day streak states (active / freeze / missed). `GET /v1/me/progress` has none. | Add `week: [{dayKey, state}]` for the current Baku week; fallback (old cached response only) derives active days from `streakCurrent`, never shows freeze or missed dots (not from events either). | **Decided and built 2026-11-19** (backend `week`, 5 states incl. `today_pending`) |
| **OQ-D36** | Brief says the reminder row shows a "time"; the reminder is server-fixed at 10:00 Baku (OQ-088) and settings decided "no clock times". | Keep "Səhər" without a time, no picker. Alternative: "Səhər, 10:00 (Bakı vaxtı)". | **Decided 2026-11-17: "Səhər", no time, no picker** (Sabit) |
| **OQ-D37** | Logo mark, adaptive icon, notification accent, Play feature graphic and the web legal pages still use the v2 violet/pink palette. | Redrawn in v3 (`design/brand.md`): ink speech bubble with a Fraunces `?` in front, nar bubble behind, real 3-unit gap, brush-curved tails; paper launcher background; notification accent nar (`#A82D25` / night `#EC7B6F`), new small icon; feature graphic on paper with Fraunces wordmark; `legal.css` on paper/ink tokens. Previews `design/brand/previews/`. Follow-ups: OQ-D38 (launcher background paper vs ink), OQ-D39 (self-hosted web fonts). | **Decided by design 2026-11-19, approved by Sabit 2026-11-19** |
| **OQ-D38** | Launcher icon background: paper or ink? | Paper `#F5EFE3` in both system themes (ink bubble is the figure, matches the first frame, distinct from white icons). Alternative: ink tile with the dark-theme mark | **Decided by Sabit 2026-11-19: paper** |
| **OQ-D39** | Legal pages: brand fonts on the web? | Keep local-only fonts (system serif / sans fallback); no Google Fonts (privacy policy says no other third parties). Alternative: self-host subset WOFF (~60 KB) | **Decided by Sabit 2026-11-19: system fonts** |
| OQ-D27 | `accentKey` on Category | **Superseded** — no category colours in v3. | — |
| OQ-D28, OQ-D30 | (unchanged, still open) per-question daily results; one-time streak events | Now needed by the Gündəlik tab instead of home. | backend-dev |

## Font coverage — how it was verified

Both checks were run on 2026-11-17 on the files from github.com/google/fonts
(main) and on the Google Fonts css2 API:

1. **cmap check** (authoritative): parsed the `cmap` table of each variable TTF
   and of the static instances made with `fonttools varLib.instancer` + subset
   with `pyftsubset` (the exact pipeline android-dev will use). Fraunces and Plus
   Jakarta Sans map all of Ə ə Ğ ğ I ı İ i Ö ö Ü ü Ç ç Ş ş, plus U+2212 (minus),
   U+00B7 (middle dot), U+2026 (ellipsis). Manrope maps ə but not Ə.
2. **css2 `text=` kit** (supporting only): the API returns a kit for *any*
   character, so a kit alone proves nothing (it also returns one for Cyrillic Ж
   on Fraunces, which has no Cyrillic). The kit **size** tells: unsupported
   characters give the same 1664-byte empty kit (Fraunces Ж, Manrope Ə); real
   glyphs give 800–960 bytes (Fraunces Ə 956, Plus Jakarta Sans Ə 848).

Consequence for the v2 note in old tokens: "verified Nunito by requesting the
subset" was not a valid proof; the bundled `nunito_*.ttf` files were re-checked
by cmap today and do contain every Azerbaijani letter, so nothing shipped wrong.

Fraunces has **no tabular figures**: use it only for numbers that do not change
while visible (`tokens.json` typography).

## Per screen

"Tokens only" = the spec's layout, states and copy still hold; it looks v3 by
the new token values plus the restyled shared components (`components.md` §0,
§1, §3, §4, §8, §12 and the "v3 restyle" table). "Rewrite later" = the spec
works with tokens now but has a hero or layout made for v2 that should get a v3
pass before or soon after the v3 release.

| Spec | What changes visually in v3 | Status |
| --- | --- | --- |
| `components.md` | Flat press feedback, pills, thin timer, flat option card with ink selection, category list rows, duel card, compact banners; new §33–§38 | **Updated** |
| `home.md` | Bottom-tab root; top row (date, streak, avatar), serif greeting with squiggle, ink duel card with sticker, typographic category list; daily card, streak banners, level and settings removed | **Rewritten** |
| `daily-tab.md` | New Gündəlik tab: challenge card, streak + week row + messages, reminder row | **New** |
| `daily-challenge.md` | A superseded by `daily-tab.md`; result hero becomes a flat card with a serif score; exits return to the tab | **Updated** (note at top); D hero: rewrite later |
| `progression.md` | Placement only (home → Gündəlik / Profil); flat level badge and bars | **Updated** (note at top) |
| `profile.md` | Becomes the Profil tab: no back icon, settings icon top-right, level chip + XP bar in the header, streak card → link row | **Updated** (note at top) |
| `settings.md` | Entry from Profil; overline section headers | **Updated** (note at top) |
| `notifications.md` | Push taps → Gündəlik tab; daily switch mirrored on the tab | **Updated** (note at top) |
| `email-verification-banner.md` | Compact collapsed form on home | **Updated** (note at top) |
| `licenses.md` | Font rows Fraunces + Plus Jakarta Sans; Nunito removed | **Updated** (note at top) |
| `splash-welcome.md` | Paper background, serif app name, pill buttons; v3 logo mark and splash icon (`brand.md`, OQ-D37) | Tokens only (logo files replaced) |
| `sign-in.md`, `sign-up.md`, `password-reset.md` | Serif headline, flat fields, pill buttons | Tokens only |
| `quiz-question.md` | Thin timer line, flat option cards (ink selection), no category tint in the top bar (`category.<slot>.container` → `scheme.surface`) | Tokens only |
| `quiz-result.md` | Score hero is a v2 game card; progress card flat; "Əsas səhifə" returns to the Oyna tab | Rewrite later (hero: serif score on paper, no gradient) |
| `duel-search.md` | Search pulse rings in ink at 20 %; "Ləğv et" secondary pill | Tokens only |
| `duel-intro.md` | Accent rings nar (self) / plum (opponent) | Tokens only |
| `duel-match.md` | Flat panels with 1dp accent border; Əminlik tiles in ink density (paper / sand / ink) with signal bars, no depth; phase stepper in ink. Four phases stay distinct by layout (question only → tiles panel → options → reveal) and the phase label | Tokens only |
| `duel-reveal.md` | Flip unchanged; reveal faces without depth | Tokens only |
| `duel-special-rounds.md` | All-in pill and banner in plum `tertiaryContainer`; sudden death banner ink | Tokens only |
| `duel-interruptions.md` | Overlay card flat on paper | Tokens only |
| `duel-result.md` | Win hero used `gradient.resultWin` → solid `inverseSurface` block (ink / paper), headline serif | Rewrite later (hero proportions) |
| `duel-availability.md` | State views without the icon circle | Tokens only |
| `web-legal.md` | `legal.css` on v3 tokens (paper / ink, nar links, serif headings, hairline cards, nav pill = `navIndicator`), v3 inline mark and favicon (OQ-D37); account-deletion steps already say Profil → Parametrlər | **Updated** (fonts: OQ-D39) |

Token references that no longer exist are mapped in `tokens.json`
`color.deprecated` (e.g. `gradient.resultWin` → `scheme.inverseSurface`,
`game.pressablePrimary` → `scheme.primary`, `category.<slot>.container` →
`scheme.surfaceContainerHigh`).

## Android implementation notes (android-dev)

### Navigation
- New root: a `Scaffold` with `DuelloNavigationBar` (`components.md` §33) and a
  nested graph of three top-level destinations: `PlayRoute` (home), `DailyRoute`,
  `ProfileRoute`. Type-safe Navigation Compose; switch with
  `navigate(route) { popUpTo(start) { saveState = true }; launchSingleTop = true;
  restoreState = true }`.
- Full-screen flows live **outside** the tab scaffold (or hide the bar): quiz
  (loading, question, result, review), daily (loading, play, result), duel (all
  screens), settings, licences, delete account, auth. Returning from a flow lands
  on the tab it was started from (daily → Gündəlik, quiz/duel → Oyna).
- System back: Gündəlik / Profil → Oyna; Oyna → finish. Re-selecting a tab
  scrolls it to the top.
- Deep links: both push types (`daily_reminder`, `streak_reminder`) →
  `DailyRoute`. Settings is reached from `ProfileRoute` only.
- The Gündəlik badge reads the cached daily status (no extra request); it is
  refreshed with the home refresh and on returning from the daily flow.

### Fonts
- Remove `nunito_*.ttf` and its OFL entry. Add static instances (minSdk 24 →
  no variable axes): `fraunces_regular.ttf` (opsz 48, wght 400, SOFT 50, WONK 0),
  `fraunces_semibold.ttf` (opsz 72, wght 600, SOFT 50, WONK 0),
  `plusjakartasans_medium/semibold/bold/extrabold.ttf`, each subset to Latin +
  Latin-ext + the listed punctuation, keeping `tnum`, `locl`, `case`
  (commands in `tokens.json`; measured ≈ 48 KB per Fraunces file, ≈ 35 KB per
  sans file). Two `FontFamily`s: display and brand; map `Typography` per
  `typography.scale.*.family`; add `DuelloTypography.overline / numeral /
  numeralLarge`.
- Re-run the glyph test string on device in every weight (`design/README.md`).

### Removed components / code
- Depth edge of `PressableSurface` (and `depth.*`, `game.*Depth`,
  `motion.pressable`): replace by ripple + `motion.press` scale.
- `CategoryColors`, `LocalCategoryColors`, `categoryAccentOrder` fallback, any
  `accentKey` mapping (OQ-D27 superseded).
- Gradient brushes (`duelHero`, `resultWin`, `dailyHero`).
- Category tile grid and its single-column fallback → `CategoryRow` list.
- Home profile header card, streak badge (§24), home daily card (§28), home
  message banner placement (§29 moves to the Gündəlik tab).

### New components
`DuelloNavigationBar` (§33), `Sticker` (§34), `StreakChip` (§35),
`SectionOverline` (§35a), `GreetingWithSquiggle` (§36, string annotations →
`AnnotatedString`, path from `TextLayoutResult`), `DailyChallengeCard` v3 (§37),
`AnswerBoxes` and `WeekRow` (§38), compact `Banner` variant (§8), `DuelCard` v3
(§6), `CategoryRow` (§5).

### Colour scheme
- Map `color.light.scheme` / `color.dark.scheme` 1:1 (every key, including
  `surfaceTint` = surface). `GameColors` gains `streakFill`, `onStreakFill`,
  `navIndicator`, `inverseOnSurfaceVariant`; loses all `pressable*`, `*Depth`,
  `cardDepth`.
- Status / navigation bar icons: dark icons on paper (light theme), light icons
  on ink (dark theme).

### Brand assets (OQ-D37)

Source files in `design/brand/` (`brand.md` has the rules). Import each SVG with
Android Studio → New → Vector Asset → Local file (keep the 108 × 108 size for
launcher layers; if the importer drops the `<g transform>`, add
`<group android:translateX/Y android:scaleX/Y>` with the values from the SVG).

| Source SVG | Android resource | Notes |
| --- | --- | --- |
| `ic-launcher-background.svg` | `app/src/main/res/values/ic_launcher_background.xml` → `<color name="ic_launcher_background">#F5EFE3</color>` (or `drawable/ic_launcher_background.xml`) | Solid paper, same in night mode (no `values-night` override) |
| `ic-launcher-foreground.svg` | `drawable/ic_launcher_foreground.xml` | Fixed colours `#1E1B16`, `#A82D25`, `#F5EFE3` |
| `ic-launcher-monochrome.svg` | `drawable/ic_launcher_monochrome.xml` | Keep `android:fillType="evenOdd"` on the front-bubble path (the `?` is a hole) |
| — | `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | `<background>` / `<foreground>` / `<monochrome>` → the three above |
| `app-icon-full.svg` | `mipmap-mdpi … xxxhdpi/ic_launcher.webp` and `ic_launcher_round.webp` (48 / 72 / 96 / 144 / 192 px) | Image Asset Studio or export; only for API < 26 |
| `ic-launcher-foreground.svg` | `drawable/ic_splash_icon.xml` | `windowSplashScreenAnimatedIcon` (values-v31 theme and the `Theme.SplashScreen` compat theme) |
| `splash-icon-dark.svg` | `drawable-night/ic_splash_icon.xml` | Same name, night qualifier; splash background `@color/splash_background` = `#F5EFE3` / night `#17150F` |
| `logo-mark-light.svg`, `logo-mark-dark.svg` | `DuelloLogoMark` in `core/designsystem` as an `ImageVector` with three paths | Colours from the active scheme: back `primary`, front `onSurface`, `?` `surface` — so the in-app manual theme override is respected (a `drawable-night` file would follow the system, not the app setting). Decorative, no content description. Size `size.splashLogo` |
| `ic-stat-duello.svg` | `drawable/ic_stat_duello.xml` (24 × 24dp) | Manifest `com.google.firebase.messaging.default_notification_icon` = `@drawable/ic_stat_duello`; keep `evenOdd` |
| — | `values/colors.xml` `notification_accent` `#A82D25`, `values-night/colors.xml` `#EC7B6F` | Manifest `com.google.firebase.messaging.default_notification_color` = `@color/notification_accent`; also `NotificationCompat.Builder.setColor` for any local notification |

Play Console (Sabit): app icon = `app-icon-full.svg` exported as a 512 × 512
PNG (32-bit, sRGB, no rounded corners); feature graphic = `design/store/feature-graphic.svg`
exported at 1024 × 500 without alpha (`listing-az.md`). Both SVGs have all text
as outlines, so any SVG renderer gives the same result.

Checks: the icon at 48dp on a light and a dark launcher, a themed-icon launcher
(Android 13+), the splash in light and dark system mode, one push in the light
and the dark notification shade.

## Impact on requirements (for business-analyst)

Do not treat these as decided product changes until business-analyst updates
`docs/requirements/` and Sabit confirms; the design follows the approved v3
direction meanwhile.

| Story / decision | Today says | v3 does | Suggested change |
| --- | --- | --- | --- |
| **HOME-03** | Hint "Əminliyini sına, reytinq qazan" | "7 sual · nə qədər əminsən?", CTA "Rəqib tap", "~3 dəq", rating sticker | Update the indicative copy |
| **HOME-04** | Header shows avatar, name, "Reytinq: 1000"; tapping the header opens the profile | Avatar 32dp in the top row (→ Profil tab), name only in the greeting (first word), rating on the duel-card sticker; **with the duel flag off the rating is not on home** | Allow rating to live on the duel card / Profil; tap target = avatar |
| **HOME-06** | Settings icon in the home top app bar | Settings icon on the Profil tab; home has no top app bar | Move the story's entry point to Profil (also SET-01 … SET-05 "from home") |
| **HOME-07** | Daily card at the top of home | No daily card on home; Gündəlik tab + nav badge dot | Rewrite as "Gündəlik tab shows today's challenge"; home refresh no longer includes `GET /v1/daily` (only for the badge) |
| **HOME-08** | Level and streak in the home header; tap → profile; streak messages on home | Streak chip on home (tap → **Gündəlik**), level chip + XP on Profil, streak messages on Gündəlik | Split: streak on home + Gündəlik, level on Profil; analytics `home_progress_tapped` keeps only `target: streak` |
| Home order decision (HOME-07 "header → banner → daily → duel → categories") | — | top row → greeting → duel → categories | Replace |
| **DAILY-01** | "today's challenge … on home" | On the Gündəlik tab | Re-home the story |
| **DAILY-03 / DAILY-04** | Leave → home; result button "Əsas səhifə" | Leave → Gündəlik tab; result button "Gündəliyə qayıt" | Update |
| **NOTIF-05** | Daily reminder → home with the daily card in view; streak reminder → home | Both → Gündəlik tab | Update |
| **SET-07 / NOTIF-06** | Reminder switches in settings | Also the daily switch on the Gündəlik tab (same server value) | Add the mirror; confirm OQ-D36 (time display) |
| **PROG-06 … PROG-08** | Streak hint / freeze used / streak ended shown on home | Shown on the Gündəlik tab, same priority and once-rules | Update placement |
| **PROF-01** | Profile opened from the home header | Profil is a bottom tab (also from the home avatar) | Update |
| **PROF-11** | Streak card on the profile | Streak row linking to the Gündəlik tab (current streak in text) | Accept the lighter form or keep a card |
| New: week row | — | 7-day streak row needs per-day states | New story + OQ-D35 (backend) |
| **SET-06** / `docs/legal/account-deletion.html`, `privacy-policy.html` | "Əsas səhifədə yuxarıdakı Parametrlər ikonuna toxun" / "Parametrlər → Hesabı sil" | Path is now "Profil → Parametrlər → Hesabı sil" | **Must** be updated before a v3 build goes to testers (Play requirement: the in-app path must be correct) |
| **OQ-D27** | `accentKey` proposed for categories | Not needed | Close as superseded |
| **OQ-020** | Duel flag hides the entry | Still hides the duel card; also drops duel-themed greeting lines | Note only |
| **AUTH-08** | Verification banner on home | Compact, expandable banner on home | Note only |
| Glossary / CLAUDE.md product summary | "home" as the hub with daily and streak | Three tabs | Glossary: add "Oyna", "Gündəlik", "Profil" tab names |

## Definition-of-done status

- Light and dark: every colour role has both values (`tokens.json`).
- Contrast: `python3 design/tools/contrast.py --check` passes, 306 pairs (incl. brand / store / web v3), lowest
  text 4.75 (light) / 4.55 (dark), lowest non-text 3.41 / 3.31
  (`design/contrast-report.md`).
- Truncation: every new v3 string measured at 360dp / font scale 1.3
  (`copy/az.md` "v3 minimal"); nothing truncates, wraps are listed.
- Every state of the old home and daily-card specs has a home: home states →
  `home.md` States; daily card states → `daily-tab.md` A; streak states and
  messages → `daily-tab.md` B; level / XP → `profile.md`; "opened from a reminder"
  → `daily-tab.md` entry points.

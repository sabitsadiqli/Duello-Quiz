# Mock exams — entry on Oyna, catalogue "Sınaqlar", history "Sınaqlarım"

Stories: EXAM-01 (catalogue), EXAM-13 (home "Davam edən sınaq"), EXAM-19
(subject sınaq), EXAM-20 (retakes, history), EXAM-25 (naming, disclaimer),
EXAM-22 (display, TalkBack). Decisions: OQ-117, OQ-118, OQ-124, OQ-126 (free),
OQ-128 (Phase 1 = "Buraxılış — İngilis dili", 3 variants), OQ-130.
Tokens: `design/tokens.json` 3.1.0. Components: `components.md` §1, §8, §11,
§12, §35a, §46, "Exam rows". Copy: `copy/az.md` "exam_" (catalogue, history,
home). Flow: catalogue → `exam-info.md` → `exam-attempt.md` →
`exam-result.md` / `exam-self-assessment.md` / `exam-review.md`.

## Decided questions (Sabit, 2026-11-21)

- **OQ-D40 — where do Sınaqlar live in the 3-tab navigation?** **Decided by Sabit 2026-11-21: A as
  proposed** (section below the categories + running-attempt banner; the Oyna
  scroll trade-off is accepted). B and C stay below for the record.
- **OQ-D48 — EXAM-01 says downloaded variants "stay startable" offline;
  EXAM-04 / OQ-118 say a start needs one online request.** The design follows
  the decided OQ-118: offline, a downloaded variant shows "Başlamaq üçün
  internet lazımdır"; a *running* attempt continues offline. **Decided by Sabit 2026-11-21: proposal
  accepted**; business-analyst aligns EXAM-01.
- **OQ-D49 — variant names.** Proposal: the app builds "Sınaq №1" from
  `variantNumber` (`exam_variant_title`), the exam type title comes from the
  server i18n (`copy/az.md` "Backend i18n — exams", EXAM-25 rules). Fonts:
  "№" needs U+2116 in the subsets (`tokens.json` 3.1.0, android-dev).
  **Decided by Sabit 2026-11-21: proposal accepted.**

## Entry-point decision (OQ-D40 — decided by Sabit 2026-11-21: A)

**A (decided): a "Sınaqlar" section on the Oyna tab, below the category list.**
Overline `home_exams_overline` ("İMTAHANA HAZIRLIQ") + one exam row
(`home_exams_row_title` "Sınaqlar", supporting `home_exams_row_supporting`
"DİM formatında məşq sınaqları", 24dp `assignment` icon, chevron).

Why not a 4th tab: the three-tab bar was approved on 2026-11-16 and gives each
item ≥ 120dp; a tab for one exam type with 3 variants is thin; the entry is
behind a flag and hidden while nothing is published (EXAM-01), so a tab would
make the bar change shape between builds and accounts; Sınaqlar is a long,
deliberate session (60–180 min), not a "glance" destination like Gündəlik.
Re-evaluate a tab in Phase 2/3 when there are several exam types.

**Trade-off (stated, not hidden):** Oyna's no-scroll budget (`home.md`: 728 of
740dp, 12dp spare) cannot absorb the section. With the flag on and ≥ 1
published variant the tab grows by 20 (gap) + 24 (overline) + 64 (row) = **108dp
→ 836dp**, so Oyna scrolls by ~96dp at 360 × 740, font scale 1.0. The budget's
purpose stays intact: greeting, the duel card and all five categories remain
fully visible without scrolling, because the section is appended **after**
them. What is lost: the Sınaqlar row is below the fold. Mitigation:
- A running attempt — the moment it matters most — shows a compact banner at
  the **top** of Oyna (below), visible without scrolling.
- The section is the last block, so a scroll gesture reveals it fully in one
  move; the analytics `exam_catalog_view` source tells whether discovery is a
  problem.

Alternatives considered (not chosen):
- **B. 4th tab "Sınaqlar"** (`nav_exams`): no vertical cost; items become 90dp
  wide ("Gündəlik" 74dp at 1.3 still fits); costs the bar change described
  above. Choose B if exams become a main product line.
- **C. Row between the duel card and the categories:** visible above the fold,
  but pushes the fifth category row below it (−76dp), which breaks the core
  loop the budget protects. Not recommended.

### Oyna additions (also written into `home.md`)

| Block | Spec |
| --- | --- |
| Gap | `space.layout.homeSectionGap` after the last category row |
| Overline | `home_exams_overline`, §35a, heading |
| Row | "Exam rows" (`components.md`), `size.examRowMinHeight`, icon `assignment`, title `home_exams_row_title`, supporting: none running → `home_exams_row_supporting`; running → `home_exams_row_running` ("Davam edir — 42 dəq qalıb", minutes from the cached attempt, refreshed each minute while visible) |
| Running banner | Compact info banner (§8) directly under the top row: 20dp `timer` icon, `home_exam_running_banner` ("Davam edən sınaq: 42 dəq qalıb"), trailing chevron; whole banner is one button → the attempt (`exam-attempt.md`, resume). Order: offline banner, then this banner; the email verification banner is hidden while a sınaq runs (at most two banners). Shown from the local Room attempt, so it works offline and after an app kill (EXAM-13) |
| Hidden | Flag off, or the cached catalogue has 0 published variants (EXAM-01): no overline, no row, no gap |

Tap row → catalogue (shared axis X, full-screen flow, bottom bar hidden).
TalkBack: row `home_exams_row_cd`; banner role Button, label = its text +
`exam_resume_click_cd`.

## Purpose

Choose a sınaq: see what exists, what is downloaded, what is running and how
past attempts went; read the honest disclaimer before anything else.

## Entry points

- Oyna → "Sınaqlar" row.
- Back from `exam-info.md`, from a result / review (`exam_result_back`
  "Sınaqlara qayıt").
- Not from server push. The local "10 dəqiqə qalıb" notification (OQ-D45,
  `exam-attempt.md` I) opens the running attempt, not the catalogue. Deep
  links: none.

## Layout — catalogue (top to bottom, 360dp)

1. Top app bar (§12): back icon `common_back`, title `exam_catalogue_title`
   ("Sınaqlar").
2. `LazyColumn`, gutters `space.layout.screenGutter`, pull-to-refresh:
   1. Optional banners (§8 full): offline `exam_catalogue_offline`.
   2. `space.md`, **disclaimer note** (§46) `exam_disclaimer` — first content
      block, so it is visible without scrolling at 360dp at font scale 1.0 and
      1.3 (EXAM-01, EXAM-25): 5 lines × 16 = 80dp + 24 padding at 1.0; ~7 lines
      at 1.3 (~140dp).
   3. `space.xl`, per **exam type** (Phase 1: one):
      - overline from the server's `overline` field (authored in capitals in
        the bank, e.g. "BURAXILIŞ · İNGİLİS DİLİ"); if absent, no overline
        (never uppercase at runtime);
      - `space.xs`, type title `type.headlineSmall` (serif) `scheme.onSurface`,
        e.g. "Buraxılış — İngilis dili (DİM formatında)": 428dp → **2 lines at
        1.0 and 1.3** (allowed, wraps);
      - `space.xs`, `exam_type_meta` ("Fənn sınağı · 30 tapşırıq · 60 dəq")
        `type.bodyMedium` `scheme.onSurfaceVariant`;
      - `space.md`, **variant cards**, gap `space.md` (below).
   4. `space.xl`, row **"Sınaqlarım"** ("Exam rows"): icon `history`, title
      `exam_history_title`, supporting `exam_history_count` ("4 cəhd") or
      `exam_history_none` ("Hələ cəhd yoxdur"), chevron. Hidden while the
      catalogue is in first-load skeleton.
   5. `space.xl` + navigation inset.

At ≥ 600dp: centred, `space.layout.maxContentWidth`.

### Variant card

Flat card (§0, `shape.component.examVariantCard`, padding `space.lg`, full width,
whole card is one button, ripple only):

- Row 1: title `exam_variant_title` ("Sınaq №1") `type.titleMedium`, weight 1;
  trailing chevron 24dp `scheme.onSurfaceVariant` (or the download progress, see
  states).
- `space.xxs`, `exam_variant_meta` ("30 tapşırıq · 60 dəq · 3,4 MB")
  `type.bodyMedium` `scheme.onSurfaceVariant` (243dp at 1.3 of 264dp: 1 line).
- `space.sm`, **status line**: 18dp icon + `type.labelMedium`, colour by state
  (table). One status only, by priority: running > result pending > needs
  assessment > update needed > last result > downloaded > not started.
- Optional `space.xs`, chip row: `exam_retake_label` ("Təkrar cəhd") when the
  last result is a retake — pill `shape.full`, face
  `scheme.surfaceContainerHigh`, `type.labelMedium` `scheme.onSurface`, 28dp
  high (not interactive).

| Variant state (server attempt summary + local package) | Status line | Icon | Colour |
| --- | --- | --- | --- |
| Never started, not downloaded | `exam_status_not_started` "Başlanmayıb" | `radio_button_unchecked` | `scheme.onSurfaceVariant` |
| Downloaded (package verified) | `exam_status_downloaded` "Yüklənib · başlamağa hazır" | `download_done` | `scheme.onSurfaceVariant` |
| Downloading | `exam_status_downloading` "Yüklənir… 42%"; chevron → 20dp determinate progress | — | `scheme.onSurface` |
| Running (this device or another) | `exam_status_running` "Davam edir — 42 dəq qalıb" | `timer` | `scheme.onSurface`, Bold; card border 1dp `scheme.onSurface` (ink) to stand out |
| Submitted, result pending (offline submit) | `exam_status_pending` "Nəticə hesablanır" | `cloud_upload` | `scheme.onSurfaceVariant` |
| Scored, written tasks not assessed | `exam_status_needs_assessment` "Yazılılar qiymətləndirilməyib" | `rate_review` | `scheme.onSurface` |
| Scored | `exam_status_score` "Təxmini bal: 67,7" (last attempt) | `check_circle` | `scheme.onSurface` |
| Package outdated (`variant_version_mismatch`, known from the catalogue `version`) | `exam_status_update` "Yenilənib — yenidən yüklə" | `update` | `scheme.onSurface` |
| Offline, not downloaded | as "Başlanmayıb" + card still opens the info screen (which explains) | | |

Width check: every status ≤ 253dp at 1.3 vs 264dp; "Təxmini bal: 67,7" and
"Təkrar cəhd" are on separate lines on purpose (together 253dp, too tight).

## Layout — history "Sınaqlarım" (EXAM-20)

1. Top app bar: back, title `exam_history_title` ("Sınaqlarım").
2. `LazyColumn` of history rows, newest first, 1dp `game.cardBorder` hairlines
   between rows, paged (20 per page):
   - title: `exam_history_row_title` ("Sınaq №1 · İngilis dili");
   - supporting line 1: date (Baku day) `exam_history_date` ("21 noyabr" /
     "21 noyabr 2027" for another year; months from `profile_months`) · result:
     `exam_status_score` or `exam_status_needs_assessment` /
     `exam_status_pending` / `exam_status_running` / `exam_history_abandoned`
     ("Bitirilməyib — server yekunlaşdırıb");
   - chip row: `exam_mode_subject` ("Fənn sınağı"; Phase 2 adds
     `exam_mode_full`), `exam_retake_label` when a retake;
   - trailing chevron. Tap → `exam-result.md` of that attempt (running → the
     attempt).
3. Empty: state view (§11): `history` icon, `exam_history_empty_title`
   ("Hələ sınaq verməmisən"), `exam_history_empty_body`, secondary button
   `exam_history_empty_cta` ("Sınaq seç") → back to the catalogue.

## Components

Top app bar, exam note (§46), section overline, variant card, chips, exam rows,
banners, state views, skeletons, pull-to-refresh, snackbar.

## States — catalogue

| State | What the user sees |
| --- | --- |
| First load, no cache | Disclaimer (local string) at once; type block as skeleton (overline bar 120dp, title bar 240 × 24dp, 3 card skeletons 104dp, `scheme.surfaceContainerHighest`, shimmer 0.5 ↔ 1.0, none with reduced motion); loading indicator not shown before 300ms |
| Success | As laid out |
| Empty (0 published — reachable only if the list changed since home was drawn) | State view (§11): `inbox`, `exam_catalogue_empty_title` "Hələ ki sınaq yoxdur", body `exam_catalogue_empty_body`, `common_refresh`; home hides the entry on its next refresh |
| Error (5xx / 15 s timeout), no cache | State view: `error`, `exam_catalogue_error`, `common_retry` |
| Offline, cache | Full warning banner `exam_catalogue_offline` above the disclaimer; cached cards; downloaded and running variants open normally; a running attempt resumes offline |
| Offline, no cache | State view: `cloud_off`, `home_offline_title`, `exam_catalogue_offline_body`, `common_retry`; **a running local attempt still appears** as a single running card above the state view (from Room) |
| Refreshing | Cards kept; pull indicator only |
| Download running (started on the info screen) | Card shows progress; tapping opens the info screen with the live progress |
| Running attempt elsewhere (other device) | Running card; tap → info screen with "Davam et" (downloads the package first if missing, `exam-info.md`) |
| Flag off | Not reachable (no entry, no deep link) |
| Signed out / account switch | Cache of the previous account never shown; local packages may stay (no personal data in them), local attempts are deleted (EXAM-23) |

## States — history

Loading (skeleton rows), success, empty (above), error (state view
`exam_history_error` + `common_retry`), offline with cache (banner
`common_offline_banner`, cached rows), offline no cache (state view
`cloud_off`, `home_offline_title`, `common_retry`), paging error (inline row
`common_retry`).

## Interactions and animations

- Variant card → `exam-info.md` (shared axis X).
- "Sınaqlarım" → history (shared axis X); row → result (shared axis X).
- Pull-to-refresh as on Oyna. Status changes cross-fade 150ms.
- System back → previous screen (catalogue → Oyna).
- Analytics: `exam_catalog_view {variant_count}` once per open.

## Accessibility

- Disclaimer note is read first after the title (traversal order = visual).
- Type title: heading. Each variant card one node, role Button, label
  `exam_variant_card_cd` ("Sınaq №1. 30 tapşırıq, 60 dəqiqə, 3,4 meqabayt.
  Davam edir, 42 dəqiqə qalıb."); the chip and status icon are inside the label.
- History row one node: title, date, result, chips.
- Touch targets: cards ≥ 104dp high, rows ≥ 64dp.
- Font scale 1.3 at 360dp: type title 2 lines, every card line fits (widths
  above, `copy/az.md`).

## String keys

`home_exams_overline`, `home_exams_row_title`, `home_exams_row_supporting`,
`home_exams_row_running`, `home_exams_row_cd`, `home_exam_running_banner`,
`exam_resume_click_cd`, `exam_catalogue_title`, `exam_disclaimer`,
`exam_type_meta`, `exam_variant_title`, `exam_variant_meta`, `exam_size_mb`,
`exam_duration_min`, `exam_duration_hm`, `exam_status_not_started`,
`exam_status_downloaded`, `exam_status_downloading`, `exam_status_running`,
`exam_status_pending`, `exam_status_needs_assessment`, `exam_status_score`,
`exam_status_update`, `exam_retake_label`, `exam_variant_card_cd`,
`exam_history_title`, `exam_history_count`, `exam_history_none`,
`exam_history_row_title`, `exam_history_date`, `exam_history_abandoned`,
`exam_mode_subject`, `exam_history_empty_title`, `exam_history_empty_body`,
`exam_history_empty_cta`, `exam_history_error`, `exam_catalogue_empty_title`,
`exam_catalogue_empty_body`, `exam_catalogue_error`, `exam_catalogue_offline`,
`exam_catalogue_offline_body`, `home_offline_title`, `common_offline_banner`,
`common_back`, `common_retry`, `common_refresh`, `profile_months`.

## Data needed (for backend-dev, `docs/api/`)

Catalogue item per variant: `variantId`, `variantNumber`, `version`,
`taskCount`, `durationMin`, `packageSizeBytes`, exam type (`title`,
`overline`, i18n), and the user's attempt summary: `state` (`none` | `running`
| `pending` | `scored`), `endsAtEpochMs` (running), `estimate` (decimal, one
place), `unassessedCount`, `isRetake`, `attemptCount`. History: attempts with
`baku day`, mode, the same summary fields, `finalizedBy` (`user` | `deadline` |
`server`).

## Impact on requirements (for business-analyst) — all exam specs

Sabit decided OQ-D40 … OQ-D49 on 2026-11-21 (all as proposed, except OQ-D44
and OQ-D45, which are **in** Phase 1). business-analyst mirrors these rows in
`docs/requirements/mock-exams.md`.

| Story | Requirement says | Design does | Suggested change |
| --- | --- | --- | --- |
| EXAM-01 | "opens Sınaqlar from home" | Section below the categories on Oyna; Oyna scrolls ~96dp when the feature is on (OQ-D40) | **Decided 2026-11-21**: placement and trade-off accepted; HOME no-scroll rule gets this exception |
| EXAM-01 | Offline: "downloaded variants stay startable" | Start needs internet (OQ-118 / EXAM-04); running attempts continue offline | **Decided 2026-11-21** (OQ-D48): reword to "downloaded variants stay openable; a running attempt continues" |
| EXAM-04 | `deadlineEpochMs` = start + duration + grace drives the countdown | Countdown to `endsAtEpochMs` (no grace shown) (OQ-D41) | **Decided 2026-11-21** (OQ-D41): add `endsAtEpochMs` to the AC (backend draft already has it) |
| EXAM-04 | Notices at 30 / 10 / 1 min | Same, plus warning colour from 10 min, no pulse | Note only |
| EXAM-05 | Header "Riyaziyyat · 14 / 25" | "14 / 30" + section name on a second header line (height fixed per attempt) | Note only |
| EXAM-09 | "split view or Mətn tab" | Toggle "Tapşırıq / Mətn" on phones (first visit of a group opens Mətn), split view ≥ 600dp | Fix the choice in the AC |
| EXAM-10 | TalkBack "Dinlə, 2 dəfədən 1-i istifadə olunub" | "Dinlə. İstifadə olunub: 1 / 2" (no number-dependent suffix) | Update the indicative copy |
| EXAM-10 | App kill: play counted if > 10 s | Counted **and** resumed paused at the saved position (OQ-D43) | **Decided 2026-11-21** (OQ-D43): add the resume position to the AC (backend stores `positionMs`) |
| EXAM-10 / OQ-119 | Skip listening "with a note" | Chosen on the info screen before start only; score shown out of the reduced maximum (OQ-D42) | **Decided 2026-11-21** (OQ-D42): before start only; score out of the reduced maximum |
| EXAM-12 / EXAM-14 | "Cavabsız: 7, İşarələnmiş: 3. Sınağı bitirmək istəyirsən?" | Title + counts line + consequence line; empty written tasks count as cavabsız here | Update copy; state the empty-written rule |
| EXAM-13 | Home shows "Davam edən sınaq" | Compact banner at the top of Oyna + Sınaqlar row text; verification banner hidden while it shows | Accept |
| EXAM-16 | "Yazılı tapşırıqları qiymətləndir (7)" | Card title without the count + button "Qiymətləndir (7)"; marks editable later (OQ-D47) | **Decided 2026-11-21** (OQ-D47): marks can be changed later from the result screen (and the review) |
| EXAM-18 / EXAM-10 | Transcript in the review; practice player in Phase 2 | Transcript **plus** review player with free replay and seeking, after submit only (`components.md` §42a) | **Decided 2026-11-21** (OQ-D44): in Phase 1 |
| EXAM-20 | "Əvvəlcə davam edən sınağı bitir" with "Davam et" / "Bitir" | "Ləğv et" / "Ona keç" (finishing only inside the attempt, with its confirmation) | Update the AC |
| EXAM-20 | "Sınaqlarım" | A row at the bottom of the catalogue (not on Profil) | Note only |
| EXAM-25 | Disclaimer on catalogue, info, result, "Haqqında" | All four; settings gets a "Sınaqlar haqqında" info row (flag on only) | Note only |
| OQ-120 | CC BY voices credited on the licences screen | Section "DİNLƏMƏ SƏSLƏRİ" (flag on only) | Note only |
| OQ-125 | Active day, no XP | Result shows the streak row only, no XP row | Note only |
| (new) / EXAM-04 | Notices in the app only | Plus a local notification "10 dəqiqə qalıb" while the app is in the background (`exam-attempt.md` I) | **Decided 2026-11-21** (OQ-D45): in Phase 1; new story needed |

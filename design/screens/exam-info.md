# Mock exams — info and download ("Sınaq №1")

Stories: EXAM-02 (info), EXAM-03 (download package), EXAM-04 (start: the
"Başla" request), EXAM-19 (subject sınaq), EXAM-20 (retake, one running
attempt), EXAM-25 (disclaimer), OQ-119 (skip listening). Decisions: OQ-117
(60 min, no pause), OQ-118 (download before start, online start), OQ-119,
OQ-124 (unlimited retakes, 1 running, 10 starts per Baku day), OQ-126 (free).
Tokens 3.1.0. Components: `components.md` §1, §8, §9, §10, §12, §14, §31,
§35a, §46, "Exam rows". Copy: `copy/az.md` "exam_info_", "exam_download_",
"exam_start_".

## Decided questions

- **OQ-D42 — skip listening (OQ-119): when can it be chosen, and how is the
  estimate shown?** Proposal: only **before the start**, as a switch on this
  screen (stored on the attempt at start; it cannot be switched mid-attempt,
  so it can never be used to drop tasks after hearing them). The result then
  shows the score **out of the reduced maximum** ("58,4 / 83,8"), not rescaled to
  100, with the note `exam_result_listening_skipped` — honest and simple.
  Alternative: rescale to 100 (looks comparable, but it is an extrapolation).
  **Decided by Sabit 2026-11-21: as proposed** (skip only before the start; score out of the
  reduced maximum).
- **OQ-D41 — displayed deadline.** EXAM-04 returns `deadlineEpochMs` = start
  + duration + 2-min grace. The timer must show 60:00, not 62:00. Proposal: the
  start response also returns `endsAtEpochMs` (start + duration) for display;
  the client never displays the grace. **Decided by Sabit 2026-11-21: proposal accepted.** Status
  2026-11-21: the backend draft on `feat/exams-phase1` (`ExamDtos.kt`) already
  returns `endsAtEpochMs` and `deadlineEpochMs` and takes `skipListening` in the
  start request — consistent with this spec; confirm in `docs/api/`.

## Purpose

Tell the student exactly what the sınaq is and how it is scored, get the whole
package onto the phone, and start the clock only when they are ready.

## Entry points

- Catalogue variant card (`exam-catalogue.md`).
- A running attempt on another device / after reinstall: catalogue running card
  → this screen with "Davam et" (package download first if missing).

## Layout (top to bottom, 360dp)

1. Top app bar (§12): back icon, no title (the title is the headline).
2. Scrollable column, gutters `space.layout.screenGutter`:
   1. `space.sm`, overline `exam_info_overline` ("FƏNN SINAĞI · DİM FORMATINDA")
      §35a.
   2. `space.xs`, headline `exam_variant_title` ("Sınaq №1")
      `type.headlineMedium` (serif; "№" U+2116 is in Fraunces, subset rule in
      tokens 3.1.0), heading.
   3. `space.xs`, exam type title (server) `type.titleMedium`
      `scheme.onSurfaceVariant`, wraps (2 lines at 1.0).
   4. `space.lg`, **facts row**: three equal items (≈ 104dp), start-aligned:
      20dp icon `scheme.onSurfaceVariant`, then the number `type.titleLarge`
      tabular `scheme.onSurface` ("60", "30", "100"), then a caption
      `type.bodySmall` `scheme.onSurfaceVariant`: `timer` +
      `exam_info_fact_duration_cap` ("dəqiqə"), `assignment` +
      `exam_info_fact_tasks_cap` ("tapşırıq"), `grade` + `exam_info_fact_max_cap`
      ("maksimum bal"; 85 / 110dp of 104dp → 2 lines at 1.3, allowed, all three
      items take the height of the tallest). TalkBack:
      one node per item, `exam_info_fact_cd` ("60 dəqiqə").
   5. `space.xl`, overline `exam_info_structure_overline` ("QURULUŞ"), then one
      row per section (14 list row, not clickable, 1dp hairlines between):
      title = section name (server) `type.titleMedium`, supporting
      `exam_info_section_counts` ("6 tapşırıq: 3 qapalı, 3 yazılı") or
      `exam_info_section_closed_only` ("16 qapalı tapşırıq"); leading 24dp icon
      by section kind: `headphones` (listening), `spellcheck` (grammar /
      vocabulary), `menu_book` (reading). Order = exam order.
   6. `space.xl`, overline `exam_info_rules_overline` ("QAYDALAR"), then rule
      rows (icon 20dp + `type.bodyMedium` `scheme.onSurface`, wraps, `space.md`
      between):
      - `schedule`: `exam_info_rule_time` ("Vaxt 60 dəqiqədir. Fasilə yoxdur,
        vaxt arxa planda da işləyir.")
      - `swap_horiz`: `exam_info_rule_navigation` (any order, change answers until
        submit)
      - `headphones`: `exam_info_rule_listening` (2 plays, no rewind, pause
        allowed, headphones recommended) — only when the exam has listening
      - `edit_note`: `exam_info_rule_written` ("Yazılı tapşırıqları sınaqdan
        sonra özün qiymətləndirəcəksən.") — only with written tasks (EXAM-02)
      - `calculate`: `exam_info_rule_scoring` (plain-words formula, values from
        the template: 1 per closed, up to 2 per written, 37 → 100, wrong answers
        cost nothing). Phase 3 adds `exam_info_rule_penalty` (−1/4).
      - `wifi`: `exam_info_rule_online` (start needs internet, then offline is
        fine)
   7. `space.lg`, **skip-listening switch** (§31) — only when the exam has
      listening: headline `exam_info_skip_listening`, supporting
      `exam_info_skip_listening_supporting`. Default off; disabled while an
      attempt is running (shows the attempt's value).
   8. `space.lg`, disclaimer note (§46) `exam_disclaimer` (EXAM-25) and, in the
      same note, a second paragraph `exam_estimate_note` ("Nəticə təxmini
      baldır, rəsmi DİM nəticəsi deyil.").
   9. `space.xl` + bottom action area height (content is never hidden under it).
3. **Bottom action area** (pinned, `scheme.surface`, 1dp `game.cardBorder` on
   top when content scrolls under it, padding `space.lg` + navigation inset):
   one primary pill, full width, plus an optional line above it (state table).

## Bottom action area — states

| State | Line above the button (`type.bodySmall`, wraps) | Button |
| --- | --- | --- |
| Not downloaded | `exam_info_download_hint` ("Sınaq əvvəlcədən telefona yüklənir ki, zəif internet vaxtını almasın.") | Primary `exam_info_download` ("Yüklə · 3,4 MB") |
| Downloading | 4dp determinate bar (`game.timerTrack` / `scheme.onSurface`) + `exam_info_downloading` ("Yüklənir… 1,2 / 3,4 MB") | Secondary `common_cancel` (stops; finished files are kept, EXAM-03) |
| Download paused offline | warning banner (§8 full) `exam_download_paused_offline`; bar frozen | Secondary `common_cancel`; resumes automatically when the network returns |
| Download failed (5xx, timeout, hash mismatch after 1 automatic retry) | warning banner `exam_download_failed` (hash: `exam_download_verify_failed`) | Primary `common_retry` (resumes, no re-download of verified files) |
| Not enough storage (checked before the first byte: needed = package size × 1.1) | warning banner `exam_download_storage` ("… 3,4 MB lazımdır.") | Primary `common_retry` (re-checks) |
| Downloaded, verified | `exam_info_ready_hint` ("Hazırsan? Başlayanda vaxt işləməyə başlayacaq."); if notifications are not allowed (permission, app or `exam` channel off) also `exam_info_notif_off_hint` (OQ-D45) | Primary `exam_info_start` ("Başla") |
| Start request running | — | Primary in loading state; back and switch disabled |
| Running attempt of this variant, package here | `exam_info_running_hint` ("Vaxt gedir: 42 dəq qalıb.") in `scheme.onSurface` Bold | Primary `exam_info_resume` ("Davam et") |
| Running attempt, package missing (other device / reinstall / cleared storage) | `exam_info_resume_download` ("Davam etmək üçün sınağı bu telefona yüklə. Vaxt gedir: 42 dəq qalıb.") | Primary `exam_info_download`; when done it becomes "Davam et" automatically |
| Finished before (retake) | `exam_info_retake_hint` ("Bu sınağı artıq vermisən — nəticə «Təkrar cəhd» kimi qeyd olunacaq.") | Primary `exam_info_retake` ("Yenidən başla"); a secondary `exam_info_last_result` ("Son nəticə") above it opens the last result |
| Package outdated (`version` differs) | warning banner `exam_variant_updated` | Primary `exam_info_download` (new package; old one deleted after success) |
| Variant unpublished (`variant_unavailable` on start / 404) | full state view (§11) `exam_variant_unavailable` + `common_back`; local package deleted | — |
| Offline, downloaded, nothing running | warning banner `exam_start_needs_internet` | Primary `exam_info_start` stays **enabled** (a disabled button gives no reason); tapping shows the snackbar `exam_start_needs_internet` |
| Offline, not downloaded | banner `exam_download_needs_internet` | Primary `exam_info_download` enabled; tap → same snackbar |

## Start confirmation (EXAM-04)

"Başla" / "Yenidən başla" opens a dialog (§9): title `exam_start_confirm_title`
("Sınağa başlayırsan?"), body `exam_start_confirm_body` ("60 dəqiqə vaxtın
olacaq. Vaxtı dayandırmaq olmur — tətbiqdən çıxsan da sınaq davam edir."),
actions `common_cancel` / `exam_start_confirm` ("Başla"). Back / scrim = cancel.
With skip listening on, the body adds `exam_start_confirm_skip`
("Dinləmə hissəsi keçiləcək.").

Start request results (idempotency key per tap, EXAM-04):

| Result | What the user sees |
| --- | --- |
| 201 attempt | → `exam-attempt.md`, first task (shared axis X); `exam_start` analytics |
| Same variant already running (server returns it) | → attempt, resume (no second attempt) |
| Another variant running (409 `attempt_running`) | Dialog title `exam_start_other_running_title` ("Əvvəlcə davam edən sınağı bitir"), body `exam_start_other_running_body` ("«Sınaq №2» hələ davam edir: 12 dəq qalıb."), actions `common_cancel`, `exam_start_other_running_open` ("Ona keç") — opens that attempt, where the user can finish it. (EXAM-20 mentions "Bitir" here: finishing an exam from a dialog without seeing it is too easy to hit by mistake; the attempt screen has the confirm flow.) |
| Offline / timeout | Snackbar `exam_start_needs_internet`; nothing started |
| 429 `rate_limited` | Snackbar `exam_start_rate_limited` ("Bu gün çox sınaq başlatmısan. Sabah yenidən cəhd et.") |
| 409 `variant_version_mismatch` | Banner `exam_variant_updated`, button → download |
| 404 / `variant_unavailable` | State view as above |
| 5xx | Snackbar `exam_start_failed` + action `common_retry` |

## Interactions and animations

- Download runs in the app process with a foreground-safe worker
  (android-dev: WorkManager; leaving the screen keeps it going, the catalogue
  card shows progress). No system notification in Phase 1 unless Android
  requires one for the worker type (then title `exam_download_notif`).
- Progress updates at most every 250ms; percentages are whole numbers; sizes
  with one decimal and a decimal comma ("1,2 / 3,4 MB").
- After success: the line cross-fades to `exam_info_ready_hint`, button label
  cross-fades to "Başla" (150ms); polite announcement `exam_download_done_cd`.
- Back during a download: leaves, download continues. Back while the start
  request runs: ignored.
- The skip switch writes nothing to the server until the start request (it is
  part of it).

## Accessibility

- Headline order: overline, "Sınaq №1", type title (one heading node for the
  last two: "Sınaq №1, Buraxılış — İngilis dili (DİM formatında)").
- Section rows: "Dinləmə, 6 tapşırıq: 3 qapalı, 3 yazılı".
- Download bar: `ProgressBarRangeInfo` + the text; announcements at 25 % steps
  only.
- Bottom area is after the content in traversal order.
- Touch targets: switch row ≥ 56dp, buttons 48dp.

## String keys

`exam_info_overline`, `exam_variant_title`, `exam_info_fact_duration_cap`,
`exam_info_fact_tasks_cap`, `exam_info_fact_max_cap`, `exam_info_fact_cd`,
`exam_info_structure_overline`, `exam_info_section_counts`,
`exam_info_section_closed_only`, `exam_info_rules_overline`,
`exam_info_rule_time`, `exam_info_rule_navigation`,
`exam_info_rule_listening`, `exam_info_rule_written`, `exam_info_rule_scoring`,
`exam_info_rule_online`, `exam_info_skip_listening`,
`exam_info_skip_listening_supporting`, `exam_disclaimer`, `exam_estimate_note`,
`exam_info_download_hint`, `exam_info_notif_off_hint`, `exam_info_download`, `exam_info_downloading`,
`exam_download_paused_offline`, `exam_download_failed`,
`exam_download_verify_failed`, `exam_download_storage`,
`exam_download_needs_internet`, `exam_download_done_cd`,
`exam_download_notif`, `exam_info_ready_hint`, `exam_info_start`,
`exam_info_running_hint`, `exam_info_resume`, `exam_info_resume_download`,
`exam_info_retake_hint`, `exam_info_retake`, `exam_info_last_result`,
`exam_variant_updated`, `exam_variant_unavailable`, `exam_start_confirm_title`,
`exam_start_confirm_body`, `exam_start_confirm_skip`, `exam_start_confirm`,
`exam_start_other_running_title`, `exam_start_other_running_body`,
`exam_start_other_running_open`, `exam_start_needs_internet`,
`exam_start_rate_limited`, `exam_start_failed`, `common_cancel`,
`common_retry`, `common_back`, `exam_size_mb`.

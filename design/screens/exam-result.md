# Mock exams — result ("Təxmini bal")

Stories: EXAM-15 (server scoring, "Təxmini bal", never "DİM balı"), EXAM-16
(entry to self-assessment, split), EXAM-17 (result per section), EXAM-19
(subject estimate, max 100), EXAM-20 ("Təkrar cəhd"), EXAM-25 (disclaimer),
OQ-119 (skip / failed listening excluded with a note), OQ-125 (active day,
no XP). Formula (DİM, `mock-exams.md` §2.1): English = 100/37 · (2·W + C);
auto part = 100/37 · C (max 62,2), written part = 100/37 · 2·W (max 37,8).
Tokens 3.1.0. Components: `components.md` §1, §8, §11, §12, §26 (streak row
only), §35a, §45, §46. Copy: `copy/az.md` "exam_result_", "exam_split_".

## Decided questions

- **OQ-D42** (`exam-info.md`): with skipped or failed listening the score is
  shown out of the reduced maximum ("58,4 / 83,8"); the server returns
  `maxScore` with the estimate. **Decided by Sabit 2026-11-21: as proposed.**
- **OQ-D47** (`exam-self-assessment.md`): **Decided by Sabit 2026-11-21: yes** — "Qiymətləri dəyiş"
  is shown once every written task is assessed.
- Assumption: the server returns all numbers already rounded to 0.1 (OQ-129
  rounding); the client only formats them with a decimal comma and never
  recomputes (server authoritative).

## Purpose

Say honestly how the sınaq went: one estimated score, how much of it the app
checked and how much the student assessed, where the points were lost, and the
next step (assess the written tasks, then learn from the review).

## Entry points

- After submit (H) or auto-submit (G) in `exam-attempt.md`, when the server has
  scored the attempt.
- Back from `exam-self-assessment.md` (recomputed estimate).
- History row (`exam-catalogue.md`), catalogue card with a result.
- Not reachable before the server scored the attempt (offline submit shows the
  attempt's offline view instead).

## Layout (top to bottom, 360dp)

1. Top app bar (§12): close icon (`common_close`) → catalogue; title
   `exam_result_title` ("Nəticə").
2. Scrollable column, gutters `space.layout.screenGutter`:
   1. Optional banner (§8 full, info): `exam_assess_saved_offline` while marks
      wait for sync.
   2. `space.lg`, line `exam_history_row_title` ("Sınaq №1 · İngilis dili")
      `type.titleMedium` `scheme.onSurfaceVariant`; `space.xs`, chips (as in
      the catalogue): `exam_mode_subject`, `exam_retake_label` (if a retake),
      `exam_history_date`.
   3. `space.xl`, **score block** (no card, on paper, start-aligned):
      - overline `exam_result_overline` ("TƏXMİNİ BAL");
      - `space.xs`, row on one baseline: score `typography.custom.numeralLarge`
        (Fraunces SemiBold 56) `scheme.onSurface`, e.g. "67,7" (101 / 131dp);
        `space.xs`; `exam_result_max` (" / 100") `type.headlineSmall`
        `scheme.onSurfaceVariant`. Never a count-up (Fraunces has no tabular
        figures); changes after self-assessment cross-fade
        (`motion.exam.scoreChange`).
      - `space.xs`, 18dp `info` icon + `exam_result_not_official` ("Bu, rəsmi
        DİM nəticəsi deyil.") `type.bodyMedium` `scheme.onSurfaceVariant`
        (EXAM-15: always visible, right under the number).
      - One block, TalkBack `exam_result_score_cd`.
   4. Optional `space.md`, exam note (§46), at most one, by priority:
      `exam_result_finalized` (server finalized, never submitted) >
      `exam_result_auto_submitted` (time up) > `exam_result_listening_failed`
      > `exam_result_listening_skipped`. (If listening was excluded **and** the
      time ran out, both notes show; the listening note is about the score.)
   5. `space.xl`, **score split bar** §45 with the legend (auto / self /
      unassessed).
   6. `space.lg`, **assessment card** — only when `unassessedCount > 0`: flat
      card (§0), padding `space.lg`: 24dp `rate_review` icon, title
      `exam_result_assess_title` ("Yazılı tapşırıqları qiymətləndir")
      `type.titleMedium` (229 / 298dp → wraps at 1.3), body
      `exam_result_assess_body` ("Qiymətləndirməsən, 7 yazılı tapşırıq 0
      sayılır.") `type.bodyMedium`, `space.md`, primary pill
      `exam_result_assess_cta` ("Qiymətləndir (7)"), full card width.
      When everything is assessed (OQ-D47): instead a text button
      `exam_result_edit_marks` ("Qiymətləri dəyiş") under the legend.
   7. `space.xl`, overline `exam_result_sections_overline` ("BÖLMƏLƏR"); one
      row per section, hairlines between (not clickable): title = section name
      `type.titleMedium`; supporting lines `type.bodyMedium`
      `scheme.onSurfaceVariant`: `exam_result_closed` ("Qapalı: 2 / 3 düzgün")
      and, if the section has written tasks, `exam_result_written` ("Yazılı:
      1,7 / 3") or `exam_result_written_unassessed` ("Yazılı:
      qiymətləndirilməyib"); an excluded section reads
      `exam_result_section_excluded` ("Bala daxil edilməyib"). Then
      `space.md`, one total line `exam_result_closed_summary` ("Qapalı
      tapşırıqlar: 18 düzgün · 4 səhv · 1 cavabsız"), wraps.
   8. `space.lg`, **streak row** (§26 streak row, no XP, OQ-125 B): shown when
      the server says this submit made today active; same keys and
      `motion.progression.streakIgnite` as the quiz result.
   9. `space.xl`, buttons, full width, gap `space.md`:
      - If the assessment card is shown: secondary `exam_result_review`
        ("Təhlilə bax"), then secondary `exam_result_back` ("Sınaqlara qayıt").
        (The card holds the one primary action.)
      - Otherwise: primary `exam_result_review`, secondary `exam_result_back`.
   10. `space.lg`, disclaimer note §46 `exam_disclaimer` (EXAM-25 on the
       result screen).
   11. `space.xl` + navigation inset.

Phase 2 (full buraxılış): a total block ("Təxmini bal: 214,3 / 300") above
per-subject blocks of the same anatomy; out of scope here.

## States

| State | What the user sees |
| --- | --- |
| Loading (opened from history, not cached) | App bar + loading view after 300ms |
| Success, written tasks unassessed (first open after submit) | Score = auto part only; split shows segment 1 + "Qiymətləndirilməyib: 7 tapşırıq"; assessment card |
| Partly assessed | Split with both segments + "Qiymətləndirilməyib: 3 tapşırıq"; card with the remaining count |
| Fully assessed | Both segments; no card; optional "Qiymətləri dəyiş" |
| Estimate updated after assessment | Score cross-fades; split animates old → new width; snackbar `exam_result_updated` |
| Retake | Chip "Təkrar cəhd" (EXAM-20); everything else the same |
| Auto-submitted (time up) | Note `exam_result_auto_submitted` |
| Server finalized (abandoned, late window passed) | Note `exam_result_finalized`; only synced answers counted |
| Listening skipped (OQ-D42) | Score " / 83,8"; note `exam_result_listening_skipped`; the listening section row reads "Bala daxil edilməyib" |
| Audio failed (EXAM-10) | Same as skipped with `exam_result_listening_failed`; never a silent 0 |
| Score 0 | Same layout; message-free (no jokes, no praise); the assessment card still invites written marks |
| Offline, cached result | Compact info line `common_offline_banner`; assessment still possible (marks queue); "Təhlilə bax" works if the review is cached, otherwise its own offline state |
| Error (history open, no cache) | State view `error`, `exam_result_error`, `common_retry` |
| Assessment sync pending | Info banner (2.1); the estimate shown is the server's last value (never recomputed on the client) |
| Key corrected later (EXAM-15) | Score unchanged; the review marks the task (`exam_review_corrected`) |

## Interactions and animations

- Split bar fill once (`motion.exam.scoreSplitFill`); reduced motion: final.
- Streak ignite as in `progression.md` A.
- Assessment card / CTA → `exam-self-assessment.md` (shared axis X), first
  unassessed task.
- "Təhlilə bax" → `exam-review.md` (shared axis X).
- Close, "Sınaqlara qayıt", system back → catalogue (the attempt screen is
  not in the back stack).
- Analytics `exam_result_view {score_bucket}`.

## Accessibility

- `exam_result_score_cd`: "Təxmini bal: 67,7, 100 baldan. Bu, rəsmi DİM
  nəticəsi deyil." (decimal read from the text "67,7"; android-dev passes the
  number as words-safe text, not a float).
- Split: `exam_split_cd` ("Avtomatik yoxlanılıb: 46,9 baldan 62,2. Özün
  qiymətləndirdin: 20,8 baldan 37,8.").
- Section rows: one node each; heading for the overline.
- Font scale 1.3 on 360dp: score row 131 + 80dp fits on one line; all labels
  wrap (`copy/az.md`).

## String keys

`exam_result_title`, `exam_history_row_title`, `exam_mode_subject`,
`exam_retake_label`, `exam_history_date`, `exam_result_overline`,
`exam_result_max`, `exam_result_not_official`, `exam_result_score_cd`,
`exam_result_finalized`, `exam_result_auto_submitted`,
`exam_result_listening_failed`, `exam_result_listening_skipped`,
`exam_split_auto`, `exam_split_self`, `exam_split_unassessed_count`,
`exam_split_value`, `exam_split_cd`, `exam_result_assess_title`,
`exam_result_assess_body`, `exam_result_assess_cta`, `exam_result_edit_marks`,
`exam_result_sections_overline`, `exam_result_closed`, `exam_result_written`,
`exam_result_written_unassessed`, `exam_result_section_excluded`,
`exam_result_closed_summary`, `exam_result_review`, `exam_result_back`,
`exam_disclaimer`, `exam_result_updated`, `exam_assess_saved_offline`,
`exam_result_error`, `common_offline_banner`, `common_close`, `common_retry`;
streak row keys in `progression.md`.

## Data needed (for backend-dev)

`estimate`, `maxScore`, `autoPoints`, `autoMax`, `selfPoints`, `selfMax`,
`unassessedCount`, per section `{name, closedCorrect, closedTotal,
writtenSum, writtenTotal, excluded}`, `closedWrong`, `closedBlank`,
`finalizedBy`, `listeningExcluded` + `listeningExclusionReason` (`skipped` |
`audio_failed`; names as in the backend draft `ExamDtos.kt`),
`isRetake`, `progress` (streak only). All decimals pre-rounded to 0.1.

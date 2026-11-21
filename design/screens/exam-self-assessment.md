# Mock exams — self-assessment of written tasks ("Qiymətləndirmə")

Stories: EXAM-16 (self-assessment), EXAM-11 (written answers, empty allowed),
EXAM-15 (unassessed = 0, "Qiymətləndirilməyib"), EXAM-18 (transcript and
passage after submit), EXAM-22. Decisions: OQ-112 (B: 0, ⅓, ½, ⅔, 1 against the
model answer and the official criteria; A2 split always visible), OQ-125 (no XP
from self-assessed marks).
Tokens 3.1.0. Components: `components.md` §1, §8, §11, §12, §41, §42 (review
form), §44, §46. Copy: `copy/az.md` "exam_assess_", "exam_mark_".

## Open questions

- **OQ-D47 — can marks be changed after saving?** Proposal: yes, any time from
  the result or the history (no ranking, OQ-125 keeps XP off them; the estimate
  is recomputed each time). EXAM-16 only says "assessment stays possible later".
  Decides: business-analyst / Sabit.
- Assumption: criteria come from the bank per task as an ordered list of
  `{mark, description}` (e.g. "1 — fikir tam və düzgün ifadə olunub"); the
  description language follows the content (Azerbaijani for criteria, English
  for the model answer of the English exam).

## Purpose

Let the student mark their own written answers honestly and quickly, with the
model answer and the official criteria side by side, so the estimate covers the
whole exam.

## Entry points

- `exam-result.md`: assessment card "Qiymətləndir (7)" → first unassessed task.
- `exam-review.md`: a written task's "Qiymətləndir" / "Dəyiş" → that task.
- History → result → same card (later assessment, EXAM-16).

## Layout (top to bottom, 360dp)

Full-screen flow.

1. Top app bar (§12): close icon (`exam_assess_close_cd`) → back to where it was
   opened; title `exam_assess_title` ("Qiymətləndirmə", 159 / 207dp of 264dp).
   The progress is **not** in the bar: next to it the title would get ~206dp and
   truncate at 1.3.
2. Horizontal pager, one page per written task in exam order (Phase 1: 7),
   each a vertical scroll, gutters `space.layout.screenGutter`:
   1. On the **first page only**, once per attempt: exam note (§46)
      `exam_assess_intro` ("Cavabını nümunə ilə müqayisə et və meyarlara görə
      qiymət seç. Özünə dürüst qiymət ver — bu bal yalnız sənin üçündür.").
   2. `space.md`, task line: start `exam_assess_task_label` ("Tapşırıq 4 ·
      Dinləmə") `type.labelMedium` `scheme.onSurfaceVariant`, weight 1, wraps;
      end `exam_assess_progress` ("2 / 7") `type.titleSmall` tabular
      `scheme.onSurface`, cd `exam_assess_progress_cd`.
   3. `space.sm`, stem `type.titleMedium` (as in the attempt).
   4. Context link (groups only): text button with 18dp icon —
      reading: `exam_assess_show_passage` ("Mətnə bax", `menu_book`); listening:
      `exam_assess_show_transcript` ("Transkriptə bax", `subject`). Opens a
      full-height bottom sheet with the passage (§41, text selection on) or
      the transcript (speaker label `type.labelLarge` + line `type.bodyLarge`,
      `space.sm` between turns); the listening sheet also has the review player
      (§42 review form) if OQ-D44 is accepted.
   5. `space.lg`, label `exam_assess_your_answer` ("Sənin cavabın")
      `type.labelMedium` `scheme.onSurfaceVariant`; box `shape.medium`, face
      `scheme.surfaceContainerHighest`, padding `space.md`, text
      `type.bodyLarge` `scheme.onSurface` (as typed, wraps, selectable). Empty:
      text `exam_assess_no_answer` ("Cavab yazılmayıb — kağızda həll etmisənsə,
      onunla müqayisə et.") in `scheme.onSurfaceVariant` with a 20dp
      `edit_off` icon.
   6. `space.md`, label `exam_assess_model_answer` ("Nümunəvi cavab"); box
      `shape.medium`, face `scheme.secondaryContainer`, text
      `scheme.onSecondaryContainer` `type.bodyLarge`; a 20dp `lightbulb` icon
      before the label. Several accepted model answers: each in its own box,
      label `exam_assess_model_answer_n` ("Nümunə 2").
   7. `space.lg`, label `exam_assess_criteria` ("Meyarlar") `type.titleSmall`;
      list, one row per criterion: leading mark chip (pill, 36dp min width,
      `scheme.surfaceContainerHigh`, `type.labelLarge` tabular: "1", "⅔", "½",
      "⅓", "0") + `space.md` + description `type.bodyMedium` `scheme.onSurface`,
      wraps; `space.sm` between rows. Order as in the data (highest mark first).
   8. `space.xl`, label `exam_assess_pick` ("Qiymətin") `type.titleSmall`;
      `space.sm`, mark picker §44; `space.sm`, `exam_assess_points` once chosen
      ("1,33 / 2 xal").
   9. `space.xl` + bottom bar height.
3. **Bottom bar** (pinned, like the attempt's C): secondary `exam_prev`
   ("Əvvəlki") / primary `exam_next` ("Növbəti"), side by side. On the **last**
   page the bar stacks two full-width pills (§1, primary on top, gap
   `space.md`): primary `exam_assess_finish` ("Qiymətləndirməni bitir", 166 /
   216dp — too wide for a 110dp half pill) and secondary `exam_prev`. "Növbəti"
   works without a mark (skipping is allowed, EXAM-16).

A tap on a criterion row's chip **selects that mark** in the picker (shortcut;
same result as tapping the tile).

## Saving

- A mark is saved on selection: locally first, then `PUT` per task (EXAM-16);
  no save button. Changing a mark = new value, same rules.
- "Qiymətləndirməni bitir" → back to the result, which shows the recomputed
  estimate (snackbar `exam_result_updated` "Təxmini bal yeniləndi"). Close / back
  at any page → same, with whatever was saved so far; unassessed tasks stay 0
  and "Qiymətləndirilməyib" (EXAM-15, EXAM-16).
- The server only accepts {0, 1/3, 1/2, 2/3, 1} (EXAM-16 400). The client only
  offers these.

## States

| State | What the user sees |
| --- | --- |
| Loading (review data with model answers, criteria; usually cached from the result) | Loading state view after 300ms |
| Error, no cache | State view `error`, `exam_review_error`, `common_retry` |
| Offline, no cache | State view `cloud_off`, `exam_review_offline` ("Təhlil üçün internet lazımdır."), `common_retry` |
| Task not yet assessed | Picker without selection; no points line |
| Task assessed earlier | Picker shows the saved mark; points line |
| Saving offline | Mark shown as chosen; compact warning banner under the app bar `exam_assess_saved_offline` ("Qiymətlər telefonda saxlanıldı, internet olanda göndəriləcək."); the result recomputes when the server accepts them |
| Save rejected (400 — client bug) | Snackbar `error_generic`; mark reverts to the server value |
| Attempt still pending (offline submit not yet scored) | Not reachable: the result card is hidden until the server has scored the attempt (the model answers come with the review data only after submit, EXAM-18) |
| Empty answer | `exam_assess_no_answer` box; picker works as usual |
| All assessed | Last page shows `exam_assess_finish`; result shows the full split |
| Rotation / process death | Same page; saved marks from Room |

## Interactions and animations

- Pager: `motion.exam.taskTransition`; reduced motion: instant.
- Mark tile: §44 (press, 150ms fill). Points line fades in 150ms.
- Context sheet: M3 bottom sheet default motion; scroll kept per sheet.
- Analytics `exam_self_assess_done {assessed_count, skipped_count}` when the
  student leaves the flow.

## Accessibility

- Page focus starts at the task line; reading order: stem, context link, your
  answer, model answer, criteria, picker, points.
- Boxes are labelled by their labels (merged: "Sənin cavabın: …",
  "Nümunəvi cavab: …").
- Criterion rows: "Üçdə iki: …" — the chip's spoken text is the mark word
  (`exam_mark_*_cd`), not the glyph. Each row is also a button
  ("seçmək üçün iki dəfə toxun" via the click label `exam_assess_pick_mark_cd`).
- Picker: radio group §44.
- Font scale 1.3: criteria wrap, chips keep 36dp min width, picker tiles
  ~59dp (labels ≤ 21dp), bottom pills fit ("Əvvəlki" / "Növbəti" ≤ 77dp of a
  110dp half pill; "Qiymətləndirməni bitir" 216dp of 280dp in the stacked
  last-page form).

## String keys

`exam_assess_title`, `exam_assess_close_cd`, `exam_assess_progress`,
`exam_assess_progress_cd`,
`exam_assess_intro`, `exam_assess_task_label`, `exam_assess_show_passage`,
`exam_assess_show_transcript`, `exam_assess_your_answer`,
`exam_assess_no_answer`, `exam_assess_model_answer`,
`exam_assess_model_answer_n`, `exam_assess_criteria`, `exam_assess_pick`,
`exam_assess_pick_mark_cd`, `exam_assess_points`, `exam_mark_0`,
`exam_mark_1_3`, `exam_mark_1_2`, `exam_mark_2_3`, `exam_mark_1`,
`exam_mark_0_cd`, `exam_mark_1_3_cd`, `exam_mark_1_2_cd`, `exam_mark_2_3_cd`,
`exam_mark_1_cd`, `exam_assess_finish`, `exam_assess_saved_offline`,
`exam_result_updated`, `exam_prev`, `exam_next`, `exam_review_error`,
`exam_review_offline`, `exam_transcript_title`, `exam_passage_overline`,
`error_generic`, `common_retry`.

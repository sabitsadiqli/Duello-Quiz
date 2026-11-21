# Mock exams — review ("Təhlil")

Stories: EXAM-18 (every task, answer, key, explanation, model answer,
criteria, transcript; filters), EXAM-15 (corrected key note), EXAM-10
(transcript only after submit), EXAM-16 (assess from the review), EXAM-22.
Decisions: OQ-112, OQ-119.
Tokens 3.1.0. Components: `components.md` §3 (compact review rows as in
`quiz-result.md`), §11, §12, §41, §42 (review form), §46, M3 filter chips.
Copy: `copy/az.md` "exam_review_", "exam_filter_".

## Open questions

- **OQ-D44 — replay the listening audio in the review?** Proposal: yes, with
  free play and seeking (the review player form of §42), because the attempt is
  over and the file is already on the phone; this is a small part of the Phase 2
  "Məşq rejimi" and costs no server work. If the package media was deleted
  (EXAM-03 cleanup), the player re-downloads the audio on tap. Alternative:
  transcript only in Phase 1. Decides: Sabit.

## Purpose

Learn from the whole sınaq: every task with what I answered, what was right and
why; written tasks with the model answer; the listening transcript I could not
see during the attempt.

## Entry points

- `exam-result.md` "Təhlilə bax"; history → result → "Təhlilə bax".
- Back → result.

## Layout (top to bottom, 360dp)

1. Top app bar (§12): back icon, title `exam_review_title` ("Təhlil").
2. **Filter chips** row, pinned under the app bar, horizontally scrollable
   inside its own row (the page never scrolls sideways), `space.sm` gaps,
   gutters: `exam_filter_all` ("Hamısı"), `exam_filter_wrong` ("Səhvlər"),
   `exam_filter_unanswered` ("Cavabsızlar"), `exam_filter_flagged`
   ("İşarələnmişlər"), `exam_filter_written` ("Yazılılar"). M3 FilterChip, single
   select, 32dp in a 48dp target, selected = `game.navIndicator` face +
   `check` + `scheme.onSurface`; each label carries its count in the content
   description. Total width ≈ 470dp at 1.0 → scrolls; the selected chip is
   scrolled into view.
3. `LazyColumn`, gutters, grouped by section in exam order:
   - Section header: section name `type.titleMedium` + range ("1–6")
     `type.bodySmall` `scheme.onSurfaceVariant`, heading.
   - **Group context card** once per group, before its tasks (flat card §0,
     padding `space.lg`, collapsed by default):
     - reading: title `exam_review_passage` ("Oxu mətni"), first 3 lines of the
       passage faded, text button `exam_review_show_passage` ("Mətni göstər") /
       `exam_review_hide` ("Gizlət"); expanded = the full passage (§41, text
       selection on);
     - listening: title `exam_transcript_title` ("Transkript"), the review
       player (§42 review form, OQ-D44), text button
       `exam_review_show_transcript` ("Transkripti göstər") / `exam_review_hide`;
       expanded = speaker turns (speaker `type.labelLarge`, text
       `type.bodyLarge`); excluded listening adds the note
       `exam_review_excluded_note`.
   - **Task card**, one per task, `scheme.surfaceContainerLow`,
     `shape.medium`, padding `space.lg`, gap `space.lg` between cards:
     1. Task line `exam_review_task_label` ("Tapşırıq 14 · Qapalı")
        `type.labelMedium` `scheme.onSurfaceVariant`; end: 16dp filled `flag`
        `scheme.primary` if it was flagged (cd `exam_review_flagged_cd`), and
        the outcome word: `quiz_option_outcome_correct` "Düzgün" (`game.correct`
        + check), `quiz_option_outcome_wrong` "Səhv" (`game.wrong` + cross),
        `exam_review_outcome_blank` "Cavabsız" (`scheme.onSurfaceVariant` +
        `remove`), written: `exam_review_self_mark` ("Qiymətin: ⅔") or
        `exam_review_unassessed` ("Qiymətləndirilməyib"). If label and outcome
        do not fit on one line (e.g. "Qiymətləndirilməyib" 173dp at 1.3), the
        outcome moves to its own line under the label, start-aligned (never
        truncated).
     2. Stem `type.titleMedium` (restricted markup, gaps, image thumbnail as
        in the quiz review).
     3. Closed: labels + compact option rows exactly as `quiz-result.md`
        review (`exam_review_your_answer` "Sənin cavabın" + wrong row,
        `exam_review_correct_answer` "Düzgün cavab" + correct row; correct
        answer chosen → only the correct row with label
        `exam_review_your_answer_correct` "Sənin cavabın — düzgün"; blank →
        neutral row `exam_review_not_answered` "Cavab verilməyib"). Rows show
        the letter + option text.
     4. Written: "Sənin cavabın" box and "Nümunəvi cavab" box as in
        `exam-self-assessment.md` (5, 6); criteria list collapsed behind a text
        button `exam_review_show_criteria` ("Meyarları göstər"); text button
        `exam_review_assess` ("Qiymətləndir") or `exam_review_change_mark`
        ("Qiyməti dəyiş", OQ-D47) → that task in the self-assessment.
     5. Explanation (if any): divider, label `exam_review_explanation`
        ("İzah"), text `type.bodyMedium`.
     6. Notes (exam note §46, `space.md` above): `exam_review_corrected`
        ("Bu tapşırıq sonradan düzəldilib. Balın dəyişməyib.") with the
        **current** key shown; `exam_review_excluded` ("Bala daxil edilməyib")
        for excluded listening tasks.
4. Bottom padding + navigation inset.

## States

| State | What the user sees |
| --- | --- |
| Loading (first open, not cached) | Loading view after 300ms; then cached in Room for offline use |
| Success | List as laid out, filter "Hamısı" |
| Filter with no tasks | Compact state view (§11 compact) under the chips: `check_circle`, `exam_review_filter_empty` ("Bu filtrdə tapşırıq yoxdur."), chips stay |
| Error, no cache | State view `error`, `exam_review_error` ("Təhlili yükləmək alınmadı."), `common_retry` |
| Offline, no cache | State view `cloud_off`, `exam_review_offline`, `common_retry` |
| Offline, cached | Normal; audio replay needs the local file (else `exam_audio_failed_offline` in the player) |
| 409 before submit (EXAM-18) | Not reachable from the UI; if it happens: back to the catalogue + snackbar `error_generic` (logged) |
| Media deleted (EXAM-03 cleanup) | Images and audio re-download on demand; placeholders as in the quiz |
| Listening excluded | Group note + per-task note; tasks still listed with the key and explanation |
| Return from self-assessment | Marks and outcome words updated in place; scroll position kept |

## Interactions and animations

- Chip select: list cross-fades 150ms; scroll to top of the filtered list.
- Expand / collapse cards: size animation `motion.duration.medium1`; reduced
  motion: instant.
- Analytics `exam_review_open {filter}` when a filter is applied.

## Accessibility

- Each task card is one traversal group; the outcome word is read with the task
  line ("Tapşırıq 14, qapalı, səhv"); labels precede option texts (meaning never
  by colour).
- Expand buttons expose expanded / collapsed state.
- Transcript turns: "A: …" read in order; the passage keeps the marked-word
  semantics (§41).
- Chips: role Checkbox-like FilterChip with selected state and count
  ("Səhvlər, 4 tapşırıq").
- Font scale 1.3: chips scroll, all card text wraps.

## String keys

`exam_review_title`, `exam_filter_all`, `exam_filter_wrong`,
`exam_filter_unanswered`, `exam_filter_flagged`, `exam_filter_written`,
`exam_filter_count_cd`, `exam_review_passage`, `exam_review_show_passage`,
`exam_review_hide`, `exam_transcript_title`, `exam_review_show_transcript`,
`exam_review_excluded_note`, `exam_review_task_label`,
`exam_review_flagged_cd`, `quiz_option_outcome_correct`,
`quiz_option_outcome_wrong`, `exam_review_outcome_blank`,
`exam_review_self_mark`, `exam_review_unassessed`, `exam_review_your_answer`,
`exam_review_your_answer_correct`, `exam_review_correct_answer`,
`exam_review_not_answered`, `exam_assess_your_answer`,
`exam_assess_model_answer`, `exam_review_show_criteria`, `exam_review_assess`,
`exam_review_change_mark`, `exam_review_explanation`, `exam_review_corrected`,
`exam_review_excluded`, `exam_review_filter_empty`, `exam_review_error`,
`exam_review_offline`, `exam_audio_failed_offline`, `error_generic`,
`common_retry`, `common_back`.

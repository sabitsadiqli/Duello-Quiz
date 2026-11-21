# Mock exams — attempt (task screen, navigator, submit)

Stories: EXAM-04 (timer), EXAM-05 (navigation), EXAM-06 (closed task), EXAM-09
(reading groups), EXAM-10 (listening), EXAM-11 (written), EXAM-12 (flag),
EXAM-13 (background, app kill, network loss), EXAM-14 (submit, auto-submit),
EXAM-21 (no keys on the client), EXAM-22 (display, TalkBack). Decisions:
OQ-117 (60 min, 2 min grace, no pause), OQ-118 (offline answering, resume, 24 h
late upload), OQ-119 (2 plays, no seek, pause allowed, counted after 10 s),
OQ-121 (local `.m4a`, Media3).
Tokens 3.1.0 (`motion.exam`, `size.exam*`). Components: `components.md` §1,
§3 + §3a, §4, §8, §9, §10, §11, §39 … §43, §46. Copy: `copy/az.md` "exam_"
(attempt, player, navigator, submit).

## Open questions

- **OQ-D41 — display deadline** (`exam-info.md`): the timer counts to
  `endsAtEpochMs` (start + 60 min), never to the deadline with grace.
- **OQ-D43 — app killed during a counted play.** EXAM-10 says the play is
  "used" if > 10 s were played. Proposal: it stays used **and** resumes paused
  at the saved position (position saved every 1 s), exactly like a pause — the
  student did not choose to lose the rest of the recording. Without this an app
  kill at 2:50 of 3:05 costs a whole play. Decides: Sabit / business-analyst;
  backend stores `positionMs` per play.
- **OQ-D45 — background reminder.** The timer runs in the background (no
  pause). Proposal for Phase 1: **no** local notification (needs the
  notification permission, which the app asks for the daily reminder only).
  Alternative: one local notification at 10 min left while the app is in the
  background ("Sınaqda 10 dəqiqə qaldı"). Decides: Sabit.
- Assumption: task instructions and stems of the English exam are in English
  (content); all UI chrome stays Azerbaijani.
- Assumption: the gap in a stem is the token `{gap}` of the restricted markup
  (OQ-116, backend / content-writer).

## Purpose

Let the student work through 30 tasks in any order for 60 minutes, exactly
like a paper exam: always see the time, never lose an answer, never be
surprised by the end.

## Entry points

- `exam-info.md` "Başla" (new attempt, task 1) or "Davam et" (resume).
- Oyna running banner / row, catalogue running card (resume at the last task).
- App start after a kill with a running attempt: Oyna shows the banner; one tap
  resumes (EXAM-13). The app does not jump into the attempt by itself.

## Layout (top to bottom, 360dp)

Full-screen flow (no bottom navigation), edge-to-edge, background
`scheme.background`.

### A. Exam header (pinned, never scrolls)

Container `scheme.surface`, min height `size.topAppBarHeight` (64dp), 1dp
`game.cardBorder` hairline at the bottom when content scrolls under it.

| Slot | Content |
| --- | --- |
| Start | Close icon button 48dp, `exam_close_cd` → leave dialog (E) |
| Centre, weight 1 | Line 1 `exam_progress` ("14 / 30") `type.titleMedium` tabular `scheme.onSurface`; line 2 section name (server) `type.labelMedium` `scheme.onSurfaceVariant`, wraps (max 2 lines) |
| End | Timer pill §39 (91 / 105dp reserved), `space.xs`, grid icon button 48dp (`grid_view`, `exam_grid_cd`) → navigator §40 |

No jumping between sections: the section line uses `minLines` = the largest line
count of **all** section names of this attempt at the current width and font
scale (android-dev: measure once with `TextMeasurer`). Width at 360dp: centre
budget 157dp at 1.0 / 143dp at 1.3; "Qrammatika və lüğət" = 136 / 176dp →
1 line at 1.0, 2 lines at 1.3 (header grows to ~80dp for the whole attempt).

Under the header: the **time line** (§39 / §4 bar), full width with gutters.

Under the line, optional banners (compact §8 variant — vertical space is the
scarce resource here, so the compact form is used outside tab roots on purpose):
- Offline: `game.bannerWarningContainer`, `cloud_off`, `exam_offline_banner`
  ("Oflayn — cavablar telefonda saxlanılır"). Answering is never blocked
  (EXAM-13).
- Only one banner at a time; audio failures live inside the player (§42).

### B. Task body — horizontal pager of tasks

One page per task in exam order; swipe, "Növbəti" / "Əvvəlki" and grid jumps all
move the pager (`motion.exam.taskTransition`). Images are decoded only for the
visible page ± 1 (EXAM-13 low memory). Each page is its own vertical scroll
container, gutters `space.layout.screenGutter`.

#### B1. Common task frame (every task)

1. `space.lg`, **task line**: `exam_task_label` ("Tapşırıq 14 · Qapalı" /
   "· Yazılı") `type.labelMedium` `scheme.onSurfaceVariant`, weight 1; end:
   **flag toggle** — pill `shape.full`, 32dp high in a 48dp target, padding
   `space.md`: unflagged = 18dp outlined `flag` `scheme.onSurface` + label
   `exam_flag` ("İşarələ") `type.labelLarge` `scheme.onSurface`, 1dp
   `scheme.outline`; flagged = face `scheme.primaryContainer`, filled `flag` +
   `exam_flagged` ("İşarələnib") in `scheme.onPrimaryContainer`, no border.
   Width at 1.3: ≤ 94 + 18 + 8 + 24 = 144dp; the task line wraps first.
2. Optional instruction (from data) `type.bodyMedium` `scheme.onSurfaceVariant`,
   `space.sm` above.
3. `space.md`, **stem** `type.titleMedium` `scheme.onSurface`, wraps, restricted
   markup and `{gap}` (§41). Optional image as in the quiz (16:9, max 200dp,
   dominant colour placeholder, alt text).
4. Answer area (B2 closed / B4 written).
5. `space.xl` bottom padding (+ the bottom bar height, so nothing hides under
   it).

#### B2. Closed task (EXAM-06)

- `space.lg`, option cards §3 / §3a, gap `space.sm`, letters A–E from the data.
- Tap = Chosen at once, saved locally (Room, per-task revision) and queued
  (EXAM-13); light tick; no other feedback (EXAM-06).
- When an option is chosen: `space.sm`, end-aligned text button
  `exam_clear_answer` ("Cavabı sil") with 18dp `backspace` icon; tap → not
  answered (new revision); no confirmation (it is one tap to redo).

#### B3. Reading group (EXAM-09)

- Pinned at the top of the page (under the banners, does not scroll):
  **segmented toggle** — two segments `exam_tab_task` ("Tapşırıq") and
  `exam_tab_passage` ("Mətn"), `shape.component.segmentedToggle`, 40dp high in a
  48dp target, full content width (164dp per segment), 1dp `scheme.outline`;
  selected segment face `game.navIndicator`, label `type.labelLarge` Bold
  `scheme.onSurface` + 18dp `check`; unselected label `scheme.onSurfaceVariant`.
  `space.sm` under it.
- **Tapşırıq** shows B1 + B2 / B4 for this task. **Mətn** shows the passage
  (§41) in the same scroll area; the toggle stays visible so the way back is one
  tap.
- Which segment opens: the **first time** the student reaches any task of a
  group in this attempt → Mətn; afterwards → Tapşırıq. Moving to another task
  always lands on Tapşırıq. The passage scroll position is per group (§41).
- While Mətn is shown, horizontal swipes are disabled (reading must not flip the
  task); "Növbəti" / "Əvvəlki" still work and land on Tapşırıq.
- `motion.exam.segmentSwitch` (cross-fade 150ms).
- **≥ 600dp (tablet, large landscape)**: no toggle; split view — passage
  start pane 55 %, task end pane 45 %, each its own scroll, 1dp
  `game.cardBorder` divider.

#### B4. Written task (EXAM-11)

- `space.lg`, written field §43. Empty is fine (the student may write on paper;
  EXAM-11, EXAM-16).
- While the keyboard is open: the bottom bar (C) hides (more room for text) and
  comes back when it closes; the listening player collapses to its mini form
  (B5).

#### B5. Listening group (EXAM-10)

- Pinned at the top of every page of the group (does not scroll): the
  listening player §42, full form (~124dp at 1.0). One player state for the
  group; changing tasks inside the group never stops or restarts it.
- **Mini form** (keyboard open, or the task scrolled by > 48dp): 56dp row —
  play/pause button 40dp + plays text + elapsed time; tap the row (outside the
  button) → full form. Same states as §42.
- Skipped listening (OQ-119 / OQ-D42): the group's tasks are not in the pager
  (the pager starts at task 7); the navigator shows `exam_grid_skipped`.

### C. Bottom bar (pinned)

`scheme.surface`, 1dp `game.cardBorder` on top, padding `space.sm` vertical /
gutters + navigation inset. Two pills side by side, gap `space.md`, each
(328 − 12) / 2 = 158dp:
- Start: secondary `exam_prev` ("Əvvəlki") with leading 18dp `chevron_left`;
  disabled on the first task of the pager.
- End: primary `exam_next` ("Növbəti") with trailing 18dp `chevron_right`; on the
  **last** task it becomes primary `exam_finish` ("Sınağı bitir") without an
  icon → submit dialog (F).
Widths at 1.3: "Sınağı bitir" 100dp of 110dp label budget — fits.

### D. Navigator

Grid button → §40 sheet (timer visible in the sheet header; "Sınağı bitir" at
its bottom → F).

### E. Leave dialog (EXAM-05)

Close icon or system back. Dialog §9, actions stacked (three):
- Title `exam_leave_title` ("Sınaqdan çıxırsan?"), body `exam_leave_body`
  ("Vaxt dayanmır: sınaq 42 dəq sonra avtomatik bitəcək. Cavabların
  saxlanılır.").
- Actions top → bottom: `exam_leave_later` ("Sonra davam et") → catalogue
  (Oyna shows the running banner); `exam_finish` ("Sınağı bitir") → F;
  `common_cancel` ("Ləğv et") → stay. Back / scrim = cancel.
- The timer keeps running behind the scrim; if the time ends while it is open,
  the dialog closes and G starts.

### F. Submit confirmation (EXAM-12, EXAM-14)

Dialog §9: title `exam_submit_title` ("Sınağı bitirirsən?"); body line 1
`exam_submit_counts` ("Cavabsız: 7 · İşarələnib: 3") `type.titleSmall`
`scheme.onSurface` — or `exam_submit_all_answered` when both are 0; line 2
`exam_submit_body` ("Bitirəndən sonra cavabları dəyişə bilməyəcəksən.").
Empty written tasks count as **cavabsız** in this dialog (the student should
notice them) but are still assessable later. Actions `common_cancel` /
`exam_submit_confirm` ("Bitir"). Confirm → H.

### G. Time is up (EXAM-14 auto-submit)

At `endsAtEpochMs` (server-offset clock), whatever is open:
1. All input stops at once (options, field, player, flag); open dialogs and the
   navigator close; the keyboard hides.
2. A blocking full-screen state (§11, no close icon, back does nothing) over
   the attempt: icon `timer_off` in `game.timerWarning`, title `exam_time_up_title`
   ("Vaxt bitdi"), body `exam_time_up_body` ("Sınaq avtomatik bitirildi.
   Cavabların göndərilir…") with a loading indicator. The client flushes the
   queue and calls submit (inside the server's 2-min grace).
3. Success → result (`exam-result.md`) with `exam_result_auto_submitted`.
4. Offline / failure → the same view changes to: title `exam_time_up_title`,
   body `exam_submit_offline_body` ("Cavabların telefonda saxlanılıb. İnternet
   qayıdanda avtomatik göndərəcəyik."), note `exam_submit_late_window`
   ("24 saat ərzində internetə qoşulmasan, yalnız göndərilmiş cavablar
   sayılacaq."), secondary pill `exam_back_to_catalogue` ("Sınaqlara qayıt").
   Background retry (WorkManager, network constraint) for 24 h (OQ-118).
TalkBack: assertive announcement `exam_time_up_title` once.

### H. Submitting (user submit)

Full-screen state (§11): loading indicator + `exam_submitting` ("Cavablar
göndərilir…"); back disabled. Success → result (shared axis X, the attempt is
removed from the back stack). Offline → same offline view as G4 but with title
`exam_submit_offline_title` ("Nəticə internet olanda hesablanacaq"). The attempt
is read-only from the moment of confirm (EXAM-14).

## States

| State | What the user sees |
| --- | --- |
| Opening a resumed attempt (Room + package parse > 300ms) | Header with the live timer at once; body loading indicator + `exam_attempt_loading` ("Sınaq açılır…") |
| Active | As laid out; timer counting |
| ≥ 10 min left | Normal timer colours |
| 30 / 10 / 1 min left (`motion.exam.timeNoticesMin`) | Snackbar short `exam_time_notice` ("10 dəqiqə qaldı") above the bottom bar + polite announcement; once each; skipped if the threshold was passed while the app was away (no stale notice on return) |
| ≤ 10 min left | Timer pill and time line `game.timerWarning` (no pulse) |
| Offline | Compact warning banner (A); everything works; answers queue |
| Back online | Banner collapses; queue syncs in order (no UI unless it fails) |
| Sync rejected (400 for a malformed answer — client bug) | Answer kept locally; logged non-fatal; no user message (the student cannot fix it); the review later shows the server's view |
| Answers merged from another device (EXAM-13) | Snackbar `exam_merged_other_device` ("Başqa cihazdakı cavablar əlavə olundu."); the open task re-renders if it changed |
| App backgrounded → foreground | Remaining time recomputed from the deadline; if past `endsAt` → G at once; audio keeps playing in the background (§42) |
| App killed → reopened | Oyna running banner → resume at the last task, same segment, passage scroll, written text, flags; audio paused at the saved position (OQ-D43) with `exam_audio_restored` |
| Deadline passed while the app was dead | Opening the attempt goes straight to G |
| Package files missing (storage cleared) | Blocking state view: `folder_off`, title `exam_package_missing_title` ("Sınağın faylları tapılmadı"), body `exam_package_missing_body` ("Davam etmək üçün yenidən yüklə. Vaxt gedir: 42 dəq qalıb."), primary `exam_info_download` with progress; header timer keeps running; answers already in Room are kept |
| Image failed | As in the quiz (`quiz_image_failed`), task still answerable |
| Audio states | §42 table (re-download once, failure without consuming plays, volume hint, interruptions) |
| Attempt closed on the server (409 `attempt_closed`: submitted on another device, or finalized after the late window) | Dialog: title `exam_attempt_closed_title` ("Bu sınaq artıq bitib"), body `exam_attempt_closed_body`, action `exam_open_result` → result |
| Read-only (after confirm / time up) | Never interactive again; reopened attempts go to the result |
| Rotation / theme / font change | Same task, segment, scroll (by text offset), selection, written text, audio state |
| TalkBack on | Same 60 min (server rule); see Accessibility |
| Low memory device (2 GB) | Pager keeps ± 1 page; images released off-screen; audio player is the only long-lived object |
| Empty | Not reachable (an attempt always has tasks) |

## Interactions and animations

- Pager swipe / Növbəti / Əvvəlki / grid: `motion.exam.taskTransition`;
  reduced motion: instant.
- Option tap: §0 press + light tick. Clear: ripple.
- Flag toggle: icon cross-fade 150ms; announced "İşarələnib" / "İşarə götürüldü"
  (`exam_flag_on_cd` / `exam_flag_off_cd`).
- Segment switch: cross-fade 150ms.
- Timer: wall-clock, once per second, no animation on digits (tabular).
- Screen-on is **not** forced (EXAM-22); wall-clock timing makes sleep harmless.
- Haptics: light tick on option and mark taps only.
- Analytics: `exam_resume`, `exam_audio_play`, `exam_audio_failed`,
  `exam_submit {trigger}` (mock-exams.md §10). No task or answer text.

## Accessibility

- Focus order: header (close → progress + section, read as one node
  `exam_progress_cd` "Tapşırıq 14, cəmi 30. Qrammatika və lüğət" → timer →
  grid), banner, toggle / player, task line + flag, instruction, stem, options,
  clear button, bottom bar.
- On a task change, accessibility focus moves to the task line node
  ("Tapşırıq 14, qapalı"), then the stem (EXAM-22 order: section and number,
  task text, options).
- Options: `exam_option_cd` ("A variantı: …") + `exam_option_selected_cd`
  ("seçilib"); role RadioButton inside a `selectableGroup`.
- Timer: read on focus (`exam_timer_cd`); not a live region; the 30/10/1-min
  notices are polite announcements (EXAM-22: on demand, not every second).
- Marked words and gaps: §41 (`exam_marked_word_cd`, `exam_gap_cd`).
- Toggle segments: role Tab, selected state; player: §42.
- Navigator: §40 cell labels; summary read first.
- Time-up: assertive announcement once.
- Font scale 1.3 on 360dp: header 2-line section label (fixed for the
  attempt), stems and options wrap, bottom pills fit, flag pill fits; nothing
  truncates (`copy/az.md` widths). At 2.0 everything scrolls; the header may
  wrap the section name to 3 lines (allowed, still fixed per attempt).
- Touch targets: all ≥ 48dp (cells 48, pills 48, toggle 48, player 56).

## String keys

`exam_close_cd`, `exam_progress`, `exam_progress_cd`, `exam_timer_cd`,
`exam_grid_cd`, `exam_offline_banner`, `exam_task_label`,
`exam_task_type_closed`, `exam_task_type_written`, `exam_flag`, `exam_flagged`,
`exam_flag_on_cd`, `exam_flag_off_cd`, `exam_option_cd`,
`exam_option_selected_cd`, `exam_clear_answer`, `exam_tab_task`,
`exam_tab_passage`, `exam_passage_overline`, `exam_paragraph_cd`,
`exam_marked_word_cd`, `exam_gap_cd`, `exam_written_*` (§43), `exam_audio_*`
(§42), `exam_grid_*` (§40), `exam_prev`, `exam_next`, `exam_finish`,
`exam_leave_title`, `exam_leave_body`, `exam_leave_later`, `common_cancel`,
`exam_submit_title`, `exam_submit_counts`, `exam_submit_all_answered`,
`exam_submit_body`, `exam_submit_confirm`, `exam_time_notice`,
`exam_time_up_title`, `exam_time_up_body`, `exam_submit_offline_title`,
`exam_submit_offline_body`, `exam_submit_late_window`,
`exam_back_to_catalogue`, `exam_submitting`, `exam_attempt_loading`,
`exam_merged_other_device`, `exam_package_missing_title`,
`exam_package_missing_body`, `exam_info_download`, `exam_attempt_closed_title`,
`exam_attempt_closed_body`, `exam_open_result`, `quiz_image_failed`,
`quiz_image_cd_fallback`.

## Data needed (for backend-dev)

Start / resume response: `attemptId`, `startedAtEpochMs`, `endsAtEpochMs`
(display, OQ-D41), `deadlineEpochMs` (with grace), `serverNowEpochMs`,
`listeningSkipped`, server answers with revisions (resume / other device), audio
plays per group (`count`, `lastPositionMs`). Package: tasks without keys
(EXAM-21), groups, section names, `{gap}` / restricted markup, media manifest
with sizes and hashes.

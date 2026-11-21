# Mock exams ("Sınaqlar") — requirements

- Status: **Decided — post-MVP.** Not in the MVP scope of `CLAUDE.md`; nothing here
  blocks the MVP or the closed test. OQ-112 … OQ-131 decided by Sabit 2026-11-21
  (open-questions.md); Phase 1 = "Buraxılış — İngilis dili" (OQ-128). Build starts
  when Sabit schedules it (design → backend/Android → content).
- Owner: `business-analyst`. Created 2026-11-16 on Sabit's request.
- Decisions so far (Sabit, 2026-11-16): own original questions only, never real DİM
  tasks; DİM structure, task types and scoring are followed; naming never claims to be
  an official or real DİM exam; a DİM disclaimer is shown; English listening with
  audio is wanted.
- Sources (DİM public documents, read by the tech lead 2026-11-16):
  - **Source A** — DİM, "Qəbul imtahanının birinci mərhələsi (11-illik buraxılış
    imtahanı) üzrə imtahan fənləri, tapşırıqların sayı və balların hesablanma
    qaydası", PDF dated 2024:
    https://www.dim.gov.az/storage/postFile/tam-orta-11-illik-tehsil-seviyyesi-uzre-buraxilis-imtahani-uzre-imtahan-fenleri-tapsiriqlarin-sayi-ve-ballarin-hesablanma-qaydasipdf_29-05-2024_17-06-17.pdf
  - **Source B** — DİM, "Balların hesablanma qaydası, I–IV ixtisas qrupları", PDF
    created 2022: https://dim.gov.az/storage/iblock/aef/aef09c85301866f15711ed07abc00518.pdf
    — **verify for the current year** (OQ-129).
- Terms: `docs/requirements/glossary.md` ("Mock exams"). Quoted Azerbaijani is
  indicative; the designer owns final copy (`design/copy/az.md`, "sən" tone, OQ-041).
- Spec: no `docs/mvp-spec.md` section yet. When Sabit schedules Phase 1, the decided
  rules move to a new spec section (§12) in the same change, and this file links to it.

## 1. Summary, goals, non-goals

School leavers in Azerbaijan prepare for the DİM exams for months, mostly with paid
test collections and tutoring centres. "Sınaqlar" gives them full-length practice
exams on the phone that feel like the real thing: the same subjects, task counts,
task types, time limit and scoring formula, with an honest **estimated** score and a
review with explanations. All tasks are written by our content team.

Goals
- Realistic practice: DİM structure, official time, official scoring formula.
- Honest: estimated score clearly labelled; never presented as an official DİM exam.
- Works on low-end Android and weak networks: the whole exam is downloaded before the
  timer starts; a 3-hour attempt survives backgrounding, app kill and network loss.
- Zero extra running cost: no paid TTS, no paid AI grading, no CDN (cost rule in
  `CLAUDE.md`).

Non-goals (this document)
- Copying, paraphrasing or "reconstructing" real DİM tasks or DİM test collections.
- Rankings, leaderboards or comparison with other users (out of MVP scope and not
  proposed here).
- Paid access, IAP, ads (OQ-126).
- Human or AI grading of written answers (OQ-112).
- Russian-medium sector, "Azərbaycan dili (dövlət dili kimi)", foreign languages other
  than English, 9th-grade exam, V group aptitude exams (later, OQ-129).
- Any change to the category quiz, daily challenge or duel.

## 2. DİM exam structure (facts)

Numbers in this section are DİM's, not ours; they are the source of truth for exam
templates and scoring. Abbreviations: **C** = closed tasks correct, **K** = coded open
tasks correct, **W** = sum of written-task marks (each 0, 1/3, 1/2, 2/3 or 1).

### 2.1 Stage 1 — 11th-grade graduation exam (buraxılış)

Source A, verified 2026-11-16. 85 tasks, 3 hours, max 300 (native language +
mathematics + foreign language).

| Subject | Max | Tasks | Composition | Score formula | Max raw (check) |
| --- | --- | --- | --- | --- | --- |
| Native language (Azərbaycan dili; Rus dili in Russian-medium schools) | 100 | 30 | 10 closed (language rules); 2 reading texts (literary, publicistic), each 10 tasks = 5 written + 5 closed. Totals: 20 closed, 10 written | 5/2 · (2·W + C) | 2·10 + 20 = 40 → 100 |
| Mathematics | 100 | 25 | 13 closed; 5 coded open; 7 written open (full solution) | 25/8 · (2·W + K + C) | 2·7 + 5 + 13 = 32 → 100 |
| Foreign language (English, German, French, Russian, Arabic, Persian) | 100 | 30 | Listening: 1 text (publicistic or dialogue), 6 tasks = 3 written + 3 closed; 16 closed (grammar, vocabulary); Reading: 1 text, 8 tasks = 4 written + 4 closed. Totals: 23 closed, 7 written | 100/37 · (2·W + C) | 2·7 + 23 = 37 → 100 |
| Azerbaijani as state language (non-Azerbaijani-medium schools) | 100 | 30 | Listening: 1 text, 4 tasks = 3 closed + 1 written; Reading: 2 texts × 8 tasks = 7 closed + 1 written; 10 closed. Totals: 27 closed, 3 written | 10/3 · (C + W) | 27 + 3 = 30 → 100 |

Stage 1 rules (Source A): closed and coded tasks give 1 point per correct answer;
written tasks are marked by human markers against criteria with 0, 1/3, 1/2, 2/3 or 1;
**wrong answers never reduce the score**; subject scores are rounded to 0.1.
Note: written tasks count double in all subjects except "Azerbaijani as state
language". How the state-language score relates to the 300 total is not in the facts
we have (verify, OQ-129).

Example (mathematics): C = 10, K = 3, W = 4⅓ → 25/8 · (8.667 + 3 + 10) = 67.7.

### 2.2 Stage 2 — admission exam, specialty groups I–IV

Source B (2022) — **verify for the current year** (OQ-129). Per group: 3 subjects ×
30 tasks = 90 tasks, 3 hours, max 400. Per subject: 22 closed + 8 open; open = 5 coded
(calculation, selection or matching) + 3 written (situation-based in mathematics,
physics, chemistry, biology, geography; text-based in Azerbaijani/Russian language and
literature; source-based in history), marked 0, 1/3, 1/2, 2/3 or 1.

| Part | Formula (Source B) |
| --- | --- |
| Closed relative score | NBq = (Dq − Yq/4) · 100/33, never below 0 (Dq correct, Yq wrong closed answers; **a wrong closed answer costs 1/4**, a blank costs nothing) |
| Open relative score | NBa = (Dkod + 2 · Dyazılı) · 100/33 (Dkod correct coded tasks, Dyazılı sum of written marks) |
| Subject score | NB = NBq + NBa, rounded to 0.1, multiplied by the group weight below |
| Check | max NBq = 22 · 100/33 = 66.7; max NBa = (5 + 6) · 100/33 = 33.3; NB max 100 |

| Group | Subject 1 (weight) | Subject 2 (weight) | Subject 3 (weight) | Max |
| --- | --- | --- | --- | --- |
| I | Mathematics 1.5 | Physics 1.5 | Chemistry 1 | 400 |
| II | Mathematics 1.5 | Geography 1.5 | History 1 | 400 |
| III (DT) | Azerbaijani (Russian) language 1.5 | History 1.5 | Literature 1 | 400 |
| III (TC) | Azerbaijani (Russian) language 1.5 | History 1.5 | Geography 1 | 400 |
| IV | Biology 1.5 | Chemistry 1.5 | Physics 1 | 400 |
| V | Stage 1 + an aptitude exam only (no stage 2 test) | — | — | — |

Subject order 1–3 is DİM's priority order. Total admission score = stage 1 (300) +
stage 2 (400) = **700**.

Example (subject, weight 1.5): Dq = 18, Yq = 4, Dkod = 3, Dyazılı = 2½ →
NBq = 17 · 100/33 = 51.52; NBa = 8 · 100/33 = 24.24; NB = 75.8; weighted 113.7.
Rounding of the weighted value (e.g. 75.7 · 1.5 = 113.55) is not stated (OQ-129).

### 2.3 Not verified (assumptions, to be checked)

| Item | Assumption used in this document | OQ |
| --- | --- | --- |
| Closed tasks have 5 options (A–E) | Model supports 2–5 options per task, set per exam template | OQ-113 |
| Coded answer format (digits, decimal comma, sign, fractions, length) | Proposal in EXAM-07 | OQ-114 |
| Scoring of selection and matching tasks (all-or-nothing or partial) | All-or-nothing, 1 point | OQ-115 |
| Listening audio: how many times played, pauses | Played twice, no seeking | OQ-119 |
| Stage 2 structure and formula unchanged since 2022 | Unchanged | OQ-129 |
| 9th-grade graduation exam structure | Not analysed | OQ-129 |
| Rounding method (half up) and the weighted-value rounding | Half up, once on NB, weighted value rounded to 0.1 half up | OQ-129 |

## 3. Gap analysis against today's model

| Area | Today (MVP) | Needed for sınaqlar |
| --- | --- | --- |
| Question shape | Exactly 4 options, exactly 1 correct (`content/schema/question.schema.json`, `options.position` 1–4 in `V1__initial_schema.sql`) | 2–5 options; coded, selection, matching and written tasks; accepted-answer lists; model answers and marking criteria |
| Grouping | Independent questions | Task groups sharing one reading text or one audio; sections (subjects) inside an exam |
| Text | Short plain text (~200 chars) | Long passages (1 000–3 000 chars), paragraphs, underline/bold (language tasks refer to marked words), sub/superscript, tables, formulas |
| Media | Optional WebP image | Images, diagrams, formula rendering (OQ-116), audio (OQ-120, OQ-121) |
| Session | 10 questions, 245 s, one answer per question, no resume (OQ-017) | 85–90 tasks, 3 h, answers changeable until submit, flags, resume after app kill, offline answering (OQ-118) |
| Scoring | Correct count | Official DİM formulas per subject, stage 2 penalty, weights, self-assessed written marks, "estimate" label |
| Content IDs | `^[a-z]+-[0-9]{4}$`, category folders | Exam-template, variant, section, group and task IDs; separate bank folder |
| Review | Wrong answers only | All tasks, model answers, criteria, transcripts, per-section results |
| Reviewer | Only Sabit marks `reviewed` | Subject-teacher reviewers needed at this volume (OQ-122) |
| Android | No media player, no rich text or math renderer | Audio player (Media3 ExoPlayer → ADR), rich text renderer, formula rendering (ADR) |
| Backend | `ImageStorage` (generic `put(key, bytes, contentType)`, Caddy `/media/*`) | Reusable as is for audio keys like `audio/{uuid}.m4a`; a rename to `MediaStorage` is optional |

Recommendation: exam tasks live in **their own tables and bank folder**, not in
`questions`/`options`, so the category quiz, daily and duel invariants (4 options,
1 correct) stay untouched (OQ-127).

## 4. Task-type catalogue

| DİM task type | Our type (`taskType`) | Input UI | Auto-graded? | Phase |
| --- | --- | --- | --- | --- |
| Closed, single choice | `closed_single` | Option list (letters A–E, count per template), one selectable, change or clear until submit | Yes | 1 |
| Open coded — calculation | `coded_numeric` | Custom keypad: 0–9, "," , "−" (and "/" if allowed); format hint under the field | Yes, after normalisation (OQ-114) | 2 |
| Open coded — selection ("choose all correct") | `coded_selection` | Numbered statements with checkboxes | Yes (OQ-115) | 3 |
| Open coded — matching ("1 → a …") | `coded_matching` | One row per left item with letter chips | Yes (OQ-115) | 3 |
| Open written — short answer (language listening/reading, text-based) | `written` (`answerFormat: short_text`) | Multiline text field (optional), max 1 000 chars | **No** → self-assessment (OQ-112) | 1 |
| Open written — full solution / situation-based (maths, sciences, geography) | `written` (`answerFormat: solution`) | "Kağızda həll et" hint + optional text field; no photo upload | **No** → self-assessment | 2 |
| Open written — source-based (history), text-based (literature) | `written` (`answerFormat: short_text`) | Multiline text field | **No** → self-assessment | 3 |
| Reading text with tasks | Task group `reading` | Passage pane + tasks (EXAM-09) | — | 1 |
| Listening text with tasks | Task group `listening` (audio + transcript) | Audio player + tasks (EXAM-10) | — | 1 |

Written tasks are **18–50 %** of a subject's raw points (stage 1: native 20/40 = 50 %,
mathematics 14/32 = 44 %, English 14/37 = 38 %; stage 2: 6/33 = 18 %). Dropping them makes an estimate
meaningless, hence OQ-112.

## 5. User stories

Phase column: the phase in which the story is first needed (§8). "Blocked by" lists
open questions whose answer changes the acceptance criteria.

| ID | Story | Phase | Blocked by |
| --- | --- | --- | --- |
| EXAM-01 | As a student, I want a catalogue of exam types and sınaq variants, so that I can choose what to practise. | 1 | OQ-128, OQ-130 |
| EXAM-02 | As a student, I want an info screen for a sınaq (structure, time, scoring, disclaimer), so that I know what to expect before I start. | 1 | OQ-117, OQ-130 |
| EXAM-03 | As a student, I want the whole sınaq downloaded before the timer starts, so that a weak network never eats my exam time. | 1 | OQ-118 |
| EXAM-04 | As a student, I want to start the sınaq and see a server-backed countdown, so that the time limit is real and fair. | 1 | OQ-117 |
| EXAM-05 | As a student, I want to move freely between tasks and sections, so that I can work in my own order like on paper. | 1 | — |
| EXAM-06 | As a student, I want to choose, change and clear an answer on a closed task, so that I can correct myself until I submit. | 1 | OQ-113 |
| EXAM-07 | As a student, I want to enter a coded numeric answer with a clear format, so that a correct answer is never rejected for its spelling. | 2 | OQ-114 |
| EXAM-08 | As a student, I want selection and matching tasks, so that stage 2 sınaqlar have all DİM task types. | 3 | OQ-115 |
| EXAM-09 | As a student, I want to read a long text while answering its tasks, so that reading tasks work on a phone. | 1 | OQ-116, OQ-123 |
| EXAM-10 | As a student, I want to listen to the listening text under exam-like rules, so that I practise English listening realistically. | 1 | OQ-119, OQ-120, OQ-121 |
| EXAM-11 | As a student, I want to write (or solve on paper) the written tasks, so that I practise the full exam, not only the tests. | 1 | OQ-112 |
| EXAM-12 | As a student, I want to flag tasks to come back to, so that I can manage my time. | 1 | — |
| EXAM-13 | As a student, I want my attempt to survive backgrounding, app kill and network loss, so that I never lose a 3-hour effort. | 1 | OQ-117, OQ-118 |
| EXAM-14 | As a student, I want to submit, or have the sınaq submitted when time runs out, so that the attempt always ends cleanly. | 1 | OQ-117, OQ-118 |
| EXAM-15 | As a student, I want the server to score my answers with the official formula, so that my estimated score is correct and trustworthy. | 1 | OQ-129 |
| EXAM-16 | As a student, I want to assess my written answers against a model answer and the official criteria, so that my estimate includes them. | 1 | OQ-112 |
| EXAM-17 | As a student, I want a result screen with the estimated score per subject and section, so that I see my strengths and weaknesses. | 1 | OQ-112, OQ-130 |
| EXAM-18 | As a student, I want to review every task with the correct answer, explanation, model answer and transcript, so that I learn from the sınaq. | 1 | — |
| EXAM-19 | As a student, I want to take one subject block alone ("Fənn sınağı"), so that I can practise in less than 3 hours. | 1 | OQ-117 |
| EXAM-20 | As a student, I want to retake a sınaq and see my past attempts, so that I can track progress. | 1 | OQ-124, OQ-125 |
| EXAM-21 | As the product owner, I want the server to enforce exam rules, so that a modified client cannot get answer keys early or change results. | 1 | OQ-118 |
| EXAM-22 | As a student with large fonts, dark theme or TalkBack, I want every exam screen to stay usable, so that the sınaq is fair for me too. | 1 | OQ-116 |
| EXAM-23 | As a user, I want my exam data deleted with my account, so that my privacy rights hold for this feature too. | 1 | — |
| EXAM-24 | As a content writer, I want to import a sınaq variant that is checked against its DİM template, so that no broken or incomplete exam is published. | 1 | OQ-122, OQ-127, OQ-131 |
| EXAM-25 | As the product owner, I want naming and disclaimers that never suggest an official DİM exam, so that we respect DİM's rights and Play policy. | 1 | OQ-130 |

### EXAM-01 Catalogue

- Given the feature flag for sınaqlar is on, When the user opens "Sınaqlar" from home,
  Then exam types are listed (Phase 1: only those published, e.g. "Buraxılış — İngilis
  dili (DİM formatında)"), each with its published variants "Sınaq №1", "Sınaq №2" …
- Given a variant, When it is listed, Then it shows subjects, task count, duration,
  package size (e.g. "3,4 MB") and the user's last attempt state ("Başlanmayıb",
  "Davam edir — 1 saat 12 dəq qalıb", "Təxmini bal: 67,7").
- Given no variant is published, When the user opens the catalogue, Then the entry is
  hidden on home (no empty catalogue in production).
- Given the catalogue request fails offline, When cached data exists, Then the cached
  list is shown with "İnternet yoxdur"; downloaded variants stay startable (EXAM-03).
- Given the screen, When shown, Then the DİM disclaimer line (EXAM-25) is visible
  without scrolling on 360 dp at font scale 1.0.
- Given the flag is off, When the app runs, Then no entry, deep link or push leads to
  sınaqlar.

### EXAM-02 Info screen

- Given a variant, When the user opens it, Then the screen shows: structure per
  section (task counts by type), duration, "Fasilə yoxdur, vaxt arxa planda da
  işləyir" (if OQ-117 = no pause), scoring rule in plain words, the "estimate"
  disclaimer and the DİM disclaimer, a headphones hint for listening, and "Yüklə" or
  "Başla".
- Given the exam has written tasks, When shown, Then the screen says they are
  assessed by the user after submit ("Yazılı tapşırıqları sınaqdan sonra özün
  qiymətləndirəcəksən").
- Given stage 2 (Phase 3), When shown, Then it states that a wrong closed answer
  costs 1/4 and a blank costs nothing.

### EXAM-03 Download the exam package

- Given the user taps "Yüklə", When the package downloads, Then the client fetches
  the task JSON (no keys, no explanations, no model answers, no transcripts) and every
  image and audio file, with a progress bar and the total size.
- Given the download completes, When all files are verified (size or hash from the
  package manifest), Then "Başla" is enabled; until then the attempt has not started
  and no timer runs.
- Given the download fails midway, When the user retries, Then finished files are
  not downloaded again (HTTP cache / local store) and the download resumes.
- Given the device has too little storage, When the download starts, Then "Cihazda
  kifayət qədər yer yoxdur" with the required size is shown.
- Given a downloaded package whose variant was updated or unpublished on the server,
  When the user taps "Başla", Then the server rejects it (`variant_version_mismatch`
  / `variant_unavailable`) and the client offers to download again.
- Given a downloaded package, When unused for 30 days (proposal) or after the attempt
  is finished and reviewed, Then the client may delete media to free space; the
  review re-downloads what it needs.

### EXAM-04 Start and timer

- Given a complete package, When the user taps "Başla", Then the server creates an
  attempt and returns `startedAtEpochMs`, `deadlineEpochMs` (start + duration +
  grace, OQ-117) and `serverNowEpochMs`; the countdown uses the clock-offset method of
  the duel (DUEL-12).
- Given the start request fails offline, When the user taps "Başla", Then the attempt
  does not start and "Sınağı başlatmaq üçün internet lazımdır" is shown (OQ-118
  recommendation: start needs one online request).
- Given the user double-taps "Başla", When two requests arrive, Then one attempt
  exists (idempotency key per start).
- Given an attempt of this variant is already running, When "Başla" is tapped (other
  device or reinstall), Then the server returns the running attempt (resume), never a
  second one.
- Given the countdown, When 30 min, 10 min and 1 min remain, Then a non-blocking
  notice is shown ("10 dəqiqə qaldı").
- Given the device clock is wrong, When the countdown runs, Then it still matches the
  server deadline (offset method).

### EXAM-05 Navigation

- Given an attempt, When a task is shown, Then the header shows section name, task
  number ("Riyaziyyat · 14 / 25"), remaining time and a grid button.
- Given the grid, When opened, Then every task shows a state: not answered,
  answered, flagged, (written) text entered; grouped by section and task group.
- Given any task, When the user swipes or taps "Növbəti"/"Əvvəlki" or a grid cell,
  Then that task opens; any order is allowed, also across sections.
- Given the user presses system back during an attempt, When pressed, Then a dialog
  offers "Sınağı bitir", "Sonra davam et" (leave, timer keeps running) and
  "Ləğv et".

### EXAM-06 Closed task

- Given a closed task with N options (N per template, OQ-113), When the user taps an
  option, Then it is selected immediately, the previous selection (if any) is
  replaced, and the answer is saved locally and queued for sync (EXAM-13).
- Given a selected option, When the user taps "Cavabı sil", Then the task becomes not
  answered (needed for stage 2, where a blank is better than a guess).
- Given fast double taps on two options, When registered, Then the last tap wins and
  exactly one answer revision per tap is stored in order.
- Given the task, When shown, Then no feedback about correctness is given before the
  attempt is submitted.

### EXAM-07 Coded numeric task (Phase 2)

Proposal, blocked by OQ-114:
- Given a coded task, When the user types, Then only digits, one decimal separator,
  a leading minus and (if the task allows) one "/" are accepted, max length per
  template; "." is shown as ",".
- Given the answer "0,5", ",5", "0,50" or (if fractions allowed) "1/2", When scored,
  Then all equal the accepted value 0.5 (exact rational comparison, no floating
  point); whitespace is ignored.
- Given an input that cannot be parsed ("1,2,3", "−"), When the user leaves the task,
  Then it is kept as typed and marked "Format düzgün deyil" in the grid; it scores 0.
- Given a task with several accepted answers (e.g. two roots, content-defined),
  When scored, Then any accepted value counts as correct.

### EXAM-08 Selection and matching (Phase 3)

- Given a selection task, When the user ticks statements, Then the answer is the set
  of ticked numbers; scoring per OQ-115 (proposal: exact set = 1, else 0).
- Given a matching task, When the user assigns letters to items, Then every item has
  at most one letter; whether a letter may be used twice follows the task data.
- Given a partially filled matching task, When submitted, Then it is scored per
  OQ-115 (proposal: 0 unless complete and exact).

### EXAM-09 Reading text and task groups

- Given a task belongs to a reading group, When it is shown, Then the passage is one
  tap away (designer: split view or "Mətn" tab) and the scroll position of the passage
  is kept per group.
- Given a passage of 3 000 characters at font scale 1.3 on 360 dp, When shown, Then
  it is fully readable, scrolls, never truncates, and paragraph or line numbers
  referenced by tasks are visible.
- Given marked words (underline/bold) referenced by a task, When shown, Then the
  marking is visible in light and dark theme and announced by TalkBack (e.g.
  "altından xətt çəkilmiş söz").
- Given the passage is a literary excerpt, When imported, Then its licence is
  recorded (OQ-123); without a licence the import fails.

### EXAM-10 Listening (audio)

Proposal, blocked by OQ-119 and OQ-121 (audio production decided: Piper, OQ-120):
- Given the package was downloaded, When the listening group is opened, Then the
  audio plays from local storage (never streamed), so buffering never costs time.
- Given exam rules (proposal: played twice, no seeking, no speed change), When the
  user taps "Dinlə", Then playback starts; pause is allowed (OQ-119); the remaining
  plays are shown ("1 dəfə qalıb"); after the last play the button is disabled.
- Given the app is killed during a play, When the attempt resumes, Then that play
  counts as used if more than 10 s were played (proposal), otherwise it is restored.
- Given the audio file is missing or corrupt locally, When playback fails, Then the
  client re-downloads it once; if that fails, "Audio yüklənmədi" is shown, the plays
  are not consumed, and after submit the listening tasks are marked "audio
  işləmədi" and excluded from the estimate with a note (never silently scored 0).
- Given TalkBack is on, When the player is focused, Then controls are labelled
  ("Dinlə, 2 dəfədən 1-i istifadə olunub").
- Given no headphones and media volume 0, When the user taps "Dinlə", Then a hint
  "Səsi artır və ya qulaqcıq tax" is shown (no blocking).
- Given the attempt is submitted, When the review opens, Then the transcript is
  available; it is never available before submit (it would answer the tasks).
- Given "Məşq rejimi" for listening (Phase 2, proposal), When used outside an
  attempt, Then free play, seek and transcript are allowed and nothing is scored.

### EXAM-11 Written tasks

- Given a written task (`short_text`), When shown, Then an optional multiline text
  field (max 1 000 chars) is shown; the typed text is saved locally and synced like
  other answers.
- Given a `solution` task (Phase 2), When shown, Then the hint "Həlli kağızda yaz;
  sınaqdan sonra nümunəvi həll ilə müqayisə edəcəksən" and an optional text field are
  shown; no photo upload.
- Given the user writes nothing, When submitted, Then the task can still be
  self-assessed (EXAM-16), because the user may have solved it on paper.
- Given text typed in English on an Azerbaijani keyboard, When saved, Then it is
  stored as typed (UTF-8, no normalisation).

### EXAM-12 Flag for review

- Given any task, When the user taps "İşarələ", Then the flag toggles, is stored with
  the attempt and shown in the grid; flags never affect the score.
- Given submit, When the confirmation is shown, Then it lists the counts of
  unanswered and flagged tasks.

### EXAM-13 Interruptions: background, app kill, network loss

Proposal, blocked by OQ-117 and OQ-118:
- Backgrounding: Given an attempt, When the app goes to the background, Then the
  timer keeps running in wall-clock time (no pause); on return the remaining time is
  recomputed from the server deadline.
- App kill: Given Android killed the app, When the user reopens it, Then home shows
  "Davam edən sınaq" and one tap restores the attempt at the last task with all local
  answers, flags and written text (Room; unlike the quiz, OQ-017 does not apply).
- Another device: Given the attempt runs on device A, When the user opens it on
  device B, Then B downloads the package and the server's last synced answers;
  answers not yet synced from A merge by per-task revision (latest client revision
  wins).
- Network loss: Given no network, When the user answers, Then answers are saved
  locally and synced in order when the network returns; a small "Oflayn — cavablar
  telefonda saxlanılır" indicator is shown; answering is never blocked.
- Given the network is down at the deadline, When the deadline passes, Then the
  client ends the attempt locally, shows "Nəticə üçün internetə qoşul", and uploads
  the locally stored answers later (late-upload window, OQ-118).
- Configuration change: Given rotation or theme change, When the screen is
  recreated, Then the same task, scroll position and audio state are restored.
- Low memory: Given a low-end device (2 GB RAM), When an 85-task attempt runs, Then
  only the visible task's images are decoded; memory does not grow with the number
  of visited tasks.

### EXAM-14 Submit and auto-submit

- Given the user taps "Sınağı bitir", When the confirmation ("Cavabsız: 7,
  İşarələnmiş: 3. Sınağı bitirmək istəyirsən?") is accepted, Then the client syncs all
  pending answers and calls submit; the attempt becomes read-only.
- Given the deadline passes, When the user is in the app, Then input is disabled,
  "Vaxt bitdi" is shown and submit is called automatically.
- Given submit is called twice (double tap, retry), When the second arrives, Then
  the same result is returned and nothing changes.
- Given the user never submits and never returns, When the server deadline plus the
  late-upload window passes, Then the server finalizes the attempt with the synced
  answers.
- Given submit fails offline, When the user waits, Then "Nəticə internet olanda
  hesablanacaq" is shown and the client retries in the background.

### EXAM-15 Server-side scoring and estimated DİM score

- Given a submitted attempt, When the server scores it, Then it uses only stored
  answers and the answer keys on the server, and the formulas of §2 for the exam
  type (stage 1: no penalty; stage 2: NBq with −1/4, floor 0, NBa, weights).
- Given written tasks without a self-assessment, When the result is first shown,
  Then they count as 0 and are marked "Qiymətləndirilməyib" (OQ-112).
- Given scores, When shown, Then subject scores are rounded to 0.1 (rounding rule
  OQ-129) and written with a decimal comma ("67,7").
- Given any result, When shown, Then the label is "Təxmini bal" and the note "Bu,
  rəsmi DİM nəticəsi deyil" is visible; never "DİM balı".
- Given the answer keys change after the attempt (content fix), When the attempt is
  viewed later, Then the stored score is not recalculated silently (proposal: score
  stays, review shows the corrected key with "Bu tapşırıq düzəldilib").
- Given unit tests, When the scoring code is built, Then each formula has tests with
  the examples of §2 and the boundary cases (all correct = 100, all wrong stage 2 =
  0 not negative).

### EXAM-16 Self-assessment of written tasks

Recommended option B of OQ-112:
- Given a submitted attempt with written tasks, When the result screen opens, Then
  "Yazılı tapşırıqları qiymətləndir (7)" is offered.
- Given a written task in self-assessment, When shown, Then the user's text (or
  "Cavab yazılmayıb — kağızda həll etmisənsə, onunla müqayisə et"), the model answer
  and the marking criteria are shown, and the user picks one of 0, ⅓, ½, ⅔, 1.
- Given marks are chosen, When saved, Then the server stores them as
  `self_assessed`, recomputes the estimate and shows the written part separately
  ("Avtomatik yoxlanılıb: 46,9 · Özün qiymətləndirdin: 20,8").
- Given the user skips some tasks, When the result is shown, Then they stay 0 and
  "Qiymətləndirilməyib" until assessed; assessment stays possible later from history.
- Given a client sends a mark outside {0, 1/3, 1/2, 2/3, 1}, When received, Then the
  server rejects it (400).

### EXAM-17 Result screen

- Given a scored attempt, When shown, Then it shows the total estimate (e.g. "Təxmini
  bal: 214,3 / 300"), per-subject estimates, and per subject: closed correct/wrong/
  blank, coded correct, written marks, and per section (e.g. "Dinləmə 4 / 6").
- Given stage 2 (Phase 3), When shown, Then it shows the weighted score per subject,
  the penalty effect ("Səhv cavablara görə −1,5") and the 400 total; the 700 total
  only if the user has a stage 1 result they choose to combine (proposal, later).
- Given long subject names at font scale 1.3 on 360 dp, When shown, Then nothing is
  truncated (wraps).
- Given the result, When shown, Then progression and streak effects follow OQ-125.

### EXAM-18 Review

- Given a scored attempt, When "Təhlil" is opened, Then every task (not only wrong
  ones) is listed with the user's answer, the correct answer, the explanation, and for
  written tasks the model answer and criteria; listening groups show the transcript.
- Given the review data, When requested before submit, Then the server returns 409;
  keys, explanations, model answers and transcripts are only returned after submit
  or finalization.
- Given a filter, When the user selects "Səhvlər", "Cavabsızlar" or "İşarələnmişlər",
  Then only those tasks are listed.

### EXAM-19 Subject-only sınaq ("Fənn sınağı")

- Given a variant, When the user picks one subject block (e.g. "İngilis dili"), Then
  an attempt with only that block starts, with the subject duration of OQ-117
  (proposal 60 min) and the same rules as a full attempt.
- Given a subject attempt, When scored, Then only that subject's estimate (max 100)
  is shown; it is never shown as a full-exam total.
- Given a full attempt and a subject attempt of the same variant, When listed, Then
  they are separate attempts (OQ-124 applies to both).

### EXAM-20 Retakes and history

Proposal, blocked by OQ-124:
- Given a finished attempt, When the user starts the same variant again, Then a new
  attempt starts; the result is marked "Təkrar cəhd" (the user has seen the keys).
- Given "Sınaqlarım", When opened, Then past attempts are listed newest first with
  date (Baku day), variant, mode and estimate; tapping opens the result and review.
- Given at most one running attempt per user (proposal), When the user starts
  another variant while one runs, Then "Əvvəlcə davam edən sınağı bitir" is shown with
  "Davam et" / "Bitir".

### EXAM-21 Server enforcement and abuse

- Given the package or any endpoint before submit, When inspected, Then it contains
  no correct options, accepted values, explanations, model answers or transcripts.
- Given an answer for a task not in the attempt, an unknown option, or a malformed
  value, When it arrives, Then 400 and nothing is stored.
- Given answer revisions, When they arrive out of order, Then the server keeps the
  highest revision per task (idempotent; a repeated revision returns 200).
- Given answers uploaded after the deadline, When they claim to have been made before
  the deadline, Then they are accepted only inside the late-upload window and only
  from the device that held the attempt (OQ-118); later uploads get 409
  `attempt_closed`.
- Given a user starting many attempts to harvest keys, When more than N attempts are
  started per day (proposal: 10), Then 429 `rate_limited`.
- Given a modified client reads the package before starting, When it does, Then it
  learns only the tasks, not the answers (accepted risk: practice only, no ranking).
- Given the self-assessment, When the user over-rates themselves, Then nothing else
  is affected (no ranking; OQ-125 keeps XP off self-assessed marks).
- Given another user's attempt ID, When any endpoint is called, Then 404.

### EXAM-22 Display and accessibility

- Given font scale 1.3 on 360 dp, When any exam screen is shown (task, passage,
  grid, result, review), Then no text is truncated; long Azerbaijani subject names
  and option texts wrap.
- Given dark theme, When formulas, diagrams, tables and marked words are shown, Then
  they are readable (formula images tinted to the text colour, OQ-116).
- Given TalkBack, When a task is focused, Then the order is: section and number,
  task text, options with letters ("A variantı …"); formulas are read from their
  spoken text; the timer is announced on demand, not every second.
- Given a table in a task, When shown on 360 dp, Then it scrolls horizontally inside
  its own box; the page does not.
- Given the attempt runs, When the screen would turn off, Then it does not force the
  screen on (battery); wall-clock timing makes this harmless.

### EXAM-23 Account deletion and privacy

- Given `DELETE /v1/me`, When it runs, Then all exam attempts, answers, written texts,
  flags and self-assessments of the user are deleted in the same transaction
  (`docs/mvp-spec.md` §3a must list them when the feature ships).
- Given written answer texts, When stored, Then they are personal data: listed in the
  privacy policy (`docs/legal/`), never sent to analytics or logs.

### EXAM-24 Content import of a variant

- Given a variant file set in the private bank, When imported, Then the importer
  checks it against the exam template of §2: section task counts, task types per
  section, group sizes, option count, every closed task has exactly one correct
  option, every coded task has ≥ 1 accepted value, every written task has a model
  answer and criteria, every listening group has audio + transcript, every passage
  and image has a licence.
- Given any check fails, When imported, Then the whole variant is rejected with a
  list of errors; nothing is published partially.
- Given every task is `reviewed` (by whom: OQ-122), When the variant is published,
  Then it appears in the catalogue; a variant is published or unpublished as a whole.
- Given a published variant is edited, When re-imported, Then it gets a new version;
  running attempts keep their version (EXAM-03 mismatch rule for new starts).

### EXAM-25 Naming and disclaimer

- Given any exam title, When shown in the app, store listing, push or share text,
  Then it uses our naming ("Buraxılış sınağı №3 (DİM formatında)", "I qrup qəbul
  sınağı (DİM formatında)") and never a year, "rəsmi", "real", "DİM imtahanı" or the
  DİM logo.
- Given the catalogue, info, result and settings "Haqqında" screens, When shown, Then
  the disclaimer is visible (proposal, OQ-130): "Sınaqlar Duello Quiz komandasının
  orijinal tapşırıqlarıdır və DİM formatına uyğun hazırlanıb. Dövlət İmtahan Mərkəzi
  (DİM) ilə əlaqəsi yoxdur və DİM tərəfindən təsdiqlənməyib. Bal təxminidir."

## 6. Business rules (proposals unless marked DİM)

- Exam structure and scoring: DİM, §2 of this file (until a spec section exists).
- Durations, grace, pause policy, late-upload window, attempt limits: **not
  decided** — OQ-117, OQ-118, OQ-124. No number here may be built before Sabit
  decides.
- Server authoritative for start, deadline, keys and scoring; self-assessed marks
  are the only user-supplied score input and are always labelled.
- Answer revisions: last revision before the deadline counts.
- Media: images as in `docs/mvp-spec.md` §5; audio stored on the same server disk
  behind the same storage interface, served by Caddy with immutable cache headers
  (new file = new key), included in backups. Budget: ~0.5 MB per minute of audio,
  ~1.5 MB per listening text, 50 variants ≈ 75 MB.
- Exam results never change Elo.

## 7. Content production

| Exam type | Tasks per variant | Extra material per variant | Reviewers needed |
| --- | --- | --- | --- |
| Buraxılış, English block only (Phase 1) | 30 | 1 listening script + audio (2–4 min), 1 reading text | English teacher |
| Buraxılış, full (Az sector, English) | 85 | 2 Azerbaijani texts, 1 English reading, 1 listening | Az language, maths, English teachers |
| Stage 2, all 5 groups (I, II, III DT, III TC, IV) | 90 per group; 8 distinct subjects × 30 = **240** if subject blocks are reused across groups (OQ-131), 450 if not | Texts (language, literature), sources (history) | 8 subject teachers |

- Rough effort (assumption): ~30 min to write and source a closed task, ~60 min for a
  written task with model answer and criteria, plus ~50 % for review → ~60–70 hours
  per full buraxılış variant. A useful launch needs ≥ 3 variants per type.
- Process: content-writer (AI drafts allowed, as today) → subject-teacher review →
  publish approval by Sabit (OQ-122). Each fact keeps a source (textbook, encyclopedia).
- Originality: authors never use DİM test collections, "Abituriyent" journals or
  tutoring-book tasks as templates; each variant is checked for near-duplicates
  inside our own bank (automatic) and spot-checked by a reviewer who knows the DİM
  collections (manual). Every author confirms originality in the bank PR.
- Bank format: new folder `content/bank/exams/<template>/<variant>/` with a variant
  manifest, groups and tasks; a public schema and samples like
  `content/schema/question.schema.json` (content-writer + backend-dev, later).
- Audio: Piper TTS (OQ-120, decided): the bank stores the listening script with a
  speaker role per line; a content tool renders it with the allowed voices
  (female: `en_GB-alba-medium`, `en_GB-cori-high`; male: `en_US-libritts_r-medium`
  speaker ID 452, Sabit 2026-11-17) to AAC-LC mono 64 kbps (OQ-121) at about 0.9× speed. The voice and
  speaker ID are recorded per file for attribution. Human recordings remain an option
  per variant (then with a signed consent and licence form outside the public repo).

## 8. Suggested phasing

**Phase 1 (decided, OQ-128): "Buraxılış — İngilis dili" subject sınaq**, 30 tasks
in the stage 1 format, 3 variants, including the 7 written tasks with
self-assessment. Justification:
- Covers Sabit's explicit wish (listening audio) and the riskiest new building
  blocks — exam session engine (long timer, navigation, resume, offline, submit),
  task groups, long passages, audio download and playback, official scoring,
  self-assessment — with the smallest content effort (30 tasks + 1 recording per
  variant, one reviewer type).
- Needs no formula rendering, no coded input, no penalty scoring: those are the
  second-largest risks and wait for Phase 2.
- English texts avoid the Azerbaijani literary-excerpt copyright question (OQ-123).
- Alternative rejected: one full buraxılış without written tasks — triples content,
  needs formulas, and the estimate would miss 38–50 % of the raw points.

**Phase 2: full "Buraxılış sınağı (DİM formatında)"** — add mathematics (formulas,
coded numeric, solution-type written tasks) and Azerbaijani language (two passages,
marked words); full 85-task, 180-minute attempt, total out of 300; listening practice
mode.

**Phase 3: stage 2 groups I–IV** — after re-verifying Source B for the current year
(OQ-129): selection and matching tasks, −1/4 penalty, weights, 8 subject blocks,
optional combined 700 estimate. Later: 9th grade, state-language exam, Russian sector,
other foreign languages.

## 9. Out of scope (all phases unless decided otherwise)

Leaderboards or percentiles vs other users; human or AI marking; photo upload of
written solutions; paid access (OQ-126); real DİM tasks; adaptive/AI-generated exams;
reuse in category quizzes or the duel (OQ-127); live proctoring or anti-cheat beyond
EXAM-21.

## 10. Analytics events (Firebase Analytics)

No task text, answer text or written answers in events.

| Event | Properties | When |
| --- | --- | --- |
| `exam_catalog_view` | `variant_count` | Catalogue shown |
| `exam_download_start` | `exam_type`, `variant_id`, `size_kb` | "Yüklə" tapped |
| `exam_download_result` | `exam_type`, `variant_id`, `result`: `ok` \| `offline` \| `storage` \| `server`, `duration_s` | Download ends |
| `exam_start` | `exam_type`, `variant_id`, `mode`: `full` \| `subject`, `subject`, `retake` (bool) | Attempt created |
| `exam_resume` | `exam_type`, `reason`: `app_kill` \| `other_device` \| `return`, `remaining_min` | Attempt restored |
| `exam_audio_play` | `variant_id`, `play_no`, `completed` (bool) | Play ends |
| `exam_audio_failed` | `variant_id`, `error_type` | Playback error |
| `exam_submit` | `exam_type`, `mode`, `trigger`: `user` \| `deadline` \| `server`, `answered_count`, `flagged_count`, `duration_min`, `offline_at_submit` (bool) | Submit accepted |
| `exam_self_assess_done` | `exam_type`, `assessed_count`, `skipped_count` | Self-assessment saved |
| `exam_result_view` | `exam_type`, `mode`, `score_bucket` (0–9 by tenths of max) | Result shown |
| `exam_review_open` | `exam_type`, `filter` | Review opened |
| `exam_abandon` | `exam_type`, `progress_pct` | Attempt finalized by the server without submit |

## 11. Open questions

All decided by Sabit on 2026-11-21 (OQ-120 on 2026-11-16); full text in `open-questions.md`.
OQ-126 note: free now, in-app purchases planned later (separate decision).

| ID | Topic | Decision (short) |
| --- | --- | --- |
| OQ-112 | Written tasks: how to score | B: self-assessment against model answer + official marks, shown separately |
| OQ-113 | Closed tasks: 5 options (A–E)? | Verify; model supports 2–5 per template |
| OQ-114 | Coded answer format and normalisation | Digits, comma, minus, optional fraction; exact rational compare |
| OQ-115 | Selection/matching scoring | Verify; default all-or-nothing |
| OQ-116 | Formulas and rich text | LaTeX in the bank → tintable images at import + spoken text; restricted markup; ADR |
| OQ-117 | Durations, grace, pause | 180 min full, 60 min subject, 2 min grace, no pause |
| OQ-118 | Offline, resume, late upload | Download before start, online start, offline answering, resume, 24 h late upload |
| OQ-119 | Listening play rules | 2 plays, no seek, pause allowed, skip-listening option |
| OQ-120 | Audio production | Decided 2026-11-16: Piper TTS with CC BY / public-domain voices, credited on the licences screen |
| OQ-121 | Audio format and player | AAC-LC mono 64 kbps `.m4a`, Media3 ExoPlayer (ADR) |
| OQ-122 | Who marks exam tasks `reviewed` | Named subject reviewers; Sabit approves publishing |
| OQ-123 | Passage copyright | Own texts or public domain / CC BY only, licence recorded |
| OQ-124 | Retakes and attempt limits | Unlimited, "Təkrar cəhd" label, 1 running attempt, 10 starts/day |
| OQ-125 | XP, streak, active day | Active day yes; no XP in Phase 1 |
| OQ-126 | Free or paid | Free now; IAP planned later (no paywall built now) |
| OQ-127 | Separate tables; reuse in quizzes | Separate tables and bank folder; no reuse in Phase 1 |
| OQ-128 | Phase 1 slice | English subject sınaq (buraxılış format) |
| OQ-129 | Verification of stage 2, rounding, 9th grade | Re-verify before Phase 3; 9th grade later |
| OQ-130 | Naming, disclaimer, Play listing wording | Proposed text in EXAM-25 |
| OQ-131 | Reuse stage 2 subject blocks across groups | Yes (240 instead of 450 tasks per round) |

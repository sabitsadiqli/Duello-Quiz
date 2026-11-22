# Mock exams ("Sınaqlar") — requirements

- Status: **Decided — post-MVP.** Not in the MVP scope of `CLAUDE.md`; nothing here
  blocks the MVP or the closed test. OQ-112 … OQ-131 decided by Sabit 2026-11-21
  (open-questions.md); Phase 1 = "Buraxılış — İngilis dili" (OQ-128). Build starts
  when Sabit schedules it (design → backend/Android → content).
- Phase 1 design delivered 2026-11-21 (`design/screens/exam-catalogue.md`,
  `exam-info.md`, `exam-attempt.md`, `exam-self-assessment.md`, `exam-result.md`,
  `exam-review.md`; components §39–§45). Sabit decided the design questions
  OQ-D40 … OQ-D49 on 2026-11-21 (recorded in those files, not in
  `open-questions.md`). Stories changed by the design are marked
  **"(design, 2026-11-21)"**; superseded criteria are listed at the end of each
  changed story so tests can be retired deliberately. New: EXAM-26 (review audio
  player), EXAM-27 ("10 dəqiqə qalıb" notification), EXAM-28 (skip listening).
- Feature flag: everything in this file is behind the exams flag. With the flag off
  no entry, row, banner, settings row, licence section, notification channel or deep
  link exists (EXAM-01).
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
| Listening audio: how many times played, pauses | Played twice, no seeking (decided as our rule, OQ-119; DİM practice still to verify) | OQ-119 |
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

Phase column: the phase in which the story is first needed (§8). "Decisions" lists
the questions that shaped the acceptance criteria (all decided; OQ-D… are the design
questions decided by Sabit 2026-11-21, recorded in `design/screens/exam-*.md`).
Screen column: the design spec that implements the story.

| ID | Story | Phase | Decisions | Screen |
| --- | --- | --- | --- | --- |
| EXAM-01 | As a student, I want a catalogue of exam types and sınaq variants, reachable from the Oyna tab, so that I can choose what to practise. | 1 | OQ-128, OQ-130, OQ-D40, OQ-D48, OQ-D49; (design, 2026-11-21) | exam-catalogue |
| EXAM-02 | As a student, I want an info screen for a sınaq (structure, time, scoring, disclaimer), so that I know what to expect before I start. | 1 | OQ-117, OQ-130; (design, 2026-11-21) | exam-info |
| EXAM-03 | As a student, I want the whole sınaq downloaded before the timer starts, so that a weak network never eats my exam time. | 1 | OQ-118; (design, 2026-11-21) | exam-info |
| EXAM-04 | As a student, I want to start the sınaq and see a server-backed countdown, so that the time limit is real and fair. | 1 | OQ-117, OQ-D41, OQ-D48; (design, 2026-11-21) | exam-info, exam-attempt |
| EXAM-05 | As a student, I want to move freely between tasks and sections, so that I can work in my own order like on paper. | 1 | (design, 2026-11-21) | exam-attempt |
| EXAM-06 | As a student, I want to choose, change and clear an answer on a closed task, so that I can correct myself until I submit. | 1 | OQ-113 | exam-attempt |
| EXAM-07 | As a student, I want to enter a coded numeric answer with a clear format, so that a correct answer is never rejected for its spelling. | 2 | OQ-114 | — |
| EXAM-08 | As a student, I want selection and matching tasks, so that stage 2 sınaqlar have all DİM task types. | 3 | OQ-115 | — |
| EXAM-09 | As a student, I want to read a long text while answering its tasks, so that reading tasks work on a phone. | 1 | OQ-116, OQ-123; (design, 2026-11-21) | exam-attempt |
| EXAM-10 | As a student, I want to listen to the listening text under exam-like rules, so that I practise English listening realistically. | 1 | OQ-119, OQ-120, OQ-121, OQ-D43; (design, 2026-11-21) | exam-attempt, components §42 |
| EXAM-11 | As a student, I want to write (or solve on paper) the written tasks, so that I practise the full exam, not only the tests. | 1 | OQ-112 | exam-attempt, components §43 |
| EXAM-12 | As a student, I want to flag tasks to come back to, so that I can manage my time. | 1 | (design, 2026-11-21) | exam-attempt, components §40 |
| EXAM-13 | As a student, I want my attempt to survive backgrounding, app kill and network loss, so that I never lose a 3-hour effort. | 1 | OQ-117, OQ-118, OQ-D40, OQ-D43; (design, 2026-11-21) | exam-attempt, home |
| EXAM-14 | As a student, I want to submit, or have the sınaq submitted when time runs out, so that the attempt always ends cleanly. | 1 | OQ-117, OQ-118; (design, 2026-11-21) | exam-attempt |
| EXAM-15 | As a student, I want the server to score my answers with the official formula, so that my estimated score is correct and trustworthy. | 1 | OQ-129, OQ-D42 | exam-result |
| EXAM-16 | As a student, I want to assess my written answers against a model answer and the official criteria, and change my marks later, so that my estimate includes them. | 1 | OQ-112, OQ-D47; (design, 2026-11-21) | exam-self-assessment |
| EXAM-17 | As a student, I want a result screen with the estimated score per subject and section, so that I see my strengths and weaknesses. | 1 | OQ-112, OQ-125, OQ-130, OQ-D42; (design, 2026-11-21) | exam-result |
| EXAM-18 | As a student, I want to review every task with the correct answer, explanation, model answer and transcript, so that I learn from the sınaq. | 1 | OQ-D44; (design, 2026-11-21) | exam-review |
| EXAM-19 | As a student, I want to take one subject block alone ("Fənn sınağı"), so that I can practise in less than 3 hours. | 1 | OQ-117 | exam-info |
| EXAM-20 | As a student, I want to retake a sınaq and see my past attempts, so that I can track progress. | 1 | OQ-124, OQ-125; (design, 2026-11-21) | exam-catalogue, exam-info |
| EXAM-21 | As the product owner, I want the server to enforce exam rules, so that a modified client cannot get answer keys early or change results. | 1 | OQ-118 | — (backend) |
| EXAM-22 | As a student with large fonts, dark theme or TalkBack, I want every exam screen to stay usable, so that the sınaq is fair for me too. | 1 | OQ-116 | all exam screens |
| EXAM-23 | As a user, I want my exam data deleted with my account, so that my privacy rights hold for this feature too. | 1 | (design, 2026-11-21) | exam-catalogue (states) |
| EXAM-24 | As a content writer, I want to import a sınaq variant that is checked against its DİM template, so that no broken or incomplete exam is published. | 1 | OQ-122, OQ-127, OQ-131 | — (importer) |
| EXAM-25 | As the product owner, I want naming and disclaimers that never suggest an official DİM exam, so that we respect DİM's rights and Play policy. | 1 | OQ-130, OQ-D49; (design, 2026-11-21) | exam-catalogue, exam-info, exam-result, settings, licenses |
| EXAM-26 | As a student who finished a sınaq, I want to replay the listening audio freely with seeking next to the transcript, so that I understand what I missed. | 1 | OQ-D44 (new, design, 2026-11-21) | exam-review, exam-self-assessment, components §42a |
| EXAM-27 | As a student who left the app during a sınaq, I want one reminder when 10 minutes are left, so that time does not run out without me. | 1 | OQ-D45 (new, design, 2026-11-21) | exam-attempt I |
| EXAM-28 | As a deaf or hard-of-hearing student, I want to skip the listening part before I start, so that my estimate is honest about what I could do. | 1 | OQ-119, OQ-D42 (new, design, 2026-11-21) | exam-info, exam-attempt, exam-result |

### EXAM-01 Catalogue (design, 2026-11-21)

Screen: `design/screens/exam-catalogue.md`. Entry on Oyna: HOME-10 (`home.md`).

- Entry (OQ-D40): Given the flag is on and the cached catalogue has ≥ 1 published
  variant, When the Oyna tab is shown, Then a section "İMTAHANA HAZIRLIQ" with one
  row "Sınaqlar" ("DİM formatında məşq sınaqları") is shown **below the category
  list** (HOME-10); tapping it opens the catalogue as a full-screen flow without the
  bottom navigation. There is no fourth tab.
- Given the catalogue, When it is shown, Then the first content block is the DİM
  disclaimer (EXAM-25), visible without scrolling on 360 dp at font scale 1.0 **and
  1.3**; the disclaimer is a local string, shown at once even during the first load.
- Given the catalogue loaded, When shown, Then per exam type: the overline from the
  server (authored in capitals in the bank, never uppercased at runtime; hidden when
  absent), the type title from server i18n (e.g. "Buraxılış — İngilis dili (DİM
  formatında)", wraps to 2 lines), a meta line ("Fənn sınağı · 30 tapşırıq · 60
  dəq") and one card per published variant.
- Variant name (OQ-D49): Given a variant, When its title is shown anywhere in the
  app, Then the app builds "Sınaq №N" from `variantNumber` (string resource with
  U+2116 "№"); the server never sends a variant title string.
- Given a variant card, When shown, Then it shows "Sınaq №N", "30 tapşırıq · 60 dəq
  · 3,4 MB" and **one** status line, by priority: running ("Davam edir — 42 dəq
  qalıb", card outlined) > result pending ("Nəticə hesablanır") > written tasks not
  assessed ("Yazılılar qiymətləndirilməyib") > package outdated ("Yenilənib —
  yenidən yüklə") > last result ("Təxmini bal: 67,7") > downloaded ("Yüklənib ·
  başlamağa hazır") > not started ("Başlanmayıb"); while a download runs (also when
  started on the info screen and the user left it), the card shows "Yüklənir… 42%"
  with progress; a "Təkrar cəhd" chip on its own line when the last result is a
  retake.
- Given the catalogue, When shown, Then the last row is "Sınaqlarım" with the attempt
  count ("4 cəhd" / "Hələ cəhd yoxdur") → history (EXAM-20).
- Empty: Given 0 published variants (the list changed since Oyna was drawn), When
  the catalogue loads, Then "Hələ ki sınaq yoxdur" with "Yenilə" is shown, and Oyna
  hides the entry on its next refresh (no empty catalogue reachable from a fresh
  Oyna).
- Error: Given 5xx or a 15 s timeout and no cache, When the catalogue loads, Then an
  error state with "Yenidən cəhd et" is shown.
- Offline with cache (OQ-D48): Given no network and a cached catalogue, When shown,
  Then the banner "İnternet yoxdur. Başlanmış sınağı oflayn davam etdirə bilərsən,
  yenisini başlatmaq üçün internet lazımdır." is shown above the disclaimer;
  downloaded variants stay **openable** (the info screen explains that starting needs
  internet, EXAM-04) and a running attempt continues offline.
- Offline without cache: Given no network and no cache, When shown, Then the offline
  state view with retry is shown, **and** a running local attempt (Room) is still
  shown as a single running card above it.
- Given an attempt runs on another device, When the catalogue is shown, Then that
  variant shows the running status; tapping opens the info screen with "Davam et"
  (download first if the package is missing, EXAM-03).
- Slow network: Given the first load takes > 300 ms, When shown, Then the disclaimer
  plus skeleton cards are shown; pull-to-refresh keeps the cards visible.
- Given sign-out or account switch, When the next account opens the catalogue, Then
  no cached catalogue state or local attempt of the previous account is shown
  (EXAM-23); downloaded packages may stay (no personal data).
- Given the flag is off, When the app runs, Then no entry, deep link, banner or
  notification leads to sınaqlar.

Superseded criteria: "opens Sınaqlar from home" without a placement; "downloaded
variants stay startable" offline (contradicted OQ-118); "Davam edir — 1 saat 12 dəq
qalıb" (Phase 1 attempts are 60 min); disclaimer visible at font scale 1.0 only.

### EXAM-02 Info screen (design, 2026-11-21)

Screen: `design/screens/exam-info.md`.

- Given a variant, When the user opens it, Then the screen shows: "FƏNN SINAĞI · DİM
  FORMATINDA", "Sınaq №N" and the type title; three facts (duration "60 dəqiqə",
  "30 tapşırıq", "100 maksimum bal"); "QURULUŞ" with one row per section in exam
  order and task counts by type ("6 tapşırıq: 3 qapalı, 3 yazılı"); "QAYDALAR" with
  rule rows (time "Vaxt 60 dəqiqədir. Fasilə yoxdur, vaxt arxa planda da işləyir.",
  free navigation, listening rules only when the exam has listening, written-task
  self-assessment only when it has written tasks, scoring in plain words, "start
  needs internet, then offline is fine"); the skip-listening switch (EXAM-28) when
  the exam has listening; the DİM disclaimer and "Nəticə təxmini baldır, rəsmi DİM
  nəticəsi deyil."; and one pinned primary action.
- Given the exam has written tasks, When shown, Then the rule "Yazılı tapşırıqları
  sınaqdan sonra özün qiymətləndirəcəksən." is shown.
- Given the pinned action area, When shown, Then it follows the package and attempt
  state: "Yüklə · 3,4 MB" (not downloaded) → progress + "Ləğv et" (downloading) →
  "Başla" (downloaded) → "Davam et" with "Vaxt gedir: 42 dəq qalıb." (running here) →
  "Yenidən başla" plus "Son nəticə" (finished before, EXAM-20); details in EXAM-03 and
  EXAM-04.
- Given the package is downloaded and notifications cannot be shown (permission not
  granted, app notifications off or the "Sınaqlar" channel off), When "Başla" is
  shown, Then the hint "Bildirişlər söndürülüb: tətbiq arxa planda olanda vaxt
  xatırlatması gəlməyəcək." is shown above it; the app does **not** ask for the
  permission here (EXAM-27).
- Given 360 dp at font scale 1.3, When shown, Then the facts wrap ("maksimum bal" on
  2 lines, all three items equally tall), section and rule rows wrap, and nothing is
  hidden under the pinned action area.
- Given stage 2 (Phase 3), When shown, Then it states that a wrong closed answer
  costs 1/4 and a blank costs nothing.

Superseded criteria: none removed; the list of contents is now fixed by the design.

### EXAM-03 Download the exam package (design, 2026-11-21)

- Given the user taps "Yüklə", When the package downloads, Then the client fetches
  the task JSON (no keys, no explanations, no model answers, no transcripts) and every
  image and audio file, with a progress bar and the sizes ("Yüklənir… 1,2 / 3,4 MB",
  decimal comma, updates at most every 250 ms).
- Given a download runs, When the user leaves the info screen, Then it continues in
  the background (no system notification unless Android requires one for the worker)
  and the catalogue card shows the progress; "Ləğv et" stops it and keeps the
  verified files.
- Given the network drops during a download, When it drops, Then the download pauses
  with a warning banner and resumes by itself when the network returns.
- Given a file fails its hash check, When the one automatic retry also fails, Then
  a warning banner and "Yenidən cəhd et" are shown.
- Given the download completes, When all files are verified (size or hash from the
  package manifest), Then "Başla" is enabled; until then the attempt has not started
  and no timer runs.
- Given the download fails midway, When the user retries, Then finished files are
  not downloaded again (HTTP cache / local store) and the download resumes.
- Given the device has too little storage, When the download starts, Then before
  the first byte the client checks free space against package size × 1.1 and shows
  "Cihazda kifayət qədər yer yoxdur" with the required size and "Yenidən cəhd et".
- Given the device is offline and the package is not downloaded, When the user taps
  "Yüklə", Then the button stays enabled and a snackbar says internet is needed (a
  disabled button would give no reason).
- Given a downloaded package whose variant was updated, When the catalogue `version`
  differs or the start returns `variant_version_mismatch`, Then "Yenilənib — yenidən
  yüklə" is shown and "Yüklə" downloads the new package; the old one is deleted only
  after success.
- Given the variant was unpublished (`variant_unavailable` / 404), When the info
  screen is opened or "Başla" tapped, Then a full state view says the sınaq is no
  longer available and the local package is deleted.
- Given a downloaded package, When unused for 30 days (proposal) or after the attempt
  is finished and reviewed, Then the client may delete media to free space; the
  review re-downloads what it needs (images, and the audio on tap of the review
  player, EXAM-26).

Superseded criteria: "the server rejects it and the client offers to download again"
for both mismatch and unpublished (unpublished now shows a final state view).

### EXAM-04 Start and timer (design, 2026-11-21)

- Given a complete package, When the user taps "Başla" (or "Yenidən başla"), Then a
  confirmation "Sınağa başlayırsan?" / "60 dəqiqə vaxtın olacaq. Vaxtı dayandırmaq
  olmur — tətbiqdən çıxsan da sınaq davam edir." with "Ləğv et" / "Başla" is shown
  (plus "Dinləmə hissəsi keçiləcək." when EXAM-28 is on); back or a tap outside
  cancels; no timer runs before the confirmation.
- Given the confirmation is accepted, When the start request succeeds, Then the
  server creates the attempt and returns `startedAtEpochMs`, `endsAtEpochMs` (start
  + duration), `deadlineEpochMs` (`endsAtEpochMs` + 2 min grace, OQ-117) and
  `serverNowEpochMs`; the attempt opens at its first task.
- Displayed time (OQ-D41): Given a running attempt, When the countdown is shown, Then
  it counts down to `endsAtEpochMs` with the clock-offset method of the duel
  (DUEL-12) and starts at "60:00"; the 2-min grace is **never** displayed and only
  absorbs late requests on the server.
- Given the start request fails offline or times out (OQ-118, OQ-D48), When the user
  taps "Başla", Then the attempt does not start and "Sınağı başlatmaq üçün internet
  lazımdır." is shown; offline, the info screen also shows this as a banner above an
  **enabled** "Başla".
- Given the start request is running, When shown, Then the button shows loading and
  back and the skip switch are disabled.
- Given the user double-taps "Başla", When two requests arrive, Then one attempt
  exists (idempotency key per start).
- Given an attempt of this variant is already running, When "Başla" is tapped (other
  device or reinstall), Then the server returns the running attempt (resume), never a
  second one.
- Given another variant is running (409), When "Başla" is tapped, Then EXAM-20's
  "Əvvəlcə davam edən sınağı bitir" dialog is shown.
- Given 10 starts already happened this Baku day (OQ-124, 429), When "Başla" is
  tapped, Then "Bu gün çox sınaq başlatmısan. Sabah yenidən cəhd et." is shown.
- Given a 5xx, When "Başla" is tapped, Then a snackbar with "Yenidən cəhd et" is
  shown and nothing starts.
- Given the countdown, When 30 min, 10 min and 1 min remain, Then a short snackbar
  ("10 dəqiqə qaldı") and a polite TalkBack announcement are shown once each; a
  threshold passed while the app was away is not shown on return (no stale notice).
- Given ≤ 10 min remain, When the timer is shown, Then the pill and time line switch
  to the warning colour with **no pulse**; at 0 the timer shows "0:00" (EXAM-14).
- Given the app is in the background at 10 min left, When the time is reached, Then
  EXAM-27 applies.
- Given the device clock is wrong, When the countdown runs, Then it still matches the
  server `endsAtEpochMs` (offset method).

Superseded criteria: countdown to `deadlineEpochMs` (it would show 62:00).

### EXAM-05 Navigation (design, 2026-11-21)

- Given an attempt, When a task is shown, Then the pinned header shows a close icon,
  the progress "14 / 30" with the section name on a second line, the timer pill and
  a grid button; the bottom navigation is hidden.
- Given section names of different lengths, When the user moves between sections,
  Then the header height does not change during the attempt (sized once for the
  longest section name at the current width and font scale; 2 lines at 1.3 allowed).
- Given the grid, When opened, Then a full-height sheet shows the timer, a summary
  ("Cavablanıb: 12 · Cavabsız: 15 · İşarələnib: 3"), and per section a grid of
  cells: not answered (outlined), answered (filled), written with text (filled +
  pencil), written empty (as not answered), flagged (flag badge), current (ring +
  bold); never colour alone; "Sınağı bitir" at the bottom.
- Given any task, When the user swipes, taps "Növbəti"/"Əvvəlki" or a grid cell, Then
  that task opens; any order is allowed, also across sections; "Əvvəlki" is disabled
  on the first task; on the last task "Növbəti" becomes "Sınağı bitir" (EXAM-14).
- Given the user taps the close icon or presses system back during an attempt, When
  pressed, Then a dialog "Sınaqdan çıxırsan?" / "Vaxt dayanmır: sınaq 42 dəq sonra
  avtomatik bitəcək. Cavabların saxlanılır." offers "Sonra davam et" (→ catalogue;
  Oyna shows the running banner, HOME-10), "Sınağı bitir" (→ EXAM-14 confirmation)
  and "Ləğv et"; back or a tap outside = "Ləğv et".
- Given the leave dialog or the grid is open, When the time ends, Then it closes and
  the time-up state starts (EXAM-14).

Superseded criteria: header "Riyaziyyat · 14 / 25" on one line.

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

### EXAM-09 Reading text and task groups (design, 2026-11-21)

- Given a task belongs to a reading group on a phone (< 600 dp), When it is shown,
  Then a pinned toggle "Tapşırıq / Mətn" is shown above the task; the passage is one
  tap away and the way back is one tap.
- Given the student reaches any task of a reading group for the **first time** in the
  attempt, When it opens, Then "Mətn" is selected; afterwards, and after every move
  to another task, "Tapşırıq" is selected.
- Given "Mətn" is shown, When the student swipes horizontally, Then nothing happens
  (reading never flips the task); "Növbəti"/"Əvvəlki" still work and land on
  "Tapşırıq".
- Given the passage, When the student scrolls it and later returns to the group, Then
  the passage scroll position is restored per group (stored as a text offset, so a
  font-scale change, rotation or process death lands on the same text).
- Given ≥ 600 dp (tablet, large landscape), When a reading task is shown, Then a split
  view shows the passage (start, 55 %) and the task (end, 45 %), each with its own
  scroll; no toggle.
- Given a passage of 3 000 characters at font scale 1.3 on 360 dp, When shown, Then
  it is fully readable, scrolls, never truncates, and paragraph numbers referenced by
  tasks are visible; Phase 1 tasks refer to **paragraphs, not lines** (line numbers
  break with font scaling).
- Given an attempt, When the passage is shown, Then text selection is off (no copy
  out during the attempt); in the review it is on.
- Given marked words (underline/bold) referenced by a task, When shown, Then the
  marking is visible in light and dark theme and announced by TalkBack (e.g.
  "altından xətt çəkilmiş söz").
- Given the passage is a literary excerpt, When imported, Then its licence is
  recorded (OQ-123); without a licence the import fails.

Superseded criteria: "split view or Mətn tab" (designer's choice, now fixed);
"paragraph or line numbers".

### EXAM-10 Listening (audio) (design, 2026-11-21)

Decided: OQ-119 (2 plays, no seek, no speed change, pause allowed, a play counts
after 10 s), OQ-120 (Piper), OQ-121 (local `.m4a`, Media3), OQ-D43 (resume position).
Component: `design/screens/components.md` §42.
- Given the package was downloaded, When the listening group is opened, Then the
  player is pinned at the top of **every** task of the group with one shared state;
  changing tasks inside the group (or leaving it) never stops or restarts playback;
  the audio plays from local storage (never streamed), so buffering never costs time.
- Given 2 plays left, When the user taps play, Then playback starts; a play runs from
  the start to the end of the file; pause and resume inside a play are allowed;
  there is no seek, no drag, no restart and no speed control; the text shows
  "Dinləmə 1 / 2" while playing and "1 dəfə qalıb" after the play; after the last
  play the button is disabled with "Dinləmə imkanı bitdi".
- Given a play has run for less than 10 s, When shown, Then the caption "10
  saniyədən sonra sayılacaq" is visible; from 10 s the play counts as used and the
  client reports it (`PUT …/listening/{groupId}`).
- Given a phone call, audio-focus loss or headphones unplugged, When it happens, Then
  playback pauses automatically and shows that it was interrupted; the play is not
  lost.
- Given the app goes to the background, When a play runs, Then it keeps playing
  (media notification with play/pause only, no seek bar on the notification or lock
  screen).
- App kill (OQ-D43): Given Android killed the app during a counted play, When the
  attempt resumes, Then the play stays used **and** the player is restored **paused
  at the saved position** (saved every 1 s) with "Tətbiq bağlanmışdı. Dinləmə
  dayandığı yerdən davam edəcək."; a play killed before 10 s is not counted.
- Given the audio file is missing or corrupt locally, When playback fails, Then the
  client re-downloads it once; if that fails, "Audio yüklənmədi" is shown with
  "Yenidən cəhd et", the plays are **not** consumed, and after submit the listening
  tasks are excluded from the estimate with "Audio işləmədi, ona görə dinləmə
  tapşırıqları bala daxil edilməyib. Maksimum bal: …" (never silently scored 0,
  EXAM-17).
- Given TalkBack is on, When the play button is focused, Then it reads "Dinlə.
  İstifadə olunub: 1 / 2" (no number-dependent suffix); state changes (counted,
  ended, failed) are polite announcements.
- Given media volume 0 or muted, When the user taps play, Then playback starts and a
  non-blocking hint asks to raise the volume or plug in headphones; it hides when the
  volume is above 0.
- Given the deadline passed, When the player is shown, Then it is disabled.
- Given the attempt is submitted, When the review opens, Then the transcript and the
  review player (EXAM-26) are available; neither is available before submit (it would
  answer the tasks).
- Given the student chose to skip listening before the start (EXAM-28), When the
  attempt runs, Then the listening tasks and the player are not shown.
- Given "Məşq rejimi" for listening outside an attempt (Phase 2), When used, Then free
  play, seek and transcript are allowed and nothing is scored. (Phase 1 already has
  free replay **after submit**, EXAM-26.)

Superseded criteria: TalkBack "Dinlə, 2 dəfədən 1-i istifadə olunub"; "otherwise it
is restored" without a resume position; hint only "without headphones and volume 0".

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

### EXAM-12 Flag for review (design, 2026-11-21)

- Given any task, When the user taps "İşarələ", Then the flag toggles ("İşarələnib"
  when on), is stored with the attempt (same revision and sync as answers) and shown
  as a badge in the grid; flags never affect the score; TalkBack announces
  "İşarələnib" / "İşarə götürüldü".
- Given submit, When the confirmation is shown, Then it lists the counts of
  unanswered and flagged tasks (EXAM-14); an **empty written task counts as
  unanswered** in this count, although it can still be self-assessed later.
- Given the review, When the filter "İşarələnmişlər" is chosen, Then only flagged
  tasks are listed (EXAM-18).

### EXAM-13 Interruptions: background, app kill, network loss (design, 2026-11-21)

Decided: OQ-117, OQ-118, OQ-D40 (running banner on Oyna), OQ-D43.
- Backgrounding: Given an attempt, When the app goes to the background, Then the
  timer keeps running in wall-clock time (no pause); on return the remaining time is
  recomputed from `endsAtEpochMs`; if it has passed, the time-up state (EXAM-14)
  starts at once. Listening audio keeps playing in the background (EXAM-10).
- App kill: Given Android killed the app, When the user reopens it, Then Oyna shows
  the compact banner "Davam edən sınaq: 42 dəq qalıb" at the top (HOME-10) and one
  tap restores the attempt at the last task with the same reading segment, passage
  scroll, written text, flags and the audio paused at its saved position (Room;
  unlike the quiz, OQ-017 does not apply). The app does **not** jump into the attempt
  by itself. Other ways back in: the "Sınaqlar" row, the catalogue running card and
  the EXAM-27 notification.
- Given resuming takes > 300 ms (Room + package parse), When the attempt opens, Then
  the header with the live timer is shown at once and the body shows "Sınaq açılır…".
- Given the deadline passed while the app was dead, When the attempt is opened, Then
  it goes straight to the time-up state (EXAM-14).
- Package missing: Given the package files were removed (storage cleared), When the
  attempt is opened, Then a blocking state "Sınağın faylları tapılmadı" / "Davam
  etmək üçün yenidən yüklə. Vaxt gedir: 42 dəq qalıb." with "Yüklə" and progress is
  shown; the timer keeps running and answers already in Room are kept.
- Another device: Given the attempt runs on device A, When the user opens it on
  device B, Then B downloads the package and the server's last synced answers;
  answers not yet synced from A merge by per-task revision (latest client revision
  wins); when answers from the other device arrive in an open attempt, "Başqa
  cihazdakı cavablar əlavə olundu." is shown and the open task re-renders if it
  changed.
- Network loss: Given no network, When the user answers, Then answers are saved
  locally and synced in order when the network returns; a compact banner "Oflayn —
  cavablar telefonda saxlanılır" is shown under the time line; answering is never
  blocked; the banner collapses when the network returns.
- Given the server rejects a synced answer as malformed (400, a client bug), When it
  happens, Then the answer stays local, a non-fatal is logged and the user sees no
  message (they cannot fix it).
- Given the attempt was closed on the server (409 `attempt_closed`: submitted on
  another device or finalized after the late window), When the client syncs, Then the
  dialog "Bu sınaq artıq bitib" with a button to the result is shown.
- Given the network is down at `endsAtEpochMs`, When the time ends, Then EXAM-14's
  offline time-up view is shown and the locally stored answers are uploaded later in
  the background (24 h late-upload window, OQ-118).
- Configuration change: Given rotation, theme or font-scale change, When the screen
  is recreated, Then the same task, segment, scroll position (by text offset),
  selection, written text and audio state are restored.
- Low memory: Given a low-end device (2 GB RAM), When an attempt runs, Then only the
  visible task ± 1 has images decoded; memory does not grow with the number of
  visited tasks.
- Given the attempt runs, When the screen would turn off, Then the app does not keep
  it on (EXAM-22); wall-clock timing makes sleep harmless.

Superseded criteria: home shows a generic "Davam edən sınaq" entry (now the Oyna
banner, HOME-10); offline end text "Nəticə üçün internetə qoşul" (now EXAM-14).

### EXAM-14 Submit and auto-submit (design, 2026-11-21)

- Given the user taps "Sınağı bitir" (last task, grid sheet or leave dialog), When
  the confirmation is shown, Then it reads title "Sınağı bitirirsən?", counts
  "Cavabsız: 7 · İşarələnib: 3" (or "Bütün tapşırıqlara cavab vermisən." when both
  are 0; empty written tasks count as cavabsız, EXAM-12) and "Bitirəndən sonra
  cavabları dəyişə bilməyəcəksən.", with "Ləğv et" / "Bitir".
- Given "Bitir" is tapped, When accepted, Then the attempt is read-only at once, a
  full-screen "Cavablar göndərilir…" (back disabled) is shown, the client syncs all
  pending answers and calls submit; on success the result opens and the attempt
  screen is removed from the back stack.
- Given `endsAtEpochMs` is reached, When the user is in the app (whatever is open),
  Then all input stops at once (options, field, player, flag), dialogs, the grid and
  the keyboard close, and a blocking full-screen "Vaxt bitdi" / "Sınaq avtomatik
  bitirildi. Cavabların göndərilir…" (no close, back does nothing; assertive
  TalkBack announcement once) flushes the queue and submits inside the 2-min grace;
  on success the result shows "auto-submitted" (EXAM-17).
- Given submit is called twice (double tap, retry), When the second arrives, Then
  the same result is returned and nothing changes.
- Given the user never submits and never returns, When the server deadline plus the
  late-upload window passes, Then the server finalizes the attempt with the synced
  answers; the result and history mark it as finalized by the server.
- Given submit fails offline (user submit), When it fails, Then "Nəticə internet
  olanda hesablanacaq" / "Cavabların telefonda saxlanılıb. İnternet qayıdanda
  avtomatik göndərəcəyik." with "24 saat ərzində internetə qoşulmasan, yalnız
  göndərilmiş cavablar sayılacaq." and "Sınaqlara qayıt" is shown; the client
  retries in the background (network constraint) for 24 h (OQ-118); the catalogue
  shows "Nəticə hesablanır".
- Given the time ends offline, When it ends, Then the same offline view is shown
  with the title "Vaxt bitdi".

Superseded criteria: one-sentence confirmation "Cavabsız: 7, İşarələnmiş: 3.
Sınağı bitirmək istəyirsən?".

### EXAM-15 Server-side scoring and estimated DİM score

- Given a submitted attempt, When the server scores it, Then it uses only stored
  answers and the answer keys on the server, and the formulas of §2 for the exam
  type (stage 1: no penalty; stage 2: NBq with −1/4, floor 0, NBa, weights).
- Given written tasks without a self-assessment, When the result is first shown,
  Then they count as 0 and are marked "Qiymətləndirilməyib" (OQ-112).
- Given listening was skipped (EXAM-28) or its audio failed (EXAM-10), When the
  server scores, Then the listening tasks are removed from both the score and the
  maximum, and the server returns the reduced `maxScore` (e.g. 83,8) with the
  estimate; the result is **not** rescaled to 100 (OQ-D42).
- Given any number, When the client shows it, Then it is the server's value already
  rounded to 0.1; the client only formats it and never recomputes it.
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

### EXAM-16 Self-assessment of written tasks (design, 2026-11-21)

Decided: OQ-112 (B), OQ-D47 (marks editable later). Screen:
`design/screens/exam-self-assessment.md`.
- Given a scored attempt with unassessed written tasks, When the result screen opens,
  Then a card "Yazılı tapşırıqları qiymətləndir" / "Qiymətləndirməsən, 7 yazılı
  tapşırıq 0 sayılır." with the primary button "Qiymətləndir (7)" is shown; it opens
  the first unassessed task. The card is hidden while the result is still pending
  (offline submit not yet scored).
- Given the self-assessment flow, When it opens, Then it shows one page per written
  task in exam order, the progress "2 / 7" next to the task line (not in the app
  bar), and once per attempt on the first page the note "Cavabını nümunə ilə
  müqayisə et və meyarlara görə qiymət seç. Özünə dürüst qiymət ver — bu bal yalnız
  sənin üçündür.".
- Given a written task, When shown, Then the stem, a link to the passage ("Mətnə
  bax") or the transcript ("Transkriptə bax", with the review player, EXAM-26), the
  user's text (or "Cavab yazılmayıb — kağızda həll etmisənsə, onunla müqayisə et."),
  the model answer(s) ("Nümunəvi cavab", "Nümunə 2" …), the criteria ("Meyarlar",
  highest mark first) and the mark picker 0, ⅓, ½, ⅔, 1 are shown; tapping a
  criterion's mark chip selects that mark; the points line ("1,33 / 2 xal") appears
  once a mark is chosen.
- Given a mark is chosen, When selected, Then it is saved at once (locally first,
  then sent to the server; no save button); the server stores it as `self_assessed`
  and returns the recomputed estimate, shown separately ("Avtomatik yoxlanılıb: 46,9
  · Özün qiymətləndirdin: 20,8").
- Given "Növbəti", When tapped without a mark, Then the next task opens (skipping is
  allowed); on the last page "Qiymətləndirməni bitir" returns to the result with
  "Təxmini bal yeniləndi"; close or back at any page returns with what was saved.
- Given the user skips some tasks, When the result is shown, Then they stay 0 and
  "Qiymətləndirilməyib" until assessed; assessment stays possible later from the
  result (also opened from history) and from the review.
- Change later (OQ-D47): Given all written tasks are assessed, When the result is
  shown, Then a text button "Qiymətləri dəyiş" opens the flow with the saved marks
  pre-selected; in the review each written task offers "Qiymətləndir" or "Qiyməti
  dəyiş" for that task; every change recomputes the estimate on the server. There is
  no time limit for changes.
- Offline: Given no network, When a mark is chosen, Then it is shown as chosen, the
  banner "Qiymətlər telefonda saxlanıldı, internet olanda göndəriləcək." is shown,
  and the result keeps the server's last estimate until the server accepts the marks.
- Given the review data (model answers, criteria) is neither cached nor loadable,
  When the flow opens, Then "Təhlil üçün internet lazımdır." with retry is shown.
- Given a client sends a mark outside {0, 1/3, 1/2, 2/3, 1}, When received, Then the
  server rejects it (400) and the client reverts to the server value with a generic
  error.
- Given rotation or process death, When restored, Then the same page and the saved
  marks are shown.

Superseded criteria: "Yazılı tapşırıqları qiymətləndir (7)" as one label; "later
from history" as the only later path.

### EXAM-17 Result screen (design, 2026-11-21)

Screen: `design/screens/exam-result.md`.
- Phase 1 (subject sınaq): Given a scored attempt, When shown, Then it shows "Sınaq
  №1 · İngilis dili" with chips ("Fənn sınağı", "Təkrar cəhd" if a retake, the Baku
  date), the overline "TƏXMİNİ BAL", the estimate "67,7 / 100" and directly under it
  "Bu, rəsmi DİM nəticəsi deyil."; a score split bar with the automatic part, the
  self-assessed part and the unassessed count (EXAM-16); per section ("BÖLMƏLƏR"):
  "Qapalı: 2 / 3 düzgün" and, with written tasks, "Yazılı: 1,7 / 3" or "Yazılı:
  qiymətləndirilməyib"; a total line "Qapalı tapşırıqlar: 18 düzgün · 4 səhv · 1
  cavabsız"; "Təhlilə bax", "Sınaqlara qayıt" and the DİM disclaimer.
- Reduced maximum (OQ-D42): Given listening was skipped or its audio failed, When
  shown, Then the score is shown out of the server's reduced maximum (e.g. "58,4 /
  83,8"), the note "Dinləmə hissəsini keçdin, ona görə bu tapşırıqlar bala daxil
  edilməyib. Maksimum bal: 83,8." (or the audio-failed variant) is shown, and the
  listening section reads "Bala daxil edilməyib".
- Given at most one note fits the reason, When shown, Then the priority is: finalized
  by the server > auto-submitted at time up > audio failed > listening skipped; when
  listening was excluded **and** the time ran out, both notes are shown.
- Given the user returns from the self-assessment, When the estimate changed, Then
  the number cross-fades (no count-up), the split animates and "Təxmini bal yeniləndi"
  is shown.
- Given the score is 0, When shown, Then the layout is the same, without jokes or
  praise; the assessment card still invites written marks.
- Given the result is opened from history while offline, When a cache exists, Then
  it is shown with a compact offline line; assessment still works (marks queue);
  without cache an error state with retry is shown.
- Given the server says this submit made today an active day (OQ-125 B), When shown,
  Then the streak row is shown as on the quiz result; there is **no XP row**.
- Given a key was corrected after the attempt (EXAM-15), When shown, Then the score is
  unchanged; the review marks the task.
- Given close, "Sınaqlara qayıt" or system back, When tapped, Then the catalogue is
  shown (the attempt is not in the back stack).
- Phase 2 (full buraxılış): Given a scored full attempt, When shown, Then a total
  block ("Təxmini bal: 214,3 / 300") precedes per-subject blocks of the same anatomy,
  with coded correct counts per subject.
- Given stage 2 (Phase 3), When shown, Then it shows the weighted score per subject,
  the penalty effect ("Səhv cavablara görə −1,5") and the 400 total; the 700 total
  only if the user has a stage 1 result they choose to combine (proposal, later).
- Given long subject or section names at font scale 1.3 on 360 dp, When shown, Then
  nothing is truncated (wraps); the score row fits on one line.

Superseded criteria: Phase 1 shown with a total "/ 300" (only Phase 2); "progression
effects follow OQ-125" (now: streak row only, no XP row).

### EXAM-18 Review (design, 2026-11-21)

Screen: `design/screens/exam-review.md`.
- Given a scored attempt, When "Təhlilə bax" is tapped, Then "Təhlil" lists every
  task (not only wrong ones) grouped by section in exam order; each task card shows
  the task line with a flag mark if flagged and the outcome word ("Düzgün", "Səhv",
  "Cavabsız"; written: "Qiymətin: ⅔" or "Qiymətləndirilməyib"), the stem, for closed
  tasks "Sənin cavabın" and "Düzgün cavab" (or "Sənin cavabın — düzgün" / "Cavab
  verilməyib"), for written tasks the user's text, the model answer, the criteria
  (collapsed behind "Meyarları göstər") and "Qiymətləndir" / "Qiyməti dəyiş"
  (EXAM-16), and the explanation ("İzah") if any. Meaning is never by colour alone.
- Given a task group, When listed, Then a collapsed context card precedes its tasks:
  reading "Oxu mətni" with "Mətni göstər" / "Gizlət" (full passage, text selection
  on); listening "Transkript" with the review player (EXAM-26) and "Transkripti
  göstər" (speaker turns).
- Given excluded listening (skipped or audio failed), When listed, Then the group and
  each task carry "Bala daxil edilməyib"; the tasks still show the key and
  explanation.
- Given a key corrected after the attempt, When listed, Then the task shows "Bu
  tapşırıq sonradan düzəldilib. Balın dəyişməyib." with the **current** key.
- Given the filter chips "Hamısı", "Səhvlər", "Cavabsızlar", "İşarələnmişlər",
  "Yazılılar" (single select, scrollable row, counts in the content description),
  When one is selected, Then only those tasks are listed; an empty filter shows "Bu
  filtrdə tapşırıq yoxdur." with the chips still visible.
- Given the review data, When requested before submit, Then the server returns 409;
  keys, explanations, model answers and transcripts are only returned after submit
  or finalization; if it happens in the app, the user goes back to the catalogue with
  a generic error (logged).
- Given the review loaded once, When opened offline, Then the cached review is shown;
  audio replay needs the local file. Without cache: "Təhlil üçün internet lazımdır."
  with retry; other failures: "Təhlili yükləmək alınmadı." with retry.
- Given the user returns from the self-assessment, When the review is shown, Then
  marks and outcome words are updated in place and the scroll position is kept.

Superseded criteria: filters without "Hamısı" and "Yazılılar"; transcript without a
player (now EXAM-26).

### EXAM-19 Subject-only sınaq ("Fənn sınağı")

- Given a variant, When the user picks one subject block (e.g. "İngilis dili"), Then
  an attempt with only that block starts, with the subject duration of OQ-117
  (proposal 60 min) and the same rules as a full attempt.
- Given a subject attempt, When scored, Then only that subject's estimate (max 100)
  is shown; it is never shown as a full-exam total.
- Given a full attempt and a subject attempt of the same variant, When listed, Then
  they are separate attempts (OQ-124 applies to both).
- Note (design, 2026-11-21): Phase 1 publishes only the English subject sınaq, so
  there is no block picker in the UI yet; every Phase 1 attempt is `mode = subject`
  (60 min, max 100) and is labelled "Fənn sınağı". The picker arrives with the full
  buraxılış (Phase 2).

### EXAM-20 Retakes and history (design, 2026-11-21)

Decided: OQ-124 (unlimited retakes, 1 running attempt, 10 starts per Baku day).
- Given a finished attempt, When the user opens the same variant, Then the info screen
  says "Bu sınağı artıq vermisən — nəticə «Təkrar cəhd» kimi qeyd olunacaq." with
  "Yenidən başla" and "Son nəticə"; a new attempt is marked "Təkrar cəhd" (the user has
  seen the keys) in the catalogue, history and result.
- Given the catalogue, When the user taps the last row "Sınaqlarım" (in the
  catalogue, not on Profil), Then past attempts are listed newest first, 20 per page,
  with "Sınaq №1 · İngilis dili", the Baku date ("21 noyabr"; with the year if not the
  current one), the state (estimate, "Yazılılar qiymətləndirilməyib", "Nəticə
  hesablanır", running, or "Bitirilməyib — server yekunlaşdırıb") and chips ("Fənn
  sınağı", "Təkrar cəhd"); tapping opens the result (a running attempt opens the
  attempt).
- Given no attempts, When history opens, Then "Hələ sınaq verməmisən" with "Sınaq
  seç" (back to the catalogue) is shown.
- Given history fails, When loading, Then an error with retry; offline with cache →
  cached rows and an offline banner; a failing next page → an inline retry row.
- Given at most one running attempt per user, When the user starts another variant
  while one runs (409), Then a dialog "Əvvəlcə davam edən sınağı bitir" / "«Sınaq №2»
  hələ davam edir: 12 dəq qalıb." with "Ləğv et" / "Ona keç" is shown; "Ona keç" opens
  the running attempt, where it can be finished with the normal confirmation
  (EXAM-14). The dialog never finishes an attempt itself.

Superseded criteria: dialog actions "Davam et" / "Bitir" (finishing from a dialog
without seeing the attempt is too easy to hit by mistake).

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

### EXAM-23 Account deletion and privacy (design, 2026-11-21)

- Given `DELETE /v1/me`, When it runs, Then all exam attempts, answers, written texts,
  flags and self-assessments of the user are deleted in the same transaction
  (`docs/mvp-spec.md` §3a must list them when the feature ships).
- Given written answer texts, When stored, Then they are personal data: listed in the
  privacy policy (`docs/legal/`), never sent to analytics or logs.
- (design, 2026-11-21) Given sign-out, account switch or account deletion, When the
  local cleanup runs (AUTH-07, SET-05), Then local attempts, queued answers, marks,
  cached catalogue/result/review data and the EXAM-27 alarm and notification of that
  account are deleted or cancelled; downloaded packages may stay (no personal data in
  them).

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

### EXAM-25 Naming and disclaimer (design, 2026-11-21)

- Given any exam title, When shown in the app, store listing, push or share text,
  Then it uses our naming ("Buraxılış sınağı №3 (DİM formatında)", "I qrup qəbul
  sınağı (DİM formatında)") and never a year, "rəsmi", "real", "DİM imtahanı" or the
  DİM logo.
- Given the catalogue, info, result and settings "Haqqında" screens, When shown, Then
  the disclaimer is visible (OQ-130; final copy `exam_disclaimer` in
  `design/copy/az.md`): "Sınaqlar Duello Quiz komandasının orijinal tapşırıqlarıdır
  və DİM formatına uyğun hazırlanıb. Dövlət İmtahan Mərkəzi (DİM) ilə əlaqəsi yoxdur
  və DİM tərəfindən təsdiqlənməyib. Bal təxminidir."
- (design, 2026-11-21) Given settings with the flag on, When the "Haqqında" section is
  shown, Then a non-clickable row "Sınaqlar haqqında" carries the full disclaimer as
  its supporting text, never truncated (SET-03); with the flag off the row is absent.
- (design, 2026-11-21) Given the open-source licences screen with the flag on, When
  shown, Then a section "DİNLƏMƏ SƏSLƏRİ" credits the Piper voices used for the
  listening audio (OQ-120; CC BY 4.0 voices with a link to the model card, the
  public-domain voice listed for transparency); hidden with the flag off (SET-03).
- (design, 2026-11-21) Given a variant title, When built, Then it is "Sınaq №N" from
  `variantNumber` (OQ-D49) and the exam type title comes from server i18n; neither
  ever contains a year, "rəsmi", "real" or "DİM imtahanı".

### EXAM-26 Review audio player (new, design, 2026-11-21)

Decided: OQ-D44 (in Phase 1). Component: `design/screens/components.md` §42a. Used in
the review listening card (EXAM-18) and the transcript sheet of the self-assessment
(EXAM-16).
- Given a submitted or finalized attempt, When a listening group is shown in the
  review or the transcript sheet, Then a review player is shown with play/pause, a
  seek slider, elapsed / total time ("1:12 / 3:05") and "−10 s" / "+10 s" buttons; no
  play counter, no play dots, no "counts after 10 s" caption.
- Given the review player, When the user drags or taps the slider, Then playback
  seeks there (on release); replays are unlimited; at the end the main button becomes
  "Yenidən dinlə" and restarts at 0:00; there is no speed control.
- Given the attempt is running, When any screen of the attempt is shown, Then the
  review player mode is never available (it only exists with review data, which the
  server returns only after submit, EXAM-18, EXAM-21).
- Given the app goes to the background, When the review player plays, Then playback
  continues and the media notification shows a seek bar (seek allowed in this mode
  only).
- Given the local audio was deleted (EXAM-03 cleanup) or never downloaded on this
  device, When the user taps play online, Then the audio is downloaded first (progress
  in the button), then plays; offline → "Audio yüklənmədi. İnternetə qoşul və yenidən
  cəhd et." with "Yenidən cəhd et".
- Given the audio failed during the attempt (EXAM-10), When the review opens, Then the
  player retries the same way; the exclusion from the estimate stays (the score does
  not change).
- Given listening was skipped (EXAM-28), When the review opens, Then the transcript
  and the review player are available as for any other attempt.
- Given TalkBack, When the player is focused, Then the button reads "Dinlə" / pause /
  "Yenidən dinlə", the slider is one node "Audio mövqeyi" with the elapsed time and
  steps 5 s per adjustment.
- Given rotation or leaving the review, When it happens, Then only one player plays
  at a time; the position is kept while the review screen lives.

### EXAM-27 "10 dəqiqə qalıb" local notification (new, design, 2026-11-21)

Decided: OQ-D45 (in Phase 1). Screen: `design/screens/exam-attempt.md` I. Related:
`notifications.md` (NOTIF-09), SET-07. Local notification, not FCM; works offline.
- Given an attempt starts or is resumed, When it runs, Then the app schedules one
  inexact alarm for `endsAtEpochMs` − 10 min (no exact-alarm permission; Doze may
  delay it by a minute or two).
- Given the alarm fires, When the attempt is still running, the app is **not** in the
  foreground and ≥ 5 min remain, Then one notification is posted: title "10 dəqiqə
  qalıb" (the whole minutes left at posting, plural-aware), body "«Sınaq №1» davam
  edir. Cavablarını yoxlamağa qayıt."; it disappears by itself at `endsAtEpochMs`.
- Given the app is in the foreground, When the alarm fires, Then nothing is posted
  (the in-app 10-minute snackbar of EXAM-04 does the job).
- Given fewer than 5 min remain at fire time (late delivery), or the attempt has
  ended, When the alarm fires, Then nothing is posted.
- Given one attempt, When it runs, Then at most **one** such notification is posted,
  however often the app is backgrounded or resumed.
- Given the app comes to the foreground, the attempt is submitted or times out, or
  the user signs out, When it happens, Then the alarm and any shown notification are
  cancelled.
- Given a reboot or an app update during an attempt, When the device is back, Then
  the alarm is rescheduled.
- Given the user taps the notification, When the attempt still runs, Then the app
  opens straight into the attempt (resume, same rules as the Oyna banner, HOME-10);
  if it has ended meanwhile → its result or the time-up state.
- Permission: Given Android 13+ without `POST_NOTIFICATIONS`, or app notifications or
  the "Sınaqlar" channel turned off, When the alarm fires, Then nothing is posted and
  nothing breaks; the exam flow **never** shows the system permission dialog (before
  or during an attempt); the only hint is on the info screen (EXAM-02). The permission
  is still asked only by the NOTIF-01 card. Android 8–12: allowed by default.
- Channel: Given the first attempt starts, When the notification channel is needed,
  Then a channel "Sınaqlar" (default importance, no full-screen intent, sound per
  system) is created; it is the only control (no in-app switch in Phase 1, SET-07).
- Given the server notification rules, When this notification is posted, Then it does
  not count toward the 2-per-day server limit and the quiet hours do not apply (it is
  a once-per-attempt local reminder of an exam the user started, NOTIF-07); the text
  rules of NOTIF-07 apply (no exclamation mark, no emoji, no false urgency).

### EXAM-28 Skip listening (new, design, 2026-11-21)

Decided: OQ-119 (skip option for deaf or hard-of-hearing users), OQ-D42 (before start
only; score out of the reduced maximum).
- Given an exam with listening, When the info screen is shown, Then a switch
  "Dinləmə hissəsini keç" with "Eşitməkdə çətinlik çəkirsənsə. Dinləmə tapşırıqları
  göstərilməyəcək və təxmini bala daxil edilməyəcək." is shown; default off; nothing
  is sent to the server until the start request (it is part of it, `skipListening`).
- Given the switch is on, When the user taps "Başla", Then the confirmation adds
  "Dinləmə hissəsi keçiləcək." and the attempt stores the choice.
- Given a running attempt, When its info screen is shown, Then the switch shows the
  attempt's value and is disabled; there is **no** way to skip listening after the
  start (it could otherwise be used to drop tasks after hearing them).
- Given an attempt with skipped listening, When it runs, Then the listening tasks are
  not in the pager (it starts at the first non-listening task, e.g. task 7; tasks
  keep their exam numbers), the grid shows the section as skipped instead of cells,
  and the leave/submit counts ignore the skipped tasks. (Whether the header reads
  "7 / 30" or "1 / 24" is not specified — designer to confirm, see §11.)
- Given such an attempt is scored, When the result is shown, Then the score is out of
  the reduced maximum (e.g. "58,4 / 83,8") with the skip note, not rescaled (EXAM-15,
  EXAM-17); the review still lists the listening tasks with key, explanation,
  transcript and player, marked "Bala daxil edilməyib" (EXAM-18, EXAM-26).
- Given a client sends answers for listening tasks of a skipped attempt, When
  received, Then the server does not score them (excluded from estimate and maximum).

## 6. Business rules (DİM rules marked; the rest decided by Sabit unless "proposal")

- Exam structure and scoring: DİM, §2 of this file (until a spec section exists).
- Durations and limits (decided): subject sınaq 60 min, full sınaq 180 min (Phase 2),
  2-min server grace never displayed (OQ-117, OQ-D41), no pause, 24 h late-upload
  window (OQ-118), unlimited retakes, 1 running attempt, 10 starts per Baku day
  (OQ-124); listening 2 plays, counted after 10 s, position saved every 1 s (OQ-119,
  OQ-D43); in-app notices at 30 / 10 / 1 min, warning colour from 10 min; one local
  reminder at 10 min left, posted only with ≥ 5 min left (OQ-D45). Exact numbers live
  in the screen specs and `docs/api/openapi.yaml` until the spec section exists.
- Start needs one online request; a running attempt continues offline (OQ-118,
  OQ-D48).
- Self-assessed marks may be changed at any time after submit (OQ-D47); they never
  give XP (OQ-125).
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
EXAM-21. Phase 1 also excludes (design, 2026-11-21): a fourth navigation tab
(OQ-D40), server push (FCM) for exams, an in-app switch for the EXAM-27 reminder (the
system "Sınaqlar" channel is the control), speed control in any player, a listening
practice mode outside attempts (Phase 2), forcing the screen to stay on, a
notification for the package download (unless Android requires one for the worker).

## 10. Analytics events (Firebase Analytics)

No task text, answer text or written answers in events.

| Event | Properties | When |
| --- | --- | --- |
| `exam_catalog_view` | `variant_count`, `source`: `home_row` \| `back` \| `result` (design, 2026-11-21: tells whether the below-the-fold Oyna row is found, OQ-D40) | Catalogue shown, once per open |
| `exam_download_start` | `exam_type`, `variant_id`, `size_kb` | "Yüklə" tapped |
| `exam_download_result` | `exam_type`, `variant_id`, `result`: `ok` \| `offline` \| `storage` \| `server` \| `verify` \| `cancelled`, `duration_s` | Download ends |
| `exam_start` | `exam_type`, `variant_id`, `mode`: `full` \| `subject`, `subject`, `retake` (bool), `skip_listening` (bool), `notif_allowed` (bool) | Attempt created |
| `exam_start_failed` | `exam_type`, `reason`: `offline` \| `other_running` \| `rate_limited` \| `version_mismatch` \| `unavailable` \| `server` | Start request rejected or failed |
| `exam_resume` | `exam_type`, `reason`: `app_kill` \| `other_device` \| `return`, `entry`: `oyna_banner` \| `oyna_row` \| `catalogue` \| `notification` \| `info`, `remaining_min` | Attempt restored |
| `exam_audio_play` | `variant_id`, `play_no`, `completed` (bool), `restored` (bool, OQ-D43) | Play ends |
| `exam_audio_failed` | `variant_id`, `error_type` | Playback error |
| `exam_review_audio_play` | `variant_id`, `seeked` (bool), `listened_pct_bucket` (0–9) | Review player stops or the review closes (EXAM-26) |
| `exam_time_left_notification` | `action`: `posted` \| `skipped_foreground` \| `skipped_late` \| `blocked` \| `opened` | EXAM-27 alarm fired or notification tapped |
| `exam_submit` | `exam_type`, `mode`, `trigger`: `user` \| `deadline` \| `server`, `answered_count`, `flagged_count`, `duration_min`, `offline_at_submit` (bool) | Submit accepted |
| `exam_self_assess_done` | `exam_type`, `assessed_count`, `skipped_count`, `is_edit` (bool, OQ-D47) | Student leaves the self-assessment flow |
| `exam_result_view` | `exam_type`, `mode`, `score_bucket` (0–9 by tenths of max), `listening_excluded`: `none` \| `skipped` \| `audio_failed` | Result shown |
| `exam_review_open` | `exam_type`, `filter`: `all` \| `wrong` \| `unanswered` \| `flagged` \| `written` | Review opened or a filter applied |
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

Design questions decided by Sabit on 2026-11-21 (owned in `design/screens/exam-*.md`;
listed here so stories and tests can reference them):

| ID | Topic | Decision (short) | Stories |
| --- | --- | --- | --- |
| OQ-D40 | Entry point | Section below the categories on Oyna + running banner at the top; Oyna may scroll ~96 dp with the flag on (HOME-07 exception, HOME-10) | EXAM-01, EXAM-13 |
| OQ-D41 | Displayed deadline | Countdown to `endsAtEpochMs`; the 2-min grace is never shown | EXAM-04 |
| OQ-D42 | Skip listening | Before the start only; score out of the reduced maximum, not rescaled | EXAM-15, EXAM-17, EXAM-28 |
| OQ-D43 | App kill during a counted play | Play stays used and resumes paused at the saved position | EXAM-10, EXAM-13 |
| OQ-D44 | Review audio replay | In Phase 1: free replay with seeking, after submit only | EXAM-18, EXAM-26 |
| OQ-D45 | Background reminder | In Phase 1: one local "10 dəqiqə qalıb" notification, own channel, never asks permission | EXAM-27 |
| OQ-D47 | Change marks later | Yes, from the result and the review, any time | EXAM-16 |
| OQ-D48 | Offline start | Start needs internet; running attempts continue offline | EXAM-01, EXAM-04 |
| OQ-D49 | Variant names | App builds "Sınaq №N" from `variantNumber` | EXAM-01, EXAM-25 |

OQ-D46 is not defined in any design file (numbering gap or missing entry; the
designer confirms).

Still open:
- **OQ-132** (open-questions.md) — which Baku day becomes active when an attempt
  crosses midnight (start day vs submit day), and whether a server-finalized attempt
  counts. Affects EXAM-17 (streak row) and the backend. Recommendation: the Baku day
  of the submit, user or deadline trigger only.
- Designer to confirm: header progress with skipped listening ("7 / 30" or "1 / 24",
  EXAM-28).

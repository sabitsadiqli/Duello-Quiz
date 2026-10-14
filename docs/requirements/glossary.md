# Glossary

Owner: `business-analyst`. One meaning per term. Requirements, API docs, code
names, tests and UI copy use these terms. If a term is missing or used with a
second meaning anywhere, add it here or raise an open question.

Column "UI (az)" is the proposed Azerbaijani UI term. The designer owns final copy;
a change there must be mirrored here. "—" means the term never appears in the UI.

## Product and modes

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Category | Kateqoriya | A topic of questions (e.g. Coğrafiya, Tarix). A row in `categories`, not code. Shown only when `is_active = true`, ordered by `sort_order`. |
| Category quiz (single-player quiz) | Kateqoriya quiz-i | Single-player mode: one user answers 10 questions of one category. See `docs/mvp-spec.md` §1. Never affects Elo. |
| Session (quiz session) | — | One play-through of a category quiz, from `POST /quiz/sessions` to finish or deadline. Row in `quiz_sessions`. A session has exactly one deadline. Never use "session" for an auth sign-in session; say "sign-in state" instead. |
| Session deadline | — | Server time after which a quiz session accepts no answers: start + 10 × (20 s + 1.5 s reveal) + 30 s grace = start + 245 s (`docs/mvp-spec.md` §1). |
| Question timer | — | The 20 s countdown per question in the category quiz, shown by the client. Display aid; the server enforces only the session deadline (OQ-004). |
| Timeout (quiz) | Vaxt bitdi | The question timer reached 0 before the user picked an option. Counts as not correct. |
| Score (quiz) | Xal | The result number of a quiz session. Formula: see OQ-012. |
| Wrong-answer review | Səhvlərin təhlili | Part of the quiz result screen listing questions answered wrong or timed out, with the correct option and explanation. |
| Explanation | İzah | Optional text per question (`explanation_i18n`) shown after the answer is resolved. |
| Duel | Duel | Real-time 1v1 confidence game. Same as "match" from the player's view. |
| Match | Oyun | One duel between two players: 7 rounds plus up to 3 sudden-death questions. Row in `matches`. |
| Round | Raund | One question inside a match, consisting of four phases. Rounds 1–7 are regular; sudden-death questions are extra rounds (`sudden_death = true`). |
| Phase | — | One timed step of a round. Exactly four, in order: question, confidence, answer, result. |
| Question phase | — | 3 s; only the question text (and image) is shown. |
| Confidence phase | — | 5 s; the player picks Əminlik 1/2/3, hidden from the opponent. Called "Bet" in `docs/mvp-spec.md` §2 and in protocol names (`round.bet`); never "bet" in the UI. |
| Answer phase | — | 10 s; the server sends 4 options after both confidence picks are locked. |
| Result phase | — | 3 s; both Əminlik picks, answers and points are revealed together. |
| Əminlik (confidence) | Əminlik | The 1/2/3 level a player commits to before seeing the options. Determines points won or lost. Internal/protocol name: `bet`. UI never says "bet", "stake", "mərc" or uses casino visuals. |
| Əminlik 3 limit | — | A player may pick Əminlik 3 at most twice per match; the server enforces it, the UI disables the chip. |
| Speed bonus | Sürət bonusu | 0–50 extra points on a correct duel answer, linear by remaining answer time. Never multiplied. Not used in the category quiz. |
| All-in round | Həlledici raund | Round 7. Bet points (+ and −) are doubled; the speed bonus is not. UI copy owned by designer. |
| Sudden death | Əlavə sual | After round 7 with tied scores: up to 3 extra questions, Əminlik fixed at 1, no score change, the fastest correct answer wins. Still tied after 3 → draw. |
| Draw | Heç-heçə | Match result with no winner; Elo 0.5 for both. |
| Missed round | — | A round in which a player sent neither an Əminlik pick nor an answer. |
| Forfeit | Məğlubiyyət (texniki) | Match end after a player has 2 missed rounds in a row. That player loses (also for Elo); the opponent wins; current scores are stored. |
| Reconnect window | — | 10 s after a disconnect in which a player may rejoin the running match. |
| Calibration card | Əminlik kartı | Post-match card with the player's accuracy per Əminlik level. |
| Matchmaking queue | Rəqib axtarılır | Single global first-come-first-served queue; 30 s without opponent → "no opponent found" with retry. |

## Rating and profile

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Elo rating | Reytinq | Per-user number, start 1000, K = 32. Changed only by finished duels (win, loss, draw, forfeit). Never by the category quiz. |
| Profile | Profil | The user's backend row (`users`): display name, avatar URL, rating. Created on the first authenticated API call. |
| Display name | Ad | Name shown to the user and to duel opponents. Source per sign-in method: see OQ-011. |

## Accounts and auth

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Account | Hesab | A Firebase Authentication user plus the backend profile row linked by `firebase_uid`. |
| Sign-up | Qeydiyyat | Creating an account with email and password. |
| Sign-in | Daxil ol | Authenticating with email/password or Google. |
| Sign-out | Çıxış | Ending the sign-in state on this device. The account stays. |
| Google sign-in | Google ilə daxil ol | Sign-in through Android Credential Manager with a Google account. |
| Password reset | Şifrəni bərpa et | Firebase sends a reset link to the email; the user sets a new password outside the app. |
| Email verification | E-poçtun təsdiqi | Firebase verification link for email/password accounts. Whether it is required: OQ-010. |
| Account deletion | Hesabı sil | Permanent removal of the account and personal data, started by the user in the app (Google Play requirement). See OQ-014. |
| Sign-in state | — | Whether a Firebase user is present on the device. Not called "session". |
| ID token | — | Firebase ID token sent as `Authorization: Bearer` on every API call; verified by the backend. |

## Content

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Question | Sual | One item: text, exactly 4 options, exactly 1 correct, optional image and explanation. Stable `external_id`. |
| Option | Variant | One of the 4 answers of a question. Positions shuffled by the importer. |
| Draft question | — | `status: draft`. Written, not fact-checked. `is_active = false`; never served. |
| Reviewed question | — | `status: reviewed`. Fact-checked by Sabit. The only status that becomes `is_active = true` and can be served. |
| Retired question | — | `status: retired`. Removed from play (`is_active = false`); ID is never deleted or reused. |
| Active question | — | A question with `is_active = true` (= reviewed). "Playable" counts refer to active questions only. |
| Question bank | — | The real questions in the private content repo cloned to `content/bank/`. |
| Difficulty | — | 1 easy, 2 medium, 3 hard. Not shown in the MVP UI. |

## App

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Home | Əsas səhifə | First screen after sign-in: categories, duel entry, profile summary, settings entry. |
| Settings | Parametrlər | Theme, language, account actions, about. |
| Theme | Tema | System (Sistem), Light (İşıqlı), Dark (Qaranlıq). Default System. |
| Language | Dil | App UI language. MVP: Azərbaycan dili only. |
| Offline | İnternet yoxdur | The device has no usable network connection, or a request failed with a network error. |
| Backgrounding | — | The app leaves the foreground (home button, call, screen off) while the process stays alive. |
| Process death | — | Android kills the app process while it is in the background; the app restarts from saved state. |

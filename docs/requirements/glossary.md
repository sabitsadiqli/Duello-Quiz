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
| Reconnect window | — | 10 s after a disconnect in which a player may rejoin the running match; the server timers keep running. What happens after 10 s: OQ-032. |
| Calibration | — | How well a player's Əminlik matches their real accuracy: ideally higher Əminlik → higher share of correct answers. Counted rounds: OQ-027. |
| Calibration card | Əminlik kartı | Post-match card with the player's accuracy per Əminlik level (DUEL-21). |
| Calibration stats | Əminlik statistikası | Calibration over all counted matches, shown on the profile (PROF-05). Same counting rule as the card. |
| Matchmaking queue | Rəqib axtarılır | Single global first-come-first-served queue; 30 s without opponent → "no opponent found" with retry. |
| Queue entry | — | One user's place in the matchmaking queue. At most one per user; a newer connection of the same user replaces the older one (DUEL-04). |
| Queue timeout | Rəqib tapılmadı | The queue deadline (join + 30 s, server clock) passed without a match; the server removes the entry. |
| Match intro | Rəqib tapıldı | Short screen after `match.found` showing both players before round 1. Duration and cancellation: OQ-037. |
| Server deadline (`deadlineEpochMs`) | — | Server-clock time at which a phase, queue wait or reconnect window ends. Carried by every timed server message; the only deadline that counts. |
| Clock offset | — | Difference between server clock and device clock, estimated by the client from `serverNowEpochMs`; countdown = `deadlineEpochMs` − (device now + offset) on a monotonic clock (DUEL-12). |
| Locked Əminlik | Seçiminiz qeydə alındı | An Əminlik pick the server has accepted for a round. Final; never changes. |
| Defaulted Əminlik | Vaxt bitdi — Əminlik 1 | Əminlik 1 set by the server because no valid pick arrived before the confidence deadline. Not an active pick (calibration, OQ-027). |
| Reveal | — | The result phase moment when both players' Əminlik, answers, the correct option and points are shown at the same time. |
| Answer time (`answered_ms`) | — | Server receive time of the answer minus server send time of the options. Basis of the speed bonus and the sudden-death tie-break. |
| Too late | Vaxt bitdi | A pick or answer the server received more than 250 ms after the phase deadline; treated as not sent (OQ-036). |
| Disconnect | Bağlantı kəsildi | The server lost a player's WebSocket (closed or no heartbeat). The match keeps running. |
| Rejoin (resync) | Bağlantı bərpa olunur | A reconnecting player gets a full match snapshot (`match.state`, proposed) and continues from the current phase (DUEL-13). |
| Leave (match) | Oyundan çıx | Leaving a running match on purpose; recommended to count as an immediate forfeit (OQ-034). |
| Abandoned match | Ləğv edildi | A match ended without a result (both players gone at the same time, server restart; OQ-033). No winner, no Elo change, not counted in profile stats. |
| Cancelled match | — | A match ended before round 1 because a queue leave crossed the pairing (OQ-037). No Elo change, not shown in history. |
| End reason | — | Why a match ended: `normal`, `sudden_death`, `draw`, `forfeit` (proposed field, `match.result.endReason`). |
| Protocol version | — | Version of the duel WebSocket protocol sent by the client in the handshake; the server rejects versions below its minimum (OQ-029). |
| Duel build flag | — | Build-time switch that hides every duel entry until the duel is released (OQ-020). |

## Rating and profile

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Elo rating | Reytinq | Per-user number, start 1000, K = 32. Changed only by finished duels (win, loss, draw, forfeit). Never by the category quiz. |
| Rating change | — | Elo difference caused by one match, e.g. "+16"; stored per match (before/after ratings) and shown on the result screen and in history. Rounding: OQ-038; floor: OQ-039. |
| Profile | Profil | The user's backend row (`users`): display name, avatar URL, rating. Created on the first authenticated API call. |
| Profile screen | Profil | Screen opened from the home header: name, avatar, rating, duel record, recent matches, calibration stats, name editing (`profile.md`). |
| Duel record | — | Counts of wins, losses and draws over counted matches (finished, including forfeits; OQ-026). |
| Recent matches | Son oyunlar | The last N finished matches on the profile, newest first (OQ-031). |
| Deleted player | Silinmiş oyunçu | How an opponent whose account was deleted is shown in matches and history (`docs/mvp-spec.md` §3a). |
| Avatar | — | Profile picture: the Google photo URL, or a placeholder circle with the first letter of the display name. No upload in the MVP. |
| Display name | Ad | Name shown to the user and to duel opponents. 2–20 characters, not unique. Source per sign-in method: OQ-011; editing: PROF-06; offensive names: OQ-030. |

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

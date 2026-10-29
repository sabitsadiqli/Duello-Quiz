# Glossary

Owner: `business-analyst`. One meaning per term. Requirements, API docs, code
names, tests and UI copy use these terms. If a term is missing or used with a
second meaning anywhere, add it here or raise an open question.

Column "UI (az)" mirrors the final Azerbaijani copy in `design/copy/az.md` ("sən"
tone, OQ-041). The designer owns final copy; a change there must be mirrored here.
"—" means the term never appears in the UI. Protocol names refer to
`docs/api/duel-protocol.md`.

## Product and modes

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Category | Kateqoriya | A topic of questions (e.g. Coğrafiya, Tarix). A row in `categories`, not code. Shown only when `is_active = true`, ordered by `sort_order`. |
| Category quiz (single-player quiz) | Kateqoriya quizi | Single-player mode: one user answers 10 questions of one category. See `docs/mvp-spec.md` §1. Never affects Elo. |
| Session (quiz session) | — | One play-through of a category quiz, from `POST /quiz/sessions` to finish or deadline. Row in `quiz_sessions`. A session has exactly one deadline. Never use "session" for an auth sign-in session; say "sign-in state" instead. |
| Session deadline | — | Server time after which a quiz session accepts no answers: start + 10 × (20 s + 1.5 s reveal) + 30 s grace = start + 245 s (`docs/mvp-spec.md` §1). |
| Question timer | — | The 20 s countdown per question in the category quiz, shown by the client. Display aid; the server enforces only the session deadline (OQ-004). |
| Timeout (quiz) | Vaxt bitdi | The question timer reached 0 before the user picked an option. Counts as not correct. |
| Score (quiz) | Xal | The result number of a quiz session. Formula: see OQ-012. |
| Wrong-answer review | Səhvlərin təhlili | Part of the quiz result screen listing questions answered wrong or timed out, with the correct option and explanation. |
| Explanation | İzah | Optional text per question (`explanation_i18n`) shown after the answer is resolved. |
| Duel | Duel | Real-time 1v1 confidence game. Same as "match" from the player's view. |
| Match | Oyun | One duel between two players: 7 rounds plus up to 3 sudden-death questions. Row in `matches`. |
| Round | Raund | One question inside a match, consisting of four phases. Rounds 1–7 are regular; sudden-death questions are extra rounds 8–10 (`suddenDeath = true`, `suddenDeathNo` 1–3). UI: "Raund 3 / 7". |
| Phase | Sual / Əminlik / Cavab / Nəticə | One timed step of a round. Exactly four, in order: question, confidence, answer, result (`match.state.phase` also has `intro`). Sudden death skips the confidence phase. |
| Question phase | Sual | 3 s; only the question text (and image) is shown (`round.question`). |
| Confidence phase | Əminlik | Up to 5 s, starts with `round.confidence` (regular rounds only); the player picks Əminlik 1/2/3, hidden from the opponent. Called "Bet" in `docs/mvp-spec.md` §2 and in protocol names (`round.bet`); never "bet" in the UI. Ends early when both locked (OQ-035). |
| Answer phase | Cavab | Up to 10 s, starts with `round.options`, sent after both confidence picks are locked or the confidence phase has closed. Ends early when both answered. |
| Result phase | Nəticə | 3 s, starts with `round.result`; both Əminlik picks, answers and points are revealed together. |
| Phase close | — | The moment the server ends a confidence or answer phase without both players acting: deadline + 251 ms (millisecond clock), so a message received at exactly deadline + 250 ms still counts (OQ-064). |
| Əminlik (confidence) | Əminlik | The 1/2/3 level a player commits to before seeing the options. Determines points won or lost. Internal/protocol name: `bet`. UI never says "bet", "stake", "mərc" or uses casino visuals. |
| Əminlik 3 limit | Əminlik 3: 2 dəfə qalıb / Əminlik 3 limiti bitdi | A player may pick Əminlik 3 at most twice per match; the server enforces it (`bet_limit_reached`) and sends the remaining uses as `bet3Left`; the UI disables the chip. |
| Speed bonus | Sürət bonusu | 0–50 extra points on a correct duel answer, linear by remaining answer time. Never multiplied. Not used in the category quiz. |
| All-in round | Həlledici raund | Round 7. Bet points (+ and −) are doubled; the speed bonus is not. UI copy owned by designer. |
| Sudden death | Əlavə sual | After round 7 with tied scores: up to 3 extra questions, Əminlik fixed at 1, no score change, the fastest correct answer wins. Still tied after 3 → draw. |
| Sudden-death outcome (`suddenDeathOutcome`) | — | Field of `round.result` in sudden death (OQ-057): `you` / `opponent` (that player won the match with this question), `same_time` (both correct with identical `answeredMs` → next question), `none` (nobody correct → next question, or a draw after the 3rd). `null` in regular rounds. The client never derives it from its own timing. |
| Draw | Heç-heçə | Match result with no winner; Elo 0.5 for both. |
| Missed round | Raund buraxıldı | A round in which a player sent neither an active Əminlik pick nor an answer (sudden death: no answer). Counted in a row by `missedInARow`. |
| Forfeit | Texniki məğlubiyyət | Match end because one player gave up: `endReason = forfeit`, `forfeitedBy` = that player. That player loses (also for Elo); the opponent wins; current scores are stored. Cause (`forfeitCause`, OQ-068): `missed_rounds` (2 missed rounds in a row) or `left` (`match.leave`, or `DELETE /v1/me` during a match). History label for the winner: "Rəqib tərk etdi". |
| Reconnect window | — | 10 s after a disconnect, shown to the opponent as a countdown (`opponent.connection.reconnectDeadlineEpochMs`); the server timers keep running and the seat is held until the match ends (OQ-032). |
| Heartbeat | — | The client sends `ping` every 5 s; the server answers `pong`. Without any client message for 15 s the server closes the socket with `4003` and treats the player as disconnected (OQ-063). |
| Close code | — | WebSocket close status sent by the server: `4000` upgrade required, `4001` session replaced (both: never reconnect automatically), `4002` too many bad messages, `4003` heartbeat timeout (both: reconnect and rejoin) (OQ-062). |
| Stream sequence (`seq`) | — | Per match and player, increases by exactly 1 per match message from `match.found` (seq 1); messages outside a match have `seq = 0`. Used to drop duplicates and detect gaps (DUEL-17). |
| Active match ID (`activeMatchId`) | — | ID of the player's running match: stored by the app on `match.found`, cleared on `match.result`, `match.cancelled` or `match_not_found`; also sent by the server in `hello`, whose value wins (OQ-071). |
| Session replaced | — | A newer duel connection of the same user took over; the older one gets `session_replaced` and close `4001` (DUEL-04). UI: "Bu hesabla başqa cihazda duelə qoşuldun." |
| Calibration | — | How well a player's Əminlik matches their real accuracy: ideally higher Əminlik → higher share of correct answers. Counted rounds: OQ-027. |
| Calibration card | Əminlik kartı | Post-match card with the player's accuracy per Əminlik level (DUEL-21). The server sends counts `{level, picks, correct}`; the client chooses the hint (OQ-059). |
| Calibration stats | Əminlik statistikası | Calibration over all counted matches, shown on the profile (PROF-05, `GET /v1/me/stats`). Same counting rule as the card. |
| Matchmaking queue | Rəqib axtarılır… | Single global first-come-first-served queue; a real opponent is always preferred; 15 s without a real opponent → bot match (OQ-106); 30 s without any opponent (bot disabled) → "Rəqib tapılmadı" with "Yenidən axtar". |
| Queue entry | — | One user's place in the matchmaking queue. At most one per user; a newer connection of the same user replaces the older one (DUEL-04). |
| Queue timeout | Rəqib tapılmadı | The queue deadline (join + 30 s, server clock) passed without a match; the server removes the entry. Since OQ-106 only reached when the bot is disabled or cannot be started. |
| Bot opponent | Bot rəqib | Server-side player that fills an empty queue after 15 s (OQ-106, DUEL-23 … DUEL-29). No account, no Elo rating, never disconnects or forfeits; plays by the same rules with a probability model (OQ-108). Always shown as "Duello Bot" with the "Bot" badge. Never call it "AI", "kompüter" or a person's name in the UI. Not a ghost duel. |
| Duello Bot | Duello Bot | The single fixed display name of the bot opponent (OQ-107); "duello" is a reserved word for human names (OQ-051). |
| Bot badge | Bot | Small label shown next to the bot's name on every duel screen and in history, in place of the rating (OQ-107). |
| Bot match | Bot oyunu | A match against the bot opponent (`opponentType = bot`). Never changes Elo; half duel XP (`docs/mvp-spec.md` §10); counts as an active day if finished and not forfeited by the user; deleted with the user's account. Counting in profile stats: OQ-110. |
| Real match | — | A match between two human players (`opponentType = human`). Used where a rule differs from a bot match. |
| Bot time (`botAtEpochMs`) | — | Server time at which a lone queued player gets a bot match: `queue.joined` + 15 s (proposed field name; `null` when the bot is disabled). |
| Ghost duel | — | Playing against a recording of a real player's past match. Out of scope; not the same as the bot opponent. |
| Match intro | Rəqib tapıldı! | 3 s screen after `match.found` showing both players before round 1 (OQ-037). A `match.leave` during the intro is a forfeit; only a `queue.leave` that crossed the pairing cancels the match (OQ-065). |
| Server deadline (`deadlineEpochMs`) | — | Server-clock time at which a phase, queue wait or reconnect window ends. Carried by every timed server message; the only deadline that counts. |
| Clock offset | — | Difference between server clock and device clock, estimated by the client from `serverNowEpochMs`; countdown = `deadlineEpochMs` − (device now + offset) on a monotonic clock (DUEL-12). |
| Locked Əminlik | Seçimin qeydə alındı | An Əminlik pick the server has accepted for a round (`round.betLocked {who: self}`). Final; never changes. The opponent's lock is shown as "Seçdi" without a value. |
| Defaulted Əminlik | Vaxt bitdi — Əminlik 1 | Əminlik 1 set by the server because no valid pick arrived before the confidence phase closed (`betDefaulted = true`). Not an active pick (calibration OQ-027, missed rounds). |
| Reveal | — | The result phase moment when both players' Əminlik, answers, the correct option and points are shown at the same time; both panels flip in the same frame, with one haptic and no sound (OQ-054). |
| Answer time (`answered_ms`, `answeredMs`) | — | Raw server receive time of the answer minus the time the server sent `round.options`; not clamped, so it can exceed 10 000 inside the 250 ms grace (OQ-072). Only the speed bonus clamps the receive time to the deadline. Basis of the speed bonus and the sudden-death tie-break. |
| Too late | Seçimin gec çatdı — Əminlik 1 sayıldı / Cavabın gec çatdı — cavabsız sayıldı | A pick or answer the server received more than 250 ms after the phase deadline (`error too_late`); treated as not sent (OQ-036). |
| Disconnect | Bağlantı kəsildi | The server lost a player's WebSocket (closed, or no client message for 15 s). The match keeps running. |
| Rejoin (resync) | Bağlantı bərpa olunur… | A reconnecting player gets a full match snapshot (`match.state`, after `hello` or on `match.rejoin`) and continues from the current phase (DUEL-13). |
| Leave (match) | Oyundan çıx | Leaving a running match on purpose with `match.leave` after a confirmation dialog: an immediate forfeit (`forfeitCause = left`, OQ-034), also during the intro. |
| Abandoned match | Ləğv edildi | A match ended without a result: both players reached 2 missed rounds in the same round, or the backend restarted/redeployed during the match (marked at startup; a rejoin gets `match_not_found`; OQ-033, OQ-067). No winner, no Elo change, not counted in profile stats, listed in history. |
| Cancelled match | — | A match ended during the intro because a `queue.leave` crossed the pairing (`match.cancelled`, OQ-037). No Elo change, not shown in history; the opponent goes back to the front of the queue. |
| End reason | — | Why a match ended (`match.result.endReason`): `normal`, `sudden_death`, `draw`, `forfeit`, `abandoned`. |
| Protocol version | — | Version of the duel WebSocket protocol, sent by the client as the `protocolVersion` query parameter of the handshake (current and minimum: 1); a missing or lower version gets `upgrade_required` and close `4000` (OQ-029, OQ-061). |
| Single replica | — | The duel runs on exactly one backend instance; queue and running matches live in memory, so a redeploy abandons running matches (OQ-070). |
| Duel build flag | — | Build-time switch that hides every duel entry until the duel is released (OQ-020). |

## Rating and profile

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Elo rating | Reytinq | Per-user number, start 1000, K = 32. Changed only by finished duels against real players (win, loss, draw, forfeit). Never by the category quiz, the daily challenge or a bot match. |
| Rating change | — | Elo difference caused by one match, e.g. "+16"; stored per match (before/after ratings) and shown on the result screen and in history. Rounding: OQ-038; floor: OQ-039. |
| Profile | Profil | The user's backend row (`users`): display name, avatar URL, rating. Created on the first authenticated API call. |
| Profile screen | Profil | Screen opened from the home header: name, avatar, rating, duel record, recent matches, calibration stats, name editing (`profile.md`). |
| Duel record | — | Counts of wins, losses and draws over counted matches (finished, including forfeits; OQ-026). Proposal OQ-110: real matches only; bot matches in a separate "Bota qarşı" line. |
| Recent matches | Son oyunlar | The last N finished matches on the profile, newest first (OQ-031). |
| Deleted player | Silinmiş oyunçu | How an opponent whose account was deleted is shown in matches and history (`docs/mvp-spec.md` §3a). |
| Avatar | — | Profile picture: the Google photo URL, or a placeholder circle with the first letter of the display name. No upload in the MVP. |
| Display name | Ad | Name shown to the user and to duel opponents. 2–20 Unicode code points after trimming; letters, digits, space, `-`, `'`, `.`, no emoji (OQ-042); not unique. Source per sign-in method: OQ-011; editing: PROF-06; offensive names: OQ-030. |

## Daily challenge, progression and notifications

Added 2026-10-22 (`daily-challenge.md`, `progression.md`, `notifications.md`).
Numbers marked "proposed" wait for the linked OQ. UI (az) values are proposals until
the designer adds them to `design/copy/az.md`.

| Term | UI (az) | Meaning |
| --- | --- | --- |
| Day (Baku day) | — | The calendar day in Asia/Baku (00:00–24:00, UTC+4, no DST) from the server clock; used for the daily challenge, streak, XP caps and notifications (OQ-074). Never the device clock or time zone. `dayKey` = `"2026-10-22"`. |
| Daily challenge | Gündəlik çağırış | Same short set of questions for every player on a Baku day, one attempt per account (`docs/mvp-spec.md` §9). Never affects Elo. Not a "quiz session" in the UI. |
| Daily number | Gündəlik çağırış #12 | Running number of the daily challenge: days since the configured first day (#1). |
| Daily attempt | — | One account's play-through of a day's challenge; at most one per (user, day); resumable until its deadline (OQ-079). |
| Daily set | — | The questions chosen by the server for one day, stored and frozen for that day (OQ-076). |
| Result squares | — | One square per daily question in order: correct, wrong, no answer. Used on the result screen, the home card and the share text. |
| Share text | Paylaş | Plain text with the daily number, result squares, score, streak and store link; never question, option or category data (OQ-080). |
| XP | XP | Experience points earned from quizzes, daily challenges and duels; never decreases; independent of Elo. Always written "XP" in the UI, never "xal" (that is the score). |
| Level | Səviyyə | Overall level derived on the server from total XP by the level curve (OQ-083). Starts at 1. Not shown to opponents. |
| Category level | Kateqoriya səviyyəsi | Level per category from category XP (correct answers in that category, OQ-084). Same curve as the overall level. |
| Level-up | Yeni səviyyə! | Crossing into a higher level as the result of one finished game; shown once on the result screen. |
| Active day | — | A Baku day with real play: a quiz finished or expired with ≥ 1 answer, a finished daily attempt or a finished duel not forfeited by the user (OQ-085). Opening the app is not enough. |
| Streak | Seriya | Number of consecutive active days up to today or yesterday. "7 günlük seriya". |
| Best streak | Ən uzun seriya | Highest streak ever reached; never decreases. |
| Streak freeze | Seriya qoruyucusu | Automatic protection of the streak for exactly one missed day; one earned per 7-day streak, at most 1 held (OQ-086). Never bought. |
| Push notification | Bildiriş | A message sent by the server through FCM and shown by Android outside the app. Only the reminder types below exist in the MVP. |
| Daily reminder | Gündəlik çağırış xatırlatması | Notification type `daily_reminder`: today's daily challenge has not been started (OQ-088). |
| Streak reminder | Seriya xatırlatması | Notification type `streak_reminder`: streak ≥ 2 and today is not active yet (OQ-088). |
| Quiet hours | — | Time window in which no notification is sent (proposed 22:00–09:00 Baku, OQ-089). Not user-configurable in the MVP. |
| Notification permission | — | Android 13+ runtime permission `POST_NOTIFICATIONS`; asked only after the in-app explanation card (OQ-090). |
| Push token | — | FCM registration token of one app install; stored on the server per device and deleted on sign-out, invalidity, staleness or account deletion. |

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
| Home | Əsas səhifə | First screen after sign-in: profile summary with level and streak, daily challenge card, duel entry, categories, settings entry. |
| Settings | Parametrlər | Theme, language, notifications, account actions, about. |
| Theme | Tema | System (Sistem), Light (İşıqlı), Dark (Qaranlıq). Default System. |
| Language | Dil | App UI language. MVP: Azərbaycan dili only. |
| Offline | İnternet yoxdur | The device has no usable network connection, or a request failed with a network error. |
| Backgrounding | — | The app leaves the foreground (home button, call, screen off) while the process stays alive. |
| Process death | — | Android kills the app process while it is in the background; the app restarts from saved state. |

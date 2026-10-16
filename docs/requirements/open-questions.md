# Open questions

Owner: `business-analyst`. Each item: ID, question, options, recommendation, who decides.

## Open

| ID | Question | Recommendation | Decides |
| --- | --- | --- | --- |
| OQ-005 | Hosting account: Oracle Cloud Always Free (card for verification only) or a paid ~5 €/month VM? | Oracle Always Free; paid VM only if the account cannot be created | Sabit |
| OQ-006 | Domain: free DuckDNS subdomain or a bought domain? | DuckDNS until production release | Sabit |
| OQ-007 | Minimum questions per category before the closed test | 60 reviewed per category (duel uses 7 + up to 3 sudden death per match) | Sabit |
| OQ-073 | What does the app do when a `queue.leave` reaches the server after the intro and gets `already_in_match {matchId}` (the user tapped "Ləğv et" but round 1 already started)? A. Rejoin and show the running match; the user can still leave through the DUEL-15 dialog. B. Send `match.leave` automatically (forfeit, Elo loss, without a confirmation). C. Go home silently; the missed-round rule ends the match (≈ 40 s, forfeit). | A: a forfeit always needs an explicit confirmation; the case is rare (the leave must arrive > 3 s after `match.found`). Blocks one criterion of DUEL-03 | Sabit |

## Decided by Sabit (2026-10-14) — duel and profile

All accepted as recommended, except OQ-036 (Sabit chose a 250 ms grace instead of none).

| ID | Question | Decision |
| --- | --- | --- |
| OQ-024 | What does a player see about the opponent before, during and after a match? | Name, avatar, rating at match found; "opponent locked/answered" without values during rounds; name in history |
| OQ-025 | Rematch with the same opponent after a match? | Out of scope for the MVP; "Yenidən oyna" re-joins the global queue |
| OQ-026 | Do forfeited matches keep their points, and how do forfeits and abandoned matches appear in history and stats? | Forfeit: stored with current scores, counted as win/loss with a label; abandoned: shown as "Ləğv edildi", not counted |
| OQ-027 | Which rounds count for the calibration card and profile stats? | Rounds 1–7 with an actively locked Əminlik; exclude defaulted picks, missed rounds and sudden death |
| OQ-028 | What happens when the queue is mostly empty (one player waiting, few testers)? | Keep 30 s + retry; no bots; measure timeout rate; agree "duel hours" with closed-test testers |
| OQ-029 | Minimum app version for the duel protocol | Client sends a protocol version in the handshake; server rejects older ones with `upgrade_required` → "Duel üçün tətbiqi yenilə" |
| OQ-030 | Offensive or impersonating display names | Small server-side blocklist (az/tr/ru/en, normalized) on sign-up, Google name and edit; no reporting in the MVP |
| OQ-031 | How many recent matches does the profile show? | Last 20, newest first, no pagination |
| OQ-032 | What does the 10 s reconnect window mean when the player is not back after 10 s? | Seat is held and rejoin allowed until the match ends; only the 2-missed-rounds rule decides the outcome |
| OQ-033 | Both players miss their 2nd round in a row in the same round, or the server loses the match (restart) | Match "abandoned": no winner, no Elo change, scores stored |
| OQ-034 | Can a player leave a running match on purpose, and what does it count as? | Yes, with a confirmation dialog; immediate forfeit (Elo loss) |
| OQ-035 | Do the confidence and answer phases end early when both players have acted? | Yes, both end early; the fixed lengths stay the maximum |
| OQ-036 | Latency grace at deadlines (pick or answer sent in time but received late) | 250 ms server-side grace: a pick or answer received at or before deadline + 250 ms (server clock) counts; the speed bonus uses the receive time clamped to the deadline (bonus ≥ 0); the client still disables input at its displayed 0 |
| OQ-037 | Match intro length and leaving between match found and round 1 | 3 s intro, no "ready" tap; a queue leave that crosses the pairing cancels the match without Elo and re-queues the opponent first |
| OQ-038 | Rounding of the speed bonus and of the Elo change | Speed bonus `floor(50 × remainingMs / 10000)`; Elo change rounded half away from zero, equal and opposite for both players |
| OQ-039 | Elo floor (minimum rating) | No floor in the MVP; revisit after the closed test |
| OQ-040 | Assumptions written into `duel.md` without an OQ | Confirmed: newest connection wins (`session_replaced` for the older one); backgrounding while searching sends `queue.leave`; sudden death "missed" = no answer and the 2-in-a-row count continues from round 7; both correct in sudden death → earlier server receive time wins, identical time → next question; duel needs ≥ 10 active questions in total (`duel_unavailable`); after process death the app rejoins via saved `activeMatchId` |

## Decided by Sabit (2026-10-14) — design questions

Raised by `designer` in `design/screens/*.md` (OQ-D1 … OQ-D6); accepted as proposed.

| ID | Question | Decision |
| --- | --- | --- |
| OQ-041 | UI tone: "sən" or "siz"? | "sən" everywhere in the UI (game, peer contest, shorter on 360dp). Final copy: `design/copy/az.md` |
| OQ-042 | Allowed characters in the display name (OQ-D1) | Letters of any script, digits, space, `-`, `'`, `.`; trimmed, inner spaces collapsed, no emoji; length 2–20 code points after trimming; the server enforces the same rule |
| OQ-043 | How long does dismissing the email-verification banner last? (OQ-D2) | Until the next cold start of the app (in-memory flag) |
| OQ-044 | Category `icon_key` values (OQ-D3) | `geography`, `history`, `biology`, `physics`, `sports`; unknown keys show the generic icon |
| OQ-045 | Maximum category name length (OQ-D4) | 20 characters per language (validated by the importer/admin) |
| OQ-046 | When does "Bağlantı bərpa olunur…" appear in the quiz? (OQ-D5) | After an answer has been pending for 2 s or on the first network failure; the 1.5 s reveal starts when the server result arrives |
| OQ-047 | What does a queued (offline) answer show? (OQ-D6) | "Queued" option state with "Cavab yadda saxlanıldı" for 1.5 s, no correct/wrong; the result appears in the review |

## Decided by Sabit (2026-10-16) — duel engine and design

IDs continue after OQ-052 (backend foundation decisions). OQ-053 … OQ-055 were
raised by `designer` and decided by Sabit; OQ-056 … OQ-060 are designer questions
resolved by `backend-dev` in `docs/api/`; OQ-061 … OQ-072 are duel engine decisions
now in `docs/api/duel-protocol.md`. The designer's OQ-D13 (name length) needs no new
ID: it is resolved by OQ-042 and `profile.md` PROF-06 now follows it.

| ID | Question | Decision |
| --- | --- | --- |
| OQ-053 | Search screen content while waiting (OQ-D7) | Three short rule tips rotate every 10 s in a "Bilirsən?" card; no opponent count |
| OQ-054 | Sound and haptics at the reveal (OQ-D9) | No sound in the MVP; one haptic when both panels flip (stronger on an own correct answer, light on wrong/timeout) |
| OQ-055 | App logo (OQ-D15) | The "two speech bubbles" mark in `design/brand.md` is approved; replaceable later without code changes |
| OQ-056 | How does the client learn the start and deadline of the Əminlik phase? (OQ-D8) | Explicit `round.confidence {roundNo, allIn, bet3Left, deadlineEpochMs}` when the question phase ends; regular rounds only |
| OQ-057 | How does the client show who won a sudden-death question? (OQ-D10) | `round.result.suddenDeathOutcome`: `you` \| `opponent` \| `same_time` \| `none` (`null` in regular rounds); the client never uses its own timing |
| OQ-058 | Leaving while offline, then joining again (OQ-D11) | `already_in_match` carries `matchId`, and for `queue.join` a `match.state` follows; the app resumes the match, or sends `match.leave` if the user had already confirmed leaving it |
| OQ-059 | Who chooses the calibration hint? (OQ-D12) | Server sends per-level counts `{level, picks, correct}` (`match.result`, `GET /v1/me/stats`); the client chooses hint text, "—" and "Az məlumat" |
| OQ-060 | Data for "Qoşulub" on the profile (OQ-D14) | `createdAt` in `GET /v1/me` (`Profile`) |
| OQ-061 | How is the protocol version sent? | Required query parameter `protocolVersion` (current and minimum 1); missing or lower → `error upgrade_required` + close `4000` |
| OQ-062 | Close codes | `4000` upgrade required, `4001` session replaced (no auto-reconnect for either), `4002` 10 bad messages, `4003` heartbeat timeout |
| OQ-063 | Heartbeat | Client `ping` every 5 s; server closes after 15 s without any client message (`4003`) |
| OQ-064 | When exactly does a phase close with the 250 ms grace? | At deadline + 251 ms (ms clock), so a message received at exactly deadline + 250 ms counts; earlier if both players acted |
| OQ-065 | `match.leave` during the 3 s intro | A forfeit like any leave; only a `queue.leave` crossing the pairing cancels the match (`match.cancelled`, opponent re-queued at the front) |
| OQ-066 | `queue.leave` after the intro | `error already_in_match` with `matchId`; the match is not cancelled (client reaction: OQ-073) |
| OQ-067 | Running match lost by a backend restart | Marked `abandoned` at startup; `match.rejoin` answers `match_not_found`; no Elo change |
| OQ-068 | Distinguishing forfeits | `match.result.forfeitCause`: `missed_rounds` \| `left` (`null` otherwise, and on results rebuilt after a restart) |
| OQ-069 | Account deletion during a running duel | `DELETE /v1/me` first ends a running match as a forfeit by the user (`forfeitCause = left`) and removes any queue entry |
| OQ-070 | Scaling of the duel backend | Single backend replica; queue and matches in memory; a redeploy abandons running matches |
| OQ-071 | Resuming after reconnect or process death | `hello.activeMatchId` and an automatic `match.state` on connect; the server's value wins over the app's saved `activeMatchId` |
| OQ-072 | Is `answered_ms` clamped? | No: raw receive time − options send time; only the speed bonus clamps the receive time to the deadline |

## Details (background for OQ-024 … OQ-039, all decided — see above)

Added by `business-analyst` on 2026-10-14 while writing `duel.md` and `profile.md`.
All decided on 2026-10-14 (see the table above); kept as background.

### OQ-024 What the opponent sees
The opponent's data leaves the server in `match.found`, during rounds and in history.
- A. Display name, avatar, rating at match found; during rounds only "opponent
  locked" / "opponent answered" without values; in history the current name.
  Personal enough to feel real; no private data.
- B. Same as A without the rating. Less pressure for new players; the rating is the
  only skill signal before the match.
- C. Hide "opponent answered" during the answer phase. Removes time pressure from
  seeing the opponent answer; less tension.
- Recommendation: A. Never email, `firebase_uid` or user ID in the UI. Blocks
  DUEL-05, DUEL-08 (indicator only), PROF-04. Decides: Sabit.

### OQ-025 Rematch
- A. Out of scope: "Yenidən oyna" re-joins the global queue. No extra protocol.
- B. "Revanş" button: both must accept within N s. New messages, timers, states
  (one accepts, the other leaves), and an easy Elo-farming loop between two friends.
- Recommendation: A for the MVP (`CLAUDE.md` lists friends/invites as out of scope).
  Decides: Sabit.

### OQ-026 Forfeited and abandoned matches in history and stats
`docs/mvp-spec.md` §2 stores current scores on a forfeit, but does not say how the
match is shown or counted.
- A. Forfeit: counted as win/loss in the record, listed with a label ("Rəqib tərk
  etdi" / "Texniki məğlubiyyət") and its scores; rounds played count for
  calibration (per OQ-027). Abandoned: listed as "Ləğv edildi", not in the record.
  Cancelled (before round 1): not listed.
- B. Hide forfeits from history entirely. Cleaner list; but Elo changed, so the
  history would not explain the rating.
- C. Discard the forfeiting player's points (score shown as 0). Simpler message;
  contradicts §2 ("current scores stored").
- Recommendation: A. Blocks DUEL-20, PROF-03, PROF-04. Decides: Sabit.

### OQ-027 Calibration counting rule
"Accuracy per bet level" (§2) needs a rule for rounds without an active choice.
- A. Count only rounds 1–7 with an actively locked Əminlik (a timeout after an active
  pick counts as wrong at that level). Exclude defaulted picks, missed rounds and
  sudden death (fixed at 1). Measures real judgement.
- B. Count every regular round including defaults. Simpler; defaults inflate level 1
  with disconnects and slow tappers.
- Recommendation: A, same rule for the card (DUEL-21) and the profile (PROF-05).
  Decides: Sabit.

### OQ-028 Empty or odd queue
With 15–20 closed-test testers, most searches will find nobody, and an odd player
always waits.
- A. Keep 30 s + retry, measure `duel_queue_timeout`, agree fixed "duel hours" with
  testers. Zero cost, honest.
- B. Bot or ghost opponents. Out of scope (`CLAUDE.md`: no ghost duels).
- C. Longer timeout (e.g. 60 s). Changes a game number; more waiting.
- D. Show "N players searching". Small extra endpoint; with tiny numbers it shows "0"
  and discourages.
- Recommendation: A; revisit after the closed test. Decides: Sabit.

### OQ-029 Minimum app version for the duel protocol
An old app talking to a changed protocol can break a match for both players.
- A. Client sends `protocolVersion` in the handshake; the server has a minimum and
  answers `upgrade_required`; the app shows "Duel üçün tətbiqi yenilə." with a Play
  link. Both players in a match always speak a supported version.
- B. Play in-app updates API (forced update) for the whole app. Heavier; blocks the
  quiz too.
- Recommendation: A now; B only if needed later. Blocks DUEL-01. Decides: Sabit.

### OQ-030 Offensive or impersonating display names
Opponents see the name (OQ-011 deferred this to the profile feature).
- A. No check. Zero work; offensive names reach other players (Play policy on
  user-generated content may then expect reporting tools).
- B. Server-side blocklist (Azerbaijani, Turkish, Russian, English; case-folded,
  common letter swaps like 0→o, ə→e) plus reserved words ("admin", "duello",
  "moderator"); applied at sign-up, Google name creation (fallback "Oyunçu NNNN") and
  edit. Cheap; misses creative spellings.
- C. B plus "report player" and manual review. Better moderation; new UI and admin
  work.
- Also: a limit on name changes (e.g. once per day) — recommendation: none in the MVP.
- Recommendation: B; Sabit maintains the list in the private content repo (it is
  not public). Blocks PROF-06. Decides: Sabit.

### OQ-031 Number of recent matches on the profile
- A. Last 20, no pagination. One query, fits one screen scroll.
- B. Paginated full history. More API and UI states; little value in the MVP.
- Recommendation: A. Decides: Sabit.

### OQ-032 Meaning of the 10 s reconnect window
§2 gives 10 s to reconnect but also says missed picks default and that only 2 missed
rounds in a row forfeit. A round is about 21 s, so it is unclear what changes after
10 s.
- A. The window is informational: the opponent sees a 10 s countdown, the client
  auto-reconnects silently for 10 s and then shows "Yenidən qoşul"; the seat is held
  until the match ends; only the missed-round rule decides the outcome.
- B. After 10 s without reconnect the player cannot rejoin; remaining rounds are
  missed until the forfeit (≈ 1–2 rounds later). Same result, less flexibility.
- C. Forfeit immediately after 10 s. Harsh on weak networks; contradicts the
  missed-round rule.
- Recommendation: A. Blocks DUEL-13, DUEL-16. Decides: Sabit.

### OQ-033 Both players gone, or the server loses the match
- A. "Abandoned": no winner, no Elo change, scores stored, shown as "Ləğv edildi".
  Also used when a server restart loses an in-memory match.
- B. Draw (Elo 0.5 each). Moves ratings for a match nobody finished.
- C. Higher score wins. Rewards leaving when ahead.
- Recommendation: A. Rule: whoever reaches 2 missed rounds in a row first forfeits;
  A applies only when both reach it in the same round. Blocks DUEL-14. Decides: Sabit.

### OQ-034 Leaving a running match on purpose
§2 has no explicit leave; today a player who closes the screen waits out 2 missed
rounds (≈ 40 s) and the opponent waits too.
- A. "Oyundan çıx" with a confirmation that says it counts as a loss; the server
  forfeits at once (`match.leave`). Fast and honest for both.
- B. No leave button; back is blocked during a match. Annoying, and users kill the
  app anyway.
- C. Leave = disconnect; the missed-round rule decides. No new message; the opponent
  waits ≈ 40 s.
- Recommendation: A. Blocks DUEL-15. Decides: Sabit.

### OQ-035 Phases ending early
§2 says options are sent "only after both bets are locked"; it does not say whether
the confidence phase (5 s) and the answer phase (10 s) end early.
- A. Both end early when both players have acted; durations are maximums. Faster
  matches, less idle waiting; match length varies.
- B. Fixed lengths; options always at 5 s. Predictable rhythm, simpler tests; idle
  time on every round.
- C. Only the confidence phase ends early.
- Recommendation: A. The speed bonus is unaffected (it uses each player's own
  remaining time). Blocks DUEL-07, DUEL-08. Decides: Sabit.

### OQ-036 Latency grace at deadlines
A tap at 9.9 s with 200 ms latency arrives after the deadline.
- A. No grace: received ≤ deadline counts. Simple, the countdown already shows server
  time (clock offset); players on slow networks lose the last ~100–300 ms.
- B. Fixed grace (e.g. 300 ms) after the deadline. Kinder; changes a game timer and
  gives fast networks a hidden extra window.
- C. Per-player latency compensation (RTT/2). Fairest; complex and spoofable.
- Recommendation: A. Blocks DUEL-07, DUEL-08, DUEL-12 edge cases. Decides: Sabit.

### OQ-037 Match intro and cancellation before round 1
§2 does not define the time between `match.found` and round 1.
- A. 3 s intro, no "ready" tap; a `queue.leave` that crossed the pairing cancels the
  match (no Elo) and puts the opponent back at the front of the queue. After the
  intro normal rules apply.
- B. Both tap "Hazıram" within 10 s. Confirms presence; one more step and a new
  timeout path.
- C. No intro, round 1 at once. Players do not see who they play.
- Recommendation: A. Blocks DUEL-03, DUEL-05. Decides: Sabit.

### OQ-038 Rounding of the speed bonus and the Elo change
The numbers are fixed by §2; the rounding is not.
- Speed bonus — A. `floor(50 × remainingMs / 10000)` with remaining = answer
  deadline − server receive time: 0 at the deadline, 50 only for an instant answer.
  B. `round(...)`. Tiny difference; floor never exceeds 50.
- Elo — A. Compute the winner's change, round half away from zero, give the loser the
  exact negative: ratings stay zero-sum. B. Round each player separately: can drift
  by 1 point.
- Recommendation: A for both. Blocks DUEL-09, DUEL-22. Decides: Sabit.

### OQ-039 Elo floor
- A. No floor. Pure Elo, zero-sum; a rating far below 1000 can discourage.
- B. Floor at a fixed value (e.g. 100). Breaks zero-sum slightly; rare in practice.
- Recommendation: A for the MVP; revisit with closed-test data. Decides: Sabit.

## Decided by Sabit (2026-10-14) — accepted as recommended

| ID | Question | Decision |
| --- | --- | --- |
| OQ-010 | Is email verification required for email/password accounts? | Send a verification email, show a banner, do not block play |
| OQ-011 | Display name for email/password users (no Google profile) | Ask for a name at sign-up (2–20 chars); never derive it from the email |
| OQ-012 | Single-player quiz score formula | Score = number of correct answers (0–10); no time component |
| OQ-013 | Categories with fewer than 10 active questions | Server omits them from `GET /categories`; `POST /quiz/sessions` returns 409 |
| OQ-014 | Account deletion: what is deleted, what is kept, how long | Delete profile, quiz data and Firebase user; anonymize the user in opponents' matches; immediate, backups roll off within 30 days |
| OQ-015 | Where are the privacy policy and the deletion-request page hosted (zero cost)? | GitHub Pages from this public repo (`docs/legal/`) |
| OQ-016 | Quiz: how is a question timeout sent, and does the timer pause in the background? | Send `optionId = null`; timer never pauses (wall clock) |
| OQ-017 | Quiz: resume after process death or app relaunch? | No resume in the MVP; server finalizes at the deadline |
| OQ-018 | Password policy for email sign-up | Minimum 8 characters, no composition rules (Firebase password policy) |
| OQ-019 | Same email used with Google and with password | Keep Firebase default "one account per email"; no linking UI in the MVP |
| OQ-020 | Duel entry on home before the duel ships | Hide the entry until the duel is released (build flag); no "coming soon" |
| OQ-021 | Question selection and repetition across quiz sessions | Random 10 distinct active questions per session; least-recently-seen later |
| OQ-022 | Rate limit for starting quiz sessions | 30 sessions per user per hour, then 429 |
| OQ-023 | Reveal/explanation time counts against the 230 s session deadline | Auto-advance after 1.5 s; explanations only in the end review; deadline becomes 245 s (10 × 21.5 s + 30 s) |

## Details (background for OQ-010 … OQ-023)

All items below are decided (see the table above). Added by `business-analyst` on 2026-10-14 while writing `auth.md`, `home.md`,
`quiz.md` and `settings.md`. Each item lists options, trade-offs and the
recommendation; the row above is the short form.

### OQ-010 Email verification
- A. Not required, no email sent. Simplest; typos in the email make password reset
  impossible for those users; throw-away accounts.
- B. Send a verification email, show a banner, do not block play. Catches typos, no
  friction; some unverified accounts remain.
- C. Required before playing. Fewest fake accounts; extra step on first launch, and
  Firebase emails can land in spam → drop-off for a new app.
- Recommendation: B. Blocks AUTH-08 only. Decides: Sabit.

### OQ-011 Display name for email/password users
`docs/mvp-spec.md` §3 creates the profile with a display name, but email accounts
have none, and duel opponents see it.
- A. "Ad" field at sign-up (2–20 chars, trimmed). One more field; real names from
  the start.
- B. Generate "Oyunçu 4821". Zero friction; impersonal, and users cannot change it
  until a profile-edit feature exists.
- C. Use the email local part. Leaks personal data to opponents — not acceptable.
- Recommendation: A, with B as fallback for any account without a name. A rule for
  offensive names is needed later (profile feature). Decides: Sabit.

### OQ-012 Quiz score formula
`docs/mvp-spec.md` §1 lists "score" and "correct/total" but does not define score.
Per OQ-004, `elapsedMs` cannot be used for scoring.
- A. Score = correct count (0–10). Honest and simple; duplicates correct/total.
- B. 100 points per correct answer (0–1000). Feels like the duel; same information.
- C. Server-timed speed bonus. Needs per-question server timestamps; latency makes
  it unfair on weak connections.
- Recommendation: A (show only "7 / 10"). Decides: Sabit.

### OQ-013 Categories with fewer than 10 active questions
- A. Server omits them from `GET /categories`. Users never see a broken category; a
  new category appears only when it is ready.
- B. Return them with `playable = false` and show them disabled ("Tezliklə"). Shows
  the roadmap; disabled tiles look broken and need extra UI states.
- C. Serve fewer than 10 questions. Breaks §1 and the deadline formula.
- Recommendation: A, plus 409 `category_not_playable` from `POST /quiz/sessions` for
  the race. The threshold equals the questions per session (§1). Decides: Sabit.

### OQ-014 Account deletion scope and retention
Google Play requires in-app deletion and a web resource for apps with accounts.
- Always deleted: `users` row, quiz sessions and their answers, the Firebase user.
- Matches — A. Anonymize: keep match rows, show the player as "Silinmiş oyunçu";
  opponents' history and Elo stay consistent. B. Hard-delete all matches of the
  user; simplest privacy story, opponents lose history.
- Timing: immediate in the database; backups keep data until they are overwritten.
- Recommendation: A; immediate deletion; the web page states that backups are
  overwritten within 30 days (devops must keep backup retention ≤ 30 days). The
  backend deletes the Firebase user with the Admin SDK, so no re-authentication on
  the device. Needs `DELETE /v1/me` in the API. Decides: Sabit.

### OQ-015 Privacy policy and deletion page hosting
Play requires a privacy policy URL and a deletion URL; zero cost is a goal.
- A. GitHub Pages from this public repo (`docs/legal/`). Free, versioned.
- B. A page served by Caddy on the backend host. Free; depends on server uptime and
  the domain (OQ-006).
- C. A Google Sites page. Free; not version-controlled.
- Recommendation: A. The text is a legal statement and needs Sabit's review.
  Decides: Sabit.

### OQ-016 Quiz timeout and backgrounding
`POST /quiz/sessions/{id}/answers` requires `optionId`; a timeout has none. The
session deadline is absolute (start + 230 s at the time of writing; 245 s since OQ-023).
- Timeout — A. Send `optionId = null`; the server stores "not answered" and returns
  `correctOptionId` (one endpoint, immediate reveal). B. Send nothing; the server
  treats missing answers as not answered at finish (no immediate reveal).
- Background — A. Timer keeps running (wall clock): consistent with the absolute
  deadline, limits looking answers up. B. Pause: kinder, but the server deadline
  still runs and pausing invites searching for answers.
- Recommendation: timeout A, background A. Decides: Sabit.

### OQ-017 Resume after process death
- A. No resume. Simple; a quiz killed during a phone call is lost (short, no Elo).
- B. Resume from local state before the deadline. Needs persisted session, answer
  state and per-question start times; little value because the timer keeps running
  anyway (OQ-016).
- Recommendation: A; the server finalizes abandoned sessions after the deadline.
  Decides: Sabit.

### OQ-018 Password policy
- A. Firebase default (6 chars). Weak.
- B. 8+ characters, no composition rules (current NIST guidance).
- C. 8+ with upper/lower/digit/symbol. More friction, little gain.
- Recommendation: B, set in the Firebase password policy (free) and mirrored in the
  client validation. Decides: Sabit.

### OQ-019 Same email with Google and password
With Firebase's default "one account per email", password sign-up fails for an
email that already has a Google account, and a Google sign-in may replace an
unverified password provider for the same email.
- A. Keep the default; show "Bu e-poçtla artıq hesab var" and suggest Google.
  No linking UI.
- B. Build account linking. Better UX for a rare case; more flows and tests.
- Recommendation: A for the MVP. Decides: Sabit.

### OQ-020 Duel entry before the duel ships
The duel comes in build-order step 5, after home.
- A. Hide the entry until the duel is released (build flag). Clean UI.
- B. Show a disabled "Tezliklə" entry. Teases the feature; looks like a dead button.
- Recommendation: A. Decides: Sabit.

### OQ-021 Question selection and repetition
With ~60 questions per category (OQ-007), random selection repeats often.
- A. Random 10 distinct active questions per session. Simplest.
- B. Prefer questions the user has not seen recently (needs per-user answer history,
  which the review needs anyway).
- Note: the quiz returns `correctOptionId`, so frequent quiz players learn answers
  that also appear in duels. Separate quiz/duel pools would halve the bank; not
  recommended for the MVP.
- Recommendation: A now; B after the closed test if repetition is a complaint.
  Decides: Sabit.

### OQ-022 Quiz session rate limit
Each answer reveals the correct option, so a script could harvest the bank.
- A. No limit. Simplest; harvesting is cheap.
- B. 30 sessions per user per hour (a human plays at most ~15 per hour at ~4 min
  each), then 429. Stops scripts, never hits real players.
- Recommendation: B; the number is Sabit's call. Decides: Sabit.

### OQ-023 Reveal and explanation time vs the session deadline
The deadline (10 × 20 s + 30 s grace, `docs/mvp-spec.md` §1) includes the time
between questions. If the user reads an explanation after each answer, ten reveals
can use up the 30 s grace and later questions get cut off.
- A. Auto-advance after a short reveal (1.5 s, correct option highlighted);
  explanations only in the end review. Fits the deadline and §1 ("review of wrong
  answers with explanations").
- B. Tap "Növbəti" after reading the explanation, with a per-question server
  deadline instead of one session deadline. Better learning; changes the decided
  OQ-004 model.
- C. Keep tap-to-continue and raise the grace. Changes a game number.
- Recommendation: A (the designer may tune the reveal within 1–2 s). Decides: Sabit.

## Decided (provisional — set by the tech lead on 2026-10-14, Sabit may change)

OQ-004 is refined by OQ-023: the session deadline includes the 1.5 s reveal, see `docs/mvp-spec.md` §1.

| ID | Question | Decision |
| --- | --- | --- |
| OQ-001 | Sudden death when neither player answers correctly | Another sudden-death question, at most 3; still tied → draw (Elo 0.5 each) |
| OQ-002 | "2 missed rounds" → consecutive or total? Elo on forfeit? | 2 in a row; a missed round = no bet and no answer; forfeit = loss for Elo |
| OQ-003 | Does round 7 double the speed bonus? | No. Only bet points (+ and −) are doubled; the speed bonus is never multiplied |
| OQ-004 | Client-reported `elapsedMs` in single-player quiz can be faked | Display only; server enforces one answer per question and a session deadline; single-player never affects Elo |
| OQ-008 | Image storage without cost | Backend's local disk + Caddy, behind `ImageStorage`; S3/R2 only later if needed (`docs/mvp-spec.md` §5) |
| OQ-009 | Where does the question bank live, given the repo is public? | Separate private repo cloned to `content/bank/` |

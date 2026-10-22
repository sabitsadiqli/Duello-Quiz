-- Retention features (docs/mvp-spec.md §9–§11, OQ-074 … OQ-096): daily challenge,
-- XP / levels / streak, push notifications. Days are Baku calendar days (DATE, 00:00
-- Asia/Baku, OQ-074) computed by the backend from the server clock.
-- Every personal table references users ON DELETE CASCADE, so DELETE /v1/me removes
-- it in the same statement as the user row (§3a). The per-day question sets are not
-- personal data and stay.

-- ---------------------------------------------------------------------------
-- Daily challenge (§9)
-- ---------------------------------------------------------------------------
-- One row per Baku day that has a challenge. Created lazily by the first request of
-- the day (or the 10:00 reminder job); the set is chosen server-random (OQ-076) and
-- frozen for the day. A day without enough active questions has no row.
CREATE TABLE daily_challenges (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    day         DATE         NOT NULL,
    -- #N: days since the configured first day (#1).
    number      INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT daily_challenges_day_key UNIQUE (day),
    CONSTRAINT daily_challenges_number_key UNIQUE (number),
    CONSTRAINT daily_challenges_number_positive CHECK (number >= 1)
);

CREATE TABLE daily_challenge_questions (
    daily_id     UUID      NOT NULL REFERENCES daily_challenges (id) ON DELETE CASCADE,
    position     SMALLINT  NOT NULL,
    question_id  UUID      NOT NULL REFERENCES questions (id),
    -- Category at selection time: category XP of a daily answer goes here (OQ-084).
    category_id  INT       NOT NULL REFERENCES categories (id),
    PRIMARY KEY (daily_id, position),
    CONSTRAINT daily_challenge_questions_distinct UNIQUE (daily_id, question_id),
    CONSTRAINT daily_challenge_questions_position_positive CHECK (position >= 1)
);
-- "Not used in a daily during the last 60 days" lookups.
CREATE INDEX daily_challenge_questions_question_idx ON daily_challenge_questions (question_id);

-- One attempt per account per day (OQ-079); resumable until deadline_at.
CREATE TABLE daily_attempts (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    daily_id       UUID         NOT NULL REFERENCES daily_challenges (id),
    status         VARCHAR(16)  NOT NULL DEFAULT 'active',
    correct_count  SMALLINT,
    started_at     TIMESTAMPTZ  NOT NULL,
    deadline_at    TIMESTAMPTZ  NOT NULL,
    finished_at    TIMESTAMPTZ,
    CONSTRAINT daily_attempts_user_daily_key UNIQUE (user_id, daily_id),
    CONSTRAINT daily_attempts_status_values CHECK (status IN ('active', 'finished', 'expired')),
    CONSTRAINT daily_attempts_deadline_after_start CHECK (deadline_at > started_at),
    CONSTRAINT daily_attempts_closed_fields CHECK (
        (status = 'active' AND finished_at IS NULL AND correct_count IS NULL)
        OR (status <> 'active' AND finished_at IS NOT NULL AND correct_count IS NOT NULL)
    ),
    CONSTRAINT daily_attempts_correct_count_range CHECK (correct_count IS NULL OR correct_count >= 0)
);
CREATE INDEX daily_attempts_daily_idx ON daily_attempts (daily_id);
CREATE INDEX daily_attempts_active_deadline_idx ON daily_attempts (deadline_at) WHERE status = 'active';

CREATE TABLE daily_answers (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id   UUID         NOT NULL REFERENCES daily_attempts (id) ON DELETE CASCADE,
    question_id  UUID         NOT NULL REFERENCES questions (id),
    option_id    UUID         REFERENCES options (id),
    is_correct   BOOLEAN      NOT NULL,
    answered_at  TIMESTAMPTZ  NOT NULL,
    elapsed_ms   INT          NOT NULL,
    CONSTRAINT daily_answers_attempt_question_key UNIQUE (attempt_id, question_id),
    CONSTRAINT daily_answers_elapsed_non_negative CHECK (elapsed_ms >= 0),
    CONSTRAINT daily_answers_timeout_is_wrong CHECK (option_id IS NOT NULL OR NOT is_correct)
);

-- ---------------------------------------------------------------------------
-- XP, levels, streak (§10). Everyone starts at 0 XP, level 1, streak 0 (OQ-093).
-- ---------------------------------------------------------------------------
-- Totals and streak state; one row per user, locked (FOR UPDATE) while an award is
-- applied, which serializes awards and cap counting per user.
CREATE TABLE user_progress (
    user_id           UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    xp_total          BIGINT       NOT NULL DEFAULT 0,
    streak_current    INT          NOT NULL DEFAULT 0,
    streak_best       INT          NOT NULL DEFAULT 0,
    -- Last Baku day with an activity (OQ-085); the streak is evaluated lazily against today.
    last_active_day   DATE,
    -- At most one freeze held (OQ-086).
    freeze_available  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- The missed day the last freeze covered (home shows "… dünən seriyanı qorudu" once).
    freeze_used_day   DATE,
    -- The streak that ended at the last reset and its first missed day, so the
    -- "streak ended" home message (PROG-08) still appears after the player replays.
    streak_lost_from  INT,
    streak_lost_day   DATE,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT user_progress_non_negative CHECK (xp_total >= 0 AND streak_current >= 0 AND streak_best >= streak_current)
);

CREATE TABLE user_category_xp (
    user_id      UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  INT     NOT NULL REFERENCES categories (id),
    xp           BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, category_id),
    CONSTRAINT user_category_xp_non_negative CHECK (xp >= 0)
);

CREATE TABLE user_active_days (
    user_id  UUID  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day      DATE  NOT NULL,
    PRIMARY KEY (user_id, day)
);

-- One award per (user, finalized quiz session / daily attempt / match). The primary
-- key is the idempotency guard: finalizing a source twice never awards twice, and a
-- retried finish returns the stored snapshot. `eligible` rows count towards the daily
-- caps (OQ-082) of their `day` (the Baku day the activity started, OQ-087).
CREATE TABLE progress_awards (
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type  VARCHAR(8)   NOT NULL,
    source_id    UUID         NOT NULL,
    day          DATE         NOT NULL,
    eligible     BOOLEAN      NOT NULL,
    capped       BOOLEAN      NOT NULL,
    xp_gained    INT          NOT NULL,
    opponent_id  UUID         REFERENCES users (id) ON DELETE SET NULL,
    snapshot     JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_type, source_id),
    CONSTRAINT progress_awards_source_type_values CHECK (source_type IN ('quiz', 'daily', 'duel')),
    CONSTRAINT progress_awards_xp_non_negative CHECK (xp_gained >= 0)
);
CREATE INDEX progress_awards_cap_idx ON progress_awards (user_id, source_type, day) WHERE eligible;

-- XP ledger: every positive amount with its source, so totals can be audited and
-- recomputed (PROG-09). idempotency_key = "<source_type>:<source_id>:<reason>[:<category_id>]".
CREATE TABLE xp_events (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type      VARCHAR(8)    NOT NULL,
    source_id        UUID          NOT NULL,
    reason           VARCHAR(24)   NOT NULL,
    category_id      INT           REFERENCES categories (id),
    amount           INT           NOT NULL,
    day              DATE          NOT NULL,
    idempotency_key  VARCHAR(128)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT xp_events_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT xp_events_source_type_values CHECK (source_type IN ('quiz', 'daily', 'duel')),
    CONSTRAINT xp_events_reason_values CHECK (
        reason IN ('correct_answers', 'daily_bonus', 'duel_played', 'duel_win', 'duel_draw')
    ),
    CONSTRAINT xp_events_amount_positive CHECK (amount > 0)
);

-- ---------------------------------------------------------------------------
-- Push notifications (§11)
-- ---------------------------------------------------------------------------
-- One row per device token; a token registered by another account moves to it.
CREATE TABLE push_tokens (
    id            BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token         VARCHAR(1024)  NOT NULL,
    locale        VARCHAR(16)    NOT NULL DEFAULT 'az',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT push_tokens_token_key UNIQUE (token)
);
CREATE INDEX push_tokens_user_idx ON push_tokens (user_id);
CREATE INDEX push_tokens_last_seen_idx ON push_tokens (last_seen_at);

-- Per-account reminder switches (OQ-091); no row = both on.
CREATE TABLE notification_settings (
    user_id          UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    daily_reminder   BOOLEAN      NOT NULL DEFAULT TRUE,
    streak_reminder  BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One record per (user, type, Baku day), written *before* sending: a job retry or a
-- restart never sends the same reminder twice (at most once, max 2 per day).
CREATE TABLE notification_log (
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(16)  NOT NULL,
    day         DATE         NOT NULL,
    outcome     VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type, day),
    CONSTRAINT notification_log_type_values CHECK (type IN ('daily_reminder', 'streak_reminder')),
    CONSTRAINT notification_log_outcome_values CHECK (outcome IN ('pending', 'sent', 'failed'))
);
CREATE INDEX notification_log_day_idx ON notification_log (day);

-- Mock exams "Sınaqlar", Phase 1 (docs/adr/0002-mock-exams-data-and-audio.md,
-- docs/requirements/mock-exams.md, OQ-112 … OQ-131). Separate from questions/options
-- (OQ-127): the 4-option invariant of quiz, daily and duel stays untouched.
--
-- Content tables (templates, variants, versions, groups, tasks, options, audio) are
-- written only by the importer. A content change creates a new immutable version, so an
-- attempt always scores against the keys it was started with (EXAM-15, EXAM-24).
-- Personal data (attempts and everything below them) cascades from users (EXAM-23).

CREATE TABLE exam_templates (
    id            VARCHAR(64)  PRIMARY KEY,
    title_i18n    JSONB        NOT NULL,
    stage         SMALLINT     NOT NULL,
    mode          VARCHAR(16)  NOT NULL,
    scoring_rule  VARCHAR(32)  NOT NULL,
    -- OQ-126: NULL = free. Reserved for a later paid tier; nothing reads it for access yet.
    access_tier   VARCHAR(16),
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT exam_templates_id_format CHECK (id ~ '^[a-z]+(-[a-z]+)*$'),
    CONSTRAINT exam_templates_title_has_az CHECK (jsonb_typeof(title_i18n) = 'object' AND title_i18n ? 'az'),
    CONSTRAINT exam_templates_stage_values CHECK (stage IN (1, 2)),
    CONSTRAINT exam_templates_mode_values CHECK (mode IN ('subject', 'full'))
);

-- Listening audio (AAC-LC mono .m4a, OQ-121). The file lives in ImageStorage under
-- storage_key (`audio/{uuid}.m4a`); source_sha256 = original file (dedupe on re-import),
-- file_sha256 = stored file after metadata stripping (package manifest).
CREATE TABLE exam_audio (
    id              UUID          PRIMARY KEY,
    storage_key     VARCHAR(255)  NOT NULL UNIQUE,
    content_type    VARCHAR(32)   NOT NULL,
    bytes           INT           NOT NULL,
    duration_ms     INT           NOT NULL,
    sample_rate     INT           NOT NULL,
    channels        SMALLINT      NOT NULL,
    avg_bitrate     INT           NOT NULL,
    source_sha256   CHAR(64)      NOT NULL UNIQUE,
    file_sha256     CHAR(64)      NOT NULL,
    -- Voice attribution (OQ-120): [{"role","voice","speakerId","licence"}].
    voices          JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT exam_audio_positive CHECK (bytes > 0 AND duration_ms > 0 AND sample_rate > 0 AND channels > 0 AND avg_bitrate > 0),
    CONSTRAINT exam_audio_sha_format CHECK (source_sha256 ~ '^[0-9a-f]{64}$' AND file_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE exam_variants (
    id                   UUID         PRIMARY KEY,
    external_id          VARCHAR(64)  NOT NULL UNIQUE,
    template_id          VARCHAR(64)  NOT NULL REFERENCES exam_templates (id),
    number               INT          NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    current_version_id   UUID,
    publish_approved_by  VARCHAR(64),
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT exam_variants_status_values CHECK (status IN ('draft', 'published', 'retired')),
    CONSTRAINT exam_variants_number_positive CHECK (number >= 1),
    CONSTRAINT exam_variants_published_approved CHECK (status <> 'published' OR publish_approved_by IS NOT NULL),
    CONSTRAINT exam_variants_template_number_key UNIQUE (template_id, number)
);

CREATE TABLE exam_variant_versions (
    id             UUID         PRIMARY KEY,
    variant_id     UUID         NOT NULL REFERENCES exam_variants (id),
    version        INT          NOT NULL,
    content_hash   CHAR(64)     NOT NULL,
    language       VARCHAR(8)   NOT NULL,
    duration_ms    BIGINT       NOT NULL,
    grace_ms       BIGINT       NOT NULL,
    package_bytes  BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT exam_variant_versions_version_positive CHECK (version >= 1),
    CONSTRAINT exam_variant_versions_timing_positive CHECK (duration_ms > 0 AND grace_ms >= 0),
    CONSTRAINT exam_variant_versions_key UNIQUE (variant_id, version)
);

ALTER TABLE exam_variants
    ADD CONSTRAINT exam_variants_current_version_fk FOREIGN KEY (current_version_id) REFERENCES exam_variant_versions (id);

CREATE TABLE exam_groups (
    id                 UUID         PRIMARY KEY,
    version_id         UUID         NOT NULL REFERENCES exam_variant_versions (id),
    external_id        VARCHAR(32)  NOT NULL,
    section            VARCHAR(32)  NOT NULL,
    kind               VARCHAR(16)  NOT NULL,
    position           SMALLINT     NOT NULL,
    title              TEXT,
    instructions_i18n  JSONB,
    -- Reading text in restricted markup (ADR 0002 §2).
    passage            TEXT,
    -- OQ-123: every passage and script has a licence.
    licence            VARCHAR(32)  NOT NULL,
    author             TEXT         NOT NULL,
    source_url         TEXT,
    audio_id           UUID         REFERENCES exam_audio (id),
    -- Listening only, returned after submit: [{"role","name","voice","speakerId"}], [{"speaker","text"}].
    speakers           JSONB,
    script             JSONB,
    CONSTRAINT exam_groups_kind_values CHECK (kind IN ('reading', 'listening')),
    CONSTRAINT exam_groups_kind_content CHECK (
        (kind = 'reading' AND passage IS NOT NULL AND audio_id IS NULL AND script IS NULL) OR
        (kind = 'listening' AND passage IS NULL AND audio_id IS NOT NULL AND script IS NOT NULL AND speakers IS NOT NULL)
    ),
    CONSTRAINT exam_groups_key UNIQUE (version_id, external_id)
);

CREATE TABLE exam_tasks (
    id                UUID         PRIMARY KEY,
    version_id        UUID         NOT NULL REFERENCES exam_variant_versions (id),
    group_id          UUID         REFERENCES exam_groups (id),
    external_id       VARCHAR(32)  NOT NULL,
    section           VARCHAR(32)  NOT NULL,
    position          SMALLINT     NOT NULL,
    task_type         VARCHAR(16)  NOT NULL,
    answer_format     VARCHAR(16),
    prompt            TEXT         NOT NULL,
    explanation_i18n  JSONB,
    model_answer      TEXT,
    -- [{"mark": "1/2", "description": {"az": "..."}}]
    criteria          JSONB,
    status            VARCHAR(16)  NOT NULL,
    author            VARCHAR(64)  NOT NULL,
    reviewed_by       VARCHAR(64),
    CONSTRAINT exam_tasks_type_values CHECK (task_type IN ('closed_single', 'written')),
    CONSTRAINT exam_tasks_status_values CHECK (status IN ('draft', 'reviewed')),
    CONSTRAINT exam_tasks_position_positive CHECK (position >= 1),
    CONSTRAINT exam_tasks_written_content CHECK (
        task_type <> 'written' OR (model_answer IS NOT NULL AND criteria IS NOT NULL AND answer_format IS NOT NULL)
    ),
    CONSTRAINT exam_tasks_reviewer CHECK (status <> 'reviewed' OR (reviewed_by IS NOT NULL AND lower(reviewed_by) <> lower(author))),
    CONSTRAINT exam_tasks_position_key UNIQUE (version_id, position),
    CONSTRAINT exam_tasks_external_key UNIQUE (version_id, external_id)
);

CREATE TABLE exam_task_options (
    id          UUID      PRIMARY KEY,
    task_id     UUID      NOT NULL REFERENCES exam_tasks (id),
    position    SMALLINT  NOT NULL,
    text        TEXT      NOT NULL,
    is_correct  BOOLEAN   NOT NULL,
    -- OQ-113: 2–5 options per template.
    CONSTRAINT exam_task_options_position_range CHECK (position BETWEEN 1 AND 5),
    CONSTRAINT exam_task_options_key UNIQUE (task_id, position)
);
CREATE UNIQUE INDEX exam_task_options_one_correct ON exam_task_options (task_id) WHERE is_correct;

-- ---------------------------------------------------------------------------
-- Attempts (personal data; ON DELETE CASCADE from users, EXAM-23)
-- ---------------------------------------------------------------------------

CREATE TABLE exam_attempts (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    variant_id        UUID          NOT NULL REFERENCES exam_variants (id),
    version_id        UUID          NOT NULL REFERENCES exam_variant_versions (id),
    mode              VARCHAR(16)   NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    is_retake         BOOLEAN       NOT NULL,
    skip_listening    BOOLEAN       NOT NULL,
    idempotency_key   VARCHAR(64)   NOT NULL,
    -- OQ-118: the device of the latest accepted write before the deadline; only it may upload late.
    holder_device_id  VARCHAR(64)   NOT NULL,
    started_at        TIMESTAMPTZ   NOT NULL,
    -- Baku day of started_at: 10 starts per day (OQ-124), streak day (OQ-087).
    started_day       DATE          NOT NULL,
    ends_at           TIMESTAMPTZ   NOT NULL,
    deadline_at       TIMESTAMPTZ   NOT NULL,
    late_until        TIMESTAMPTZ   NOT NULL,
    submitted_at      TIMESTAMPTZ,
    submit_trigger    VARCHAR(8),
    auto_score        NUMERIC(5, 1),
    written_score     NUMERIC(5, 1),
    total_score       NUMERIC(5, 1),
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT exam_attempts_mode_values CHECK (mode IN ('subject', 'full')),
    CONSTRAINT exam_attempts_status_values CHECK (status IN ('running', 'awaiting_upload', 'submitted', 'finalized')),
    CONSTRAINT exam_attempts_trigger_values CHECK (submit_trigger IS NULL OR submit_trigger IN ('user', 'deadline', 'server')),
    CONSTRAINT exam_attempts_timing CHECK (ends_at > started_at AND deadline_at >= ends_at AND late_until >= deadline_at),
    CONSTRAINT exam_attempts_closed_fields CHECK (
        (status IN ('running', 'awaiting_upload') AND submitted_at IS NULL AND submit_trigger IS NULL AND total_score IS NULL) OR
        (status IN ('submitted', 'finalized') AND submitted_at IS NOT NULL AND submit_trigger IS NOT NULL AND total_score IS NOT NULL)
    ),
    CONSTRAINT exam_attempts_idempotency_key UNIQUE (user_id, idempotency_key)
);
-- OQ-124: at most one running attempt per user, also under concurrent starts.
CREATE UNIQUE INDEX exam_attempts_one_running ON exam_attempts (user_id) WHERE status = 'running';
CREATE INDEX exam_attempts_user_started_idx ON exam_attempts (user_id, started_at DESC);
CREATE INDEX exam_attempts_user_day_idx ON exam_attempts (user_id, started_day);
CREATE INDEX exam_attempts_open_idx ON exam_attempts (late_until) WHERE status IN ('running', 'awaiting_upload');

-- Latest revision per task (OQ-118). answer_text is personal data: never logged.
CREATE TABLE exam_answers (
    attempt_id   UUID           NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    task_id      UUID           NOT NULL REFERENCES exam_tasks (id),
    revision     INT            NOT NULL,
    option_id    UUID           REFERENCES exam_task_options (id),
    answer_text  VARCHAR(1000),
    flagged      BOOLEAN        NOT NULL,
    saved_at     TIMESTAMPTZ    NOT NULL,
    received_at  TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (attempt_id, task_id),
    CONSTRAINT exam_answers_revision_positive CHECK (revision >= 1),
    CONSTRAINT exam_answers_one_kind CHECK (option_id IS NULL OR answer_text IS NULL)
);

-- OQ-119: plays used (max 2) and a reported audio failure per listening group.
CREATE TABLE exam_listening_plays (
    attempt_id    UUID         NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    group_id      UUID         NOT NULL REFERENCES exam_groups (id),
    plays_used    SMALLINT     NOT NULL,
    audio_failed  BOOLEAN      NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (attempt_id, group_id),
    CONSTRAINT exam_listening_plays_range CHECK (plays_used BETWEEN 0 AND 9)
);

-- OQ-112: self-assessed written marks in sixths (0, 1/3, 1/2, 2/3, 1 = 0, 2, 3, 4, 6).
CREATE TABLE exam_self_assessments (
    attempt_id   UUID         NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    task_id      UUID         NOT NULL REFERENCES exam_tasks (id),
    mark_sixths  SMALLINT     NOT NULL,
    assessed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (attempt_id, task_id),
    CONSTRAINT exam_self_assessments_mark_values CHECK (mark_sixths IN (0, 2, 3, 4, 6))
);

-- OQ-125: a submitted attempt makes its day active (streak), no XP.
ALTER TABLE progress_awards DROP CONSTRAINT progress_awards_source_type_values;
ALTER TABLE progress_awards
    ADD CONSTRAINT progress_awards_source_type_values CHECK (source_type IN ('quiz', 'daily', 'duel', 'exam'));
ALTER TABLE xp_events DROP CONSTRAINT xp_events_source_type_values;
ALTER TABLE xp_events
    ADD CONSTRAINT xp_events_source_type_values CHECK (source_type IN ('quiz', 'daily', 'duel', 'exam'));

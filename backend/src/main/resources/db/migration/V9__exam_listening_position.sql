-- OQ-D43: after an app kill during a counted listening play, playback resumes paused at
-- the saved position. Stored per attempt + group next to the play counter.
-- position_saved_at is the client's savedAtEpochMs of the update that set the position
-- (last write wins within the same play count; a higher play count always wins).
ALTER TABLE exam_listening_plays
    ADD COLUMN position_ms       INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN position_saved_at TIMESTAMPTZ  NULL,
    ADD CONSTRAINT exam_listening_plays_position_non_negative CHECK (position_ms >= 0);

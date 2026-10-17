-- Why a match was forfeited (OQ-068, OQ-D16): 'missed_rounds' or 'left'. Shown in the
-- profile history and in a match.result rebuilt from storage. NULL for every other end
-- and for forfeits stored before this migration.
ALTER TABLE matches
    ADD COLUMN forfeit_cause VARCHAR(16),
    ADD CONSTRAINT matches_forfeit_cause_values CHECK (forfeit_cause IS NULL OR forfeit_cause IN ('missed_rounds', 'left')),
    ADD CONSTRAINT matches_forfeit_cause_only_on_forfeit CHECK (forfeit_cause IS NULL OR end_reason = 'forfeit');

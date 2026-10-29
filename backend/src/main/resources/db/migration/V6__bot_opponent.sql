-- Bot opponent (docs/mvp-spec.md §2 "Bot opponent", OQ-106 … OQ-110,
-- docs/api/duel-protocol.md §7a).

-- Explicit marker of a match against the server bot. The player is always side 'a';
-- side 'b' is the bot: player_b stays NULL and there are no side-b ratings. In a
-- human match player_b = NULL keeps meaning "deleted user" (§3a), so the marker is
-- needed to tell the two apart. Bot matches are deleted with their player (the account
-- deletion deletes them explicitly; no other person is in them).
ALTER TABLE matches
    ADD COLUMN opponent_type VARCHAR(8) NOT NULL DEFAULT 'human',
    ADD CONSTRAINT matches_opponent_type_values CHECK (opponent_type IN ('human', 'bot')),
    ADD CONSTRAINT matches_bot_has_no_side_b CHECK (
        opponent_type <> 'bot' OR (player_b IS NULL AND rating_b_before IS NULL AND rating_b_after IS NULL)
    );
CREATE INDEX matches_bot_player_idx ON matches (player_a, finished_at DESC) WHERE opponent_type = 'bot';

-- Bot awards (OQ-109): they count towards the shared 20-per-day duel cap but never
-- towards the 3-per-opponent cap, so they carry no opponent and an explicit flag
-- (never grouped with matches whose opponent was deleted).
ALTER TABLE progress_awards
    ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT progress_awards_bot_is_duel CHECK (NOT is_bot OR (source_type = 'duel' AND opponent_id IS NULL));

-- Half duel XP for bot matches has its own reasons (shown in xpBreakdown).
ALTER TABLE xp_events
    DROP CONSTRAINT xp_events_reason_values,
    ADD CONSTRAINT xp_events_reason_values CHECK (
        reason IN ('correct_answers', 'daily_bonus', 'duel_played', 'duel_win', 'duel_draw',
                   'duel_bot_played', 'duel_bot_win', 'duel_bot_draw')
    );

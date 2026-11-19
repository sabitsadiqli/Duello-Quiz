-- Week row of the Gündəlik tab (PROG-11, OQ-D35): GET /v1/me/progress returns a state
-- per day of the current Baku week. Active days are already stored (user_active_days);
-- until now only the latest freeze-covered day was kept (user_progress.freeze_used_day),
-- so a week with two freezes could not be shown. This table keeps every Baku day a
-- consumed streak freeze covered (PROG-07). Written in the same transaction as the award
-- that used the freeze; rebuilt from all active days when a day arrives out of order.
-- Personal data: ON DELETE CASCADE, so DELETE /v1/me removes it with the user row (§3a).
CREATE TABLE user_freeze_days (
    user_id  UUID  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day      DATE  NOT NULL,
    -- (user_id, day) also serves the week range scan of GET /v1/me/progress.
    PRIMARY KEY (user_id, day)
);

-- Backfill: the latest covered day is known for every user. Earlier freezes are not
-- stored anywhere in SQL form (they can only be recomputed by replaying all active days
-- through the streak rules); they are older than the latest one, so at most a week with
-- two freezes shows the earlier one as "missed". The next out-of-order replay rewrites
-- the full set. A day that later became active is skipped (active wins anyway).
INSERT INTO user_freeze_days (user_id, day)
SELECT p.user_id, p.freeze_used_day
FROM user_progress p
WHERE p.freeze_used_day IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_active_days a WHERE a.user_id = p.user_id AND a.day = p.freeze_used_day
  );

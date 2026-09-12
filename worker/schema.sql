-- Pre-registered modpacks only - the leaderboard/submit endpoints silently ignore any modpack_id
-- that isn't a row here, instead of implicitly creating a leaderboard for whatever a client sends.
-- Rows are added by hand (see worker/README notes / `wrangler d1 execute ... --command="INSERT..."`),
-- never through the public API, so nobody can register their own modpack id to squat on a name or
-- pollute someone else's board.
CREATE TABLE IF NOT EXISTS modpacks (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    -- Milliseconds since epoch, same unit as leaderboard.created_at (which the worker sets via
    -- Date.now()) - filled in automatically so the INSERT only needs to pass id and name.
    created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
);

-- One row per (modpack, player) - a new submission for a player already on the board replaces
-- their old row (via the worker's INSERT ... ON CONFLICT), so nobody appears twice. Only the top
-- 10 rows per modpack are kept; the worker prunes the rest after every accepted submission.
CREATE TABLE IF NOT EXISTS leaderboard (
    modpack_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    damage REAL NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (modpack_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_modpack_damage
    ON leaderboard (modpack_id, damage DESC, created_at ASC);

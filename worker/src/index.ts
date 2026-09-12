/**
 * Dummy Scoreboard leaderboard worker.
 *
 * Endpoints:
 *   GET  /leaderboard/:modpackId  -> top 10 [{ player_name, damage }], highest damage first
 *   POST /submit                  -> { modpack_id, snapshot } - see PlayerCombatSnapshot on the
 *                                     mod side for the snapshot shape. Only its player_uuid,
 *                                     player_name and damage fields are inspected here; the rest
 *                                     is stored as an opaque JSON blob for later audit lookup.
 *
 * The worker re-validates every submission itself (current top 10 and the submitting player's own
 * existing entry) rather than trusting the mod's own pre-check - that check exists purely so the
 * mod doesn't spam this endpoint with hits that obviously don't matter.
 */

export interface Env {
    DB: D1Database;
    // Optional: if set, /submit requires a matching "X-Api-Key" header. Set via `.dev.vars`
    // locally or `wrangler secret put SUBMIT_API_KEY` in production. Left unset, /submit is open -
    // fine for local testing, not for a real deployment.
    SUBMIT_API_KEY?: string;
}

interface LeaderboardRow {
    player_name: string;
    damage: number;
}

const CORS_HEADERS: Record<string, string> = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-Api-Key",
};

function json(data: unknown, status = 200): Response {
    return new Response(JSON.stringify(data), {
        status,
        headers: { "Content-Type": "application/json", ...CORS_HEADERS },
    });
}

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        if (request.method === "OPTIONS") {
            return new Response(null, { headers: CORS_HEADERS });
        }

        const url = new URL(request.url);
        const leaderboardMatch = url.pathname.match(/^\/leaderboard\/([^/]+)$/);

        if (request.method === "GET" && leaderboardMatch) {
            return handleGetLeaderboard(env, decodeURIComponent(leaderboardMatch[1]));
        }

        if (request.method === "POST" && url.pathname === "/submit") {
            return handleSubmit(request, env);
        }

        return json({ error: "not found" }, 404);
    },
} satisfies ExportedHandler<Env>;

/**
 * Modpack ids are pre-registered by hand (see schema.sql) specifically so nobody can point their
 * own server at, say, "atm11" and pollute or squat on a real modpack's board. An id with no row
 * here is treated exactly like "nothing to see" - no error, no distinguishing response - rather
 * than implicitly creating a leaderboard for it.
 */
async function isRegisteredModpack(env: Env, modpackId: string): Promise<boolean> {
    const row = await env.DB.prepare("SELECT 1 FROM modpacks WHERE id = ?1").bind(modpackId).first();
    return row !== null;
}

async function handleGetLeaderboard(env: Env, modpackId: string): Promise<Response> {
    if (!modpackId) {
        return json({ error: "missing modpack id" }, 400);
    }
    if (!(await isRegisteredModpack(env, modpackId))) {
        return json([]);
    }
    const { results } = await env.DB.prepare(
        "SELECT player_name, damage FROM leaderboard WHERE modpack_id = ?1 ORDER BY damage DESC, created_at ASC LIMIT 10"
    ).bind(modpackId).all<LeaderboardRow>();
    return json(results ?? []);
}

async function handleSubmit(request: Request, env: Env): Promise<Response> {
    if (env.SUBMIT_API_KEY && request.headers.get("X-Api-Key") !== env.SUBMIT_API_KEY) {
        return json({ error: "unauthorized" }, 401);
    }

    let body: any;
    try {
        body = await request.json();
    } catch {
        return json({ error: "invalid json" }, 400);
    }

    const modpackId = body?.modpack_id;
    const snapshot = body?.snapshot;
    const playerUuid = snapshot?.player_uuid;
    const playerName = snapshot?.player_name;
    const damage = snapshot?.damage;

    if (
        typeof modpackId !== "string" || modpackId.length === 0 ||
        typeof playerUuid !== "string" || playerUuid.length === 0 ||
        typeof playerName !== "string" || playerName.length === 0 ||
        typeof damage !== "number" || !(damage > 0)
    ) {
        return json({ error: "invalid payload" }, 400);
    }

    if (!(await isRegisteredModpack(env, modpackId))) {
        return json({ accepted: false, reason: "unknown modpack" });
    }

    const existing = await env.DB.prepare(
        "SELECT damage FROM leaderboard WHERE modpack_id = ?1 AND player_uuid = ?2"
    ).bind(modpackId, playerUuid).first<{ damage: number }>();

    if (existing && existing.damage >= damage) {
        return json({ accepted: false, reason: "not an improvement over your own record" });
    }

    const { results: rivals } = await env.DB.prepare(
        "SELECT damage FROM leaderboard WHERE modpack_id = ?1 AND player_uuid != ?2 ORDER BY damage DESC, created_at ASC LIMIT 10"
    ).bind(modpackId, playerUuid).all<{ damage: number }>();

    const qualifies = rivals.length < 10 || damage > rivals[rivals.length - 1].damage;
    if (!qualifies) {
        return json({ accepted: false, reason: "did not make the top 10" });
    }

    await env.DB.prepare(
        `INSERT INTO leaderboard (modpack_id, player_uuid, player_name, damage, snapshot_json, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)
         ON CONFLICT(modpack_id, player_uuid) DO UPDATE SET
             player_name = excluded.player_name,
             damage = excluded.damage,
             snapshot_json = excluded.snapshot_json,
             created_at = excluded.created_at`
    ).bind(modpackId, playerUuid, playerName, damage, JSON.stringify(snapshot), Date.now()).run();

    await env.DB.prepare(
        `DELETE FROM leaderboard WHERE modpack_id = ?1 AND player_uuid NOT IN (
             SELECT player_uuid FROM leaderboard WHERE modpack_id = ?1 ORDER BY damage DESC, created_at ASC LIMIT 10
         )`
    ).bind(modpackId).run();

    return json({ accepted: true });
}

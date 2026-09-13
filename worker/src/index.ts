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
    // Per-IP request caps (see wrangler.jsonc) - protects the daily Workers/D1 free-tier budget
    // from someone hitting the endpoints directly, not meant to affect normal mod traffic.
    RATE_LIMITER_GET: RateLimit;
    RATE_LIMITER_SUBMIT: RateLimit;
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
        const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";

        if (request.method === "GET" && leaderboardMatch) {
            if (!(await env.RATE_LIMITER_GET.limit({ key: ip })).success) {
                return json({ error: "rate limited, try again shortly" }, 429);
            }
            return handleGetLeaderboard(env, decodeURIComponent(leaderboardMatch[1]));
        }

        if (request.method === "POST" && url.pathname === "/submit") {
            if (!(await env.RATE_LIMITER_SUBMIT.limit({ key: ip })).success) {
                return json({ error: "rate limited, try again shortly" }, 429);
            }
            return handleSubmit(request, env);
        }

        return json({ error: "not found" }, 404);
    },
} satisfies ExportedHandler<Env>;

interface ModpackRow {
    api_key: string | null;
}

/**
 * Modpack ids are pre-registered by hand (see schema.sql) specifically so nobody can point their
 * own server at, say, "atm11" and pollute or squat on a real modpack's board. An id with no row
 * here is treated exactly like "nothing to see" - no error, no distinguishing response - rather
 * than implicitly creating a leaderboard for it.
 */
async function getModpack(env: Env, modpackId: string): Promise<ModpackRow | null> {
    return env.DB.prepare("SELECT api_key FROM modpacks WHERE id = ?1").bind(modpackId).first<ModpackRow>();
}

async function handleGetLeaderboard(env: Env, modpackId: string): Promise<Response> {
    if (!modpackId) {
        return json({ error: "missing modpack id" }, 400);
    }
    if (!(await getModpack(env, modpackId))) {
        return json([]);
    }
    const { results } = await env.DB.prepare(
        "SELECT player_name, damage FROM leaderboard WHERE modpack_id = ?1 ORDER BY damage DESC, created_at ASC LIMIT 10"
    ).bind(modpackId).all<LeaderboardRow>();
    return json(results ?? []);
}

interface SnapshotAttribute {
    id?: string;
    base?: number;
}

// Mirrors the vanilla default base value for a player - see AttackIntegrity.java on the mod side
// for the full reasoning. Kept in sync by hand; if that file's map changes, update this one too.
const EXPECTED_ATTRIBUTE_BASE: Record<string, number> = {
    "minecraft:attack_damage": 1,
    "minecraft:attack_speed": 4,
    "minecraft:max_health": 20,
    "minecraft:armor": 0,
    "minecraft:armor_toughness": 0,
    "minecraft:knockback_resistance": 0,
    "minecraft:luck": 0,
};
const ATTRIBUTE_EPSILON = 1e-4;

/**
 * Independent re-check of the same signal the mod itself already looks for before ever offering a
 * candidate - this exists so a modified client (or a request crafted by hand against this endpoint
 * directly) can't just skip the Java-side check. Mods layer their bonuses on as modifiers and never
 * touch base, so a base drifting from vanilla's own default is a tamper signal, not a false
 * positive waiting to happen.
 */
function hasTamperedAttributes(snapshot: any): boolean {
    const attributes = snapshot?.attributes;
    if (!Array.isArray(attributes)) {
        return false;
    }
    for (const attribute of attributes as SnapshotAttribute[]) {
        if (typeof attribute?.id !== "string" || typeof attribute?.base !== "number") {
            continue;
        }
        const expected = EXPECTED_ATTRIBUTE_BASE[attribute.id];
        if (expected !== undefined && Math.abs(attribute.base - expected) > ATTRIBUTE_EPSILON) {
            return true;
        }
    }
    return false;
}

async function handleSubmit(request: Request, env: Env): Promise<Response> {
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

    if (hasTamperedAttributes(snapshot)) {
        return json({ accepted: false, reason: "attribute tampering detected" });
    }

    const modpack = await getModpack(env, modpackId);
    if (!modpack) {
        return json({ accepted: false, reason: "unknown modpack" });
    }
    if (modpack.api_key && request.headers.get("X-Api-Key") !== modpack.api_key) {
        return json({ error: "unauthorized" }, 401);
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

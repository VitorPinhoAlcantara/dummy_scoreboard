package com.dummyscoreboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {

    // Baked into the jar rather than exposed as a config field, so a regular server admin can't
    // casually read or repoint these from the visible, documented settings file - only someone who
    // decompiles the jar could (this is obfuscation, not real secrecy; see the per-modpack api_key
    // design on the worker side for what actually limits the damage from that). Used only as the
    // fallback when the admin hasn't set up their own dedicated modpack: every server that installs
    // this mod without touching the config still gets to participate in a shared default
    // leaderboard instead of being local-only, while modpacks like ATM 11 get their own private one
    // by simply setting modpackId below.
    //
    // This is its own modpack row, separate from the 26.1.2 branch's default - each Minecraft
    // version this mod is built for gets its own default leaderboard, not one shared across all of
    // them (same worker/database either way, just a different row per version).
    private static final String DEFAULT_MODPACK_ID = "c66b7617-97de-484f-8d70-2c01d4ef28de";
    private static final String DEFAULT_MODPACK_API_KEY = "80d3e91695ebfb4815eb92788670adb3760187604f8aaefb3443a357a61562f8";
    // Not a secret (it's a public HTTPS endpoint visible in every request anyway) - baked in purely
    // so the default modpack works without the admin having to type this in themselves. Same worker
    // as every other branch/version of this mod - only the modpack id/key above differ per version.
    private static final String DEFAULT_WORKER_BASE_URL = "https://dummyscoreboard-worker.vitoralcantara1722.workers.dev";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Free-form string rather than a strictly-validated UUID: the modpack id is just an opaque key
    // the worker groups leaderboards by, and forcing UUID syntax here would only get in the way of
    // a server admin picking something readable (a real UUID still works fine as a value).
    public static final ModConfigSpec.ConfigValue<String> MODPACK_ID = BUILDER
            .comment("Identifies this server's modpack to the leaderboard worker. Every server meant to " +
                    "share the same leaderboard must use the same value here. Leave blank to use the " +
                    "mod's built-in default shared leaderboard instead of a dedicated one.")
            .define("modpackId", "");

    // Purely cosmetic (shown in the global board's title) - the worker doesn't return this, so the
    // admin just types in whatever display name they already registered for modpackId in the D1
    // modpacks table. Leave blank to fall back to the generic "Global Leaderboard" title.
    public static final ModConfigSpec.ConfigValue<String> MODPACK_DISPLAY_NAME = BUILDER
            .comment("Friendly name shown in the global leaderboard's title (e.g. \"ATM 11\"). Purely " +
                    "cosmetic - has no effect on which leaderboard is used, that's still modpackId. " +
                    "Leave blank to just show a generic title.")
            .define("modpackDisplayName", "");

    public static final ModConfigSpec.IntValue LEADERBOARD_REFRESH_SECONDS = BUILDER
            .comment("Minimum time between re-fetching the leaderboard from the worker. Clients ask this " +
                    "server for the leaderboard on demand (opening the scoreboard dummy's screen); this " +
                    "only limits how often the server itself re-checks the worker for updates.")
            .defineInRange("leaderboardRefreshSeconds", 600, 10, 86_400);

    public static final ModConfigSpec.IntValue RECORD_DEBOUNCE_SECONDS = BUILDER
            .comment("After a hit qualifies for the top 10, how long to wait (without an even bigger " +
                    "qualifying hit from the same player) before actually submitting it. Prevents " +
                    "sending a record that's immediately beaten by the same player's next swing.")
            .defineInRange("recordDebounceSeconds", 20, 5, 3600);

    public static final ModConfigSpec.ConfigValue<String> WORKER_BASE_URL = BUILDER
            .comment("Base URL of the leaderboard worker (e.g. http://127.0.0.1:8787 for a local " +
                    "`wrangler dev` you're running yourself). Leave blank to use the mod's built-in worker.")
            .define("workerBaseUrl", "");

    public static final ModConfigSpec.ConfigValue<String> WORKER_API_KEY = BUILDER
            .comment("Sent as the X-Api-Key header on every submission, if set. Must match modpackId's " +
                    "own key on the worker. Leave blank to use the mod's built-in default modpack's key.")
            .define("workerApiKey", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * {@link #MODPACK_ID}, falling back to the mod's own default modpack when the admin hasn't set
     * one. Always non-blank - unlike a blank {@link #MODPACK_DISPLAY_NAME}, there's no "no global
     * board at all" mode any more, only "the shared default one" vs. "your own dedicated one".
     */
    public static String effectiveModpackId() {
        String configured = MODPACK_ID.get();
        return configured.isBlank() ? DEFAULT_MODPACK_ID : configured;
    }

    public static String effectiveWorkerBaseUrl() {
        String configured = WORKER_BASE_URL.get();
        return configured.isBlank() ? DEFAULT_WORKER_BASE_URL : configured;
    }

    public static String effectiveWorkerApiKey() {
        String configured = WORKER_API_KEY.get();
        return configured.isBlank() ? DEFAULT_MODPACK_API_KEY : configured;
    }

    private CommonConfig() {
    }
}

package com.dummyscoreboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Free-form string rather than a strictly-validated UUID: the modpack id is just an opaque key
    // the worker groups leaderboards by, and forcing UUID syntax here would only get in the way of
    // a server admin picking something readable (a real UUID still works fine as a value).
    public static final ModConfigSpec.ConfigValue<String> MODPACK_ID = BUILDER
            .comment("Identifies this server's modpack to the leaderboard worker. Every server meant to " +
                    "share the same leaderboard must use the same value here. Leave blank to disable " +
                    "leaderboard reporting entirely.")
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
                    "`wrangler dev`, or the real deployed worker's URL later). Leave blank to keep using " +
                    "the built-in in-memory/local-file stub instead of a real HTTP backend.")
            .define("workerBaseUrl", "");

    public static final ModConfigSpec.ConfigValue<String> WORKER_API_KEY = BUILDER
            .comment("Sent as the X-Api-Key header on every submission, if set. Must match the worker's " +
                    "SUBMIT_API_KEY. Leave blank if the worker doesn't require one (e.g. local dev).")
            .define("workerApiKey", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}

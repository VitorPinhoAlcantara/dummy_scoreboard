package com.dummyscoreboard.persistence;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.rank.LeaderboardCache;
import com.dummyscoreboard.rank.LocalStubLeaderboardService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Saves/restores {@link LeaderboardCache}'s local board and (when the service is the local stub)
 * its per-modpack cache to a JSON file in the world's save folder, so both survive a server
 * restart. Once a real Worker-backed {@link com.dummyscoreboard.rank.LeaderboardService} exists,
 * the global side of this becomes redundant (the worker's own database is the source of truth) -
 * only the always-local board still needs this.
 */
public final class LeaderboardPersistence {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath;

    public static void bind(MinecraftServer server) {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("dummyscoreboard");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DummyScoreboardMod.LOGGER.error("Could not create dummyscoreboard data folder", e);
            filePath = null;
            return;
        }
        filePath = dir.resolve("leaderboard.json");
        load();
    }

    public static void unbind() {
        save();
        filePath = null;
    }

    public static synchronized void save() {
        if (filePath == null) {
            return;
        }
        Map<String, java.util.List<com.dummyscoreboard.snapshot.PlayerCombatSnapshot>> globalState =
                LeaderboardCache.service() instanceof LocalStubLeaderboardService stub ? stub.exportState() : Map.of();
        PersistedLeaderboardState state = new PersistedLeaderboardState(LeaderboardCache.localEntries(), globalState);

        DataResult<JsonElement> result = PersistedLeaderboardState.CODEC.encodeStart(JsonOps.INSTANCE, state);
        result.resultOrPartial(error -> DummyScoreboardMod.LOGGER.error("Failed to encode leaderboard data: {}", error))
                .ifPresent(json -> {
                    try (Writer writer = Files.newBufferedWriter(filePath)) {
                        GSON.toJson(json, writer);
                    } catch (IOException e) {
                        DummyScoreboardMod.LOGGER.error("Failed to save leaderboard data", e);
                    }
                });
    }

    public static synchronized void load() {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonElement json = JsonParser.parseReader(reader);
            PersistedLeaderboardState.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> DummyScoreboardMod.LOGGER.error("Failed to decode leaderboard data: {}", error))
                    .ifPresent(state -> {
                        LeaderboardCache.restoreLocalEntries(state.local());
                        if (LeaderboardCache.service() instanceof LocalStubLeaderboardService stub) {
                            stub.importState(state.globalByModpack());
                        }
                    });
        } catch (IOException e) {
            DummyScoreboardMod.LOGGER.error("Failed to load leaderboard data", e);
        }
    }

    private LeaderboardPersistence() {
    }
}

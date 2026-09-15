package com.dummyscoreboard.rank;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Talks to the real worker (see /worker in this repo) over plain HTTP. Requests are made
 * synchronously on whatever thread calls in - fine for the low-latency local `wrangler dev` loop
 * this was built against, but a real deployment with real network latency would want these made
 * asynchronously instead of blocking the server tick thread. Left synchronous for now to match the
 * rest of the mod's straightforward style; revisit if it ever causes noticeable tick lag.
 */
public class HttpLeaderboardService implements LeaderboardService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient client;

    public HttpLeaderboardService(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public Optional<List<LeaderboardEntry>> fetchTop10(String modpackId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + "/leaderboard/" + URLEncoder.encode(modpackId, StandardCharsets.UTF_8)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                DummyScoreboardMod.LOGGER.error("Leaderboard fetch failed: HTTP {} - {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonElement json = JsonParser.parseString(response.body());
            return LeaderboardEntry.CODEC.listOf().parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> DummyScoreboardMod.LOGGER.error("Failed to decode leaderboard response: {}", error));
        } catch (IOException e) {
            DummyScoreboardMod.LOGGER.error("Leaderboard fetch failed", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public boolean submitCandidate(String modpackId, PlayerCombatSnapshot snapshot, HolderLookup.Provider registries) {
        // Plain JsonOps can't resolve registry-backed data on the captured item stacks (enchantments,
        // trims, etc. are stored as holder references, not inline values) - encoding one without a
        // registry-aware ops fails with "Can't access registry ResourceKey[...]" and silently drops
        // the whole submission.
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        DataResult<JsonElement> encoded = PlayerCombatSnapshot.CODEC.encodeStart(ops, snapshot);
        return encoded.resultOrPartial(error -> DummyScoreboardMod.LOGGER.error("Failed to encode snapshot: {}", error))
                .map(snapshotJson -> this.postSubmit(modpackId, snapshotJson))
                .orElse(false);
    }

    private boolean postSubmit(String modpackId, JsonElement snapshotJson) {
        JsonObject body = new JsonObject();
        body.addProperty("modpack_id", modpackId);
        body.add("snapshot", snapshotJson);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + "/submit"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!this.apiKey.isBlank()) {
            requestBuilder.header("X-Api-Key", this.apiKey);
        }

        try {
            HttpResponse<String> response = this.client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                DummyScoreboardMod.LOGGER.error("Leaderboard submit failed: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (IOException e) {
            DummyScoreboardMod.LOGGER.error("Leaderboard submit failed", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

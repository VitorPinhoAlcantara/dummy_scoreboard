package com.dummyscoreboard.rank;

import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;

import java.util.List;

/**
 * Talks to whatever actually stores the cross-server leaderboard. {@link LocalStubLeaderboardService}
 * is a placeholder (in-memory, this server only) used while the real Cloudflare Worker doesn't exist
 * yet - swap the binding in {@link com.dummyscoreboard.DummyScoreboardMod} once it does, nothing else
 * in the mod needs to change.
 */
public interface LeaderboardService {

    /**
     * Top 10 entries for a modpack, sorted highest damage first. Never null; empty if none yet.
     */
    List<LeaderboardEntry> fetchTop10(String modpackId);

    /**
     * Submits a candidate record. Implementations are expected to only keep it if it actually beats
     * the current top 10 (or the submitting player's own existing entry) - callers already checked
     * this against their local cache, but the service is the source of truth.
     */
    void submitCandidate(String modpackId, PlayerCombatSnapshot snapshot);
}

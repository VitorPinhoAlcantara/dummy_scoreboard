package com.dummyscoreboard.rank;

import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import net.minecraft.core.HolderLookup;

import java.util.List;
import java.util.Optional;

/**
 * Talks to whatever actually stores the cross-server leaderboard. {@link LocalStubLeaderboardService}
 * is a placeholder (in-memory, this server only) used while the real Cloudflare Worker doesn't exist
 * yet - swap the binding in {@link com.dummyscoreboard.DummyScoreboardMod} once it does, nothing else
 * in the mod needs to change.
 */
public interface LeaderboardService {

    /**
     * Top 10 entries for a modpack, sorted highest damage first. Empty (not absent) if the fetch
     * succeeded but the board genuinely has nothing yet; {@link Optional#empty()} means the fetch
     * itself failed (network error, bad response, etc.) - callers need to tell these apart so a
     * connectivity problem doesn't get reported to the player as "you got outranked".
     */
    Optional<List<LeaderboardEntry>> fetchTop10(String modpackId);

    /**
     * Submits a candidate record. Implementations are expected to only keep it if it actually beats
     * the current top 10 (or the submitting player's own existing entry) - callers already checked
     * this against their local cache, but the service is the source of truth.
     *
     * @param registries registry access needed to encode the snapshot's item stacks (enchantments,
     *                    trims, etc. are registry-backed and can't be encoded with a bare JsonOps).
     * @return true if the server was reachable and gave a proper response (whether it accepted the
     *         record or not) - false only for a communication failure (network error, unexpected
     *         status code, etc.), never for a legitimate "no thanks" from the server.
     */
    boolean submitCandidate(String modpackId, PlayerCombatSnapshot snapshot, HolderLookup.Provider registries);
}

package com.dummyscoreboard.rank;

import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-server placeholder for the eventual Worker-backed service. Keeps full snapshots (not just
 * the public entry) so the audit view has something to show once it exists. Held only in memory
 * here - {@link com.dummyscoreboard.persistence.LeaderboardPersistence} is what saves/restores it
 * across restarts via {@link #exportState()}/{@link #importState}.
 */
public final class LocalStubLeaderboardService implements LeaderboardService {

    private static final int TOP_N = 10;

    private final Map<String, List<PlayerCombatSnapshot>> byModpack = new ConcurrentHashMap<>();

    @Override
    public List<LeaderboardEntry> fetchTop10(String modpackId) {
        return this.byModpack.getOrDefault(modpackId, List.of()).stream()
                .map(snapshot -> new LeaderboardEntry(snapshot.playerName(), snapshot.damage()))
                .toList();
    }

    public List<PlayerCombatSnapshot> fetchTop10Snapshots(String modpackId) {
        return List.copyOf(this.byModpack.getOrDefault(modpackId, List.of()));
    }

    @Override
    public synchronized void submitCandidate(String modpackId, PlayerCombatSnapshot snapshot, HolderLookup.Provider registries) {
        List<PlayerCombatSnapshot> current = new ArrayList<>(this.byModpack.getOrDefault(modpackId, List.of()));

        UUID player = snapshot.playerUuid();
        current.removeIf(existing -> existing.playerUuid().equals(player) && existing.damage() >= snapshot.damage());
        boolean alreadyHasBetter = current.stream()
                .anyMatch(existing -> existing.playerUuid().equals(player) && existing.damage() > snapshot.damage());
        if (alreadyHasBetter) {
            this.byModpack.put(modpackId, current);
            return;
        }

        current.add(snapshot);
        current.sort(Comparator.comparingDouble(PlayerCombatSnapshot::damage).reversed());
        List<PlayerCombatSnapshot> trimmed = current.size() > TOP_N ? current.subList(0, TOP_N) : current;
        this.byModpack.put(modpackId, List.copyOf(trimmed));
    }

    public Map<String, List<PlayerCombatSnapshot>> exportState() {
        return Map.copyOf(this.byModpack);
    }

    public void importState(Map<String, List<PlayerCombatSnapshot>> state) {
        this.byModpack.clear();
        this.byModpack.putAll(state);
    }
}

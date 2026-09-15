package com.dummyscoreboard.rank;

import com.dummyscoreboard.config.CommonConfig;
import com.dummyscoreboard.persistence.LeaderboardPersistence;
import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Server-owned leaderboard state, split into two independent boards:
 * <ul>
 *   <li><b>Local</b> - this server's own top 10, tracked live and entirely in memory. Always
 *   available, never touches {@link CommonConfig#MODPACK_ID} or the worker at all.</li>
 *   <li><b>Global</b> - the cross-server board, only active when a modpack id is configured. A
 *   qualifying hit shows up here immediately (optimistic preview), but only actually gets sent to
 *   the worker once {@link PendingCandidateTracker}'s debounce window passes and
 *   {@link #confirmGlobalCandidate} re-validates it against the freshest data.</li>
 * </ul>
 * Clients never talk to the worker directly - they ask their own server (see the network payload),
 * and the server only re-checks the worker at most once per
 * {@link CommonConfig#LEADERBOARD_REFRESH_SECONDS}, regardless of how many players ask.
 */
public final class LeaderboardCache {

    private static final int TOP_N = 10;

    /**
     * Sentinel {@link #confirmGlobalCandidate} rank meaning the server couldn't be reached (or gave
     * a bad response) - distinct from a legitimate -1 "someone else beat you to it", so the player
     * (and the server console) can tell a connectivity problem apart from an actual rejection.
     */
    public static final int COMMUNICATION_ERROR = Integer.MIN_VALUE;

    private static LeaderboardService service = new LocalStubLeaderboardService();

    private static volatile List<LeaderboardEntry> localEntries = List.of();
    private static volatile List<LeaderboardEntry> globalEntries = List.of();
    private static long lastFetchGameTime = Long.MIN_VALUE;

    public static void setService(LeaderboardService newService) {
        service = newService;
        lastFetchGameTime = Long.MIN_VALUE;
    }

    public static LeaderboardService service() {
        return service;
    }

    public static boolean globalAvailable() {
        return !CommonConfig.effectiveModpackId().isBlank();
    }

    public static List<LeaderboardEntry> localEntries() {
        return localEntries;
    }

    /**
     * Used only by {@link com.dummyscoreboard.persistence.LeaderboardPersistence} to restore the
     * local board on startup.
     */
    public static void restoreLocalEntries(List<LeaderboardEntry> entries) {
        localEntries = List.copyOf(entries);
    }

    public static List<LeaderboardEntry> globalEntries() {
        return globalEntries;
    }

    public static void refreshGlobalIfStale(Level level) {
        if (!globalAvailable()) {
            return;
        }
        long now = level.getGameTime();
        long refreshTicks = CommonConfig.LEADERBOARD_REFRESH_SECONDS.get() * 20L;
        if (lastFetchGameTime != Long.MIN_VALUE && now - lastFetchGameTime < refreshTicks) {
            return;
        }
        lastFetchGameTime = now;
        // A failed refresh just keeps showing the last known board rather than blanking it out -
        // a transient network hiccup shouldn't make the leaderboard look empty.
        service.fetchTop10(CommonConfig.effectiveModpackId()).ifPresent(entries -> globalEntries = entries);
    }

    public static boolean qualifiesLocal(String playerName, float damage) {
        return qualifies(localEntries, playerName, damage);
    }

    public static boolean qualifiesGlobal(String playerName, float damage) {
        return qualifies(globalEntries, playerName, damage);
    }

    /**
     * Always-on, instant, this-server-only board - no debounce, nothing to confirm against.
     *
     * @return the player's new 1-based rank on the local board.
     */
    public static synchronized int applyLocalUpdate(String playerName, float damage) {
        UpdateResult result = computeUpdate(localEntries, playerName, damage);
        localEntries = result.entries();
        LeaderboardPersistence.save();
        return result.rank();
    }

    /**
     * Optimistic preview of a global record, shown immediately while the real submission waits out
     * the debounce window.
     *
     * @return the player's new 1-based rank on the (locally previewed) global board.
     */
    public static synchronized int applyGlobalOptimisticUpdate(String playerName, float damage) {
        UpdateResult result = computeUpdate(globalEntries, playerName, damage);
        globalEntries = result.entries();
        return result.rank();
    }

    /**
     * Called once the debounce window passes. Re-fetches the freshest data first and re-checks
     * qualification against it (someone else, or another server, may have beaten this in the
     * meantime) before actually submitting.
     *
     * @return the confirmed 1-based rank, -1 if the record no longer qualifies, or
     *         {@link #COMMUNICATION_ERROR} if the worker couldn't be reached at any step.
     */
    public static synchronized int confirmGlobalCandidate(PlayerCombatSnapshot snapshot, HolderLookup.Provider registries) {
        String modpackId = CommonConfig.effectiveModpackId();

        Optional<List<LeaderboardEntry>> beforeSubmit = service.fetchTop10(modpackId);
        if (beforeSubmit.isEmpty()) {
            return COMMUNICATION_ERROR;
        }
        globalEntries = beforeSubmit.get();
        if (!qualifiesGlobal(snapshot.playerName(), snapshot.damage())) {
            return -1;
        }

        if (!service.submitCandidate(modpackId, snapshot, registries)) {
            return COMMUNICATION_ERROR;
        }

        Optional<List<LeaderboardEntry>> afterSubmit = service.fetchTop10(modpackId);
        if (afterSubmit.isEmpty()) {
            return COMMUNICATION_ERROR;
        }
        globalEntries = afterSubmit.get();
        LeaderboardPersistence.save();
        for (int i = 0; i < globalEntries.size(); i++) {
            if (globalEntries.get(i).playerName().equals(snapshot.playerName())) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean qualifies(List<LeaderboardEntry> entries, String playerName, float damage) {
        for (LeaderboardEntry entry : entries) {
            if (entry.playerName().equals(playerName)) {
                return damage > entry.damage();
            }
        }
        return entries.size() < TOP_N || damage > entries.get(entries.size() - 1).damage();
    }

    /**
     * Shared dedup/sort/trim step for both boards. Ties keep whoever already held the spot ahead of
     * a new arrival: the current list is always already sorted, and the new entry is appended at
     * the end before a stable sort, so equal-damage entries never move ahead of an existing one.
     */
    private static UpdateResult computeUpdate(List<LeaderboardEntry> current, String playerName, float damage) {
        List<LeaderboardEntry> updated = new ArrayList<>(current);
        updated.removeIf(entry -> entry.playerName().equals(playerName));
        LeaderboardEntry newEntry = new LeaderboardEntry(playerName, damage);
        updated.add(newEntry);
        updated.sort(Comparator.comparingDouble(LeaderboardEntry::damage).reversed());
        List<LeaderboardEntry> trimmed = updated.size() > TOP_N ? updated.subList(0, TOP_N) : updated;
        List<LeaderboardEntry> result = List.copyOf(trimmed);
        return new UpdateResult(result, result.indexOf(newEntry) + 1);
    }

    private record UpdateResult(List<LeaderboardEntry> entries, int rank) {
    }

    private LeaderboardCache() {
    }
}

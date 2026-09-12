package com.dummyscoreboard.rank;

import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-player debounce: a hit that qualifies for the top 10 doesn't get submitted immediately, in
 * case the same player lands an even bigger one shortly after. Only the best hit within the
 * debounce window actually gets sent, and only once the window passes without being beaten again.
 */
public final class PendingCandidateTracker {

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public static void offerCandidate(PlayerCombatSnapshot snapshot, long nowGameTime, long debounceTicks) {
        PENDING.compute(snapshot.playerUuid(), (uuid, existing) -> {
            if (existing != null && existing.snapshot.damage() >= snapshot.damage()) {
                return existing;
            }
            return new Pending(snapshot, nowGameTime + debounceTicks);
        });
    }

    public static void tick(long nowGameTime, Consumer<PlayerCombatSnapshot> onReady) {
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Pending pending = it.next().getValue();
            if (nowGameTime >= pending.readyAtGameTime) {
                onReady.accept(pending.snapshot);
                it.remove();
            }
        }
    }

    private record Pending(PlayerCombatSnapshot snapshot, long readyAtGameTime) {
    }

    private PendingCandidateTracker() {
    }
}

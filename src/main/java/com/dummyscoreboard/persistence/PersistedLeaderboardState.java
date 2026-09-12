package com.dummyscoreboard.persistence;

import com.dummyscoreboard.rank.LeaderboardEntry;
import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * On-disk shape of everything the local stub tracks: the always-on local board, plus a snapshot of
 * whatever {@link com.dummyscoreboard.rank.LocalStubLeaderboardService} holds per modpack id (the
 * stand-in for what the real worker's database would own once it exists).
 */
public record PersistedLeaderboardState(
        List<LeaderboardEntry> local,
        Map<String, List<PlayerCombatSnapshot>> globalByModpack
) {

    public static final Codec<PersistedLeaderboardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LeaderboardEntry.CODEC.listOf().fieldOf("local").forGetter(PersistedLeaderboardState::local),
            Codec.unboundedMap(Codec.STRING, PlayerCombatSnapshot.CODEC.listOf())
                    .fieldOf("global_by_modpack").forGetter(PersistedLeaderboardState::globalByModpack)
    ).apply(instance, PersistedLeaderboardState::new));
}

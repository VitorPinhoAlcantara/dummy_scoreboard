package com.dummyscoreboard.event;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.config.CommonConfig;
import com.dummyscoreboard.entity.ScoreboardDummyEntity;
import com.dummyscoreboard.rank.LeaderboardCache;
import com.dummyscoreboard.rank.PendingCandidateTracker;
import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = DummyScoreboardMod.MODID)
public final class ScoreboardCombatEvents {

    @SubscribeEvent
    static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ScoreboardDummyEntity)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        float amount = event.getInflictedDamage();
        if (amount <= 0.0F) {
            return;
        }
        String playerName = attacker.getGameProfile().name();

        if (LeaderboardCache.qualifiesLocal(playerName, amount)) {
            int localRank = LeaderboardCache.applyLocalUpdate(playerName, amount);
            attacker.sendSystemMessage(Component.translatable("dummyscoreboard.record.local", localRank));
        }

        if (LeaderboardCache.globalAvailable() && LeaderboardCache.qualifiesGlobal(playerName, amount)) {
            int globalRank = LeaderboardCache.applyGlobalOptimisticUpdate(playerName, amount);
            attacker.sendSystemMessage(Component.translatable("dummyscoreboard.record.achieved", globalRank));

            PlayerCombatSnapshot snapshot = PlayerCombatSnapshot.capture(attacker, amount);
            long debounceTicks = CommonConfig.RECORD_DEBOUNCE_SECONDS.get() * 20L;
            PendingCandidateTracker.offerCandidate(snapshot, attacker.level().getGameTime(), debounceTicks);
        }
    }

    private ScoreboardCombatEvents() {
    }
}

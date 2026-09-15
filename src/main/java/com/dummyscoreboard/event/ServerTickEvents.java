package com.dummyscoreboard.event;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.rank.LeaderboardCache;
import com.dummyscoreboard.rank.PendingCandidateTracker;
import com.dummyscoreboard.snapshot.PlayerCombatSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = DummyScoreboardMod.MODID)
public final class ServerTickEvents {

    // Cheap checks (a few volatile reads/timestamp comparisons); no need to run them every tick.
    private static final int CHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        LeaderboardCache.refreshGlobalIfStale(overworld);

        long now = overworld.getGameTime();
        PendingCandidateTracker.tick(now, snapshot -> confirmCandidate(server, snapshot));
    }

    private static void confirmCandidate(MinecraftServer server, PlayerCombatSnapshot snapshot) {
        int rank = LeaderboardCache.confirmGlobalCandidate(snapshot, server.registryAccess());
        ServerPlayer player = server.getPlayerList().getPlayer(snapshot.playerUuid());
        if (player == null) {
            return;
        }
        if (rank > 0) {
            player.sendSystemMessage(Component.translatable("dummyscoreboard.record.confirmed", rank));
        } else if (rank == LeaderboardCache.COMMUNICATION_ERROR) {
            player.sendSystemMessage(Component.translatable("dummyscoreboard.record.communication_error"));
        } else {
            player.sendSystemMessage(Component.translatable("dummyscoreboard.record.rejected"));
        }
    }

    private ServerTickEvents() {
    }
}

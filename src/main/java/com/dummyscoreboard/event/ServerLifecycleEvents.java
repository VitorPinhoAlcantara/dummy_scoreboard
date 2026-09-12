package com.dummyscoreboard.event;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.config.CommonConfig;
import com.dummyscoreboard.persistence.LeaderboardPersistence;
import com.dummyscoreboard.rank.HttpLeaderboardService;
import com.dummyscoreboard.rank.LeaderboardCache;
import com.dummyscoreboard.rank.LocalStubLeaderboardService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = DummyScoreboardMod.MODID)
public final class ServerLifecycleEvents {

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        String workerBaseUrl = CommonConfig.WORKER_BASE_URL.get();
        if (workerBaseUrl.isBlank()) {
            LeaderboardCache.setService(new LocalStubLeaderboardService());
            DummyScoreboardMod.LOGGER.info("Dummy Scoreboard: no workerBaseUrl configured, using the local in-memory/file stub.");
        } else {
            LeaderboardCache.setService(new HttpLeaderboardService(workerBaseUrl, CommonConfig.WORKER_API_KEY.get()));
            DummyScoreboardMod.LOGGER.info("Dummy Scoreboard: reporting to worker at {}", workerBaseUrl);
        }
        // Bound after the service is chosen: persistence only owns the local board (and the stub's
        // state, when that's the active service) - it needs to know which one is live first.
        LeaderboardPersistence.bind(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        LeaderboardPersistence.unbind();
    }

    private ServerLifecycleEvents() {
    }
}

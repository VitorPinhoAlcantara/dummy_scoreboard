package com.dummyscoreboard.client;

import com.dummyscoreboard.network.OpenScoreboardScreenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    public static void handleOpenScoreboardScreen(OpenScoreboardScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ScoreboardScreen(
                payload.localEntries(), payload.globalEntries(), payload.globalAvailable())));
    }

    private ClientPayloadHandler() {
    }
}

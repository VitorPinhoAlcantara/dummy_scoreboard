package com.dummyscoreboard;

import com.dummyscoreboard.registry.ModEntities;
import com.trainingdummy.client.DummyEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = DummyScoreboardMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = DummyScoreboardMod.MODID, value = Dist.CLIENT)
public class DummyScoreboardClient {

    public DummyScoreboardClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The scoreboard dummy is rendered exactly like a normal training dummy (player skin,
        // armor, name tag) - reusing the base mod's renderer directly, no new one needed.
        event.registerEntityRenderer(ModEntities.SCOREBOARD_DUMMY.get(), DummyEntityRenderer::new);
    }
}

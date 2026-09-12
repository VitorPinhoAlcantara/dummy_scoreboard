package com.dummyscoreboard;

import com.mojang.logging.LogUtils;
import com.dummyscoreboard.config.CommonConfig;
import com.dummyscoreboard.network.OpenScoreboardScreenPayload;
import com.dummyscoreboard.registry.ModCreativeTabs;
import com.dummyscoreboard.registry.ModEntities;
import com.dummyscoreboard.registry.ModItems;
import com.trainingdummy.entity.DummyEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(DummyScoreboardMod.MODID)
public class DummyScoreboardMod {

    public static final String MODID = "dummyscoreboard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DummyScoreboardMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerPayloads);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SCOREBOARD_DUMMY.get(), DummyEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(OpenScoreboardScreenPayload.TYPE, OpenScoreboardScreenPayload.STREAM_CODEC,
                com.dummyscoreboard.client.ClientPayloadHandler::handleOpenScoreboardScreen);
    }
}

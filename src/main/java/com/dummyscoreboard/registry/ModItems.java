package com.dummyscoreboard.registry;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.item.ScoreboardDummySpawnItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DummyScoreboardMod.MODID);

    public static final DeferredItem<ScoreboardDummySpawnItem> SCOREBOARD_DUMMY_SPAWNER = ITEMS.registerItem(
            "scoreboard_dummy_spawner", properties -> new ScoreboardDummySpawnItem(properties.stacksTo(16)));

    private ModItems() {
    }
}

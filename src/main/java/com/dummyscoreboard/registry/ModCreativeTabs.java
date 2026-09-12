package com.dummyscoreboard.registry;

import com.dummyscoreboard.DummyScoreboardMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DummyScoreboardMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DUMMY_SCOREBOARD_TAB =
            CREATIVE_MODE_TABS.register("dummy_scoreboard", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dummyscoreboard"))
                    .icon(() -> ModItems.SCOREBOARD_DUMMY_SPAWNER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(ModItems.SCOREBOARD_DUMMY_SPAWNER.get()))
                    .build());

    private ModCreativeTabs() {
    }
}

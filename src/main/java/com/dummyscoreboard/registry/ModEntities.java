package com.dummyscoreboard.registry;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.entity.ScoreboardDummyEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DummyScoreboardMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ScoreboardDummyEntity>> SCOREBOARD_DUMMY =
            ENTITY_TYPES.register("scoreboard_dummy", () -> EntityType.Builder.of(ScoreboardDummyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(DummyScoreboardMod.MODID + ":scoreboard_dummy"));

    private ModEntities() {
    }
}

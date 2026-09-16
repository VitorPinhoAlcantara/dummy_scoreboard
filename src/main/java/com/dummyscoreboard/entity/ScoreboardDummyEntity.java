package com.dummyscoreboard.entity;

import com.dummyscoreboard.config.CommonConfig;
import com.dummyscoreboard.network.OpenScoreboardScreenPayload;
import com.dummyscoreboard.rank.LeaderboardCache;
import com.dummyscoreboard.registry.ModItems;
import com.trainingdummy.entity.DummyDisplayMetric;
import com.trainingdummy.entity.DummyEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Training Dummy variant whose right-click screen shows the cross-server "biggest hit" leaderboard
 * instead of the base mod's armor/curios/config screen. Everything else (health, lure bait,
 * attributes, rendering) is inherited as-is from {@link DummyEntity} - removal and the per-hit
 * damage metric are overridden below since the base behavior doesn't fit this variant.
 */
public class ScoreboardDummyEntity extends DummyEntity {

    public static final Component DEFAULT_NAME = Component.translatable("entity.dummyscoreboard.scoreboard_dummy");

    public ScoreboardDummyEntity(EntityType<? extends ScoreboardDummyEntity> type, Level level) {
        super(type, level);
        // Fixed, not player-configurable here (there's no config screen for this variant): the
        // leaderboard is about single hits, so DPS/Total readouts wouldn't mean anything.
        this.setDisplayMetric(DummyDisplayMetric.PER_HIT);
    }

    @Override
    public void openMenuFor(Player player) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new OpenScoreboardScreenPayload(
                this.getId(),
                LeaderboardCache.localEntries(),
                LeaderboardCache.globalEntries(),
                LeaderboardCache.globalAvailable(),
                CommonConfig.MODPACK_DISPLAY_NAME.get()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Player player && player.getMainHandItem().is(Items.STICK)) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK,
                    this.getSoundSource(), 1.0F, 1.0F);
            this.spawnAtLocation(new ItemStack(ModItems.SCOREBOARD_DUMMY_SPAWNER.get()));
            this.discard();
            return true;
        }
        return super.hurt(source, amount);
    }
}

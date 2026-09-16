package com.dummyscoreboard.item;

import com.dummyscoreboard.entity.ScoreboardDummyEntity;
import com.dummyscoreboard.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

public class ScoreboardDummySpawnItem extends Item {

    public ScoreboardDummySpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dummyscoreboard.scoreboard_dummy.tooltip.configure")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.dummyscoreboard.scoreboard_dummy.tooltip.remove")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos placePos = clickedFace == Direction.UP ? clickedPos.above() : clickedPos;

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ScoreboardDummyEntity dummy = ModEntities.SCOREBOARD_DUMMY.get().create(serverLevel);
        if (dummy == null) {
            return InteractionResult.FAIL;
        }

        double x = placePos.getX() + 0.5D;
        double y = placePos.getY();
        double z = placePos.getZ() + 0.5D;
        float facingYaw = context.getRotation() + 180.0F;
        dummy.moveTo(x, y, z, facingYaw, 0.0F);
        dummy.setYHeadRot(facingYaw);
        dummy.setYBodyRot(facingYaw);
        dummy.setCustomName(ScoreboardDummyEntity.DEFAULT_NAME);
        dummy.setCustomNameVisible(true);

        if (!serverLevel.noCollision(dummy, dummy.getBoundingBox())) {
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(dummy);
        serverLevel.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, placePos);

        if (context.getPlayer() != null) {
            context.getPlayer().awardStat(Stats.ITEM_USED.get(this));
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}

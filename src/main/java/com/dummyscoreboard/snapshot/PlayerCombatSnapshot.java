package com.dummyscoreboard.snapshot;

import com.dummyscoreboard.curios.PlayerCuriosCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trainingdummy.item.DummyCurioEntry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The audit "print" of a qualifying hit: everything about the attacking player at that moment,
 * kept for later inspection if a record is ever disputed. Only {@link #playerName()} and
 * {@link #damage()} are shown on the public leaderboard - the rest is audit-only.
 */
public record PlayerCombatSnapshot(
        String playerName,
        UUID playerUuid,
        float damage,
        long gameTime,
        List<ItemStack> inventory,
        List<ItemStack> armor,
        ItemStack mainHand,
        ItemStack offHand,
        List<DummyCurioEntry> curios,
        List<MobEffectInstance> effects,
        List<AttributeInstance.Packed> attributes
) {

    public static final Codec<PlayerCombatSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("player_name").forGetter(PlayerCombatSnapshot::playerName),
            // STRING_CODEC, not the default CODEC: the default encodes a UUID as a 4-int array (the
            // NBT-oriented convention), but this data round-trips through a plain JSON HTTP API to a
            // JS worker and gets stored as a SQL TEXT column - a readable string is what's needed there.
            UUIDUtil.STRING_CODEC.fieldOf("player_uuid").forGetter(PlayerCombatSnapshot::playerUuid),
            Codec.FLOAT.fieldOf("damage").forGetter(PlayerCombatSnapshot::damage),
            Codec.LONG.fieldOf("game_time").forGetter(PlayerCombatSnapshot::gameTime),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(PlayerCombatSnapshot::inventory),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("armor").forGetter(PlayerCombatSnapshot::armor),
            ItemStack.OPTIONAL_CODEC.fieldOf("main_hand").forGetter(PlayerCombatSnapshot::mainHand),
            ItemStack.OPTIONAL_CODEC.fieldOf("off_hand").forGetter(PlayerCombatSnapshot::offHand),
            DummyCurioEntry.CODEC.listOf().fieldOf("curios").forGetter(PlayerCombatSnapshot::curios),
            MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(PlayerCombatSnapshot::effects),
            AttributeInstance.Packed.LIST_CODEC.fieldOf("attributes").forGetter(PlayerCombatSnapshot::attributes)
    ).apply(instance, PlayerCombatSnapshot::new));

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static PlayerCombatSnapshot capture(ServerPlayer player, float damage) {
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                inventory.add(stack.copy());
            }
        }

        List<ItemStack> armor = new ArrayList<>(ARMOR_SLOTS.length);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armor.add(player.getItemBySlot(slot).copy());
        }

        List<DummyCurioEntry> curios = PlayerCuriosCompat.isLoaded()
                ? PlayerCuriosCompat.captureAll(player)
                : List.of();

        return new PlayerCombatSnapshot(
                player.getGameProfile().name(),
                player.getUUID(),
                damage,
                player.level().getGameTime(),
                inventory,
                armor,
                player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                player.getItemBySlot(EquipmentSlot.OFFHAND).copy(),
                curios,
                List.copyOf(player.getActiveEffects()),
                player.getAttributes().pack()
        );
    }
}

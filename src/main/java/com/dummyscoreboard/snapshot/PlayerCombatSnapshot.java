package com.dummyscoreboard.snapshot;

import com.dummyscoreboard.curios.PlayerCuriosCompat;
import com.dummyscoreboard.integrity.AttackIntegrity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trainingdummy.item.DummyCurioEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
        List<CapturedAttribute> attributes
) {

    /**
     * {@code id}/{@code base} for one of {@link AttackIntegrity#trackedAttributes()} - 1.21.1 has no
     * equivalent of 26.1.2's {@code AttributeInstance.Packed} (which also carries every modifier),
     * so this only captures what the anti-tamper check itself needs: base values for the known
     * combat attributes, not a full per-attribute modifier dump.
     */
    public record CapturedAttribute(String id, double base) {
        public static final Codec<CapturedAttribute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(CapturedAttribute::id),
                Codec.DOUBLE.fieldOf("base").forGetter(CapturedAttribute::base)
        ).apply(instance, CapturedAttribute::new));
    }

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
            CapturedAttribute.CODEC.listOf().fieldOf("attributes").forGetter(PlayerCombatSnapshot::attributes)
    ).apply(instance, PlayerCombatSnapshot::new));

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static PlayerCombatSnapshot capture(ServerPlayer player, float damage) {
        List<ItemStack> inventory = new ArrayList<>();
        // Pre-EntityEquipment Inventory: `items` is already just the 36 hotbar/main slots, with
        // armor/offhand tracked separately - no equivalent of 26.1.2's getNonEquipmentItems() needed.
        for (ItemStack stack : player.getInventory().items) {
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

        List<CapturedAttribute> attributes = new ArrayList<>();
        for (Holder<Attribute> attribute : AttackIntegrity.trackedAttributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                String id = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()).toString();
                attributes.add(new CapturedAttribute(id, instance.getBaseValue()));
            }
        }

        return new PlayerCombatSnapshot(
                player.getGameProfile().getName(),
                player.getUUID(),
                damage,
                player.level().getGameTime(),
                inventory,
                armor,
                player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                player.getItemBySlot(EquipmentSlot.OFFHAND).copy(),
                curios,
                List.copyOf(player.getActiveEffects()),
                attributes
        );
    }
}

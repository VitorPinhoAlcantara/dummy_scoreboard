package com.dummyscoreboard.integrity;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.Set;

/**
 * Catches the crudest form of leaderboard cheating: `/attribute ... base set` (or any other means
 * of writing an entity's attribute base directly) used to inflate a combat stat before hitting the
 * dummy. That command requires operator-level permission, so on a properly locked-down survival
 * server this shouldn't be reachable at all - this check exists as a safety net for admin mistakes
 * or a permission plugin misconfiguration, not as the primary defense.
 * <p>
 * Every mod snapshot we've captured so far (Apotheosis, Artifacts, Apothic Attributes) layers its
 * bonuses on as named {@link net.minecraft.world.entity.ai.attributes.AttributeModifier}s rather
 * than touching the base value - that's also what NeoForge's own docs recommend, specifically so
 * unrelated mods don't clobber each other's changes. A base that drifts from vanilla's own default
 * for a player is therefore a strong tamper signal rather than a likely false positive.
 */
public final class AttackIntegrity {

    private static final double EPSILON = 1.0E-4;

    // Vanilla's own default base value for a player. Mods add their bonuses as modifiers on top of
    // these - if a base itself no longer matches, something set it directly instead.
    private static final Map<Holder<Attribute>, Double> EXPECTED_BASE = Map.of(
            Attributes.ATTACK_DAMAGE, 1.0,
            Attributes.ATTACK_SPEED, 4.0,
            Attributes.MAX_HEALTH, 20.0,
            Attributes.ARMOR, 0.0,
            Attributes.ARMOR_TOUGHNESS, 0.0,
            Attributes.KNOCKBACK_RESISTANCE, 0.0,
            Attributes.LUCK, 0.0
    );

    /**
     * The combat attributes this check watches - also what {@link com.dummyscoreboard.snapshot.PlayerCombatSnapshot}
     * captures for audit, since 1.21.1 has no bulk "pack every attribute" API to fall back to
     * (unlike the 26.1.2 branch's {@code AttributeMap#pack()}); this is the same set anyway.
     */
    public static Set<Holder<Attribute>> trackedAttributes() {
        return EXPECTED_BASE.keySet();
    }

    public static boolean hasTamperedBase(ServerPlayer player) {
        for (Map.Entry<Holder<Attribute>, Double> expected : EXPECTED_BASE.entrySet()) {
            AttributeInstance instance = player.getAttribute(expected.getKey());
            if (instance != null && Math.abs(instance.getBaseValue() - expected.getValue()) > EPSILON) {
                return true;
            }
        }
        return false;
    }

    private AttackIntegrity() {
    }
}

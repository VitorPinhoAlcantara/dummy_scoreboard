package com.dummyscoreboard.curios;

import com.trainingdummy.item.DummyCurioEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Same isolation pattern as training_dummy's own CuriosCompat: every Curios API call lives behind
 * {@link #isLoaded()}, so this class only gets touched (and its imports only get classloaded) when
 * Curios is actually installed.
 */
public final class PlayerCuriosCompat {

    private static final String CURIOS_MODID = "curios";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(CURIOS_MODID);
    }

    public static List<DummyCurioEntry> captureAll(Player player) {
        List<DummyCurioEntry> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                IItemHandlerModifiable stacks = stacksHandler.getStacks();
                String identifier = stacksHandler.getIdentifier();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        result.add(new DummyCurioEntry(identifier, i, stack.copy()));
                    }
                }
            }
        });
        return result;
    }

    private PlayerCuriosCompat() {
    }
}

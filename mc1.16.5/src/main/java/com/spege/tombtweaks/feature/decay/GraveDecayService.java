package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.core.DecaySchedule;
import com.spege.tombtweaks.core.DecayVictim;
import com.spege.tombtweaks.core.ProtectionRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.tileentity.TileEntityPlayerGrave;

import java.util.ArrayList;
import java.util.List;

/** One tick of one grave. All decisions are made by core; this is only the execution. */
public final class GraveDecayService {

    private GraveDecayService() {
    }

    public static void tick(World level, BlockPos pos, TileEntityPlayerGrave grave) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!Config.INSTANCE.decayEnabled.get()) {
            return;
        }
        // On Tombstone 7.0.0 countTicks is a public int on TileEntityWritableGrave (saved and
        // loaded with the grave, incremented on both sides in tick, reset by resetDeathTime when
        // a later death merges into the grave). It widens losslessly to the long core takes.
        if (!DecaySchedule.shouldDecay((long) grave.countTicks,
                                       Config.INSTANCE.decayStartTicks.get(),
                                       Config.INSTANCE.decayIntervalTicks.get())) {
            return;
        }

        IItemHandler inventory = grave.getInventory();
        if (inventory == null) {
            return;
        }

        // ProtectionRules.of takes List<?> and skips null and non-Strings itself,
        // so the config values go straight in, with no casting.
        ProtectionRules rules = ProtectionRules.of(
                Config.INSTANCE.decayProtectedItems.get(),
                Config.INSTANCE.decayProtectedItemPrefixes.get(),
                Config.INSTANCE.decayProtectedEnchantments.get(),
                Config.INSTANCE.decayProtectedNbtStrings.get());

        List<Integer> unprotected = new ArrayList<>();
        List<Integer> guarded = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (rules.isProtected(StackViews.view(stack))) {
                guarded.add(slot);
            } else {
                unprotected.add(slot);
            }
        }

        int victim = DecayVictim.pick(unprotected, guarded,
                Config.INSTANCE.decayProtectedNeverDecay.get(),
                bound -> level.random.nextInt(bound));
        if (victim == DecayVictim.NOTHING) {
            return;
        }

        ItemStack lost = inventory.extractItem(victim, inventory.getStackInSlot(victim).getCount(), false);
        if (lost.isEmpty()) {
            return;
        }
        // The grave's inventory is a bare ItemStackHandler without onContentsChanged - extraction
        // alone does NOT mark the tile entity as changed. Without this the chunk may be saved with
        // the old grave state, while the dropped item (an entity, saved separately) survives a
        // restart: after loading it is both on the ground and back in the grave. Duplication.
        grave.setChanged();

        // History BEFORE the drop: InventoryHelper.dropItemStack consumes the stack it is given
        // (it splits it into item entities in a loop until it is empty), so afterwards what is
        // left is minecraft:air x0.
        DecayHistory.record(level, pos, grave.getOwnerId(), lost);
        InventoryHelper.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, lost);
    }
}

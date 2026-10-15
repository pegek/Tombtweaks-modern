package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.StackView;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** The only place where an ItemStack turns into something core understands. */
public final class StackViews {

    private static final String UNKNOWN = "minecraft:air";

    private StackViews() {
    }

    public static String idOf(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? UNKNOWN : id.toString();
    }

    public static String idOf(ItemStack stack) {
        return idOf(stack.getItem());
    }

    /**
     * The components hash must be stable across JVM restarts, because snapshots are persisted.
     * See {@link #componentsHash}.
     */
    public static ItemKey keyOf(ItemStack stack, HolderLookup.Provider registries) {
        return new ItemKey(idOf(stack), componentsHash(stack, registries));
    }

    /**
     * Hash of the stack's components, stable across restarts: computed from the NBT after
     * serialization, because {@code DataComponentPatch.hashCode()} depends on {@code Holder}s
     * with an identity hash. The serialized form names every holder by its registry id, and
     * {@code CompoundTag.hashCode()} is content-based (and independent of key order).
     *
     * <p>This is the same codec {@code ItemStack.save} uses for the stack's "components", so the
     * hash covers exactly what a grave persists; transient components are skipped by the codec.
     * The stack count is not part of the hash (percentLossOnDeath changes it in the grave).
     * A patch that fails to encode hashes to 0, like an empty one - matching then relies on the
     * fallback tier of {@code SlotPlan}, as it does whenever the grave normalises a stack.
     */
    private static int componentsHash(ItemStack stack, HolderLookup.Provider registries) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return 0;
        }
        return DataComponentPatch.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch)
                .result()
                .map(Tag::hashCode)
                .orElse(0);
    }

    public static StackView view(final ItemStack stack) {
        return new StackView() {
            @Override
            public String itemId() {
                return idOf(stack);
            }

            @Override
            public Set<String> enchantmentIds() {
                // The stored enchantments for an enchanted book, the applied ones otherwise -
                // the same split 1.20.1's EnchantmentHelper.getEnchantments made, so a protected
                // enchantment protects a book that carries it, as before.
                ItemEnchantments found = EnchantmentHelper.getEnchantmentsForCrafting(stack);
                if (found.isEmpty()) {
                    return Collections.emptySet();
                }
                Set<String> ids = new HashSet<>();
                for (Holder<Enchantment> enchantment : found.keySet()) {
                    Optional<ResourceKey<Enchantment>> key = enchantment.unwrapKey();
                    if (key.isPresent()) {
                        ids.add(key.get().location().toString());
                    }
                }
                return ids;
            }

            @Override
            public String nbtString(String key) {
                CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                if (data == null) {
                    return null;
                }
                CompoundTag tag = data.copyTag();
                if (!tag.contains(key, Tag.TAG_STRING)) {
                    return null;
                }
                return tag.getString(key);
            }
        };
    }
}

package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.StackView;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The only place where an ItemStack turns into something core understands. */
public final class StackViews {

    private static final String UNKNOWN = "minecraft:air";

    private StackViews() {
    }

    public static String idOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? UNKNOWN : id.toString();
    }

    public static String idOf(ItemStack stack) {
        return idOf(stack.getItem());
    }

    /**
     * The NBT hash must be stable across JVM restarts, because snapshots are persisted.
     * {@code CompoundNBT.hashCode()} is computed from the content, so it is.
     */
    public static ItemKey keyOf(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return new ItemKey(idOf(stack), tag == null ? 0 : tag.hashCode());
    }

    public static StackView view(final ItemStack stack) {
        return new StackView() {
            @Override
            public String itemId() {
                return idOf(stack);
            }

            /**
             * On 1.16.5 {@code EnchantmentHelper.getEnchantments} reads an enchanted book's
             * {@code StoredEnchantments} (through {@code EnchantedBookItem.getEnchantments}) and
             * every other stack's {@code Enchantments} - so a protected enchantment protects
             * the book that carries it too.
             */
            @Override
            public Set<String> enchantmentIds() {
                Map<Enchantment, Integer> found = EnchantmentHelper.getEnchantments(stack);
                if (found.isEmpty()) {
                    return Collections.emptySet();
                }
                Set<String> ids = new HashSet<>();
                for (Enchantment enchantment : found.keySet()) {
                    ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return ids;
            }

            @Override
            public String nbtString(String key) {
                CompoundNBT tag = stack.getTag();
                if (tag == null || !tag.contains(key, Constants.NBT.TAG_STRING)) {
                    return null;
                }
                return tag.getString(key);
            }
        };
    }
}

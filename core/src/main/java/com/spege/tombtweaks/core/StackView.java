package com.spege.tombtweaks.core;

import java.util.Set;

/**
 * What core knows about one stack. Implemented in the platform layer, so that core
 * does not have to know ItemStack.
 */
public interface StackView {

    /** Registry name, e.g. "minecraft:diamond_sword". Never null. */
    String itemId();

    /** Registry names of the stack's enchantments. An empty set when there are none. */
    Set<String> enchantmentIds();

    /** Value of a top-level string NBT key, or null when it is absent. */
    String nbtString(String key);
}

package com.spege.tombtweaks.platform;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Four categories, file config/tombtweaks-common.toml.
 *
 * <p>No master switch: with features that each have their own toggle it was only
 * extra state to get stuck in.
 *
 * <p>The default protection lists are EMPTY. The ones from 1.12.2 belong to a specific pack,
 * and a public mod has no right to assume them.
 *
 * <p>Lists use {@code defineListAllowEmpty} with a new-element supplier (the overload without the
 * supplier is deprecated in NeoForge 21.1; the supplier only feeds a config UI's "add" button).
 * {@code defineList} would declare the lists non-empty: a config UI would enforce that, and a
 * list holding only invalid entries would be corrected back to its default - for {@code books}
 * that would silently restore both cooldowns.
 */
public final class Config {

    public final ModConfigSpec.BooleanValue restoreEnabled;
    public final ModConfigSpec.BooleanValue restoreDebugLogging;

    public final ModConfigSpec.BooleanValue decayEnabled;
    public final ModConfigSpec.IntValue decayStartTicks;
    public final ModConfigSpec.IntValue decayIntervalTicks;
    public final ModConfigSpec.IntValue decayMaxHistory;
    public final ModConfigSpec.ConfigValue<List<? extends String>> decayProtectedItems;
    public final ModConfigSpec.ConfigValue<List<? extends String>> decayProtectedItemPrefixes;
    public final ModConfigSpec.ConfigValue<List<? extends String>> decayProtectedEnchantments;
    public final ModConfigSpec.ConfigValue<List<? extends String>> decayProtectedNbtStrings;
    public final ModConfigSpec.BooleanValue decayProtectedNeverDecay;

    public final ModConfigSpec.BooleanValue cooldownEnabled;
    public final ModConfigSpec.ConfigValue<List<? extends String>> cooldownBooks;

    public final ModConfigSpec.BooleanValue graveKeysSurviveDeath;

    private Config(ModConfigSpec.Builder builder) {
        builder.comment("Items return to the slots they came from when a grave is recovered.")
               .push("restore");
        restoreEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: Tombstone's own per-player 'reverse inventory sorting' preference",
                         "runs AFTER this and rewrites the whole inventory order. If a player has it",
                         "on, their restored layout will come back reversed.")
                .define("enabled", true);
        restoreDebugLogging = builder
                .comment("Log what each death recorded and what each recovered grave put back.")
                .define("debugLogging", false);
        builder.pop();

        builder.comment("An unvisited grave slowly drops its contents on the ground.")
               .push("decay");
        decayEnabled = builder
                .comment("Off by default. Read live - no restart needed.")
                .define("enabled", false);
        decayStartTicks = builder
                .comment("Ticks the grave must have been LOADED before decay starts.",
                         "This is Tombstone's own countTicks, so it does not advance while the",
                         "chunk is unloaded. A later death merged into the same grave resets it.",
                         "Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
                .defineInRange("startTicks", 6000, 0, Integer.MAX_VALUE);
        decayIntervalTicks = builder
                .comment("Ticks between each item removal. 1200 = 60 seconds. 0 disables decay.")
                .defineInRange("intervalTicks", 1200, 0, Integer.MAX_VALUE);
        decayMaxHistory = builder
                .comment("Max number of decay records kept per player (item id + count each).")
                .defineInRange("maxHistory", 10, 0, 100);
        decayProtectedItems = builder
                .comment("Full registry names that decay must not eat, e.g. \"mymod:relic_blade\".",
                         "An empty list protects NOTHING.")
                .defineListAllowEmpty("protectedItems", Collections.<String>emptyList(), () -> "", o -> o instanceof String);
        decayProtectedItemPrefixes = builder
                .comment("Registry-name prefixes, e.g. \"mymod:relic_\".")
                .defineListAllowEmpty("protectedItemPrefixes", Collections.<String>emptyList(), () -> "", o -> o instanceof String);
        decayProtectedEnchantments = builder
                .comment("Registry names of enchantments that protect whatever carries them.")
                .defineListAllowEmpty("protectedEnchantments", Collections.<String>emptyList(), () -> "", o -> o instanceof String);
        decayProtectedNbtStrings = builder
                .comment("Top-level string keys of the item's custom_data component, as \"key=value\".",
                         "A malformed entry is ignored.")
                .defineListAllowEmpty("protectedNbtStrings", Collections.<String>emptyList(), () -> "", o -> o instanceof String);
        decayProtectedNeverDecay = builder
                .comment("true  = protection is an exemption; a grave holding only protected items stops decaying.",
                         "false = protection is only an ordering; protected items are eaten last.")
                .define("protectedNeverDecay", true);
        builder.pop();

        builder.comment("Cooldowns for Tombstone's magic books.")
               .push("cooldown");
        cooldownEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: the mixins are applied regardless of this flag; it is an early return",
                         "inside the handler, not a gate on mixin application.")
                .define("enabled", true);
        cooldownBooks = builder
                .comment("One entry per book, as \"registry_name;minutes\".",
                         "Minutes are clamped to 720 (12 h). 0 or a malformed entry means no cooldown.",
                         "Works for any of Tombstone's seven tombstone:book_of_* items, not just the",
                         "two below. Other soul-consuming items (scrolls, tablets) are ignored.")
                .defineListAllowEmpty("books", Arrays.asList(
                        "tombstone:book_of_disenchantment;6",
                        "tombstone:book_of_magic_impregnation;6"), () -> "", o -> o instanceof String);
        builder.pop();

        builder.comment("Tombstone's grave keys.")
               .push("graveKeys");
        graveKeysSurviveDeath = builder
                .comment("Whether the grave keys you are carrying stay with you when you die.",
                         "Tombstone treats every grave key as soulbound no matter what: each key carries",
                         "Tombstone's tb_soulbound data component, set to true by default, with or without",
                         "the Soulbound enchantment.",
                         "true  = Tombstone's behaviour: carried keys survive every death.",
                         "false = keys are immune to Soulbound: carried keys go into the new grave like any",
                         "        other item (even with the enchantment on them). The key to the grave you",
                         "        just made is still handed to you as usual.",
                         "On 1.21.1 an anvil cannot put Soulbound on a key anyway: Tombstone's own",
                         "supportsEnchantment hook refuses Soulbound on any stack already marked tb_soulbound.",
                         "Read live - no restart needed.")
                .define("survivesDeath", true);
        builder.pop();
    }

    public static final Config INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}

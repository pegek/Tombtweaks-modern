# In-game verification — tombtweaks 0.3.0 for Minecraft 1.21.1 (NeoForge)

Run this on the **built jar from `mc1.21.1/build/libs/` dropped into a real NeoForge 21.1 instance** with Corail Tombstone 9.5.x — not in `./gradlew runClient`.

Tick each item off. Anything that does not match "Expected" is material for a fix.

The "disabled enchantments in loot and trades" section of the 1.20.1 list does **not** apply here: on 1.21.1 Tombstone's enchantments are data-driven and Tombstone has no switch that disables them (see the README).

## 0. Startup

- [ ] The game starts; the mod list shows `tombtweaks` version `0.3.0`; the log has `[TombTweaks] 0.3.0 loading`.
- [ ] The log has **four** `Mixing … from mixins.tombtweaks.json into …` lines — for `BlockEntityPlayerGrave`, `ItemBook`, `BlockDecorativeGrave` and `DeathHandler`. Mixins apply lazily: a line only appears once its target class first loads (e.g. after a grave is placed or a book is used), so its absence before that is not an error. There is no line for `TombstoneEnchantment` — that mixin is not ported to 1.21.1.
- [ ] The log has **none** of `InvalidInjectionException`, `could not find any targets`, `Scanned 0 target(s)`, or any refmap message.
- [ ] `config/tombtweaks-common.toml` has four tables: `[restore]`, `[decay]`, `[cooldown]`, `[graveKeys]`.

## 1. Restore

Set `restore.debugLogging = true`, `/gamerule keepInventory false`.

- [ ] A distinctive layout (sword in slot 2, bread in slot 9, something in the offhand, full armour, several items in the main inventory, one empty slot in the middle of the hotbar). `/kill`. The log shows `recorded N seats`.
- [ ] Recover the grave. **Expected:** everything is back in its slot, armour worn, offhand in place, the empty slot still empty. The log shows `seated X of N`.
- [ ] An enchanted item and an item with custom data (`/give @s minecraft:stick[custom_data={key:"value"}]`) also return to their exact slots — this checks the data-component hash behind `ItemKey`.
- [ ] Die, after respawning pick something up into a slot that was occupied before the death, recover the grave. **Expected:** the occupied slot is skipped, that item lands elsewhere (Tombstone's path), nothing is lost or duplicated.
- [ ] Die twice within 20 blocks without collecting the grave (merge). **Expected:** items from the last death are in their slots, items from the first death go through the standard path, nothing is lost.
- [ ] Another player opens your grave with your key. **Expected:** Tombstone's standard behaviour, no seating.
- [ ] Quit and reload between the death and the recovery. **Expected:** seating still works (the snapshot survives in `PlayerPersisted`).
- [ ] **Reverse inventory sorting:** check Tombstone's default for this preference, then turn it on and repeat the first test. Note the result in the README ("Known limitations").

## 2. Decay

Set `decay.enabled = true`, `startTicks = 100`, `intervalTicks = 40`.

- [ ] Die with a full inventory, stand by the grave, do not open it. **Expected:** after about 5 s, one stack drops above the grave every ~2 s.
- [ ] Protection by item id: add `"minecraft:diamond_sword"` to `protectedItems`. **Expected:** with `protectedNeverDecay = true` it never drops; with `false` it drops last.
- [ ] Protection by enchantment: add an enchantment id to `protectedEnchantments` and put an **enchanted book** carrying it in the grave. **Expected:** the book is protected (checks the `STORED_ENCHANTMENTS` path).
- [ ] Protection by custom data: add `key=value` to `protectedNbtStrings` and use an item with that `custom_data`. **Expected:** it is protected.
- [ ] 🚨 **Restart mid-decay:** let 2–3 items drop, then quit to the menu / stop the server, then reload. **Expected:** the dropped items are on the ground and **not** back in the grave (the `setChanged()` duplication guard).
- [ ] After reloading, decay continues where it stopped.
- [ ] History: with the owner online, `PlayerPersisted.tombtweaks_decay_history` holds entries with a real `item` id and `count` (never `minecraft:air`). With the owner offline, only a DEBUG log line.

## 3. Book cooldowns

- [ ] A Book of Disenchantment on a decorative grave with a soul — works.
- [ ] A second book of the same type straight away — refused; the action bar shows "This book is still recovering — N s left." (Note whether the message appears more than once per click.)
- [ ] **A stack of exactly one book** still starts the cooldown.
- [ ] The cooldown survives quit-to-menu and death/respawn.
- [ ] `books = ["tombstone:book_of_disenchantment;0"]` removes the cooldown immediately, without a restart.
- [ ] A non-book soul consumer (scroll, tablet) on a decorative grave starts no cooldown and is never blocked.

## 4. Grave keys

- [ ] Default (`graveKeys.survivesDeath = true`): die carrying a key to an old grave. **Expected:** you still have it after respawning (Tombstone's behaviour).
- [ ] `survivesDeath = false`, repeat. **Expected:** the old key lies in the **new** grave; you still get the key to the new grave as usual.
- [ ] Switching `survivesDeath` live, without a restart, changes the behaviour on the next death.
- [ ] (Optional) Anvil: key + Soulbound book gives no result whatever the setting — that is vanilla's `supported_items` check, not this mod.

## 5. Dedicated server

- [ ] The same jar starts cleanly on a dedicated NeoForge server, and restore, decay, cooldowns and keys work for a connected player.

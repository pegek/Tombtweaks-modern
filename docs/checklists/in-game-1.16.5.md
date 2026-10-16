# In-game verification — tombtweaks 0.3.0 for Minecraft 1.16.5 (Forge 36)

Run this on the **reobfuscated jar from `mc1.16.5/build/libs/` dropped into a real Forge 36 instance** with Corail Tombstone 7.0.0, not in `./gradlew runClient`. Dev uses official names and hides exactly the bug (a missing or wrong refmap) that is the most dangerous one for this mod.

Tick each item off. Anything that does not match "Expected" is material for a fix.

## 0. Startup

- [ ] The game starts; the mod list shows `tombtweaks` version `0.3.0`; the log has `[TombTweaks] 0.3.0 loading`.
- [ ] `logs/debug.log` (not `latest.log` - Mixin logs these lines at DEBUG) has **five** `Mixing … from mixins.tombtweaks.json into …` lines — for `TileEntityWritableGrave`, `ItemBook`, `BlockDecorativeGrave`, `DeathHandler` and `TombstoneEnchantment`. Mixins apply lazily: a line only appears once its target class first loads (e.g. after a grave is placed or a book is used), so its absence before that is not an error.
- [ ] The log has **none** of `InvalidInjectionException`, `could not find any targets`, `Scanned 0 target(s)`, `Reference map 'tombtweaks.refmap.json' … could not be read`.
- [ ] `config/tombtweaks-common.toml` has four tables: `[restore]`, `[decay]`, `[cooldown]`, `[graveKeys]`; `decay.enabled = false`, the four protection lists empty, `cooldown.books` with two entries of 6 min each.

## 1. Restore

Set `restore.debugLogging = true`, `/gamerule keepInventory false`.

- [ ] A distinctive layout (sword in slot 2, bread in slot 9, something in the offhand, full armour, several items in the main inventory, one empty slot in the middle of the hotbar). `/kill`. The log shows `recorded N seats`.
- [ ] Recover the grave. **Expected:** everything is back in its slot, armour worn, offhand in place, the empty slot still empty. The log shows `seated X of N`.
- [ ] An enchanted item and a renamed item also return to their exact slots (checks the NBT hash behind `ItemKey`).
- [ ] Die, after respawning pick something up into a slot that was occupied before the death, recover the grave. **Expected:** the occupied slot is skipped, that item lands elsewhere (Tombstone's path), nothing is lost or duplicated.
- [ ] Die twice within 20 blocks without collecting the grave (merge). **Expected:** items from the last death are in their slots, items from the first death go through the standard path, nothing is lost.
- [ ] Another player opens your grave with your key. **Expected:** Tombstone's standard behaviour, no seating.
- [ ] Quit and reload between the death and the recovery. **Expected:** seating still works (the snapshot survives in `PlayerPersisted`).
- [ ] `restore.enabled = false` → standard behaviour. `keepInventory true` → no `recorded` in the log.
- [ ] **Reverse inventory sorting:** check Tombstone's default for this preference, then turn it on and repeat the first test. Note the result in the README ("Known limitations").

## 2. Decay

Set `decay.enabled = true`, `startTicks = 100`, `intervalTicks = 40`.

- [ ] Die with a full inventory, stand by the grave, do not open it. **Expected:** after about 5 s, one stack drops above the grave every ~2 s.
- [ ] A decorative grave (not a player's grave) never drops anything.
- [ ] Protection by item id: add `"minecraft:diamond_sword"` to `protectedItems`. **Expected:** with `protectedNeverDecay = true` it never drops; with `false` it drops last.
- [ ] Protection by enchantment: add an enchantment id to `protectedEnchantments` and put an **enchanted book** carrying it in the grave. **Expected:** the book is protected.
- [ ] 🚨 **Restart mid-decay:** let 2–3 items drop, then quit to the menu / stop the server, then reload. **Expected:** the dropped items are on the ground and **not** back in the grave (the `setChanged()` duplication guard).
- [ ] After reloading, decay continues where it stopped.
- [ ] History: with the owner online, `PlayerPersisted.tombtweaks_decay_history` holds entries with a real `item` id and `count` (never `minecraft:air`). With the owner offline, only a DEBUG log line.

## 3. Book cooldowns

Tombstone 7.0.0 has four books: disenchantment, magic impregnation, recycling, repairing.

- [ ] A Book of Disenchantment on a decorative grave with a soul — works.
- [ ] A second book of the same type straight away — refused; the action bar shows "This book is still recovering — N s left." Tombstone also prints its own "not allowed" line in chat — that is expected. (Note whether the action-bar message appears more than once per click.)
- [ ] **A stack of exactly one book** still starts the cooldown.
- [ ] The cooldown survives quit-to-menu and death/respawn.
- [ ] `books = ["tombstone:book_of_disenchantment;0"]` removes the cooldown immediately, without a restart.
- [ ] A non-book soul consumer (scroll, tablet) on a decorative grave starts no cooldown and is never blocked.

## 4. Grave keys

- [ ] Default (`graveKeys.survivesDeath = true`): die carrying a key to an old grave. **Expected:** you still have it after respawning (Tombstone's behaviour).
- [ ] `survivesDeath = false`, repeat. **Expected:** the old key lies in the **new** grave; you still get the key to the new grave as usual.
- [ ] The same with a key that had Soulbound applied earlier (under `true`). **Expected:** under `false` it goes into the grave too.
- [ ] Switching `survivesDeath` live, without a restart, changes the behaviour on the next death.
- [ ] Anvil, **in survival** (creative skips the anvil's applicability check): key + Soulbound book under `false` — no result. Under `true` — works as in Tombstone.

## 5. Tombstone's disabled enchantments

- [ ] In Tombstone's config, disable Soulbound (`enableEnchantmentSoulbound = false`).
- [ ] Generate a lot of loot with `enchant_randomly` books (e.g. `/loot spawn ~ ~ ~ loot minecraft:chests/stronghold_library` several dozen times). **Expected:** no book with Soulbound.
- [ ] New librarians (reroll the trades several dozen times). **Expected:** no book with Soulbound among the offers.
- [ ] Control: enable Soulbound again — books with it show up again (the fix does not cut out enabled enchantments).

## 6. Dedicated server

- [ ] The same jar starts cleanly on a dedicated Forge 36 server, and restore, decay, cooldowns and keys work for a connected player.

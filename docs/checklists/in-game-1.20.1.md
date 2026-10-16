# In-game verification — tombtweaks 0.2.0

Do this on the **reobfuscated jar from `build/libs/` dropped into a real instance** (Forge 47 + Tombstone 9.1.4), not in `./gradlew runClient`. Dev uses official names and hides exactly the bug (a missing refmap) that is the most dangerous one for this mod.

Mark the result next to each item. Anything that comes out different from "Expected" is material for a fix.

## 0. Startup

- [ ] The game starts, `tombtweaks` appears in the mod list with version `0.2.0`.
- [ ] The log has the line `[TombTweaks] 0.2.0 loading`.
- [ ] `logs/debug.log` (not `latest.log` - Mixin logs these lines at DEBUG) has **five** lines `Mixing … from mixins.tombtweaks.json into …` — for `BlockEntityPlayerGrave`, `ItemBook`, `BlockDecorativeGrave`, `DeathHandler` and `TombstoneEnchantment`. Mixins load lazily: the line only appears when the target class is first loaded (e.g. after placing a grave / using a book), so its absence before that moment is not an error.
- [ ] The log has **none** of: `InvalidInjectionException`, `could not find any targets`, `Reference map 'tombtweaks.refmap.json' … could not be read`.
- [ ] `config/tombtweaks-common.toml` was created with three tables `[restore]`, `[decay]`, `[cooldown]`; `decay.enabled = false`, the four protection lists empty, `cooldown.books` with two entries of 6 min each.

## 1. Restore

Set `restore.debugLogging = true`, `/gamerule keepInventory false`.

- [ ] A distinctive layout (a sword in slot 2, bread in 9, something in the offhand, full armour, a few things in the main inventory, one empty slot in the middle of the hotbar). `/kill`. The log has `recorded N seats`.
- [ ] Recover the grave. **Expected:** everything in its place, armour equipped, offhand in place, the empty slot still empty. The log has `seated X of N`.
- [ ] Die, after respawning pick something up into a slot that held something before death, recover the grave. **Expected:** the occupied slot is skipped, that item goes elsewhere (the Tombstone path), nothing is lost, nothing is duplicated.
- [ ] Die twice within 20 blocks without collecting the grave (merge). **Expected:** items from the latest death in their places, from the first one — via the standard path, nothing is lost.
- [ ] A second player (or a second account) opens your grave with a key. **Expected:** standard Tombstone behaviour, no seating.
- [ ] `restore.enabled = false` → standard behaviour. `keepInventory true` → no `recorded` in the log.
- [ ] **Reverse inventory sorting:** check the default value in Tombstone's settings, then enable it and repeat the first test. Put the result in the README (the "Known limitations" section).

## 2. Decay

Set `decay.enabled = true`, `startTicks = 100`, `intervalTicks = 40`.

- [ ] Die with a full inventory, stand by the grave, do not open it. **Expected:** after ~5 s, one stack drops above the grave every ~2 s.
- [ ] Add `"minecraft:diamond_sword"` to `protectedItems`, repeat. **Expected:** the sword stays to the end; with `protectedNeverDecay = true` it stays forever, with `false` it drops last.
- [ ] 🚨 **Restart in the middle of decay:** let 2–3 items drop, stop the server immediately (single player: quit to the menu), load the world. **Expected:** the dropped items lie on the ground and are **not back in the grave**. (This is the duplication that `setChanged()` protects against.)
- [ ] After loading, decay continues from where it stopped.
- [ ] History: with the owner online, their player data contains `tombtweaks_decay_history` with entries of `item` + `count` (not `minecraft:air`!).

## 3. Cooldown

- [ ] Book of Disenchantment on a decorative grave with a soul — works.
- [ ] Immediately a second book of the same type — refused, the action bar says "This book is still recovering — N s left." (Tombstone adds its own message in chat — that is known.)
- [ ] **A stack consisting of a single book** — the cooldown starts too.
- [ ] Quit to the menu and come back — the cooldown keeps running. Death — the cooldown keeps running.
- [ ] `books = ["tombstone:book_of_disenchantment;0"]` — the cooldown disappears immediately, without a restart.

## 3a. Grave keys (0.2.0)

- [ ] By default (`graveKeys.survivesDeath = true`): die with a key to an old grave in the inventory — after respawning the key is still with you (Tombstone's behaviour).
- [ ] `survivesDeath = false`, repeat. **Expected:** the old key lies in the **new** grave, and you get the key to the new grave as always.
- [ ] The same with a key that previously had Soulbound applied (e.g. under `true`). **Expected:** under `false` it goes into the grave too.
- [ ] Anvil: a key + a book with Soulbound under `false` — no result. Under `true` — works as in Tombstone.
- [ ] `logs/debug.log` has the line `Mixing MixinDeathHandlerKeys … into ovh.corail.tombstone.helper.DeathHandler` and `Mixing MixinTombstoneEnchantment … into ovh.corail.tombstone.enchantment.TombstoneEnchantment`.

## 3b. Tombstone's disabled enchantments (0.2.0)

- [ ] In Tombstone's config, disable Soulbound (`enableEnchantmentSoulbound = false`).
- [ ] Generate a lot of loot with `enchant_randomly` books (e.g. `/loot spawn ~ ~ ~ loot minecraft:chests/stronghold_library` several dozen times). **Expected:** no book with Soulbound.
- [ ] New librarians (reroll the trades several dozen times). **Expected:** no book with Soulbound among the offers.
- [ ] Control: enable Soulbound again — books with it show up again (the fix does not cut out enabled enchantments).

## 4. Dedicated server

- [ ] The same jar on a dedicated server starts without errors (checked statically: zero references to client classes), and item 1 works for a player on the server.

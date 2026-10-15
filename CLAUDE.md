# CLAUDE.md

Wiedza projektu dla Claude Code. Ładuje się w każdej sesji otwartej w tym folderze — to jest „pamięć" tego projektu, nie pamięć sesji w `modDev`.

## Projekt

**TombTweaks (modern)** — addon do [Corail Tombstone](https://www.curseforge.com/minecraft/mc-mods/corail-tombstone) dla Minecrafta **1.16.5, 1.20.1 i 1.21.1**. modid `tombtweaks`, pakiet `com.spege.tombtweaks`. Repo: `github.com/pegek/Tombtweaks-modern`.

`tombtweaks` dla **1.12.2** to osobna linia w repo `E:\Isuth\modDev` (moduł `tombtweaks/`, FG3, bez refmapy, inne funkcje). Nic stamtąd nie obowiązuje tutaj automatycznie — w szczególności zasada `-proc:none` / „bez refmapy" z tamtego `CLAUDE.md` jest tu **odwrotna**.

Funkcje (stan na 0.2.0 dla 1.20.1; 1.21.1 i 1.16.5 w toku — macierz w `docs/specs/2026-10-16-multiversion-design.md`):
- `[restore]` — odzyskany grób oddaje przedmioty na te same sloty;
- `[decay]` — nieodwiedzony grób gubi zawartość (domyślnie wyłączone);
- `[cooldown]` — cooldowny ksiąg Tombstone'a;
- `[graveKeys] survivesDeath` — klucze odporne na Soulbound;
- zawsze: enchanty wyłączone w configu Tombstone'a znikają z lootu i handlu (tylko tam, gdzie Tombstone ma taki przełącznik — nie na 1.21.1).

## Układ i budowanie

```
core/        WSPÓLNY kod decyzyjny — Java 8, zero typów MC/Forge/NeoForge/Tombstone, JUnit 5
mc1.16.5/    Forge 36,  ForgeGradle 5, Gradle 7, Java 8
mc1.20.1/    Forge 47,  ForgeGradle 6, Gradle 8.7, Java 17
mc1.21.1/    NeoForge 21.1, ModDevGradle, Gradle 8, Java 21
docs/        specs/, plans/, checklists/in-game-<mc>.md
```

Każdy `mcX/` to **niezależny build z własnym wrapperem**; buduje się go z jego katalogu:

```bash
cd mc1.20.1 && ./gradlew check build     # testy core + platform, straże, jar w build/libs/
```

Wszystkie buildy dołączają `../core/src/{main,test}/java` przez `sourceSets.*.java.srcDir` — **nie kopiuj `core` do buildów**. Testy `core` lecą w każdym buildzie, czyli na JVM 8, 17 i 21.

JDK: domyślne `JAVA_HOME` na tej maszynie to **JDK 8** i Gradle na nim chodzi. Kompilację robi toolchain (foojay pobiera 17/21 sam). Ręcznie rejestrowane zadanie `JavaCompile` **nie dziedziczy** toolchaina — trzeba podać `javaCompiler = javaToolchains.compilerFor {…}`, inaczej `--release` jest nieznaną flagą (tak jest w `compileCoreJava8`).

## Twarde zasady

1. **`core` = składnia i API Javy 8, zero typów obcych.** Pilnują tego mechanicznie `CoreHasNoForeignTypesTest` (skan skompilowanych klas na `net/minecraft`, `net/minecraftforge`, `net/neoforged`, `ovh/corail`) i `compileCoreJava8` (`--release 8`). Bez rekordów, `var`, `List.of`, switch-expression. Testy mogą używać nowszej Javy.
2. **Buildy Forge (1.16.5, 1.20.1): refmapa jest obowiązkowa.** Nigdy `-proc:none`. MixinGradle 0.7 **nie dodaje procesora sam** — potrzebne jawne `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`. Bez tego build jest zielony i cichy, a jar nie ma refmapy → crash w prawdziwej instancji, którego `runClient` nie pokaże (dev ma nazwy oficjalne). **Dowód poprawności to wpis w `tombtweaks.refmap.json` w zbudowanym jarze**, nie cisza procesora.
3. **NeoForge 1.21.1 ma w runtime nazwy oficjalne Mojanga** — bez SRG i bez reobfuskacji, więc refmapa nie jest potrzebna do mapowania nazw MC.
4. **`fg.deobf(files(...))` nie działa na ForgeGradle 6** — po cichu podsuwa jar zaobfuskowany, z jednym ostrzeżeniem w logu konfiguracji. Zależności z CurseMaven (`curse.maven:<slug>-<projectId>:<fileId>`).
5. **Jarów Tombstone'a ani innych modów nie commitujemy.** Licencja „All rights reserved". Źródło: CurseMaven (Tombstone = projekt 243707).
6. **Cele mixinów potwierdzaj `javap -p -c`, przypisując wywołanie do metody, w której leży** — `grep -A` przelewa się przez granice metod. Redirect wymaga dokładnie jednego wywołania celu w metodzie; policz je.
7. **Zmiana wersji Tombstone'a** = porównanie `javap -p` wszystkich klas-celów (mixiny, eventy, pola) między starą a nową wersją + liczba wywołań w metodach z redirectami. Wzorzec: 9.1.4 → 9.1.5 (identyczne). Rozpakowuj jary do świeżego katalogu — pliki w scratchpadzie bywają czyszczone i stare porównanie potrafi po cichu porównać pustkę z treścią.
8. **Mixin, który DODAJE metodę nadpisującą metodę MC** (np. `isDiscoverable` w `MixinTombstoneEnchantment`), musi dziedziczyć po tej klasie MC — wtedy reobfuskacja zamienia nazwę na SRG. Sprawdź w zbudowanym jarze `javap`: na 1.20.1 metody mają się nazywać `m_6592_` / `m_6594_`.
9. **Testy w grze robi autor, na zbudowanym jarze w prawdziwej instancji** — nie w `runClient`. Listy: `docs/checklists/in-game-<mc>.md`. Agenci nie uruchamiają `runClient`/`runServer` (blokują i otwierają okno).
10. Zakres Tombstone'a w `mods.toml` przypięty do wersji zweryfikowanych (np. `[9.1.4,9.2)`), bo mixiny są `required: true` + `defaultRequire: 1` — lepiej, żeby Forge odmówił startu z czytelnym komunikatem, niż żeby gra wywaliła się przy ładowaniu klasy.

## Niezmienniki funkcji

- **Restore: najgorszy przypadek = zachowanie Tombstone'a**, nigdy zgubiony ani zdublowany przedmiot. Gwarantuje to jedno: przedmiot jest wstawiany wyłącznie do pustego slotu, sprawdzonego ponownie tuż przed wyjęciem z grobu. `SlotPlan.NO_SEAT` sam niczego nie gwarantuje.
- Restore działa **tylko dla właściciela grobu** (`getPlayer()` to ten, kto *otwiera* grób — także posiadacz klucza). Porównanie po nazwie z `getOwnerName()`, nie przez `isOwner` (zwraca `true` dla grobu bez `ownerId`).
- Tombstone **scala groby**: druga śmierć w promieniu 20 bloków od nieodebranego grobu dokłada do niego przedmioty i zeruje `deathDate` oraz `countTicks`. Stąd dwuprzebiegowe `SlotPlan.assign` po całym grobie.
- Decay: inwentarz grobu to gołe `ItemStackHandler` bez `onContentsChanged` — **po wyjęciu obowiązkowe `grave.setChanged()`**, inaczej po restarcie przedmiot jest i na ziemi, i w grobie. Historia zapisywana **przed** `Containers.dropItemStack`, który zużywa stack.
- Cooldown kluczowany po **`Item` księgi**, nie po stacku (księga się zużywa, pusty stack raportuje `minecraft:air`).
- Lista ochrony w decay: **pusta lista nie chroni niczego.**
- Config czytany na bieżąco; flagi nie bramkują mixinów (brak `IMixinConfigPlugin`) — komentarze mówią „read live".

## Ustalenia o Tombstonie (bajtkod)

| | 1.16.5 / 7.0.0 | 1.20.1 / 9.1.4–9.1.5 | 1.21.1 / 9.5.6 (NeoForge) |
|---|---|---|---|
| Restore | `RestoreInventoryEvent` | `RestoreInventoryEvent` na `MinecraftForge.EVENT_BUS`, po loss-on-death, przed auto-equipem | ten sam event, `IItemHandler` z NeoForge |
| Snapshot przy śmierci | — | Tombstone zbiera przedmioty w `LivingDropsEvent`, jego `LivingDeathEvent` jest LOWEST → nasz HIGHEST widzi nietknięty ekwipunek | do sprawdzenia |
| Grób | TE z `tick()` | `BlockEntityPlayerGrave.serverTick` statyczne; `countTicks` zapisywany w NBT; `deathDate` = `System.currentTimeMillis()` | `serverTick`, `countTicks` |
| Księgi | — | `ItemBook.canEnchant`; `setEnchant` wołane raz, w `BlockDecorativeGrave.use` (`m_6227_`); `ConsumeResult.result()` = `SUCCESS`/`FAIL` | `canEnchant`; `setEnchant` w `useItemOn` |
| Klucz soulbound | `EntityHelper.hasSoulbound` zwraca `true` dla `grave_key`; jedno wywołanie w `storeSoulboundsOnBody` | flaga `forceSoulbound` → `getEnchantmentLevel(soulbound)` = 1; decyzja: jedno `Helper.isSoulbound` w `storeSoulboundsOnBody` | `Helper.isSoulbound(ItemStack, Level)` = komponent `SOULBOUND` lub enchant; jedno wywołanie w `storeSoulboundsOnBody` |
| Enchanty | klasy | klasy; config gatuje `canEnchant`/`canApplyAtEnchantingTable`/`isAllowedOnBooks`, **nie** `isDiscoverable`/`isTradeable` | **JSON** (`data/tombstone/enchantment/`), brak przełącznika „enable", tylko `*EnchantingTable`; wszystkie w tagach `on_random_loot`/`tradeable` |

We wszystkich wersjach klucz do grobu jest soulboundowy na sztywno, niezależnie od configu Soulbounda.

## Konwencje

- Komentarze w kodzie po polsku, **bez polskich znaków** (ASCII). Komentarze configu i README dla graczy — po angielsku w configu, po polsku w README.
- Jedna wersja moda dla wszystkich MC (`version` w każdym `mcX/build.gradle`); tagi `vX.Y.Z`. Wersję w logu czytamy z kontenera moda (manifest), nie ze stałej — stała dryfuje.
- Każda zmiana zachowania: wpis w `README.md` i odpowiedniej liście `docs/checklists/`.
- Logika decyzyjna do `core` (z testem), w `platform`/`feature` tylko adaptery i wykonanie, mixiny tylko delegują.

## Dokumenty

- `docs/specs/2026-10-16-multiversion-design.md` — układ repo, macierz funkcji, etapy.
- `docs/specs/2026-08-14-tombtweaks-1201-port-design.md` — pełne uzasadnienie funkcji (port 1.20.1, §11 = 0.2.0).
- `docs/plans/2026-08-14-tombtweaks-1201-port.md` — plan portu 1.20.1 (historyczny; adnotacje przy zadaniach, które zastąpiono).

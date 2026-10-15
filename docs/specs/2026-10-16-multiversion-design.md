# TombTweaks modern — jedno repo, trzy wersje Minecrafta

**Data:** 2026-10-16
**Status:** zatwierdzony; etap 0 (porządek) wykonany w tym samym dniu
**Poprzednik:** `2026-08-14-tombtweaks-1201-port-design.md` (port 1.20.1, wersje 0.1.0–0.2.0)

## Cel

Jeden projekt addonu do Corail Tombstone dla **1.16.5, 1.20.1 i 1.21.1**, z pełną zgodnością funkcji tam, gdzie pozwala na to dana wersja Tombstone'a. Bezpośredni powód: gracz poprosił o slot restore na 1.21.1. Repo: `E:\Isuth\tombtweaks-modern` → `github.com/pegek/Tombtweaks-modern`.

`tombtweaks` dla **1.12.2** zostaje w repo `modDev` — to osobna linia z innym zestawem funkcji, innym toolchainem (FG3, bez refmapy) i własną historią.

## Układ

```
tombtweaks-modern/
├─ CLAUDE.md        wiedza projektu — ładowana w każdej sesji otwartej w tym folderze
├─ README.md        macierz funkcji × wersje, budowanie
├─ core/            WSPÓLNY: Java 8, zero typów MC/Forge/NeoForge/Tombstone, testy JUnit 5
├─ mc1.16.5/        Forge 36, ForgeGradle 5, Gradle 7, Java 8,  Tombstone 7.0.0
├─ mc1.20.1/        Forge 47, ForgeGradle 6, Gradle 8, Java 17, Tombstone 9.1.5
├─ mc1.21.1/        NeoForge 21.1, ModDevGradle, Gradle 8, Java 21, Tombstone 9.5.6
└─ docs/            specs/, plans/, checklists/in-game-<mc>.md
```

**Dlaczego osobne buildy, a jedno repo.** Przeszkodą w sierpniu był jeden *build* Gradle (jeden wrapper), nie jedno repo git. FG5 wymaga Gradle 7, FG6 Gradle 8, a FG6 i ModDevGradle w jednym buildzie gryzą się na classpathie pluginów. Każdy `mcX/` ma więc własny wrapper i jest budowany osobno.

**Wspólny `core` bez kopiowania.** Każdy build dołącza `../core/src/main/java` i `../core/src/test/java` przez `sourceSets.*.java.srcDir`. Logika decyzyjna istnieje raz, a testy `core` lecą na trzech JVM (8, 17, 21). Dwie mechaniczne straże pilnują, że `core` nadaje się do wszystkich trzech: `CoreHasNoForeignTypesTest` (skan skompilowanych klas na `net/minecraft`, `net/minecraftforge`, `net/neoforged`, `ovh/corail`) i `compileCoreJava8` (`--release 8`, w buildach 17/21).

**Jedna wersja moda dla wszystkich MC.** 0.3.0 = pierwsza wersja na trzy MC. Jary: `tombtweaks-<mc>-<wersja>.jar`.

## Macierz funkcji — ustalenia z bajtkodu

| Funkcja | 1.16.5 / Tombstone 7.0.0 | 1.20.1 / 9.1.5 | 1.21.1 / 9.5.6 |
|---|---|---|---|
| Slot restore | `RestoreInventoryEvent` istnieje — kształt do potwierdzenia | ✅ | ✅ identyczny event (`IItemHandler` z NeoForge) |
| Grave decay | do sprawdzenia (TE 1.16 tickuje przez `ITickableTileEntity.tick`) | ✅ | ✅ `serverTick` statyczne, `countTicks` |
| Cooldown ksiąg | do sprawdzenia | ✅ | ✅ `ItemBook.canEnchant`; wywołanie `setEnchant` w `BlockDecorativeGrave.useItemOn` (1.21 rozdzieliło `use`) |
| Klucze odporne na Soulbound | ✅ `EntityHelper.hasSoulbound` z gałęzią `grave_key`, jedno wywołanie w `storeSoulboundsOnBody` | ✅ | ✅ `Helper.isSoulbound(ItemStack, Level)` — jedno wywołanie w `storeSoulboundsOnBody`; **blokada kowadła zbędna**: Soulbound w 1.21 ma `supported_items: #minecraft:enchantable/durability`, klucz nie ma wytrzymałości |
| Wyłączone enchanty poza lootem/handlem | do sprawdzenia (1.16 ma już `canGenerateInLoot`/`isTradeable`) | ✅ | ❌ **nie dotyczy**: enchanty to JSON-y (`data/tombstone/enchantment/`), Tombstone 9.5.6 nie ma przełącznika „enable", tylko `*EnchantingTable`, i wpisuje wszystkie do vanillowych tagów `on_random_loot` / `tradeable` przez `#tombstone:enchantments`. Wyłączenie = datapack nadpisujący tagi; opis w README |

Różnica w `platform` na 1.21.1: stacki nie mają NBT (komponenty danych od 1.20.5), więc `ItemKey` hashuje komponenty. `core` bez zmian — dostaje tylko `(id, hash)`.

Kowadło w 1.20.1 vs 1.21.1: w 1.20.1 klucz przechodzi przez kategorię Soulbounda (`maxStackSize == 1`), więc blokada jest potrzebna. W 1.21.1 nie — patrz macierz.

## Etapy

0. **Porządek** (wykonany 2026-10-16): zmiana nazwy folderu, `git mv` do `core/` + `mc1.20.1/` (historia zachowana), `srcDir` na wspólny core, zależność 1.20.1 przestawiona na Tombstone 9.1.5 po porównaniu sygnatur wszystkich klas-celów i liczby wywołań w metodach z redirectami (identyczne), dokumenty przeniesione z `modDev`, `CLAUDE.md`, nowe repo GitHub.
1. **1.21.1** — o nią prosi gracz. Pierwszy krok planu: pełna weryfikacja celów w 9.5.6 (w tym czy klucz niesie komponent `SOULBOUND` domyślnie), potem implementacja.
2. **1.16.5** — j.w. na 7.0.0.
3. Wydanie 0.3.0 na trzy wersje.

Każdy etap: weryfikacja celów w bajtkodzie → implementacja → recenzja → build → `javap` zbudowanego jara (refmapa / nazwy po reobfuskacji) → testy w grze po stronie autora.

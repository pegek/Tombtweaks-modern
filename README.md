# TombTweaks (modern)

Tweaki i poprawki do [Corail Tombstone](https://www.curseforge.com/minecraft/mc-mods/corail-tombstone) dla Minecrafta **1.16.5, 1.20.1 i 1.21.1**. Wersja dla 1.12.2 to osobny projekt.

| Feature | Co robi | Domyślnie |
|---|---|---|
| `[restore]` | Odzyskany grób oddaje przedmioty **na te same sloty**, z których zginęły — łącznie z pancerzem i offhandem | włączone |
| `[decay]` | Nieodwiedzony grób powoli gubi zawartość na ziemię; cztery listy ochrony wyjmują wybrane przedmioty spod tego | **wyłączone** |
| `[cooldown]` | Magiczne księgi Tombstone'a dostają cooldown, konfigurowalny per księga | włączone (2 księgi × 6 min) |
| `[graveKeys]` | `survivesDeath = false`: klucze do grobu przestają być soulboundowe — noszone klucze trafiają do nowego grobu, a Soulbounda nie da się na klucz nałożyć | `true` (zachowanie Tombstone'a) |
| *(zawsze)* | Enchanty Tombstone'a **wyłączone w jego configu** naprawdę znikają ze skrzyń i od bibliotekarzy | — |

Config: `config/tombtweaks-common.toml`. Wszystkie wartości są czytane na bieżąco — bez restartu.

## Wersje

| Minecraft | Loader | Tombstone | Stan |
|---|---|---|---|
| 1.20.1 | Forge 47+ | 9.1.4 – 9.1.x | ✅ wszystkie funkcje (0.2.0) |
| 1.21.1 | NeoForge 21.1 | 9.5.x | w przygotowaniu — wyłączanie enchantów **nie dotyczy** (patrz niżej) |
| 1.16.5 | Forge 36 | 7.0.0 | w przygotowaniu |

Zakres wersji Tombstone'a jest celowo wąski: mixiny są zweryfikowane na konkretnych wydaniach, a niezgodna wersja wolałaby nie wystartować, niż wywalić grę przy ładowaniu klasy.

**1.21.1 i wyłączone enchanty.** Od 1.21 enchanty Tombstone'a są definiowane danymi, Tombstone 9.5 nie ma przełącznika, który je wyłącza, i dopisuje wszystkie do vanillowych tagów lootu i handlu. Żeby usunąć enchant z lootu i handlu, użyj datapacka nadpisującego tagi `minecraft:on_random_loot` / `minecraft:tradeable` (albo `tombstone:enchantments`).

## Jak to działa

**Restore.** W chwili śmierci mod zapisuje *plan miejsc* — dla każdego slotu tylko id przedmiotu i hash jego NBT, nie kopię przedmiotu. Przy odzyskaniu grobu przypisuje przedmioty do miejsc w dwóch przebiegach po całym grobie (najpierw dopasowania dokładne, potem po samym id) i wstawia przedmiot **wyłącznie do pustego slotu**. Wszystko, czego nie wstawi, Tombstone rozdziela tak jak zawsze — najgorszy przypadek to zwykłe zachowanie Tombstone'a, nigdy zgubiony ani zdublowany przedmiot.

**Decay.** Licznik `countTicks` grobu (Tombstone go zapisuje) wyznacza harmonogram: po `startTicks` co `intervalTicks` jeden stack wypada na ziemię. Najpierw niechronione; chronione dopiero gdy nic innego nie zostało, a przy `protectedNeverDecay = true` wcale. Licznik rośnie tylko, gdy chunk jest załadowany.

**Cooldown.** Blokada siedzi na `ItemBook.canEnchant`, start cooldownu — na jedynym w Tombstonie wywołaniu `setEnchant`, tylko przy sukcesie.

**Klucze i Soulbound.** Tombstone traktuje każdy klucz do grobu jako soulbound bezwarunkowo (flaga `forceSoulbound`), niezależnie od enchantu i od przełącznika Soulbounda w swoim configu. Przy `survivesDeath = false` decyzja przy śmierci (`DeathHandler.storeSoulboundsOnBody`) odpowiada dla klucza „nie", więc ląduje on w nowym grobie. Klucz do grobu, który właśnie powstaje, Tombstone wydaje osobno — dostajesz go jak zawsze.

**Wyłączone enchanty.** Tombstone uzależnia od swojego configu stół do zaklinania, kowadło i księgi, ale nie `isDiscoverable()` (po nim filtruje vanillowy `enchant_randomly` w skrzyniach) ani `isTradeable()` (bibliotekarze). Mod dodaje oba nadpisania: `isEnabled() && super`. Dla enchantów włączonych nic się nie zmienia.

## Znane ograniczenia

- **Curios** nie są obsługiwane przez restore (przestrzeń slotów 200+ zarezerwowana). Tombstone ma własne `curioAutoEquip`.
- **„Reverse inventory sorting"** — preferencja Tombstone'a per gracz, uruchamiana *po* naszym evencie; gdy włączona, odwraca odzyskany układ. Wynik weryfikacji w grze: *do uzupełnienia*.
- **Restore działa tylko dla właściciela grobu.** Grób otwarty kluczem przez innego gracza rozdziela się standardowo.
- **Scalone groby.** Druga śmierć w promieniu 20 bloków od nieodebranego grobu dokłada przedmioty do niego; rozsadzane są tylko przedmioty z ostatniej śmierci, starsze idą ścieżką standardową. Scalenie zeruje też licznik decay.
- **Historia decay gracza offline** trafia tylko do logu serwera (DEBUG).
- **Dwa komunikaty przy zablokowanej księdze:** nasz (z liczbą sekund, na pasku akcji) i własny Tombstone'a „not allowed" w czacie.
- **Księga Soulbounda** (`book_of_soulbound`) użyta na kluczu przy `survivesDeath = false` nadal się zużyje, choć nic nie da — blokowana jest tylko droga przez kowadło.
- **Wyłączone enchanty** w lootach innych modów, które budują własną pulę enchantów z pominięciem vanillowych funkcji, mogą nadal przeciekać — zamknięte są ścieżki vanilli.

## Budowanie

Repo zawiera trzy niezależne buildy — każdy budujesz ze swojego katalogu:

```bash
cd mc1.20.1 && ./gradlew check build   # testy + straże, jar w mc1.20.1/build/libs/
```

JDK (8 / 17 / 21) Gradle pobiera sam przez toolchain. Tombstone pochodzi z CurseMaven — jego jar celowo nie leży w repo (licencja „All rights reserved").

**Po każdej zmianie mixina w buildzie Forge** sprawdź, że `tombtweaks.refmap.json` w zbudowanym jarze ma wpisy dla celów będących metodami Minecrafta (1.20.1: `use` → `m_6227_`, `canEnchant` → `m_6081_`). Bez refmapy build jest zielony, a gra wywala się dopiero w prawdziwej instancji — `runClient` tego nie pokaże.

## Architektura

| Katalog / pakiet | Zawartość | Testy |
|---|---|---|
| `core/` | cała logika decyzyjna, **wspólna dla wszystkich wersji**; zero typów Minecrafta, loadera i Tombstone'a; składnia Javy 8 | JUnit 5, w każdym buildzie |
| `mcX/…/platform` | config, adaptery `ItemStack` → `core`, kodek NBT | round-trip kodeka |
| `mcX/…/feature`, `mixin` | handlery eventów i mixiny | w grze — `docs/checklists/in-game-<mc>.md` |

Obie zasady `core` są pilnowane mechanicznie: `CoreHasNoForeignTypesTest` skanuje skompilowane klasy, `compileCoreJava8` kompiluje `core` z `--release 8`.

Uzasadnienie decyzji: `docs/specs/` — układ wielowersyjny w `2026-10-16-multiversion-design.md`, funkcje w `2026-08-14-tombtweaks-1201-port-design.md`.

# Weryfikacja w grze — tombtweaks 0.2.0

Rób to na **zreobfuskowanym jarze z `build/libs/` wrzuconym do prawdziwej instancji** (Forge 47 + Tombstone 9.1.4), nie w `./gradlew runClient`. Dev używa nazw oficjalnych i ukrywa dokładnie ten błąd (brak refmapy), który jest dla tego moda najgroźniejszy.

Zaznaczaj wynik obok każdego punktu. Wszystko, co wypadnie inaczej niż „Oczekiwane", to materiał na poprawkę.

## 0. Start

- [ ] Gra wstaje, w modliście `tombtweaks` z wersją `0.2.0`.
- [ ] W logu linia `[TombTweaks] 0.2.0 loading`.
- [ ] W logu **pięć** linii `Mixing … from mixins.tombtweaks.json into …` — dla `BlockEntityPlayerGrave`, `ItemBook`, `BlockDecorativeGrave`, `DeathHandler` i `TombstoneEnchantment`. Mixiny ładują się leniwie: linia pojawia się dopiero przy pierwszym załadowaniu klasy-celu (np. po postawieniu grobu / użyciu księgi), więc jej brak przed tym momentem nie jest błędem.
- [ ] **Brak** w logu: `InvalidInjectionException`, `could not find any targets`, `Reference map 'tombtweaks.refmap.json' … could not be read`.
- [ ] Powstał `config/tombtweaks-common.toml` z trzema tabelami `[restore]`, `[decay]`, `[cooldown]`; `decay.enabled = false`, cztery listy ochrony puste, `cooldown.books` z dwoma wpisami po 6 min.

## 1. Restore

Ustaw `restore.debugLogging = true`, `/gamerule keepInventory false`.

- [ ] Charakterystyczny układ (miecz w slocie 2, chleb w 9, coś w offhandzie, pełny pancerz, kilka rzeczy w głównym ekwipunku, jeden pusty slot w środku hotbara). `/kill`. W logu `recorded N seats`.
- [ ] Odzyskaj grób. **Oczekiwane:** wszystko na swoich miejscach, pancerz założony, offhand na miejscu, pusty slot dalej pusty. W logu `seated X of N`.
- [ ] Zginij, po respawnie podnieś coś na slot, na którym coś leżało przed śmiercią, odzyskaj grób. **Oczekiwane:** zajęty slot pominięty, ten przedmiot trafia gdzie indziej (ścieżka Tombstone'a), nic nie ginie, nic się nie dubluje.
- [ ] Zginij dwa razy w promieniu 20 bloków, nie odbierając grobu (scalenie). **Oczekiwane:** przedmioty z ostatniej śmierci na swoich miejscach, z pierwszej — ścieżką standardową, nic nie ginie.
- [ ] Drugi gracz (albo drugie konto) otwiera Twój grób kluczem. **Oczekiwane:** standardowe zachowanie Tombstone'a, bez rozsadzania.
- [ ] `restore.enabled = false` → zachowanie standardowe. `keepInventory true` → brak `recorded` w logu.
- [ ] **Reverse inventory sorting:** w ustawieniach Tombstone'a sprawdź wartość domyślną, potem włącz i powtórz pierwszy test. Wynik wpisz do README (sekcja „Znane ograniczenia").

## 2. Decay

Ustaw `decay.enabled = true`, `startTicks = 100`, `intervalTicks = 40`.

- [ ] Zginij z pełnym ekwipunkiem, stój przy grobie, nie otwieraj. **Oczekiwane:** po ~5 s co ~2 s jeden stack wypada nad grobem.
- [ ] Dodaj `"minecraft:diamond_sword"` do `protectedItems`, powtórz. **Oczekiwane:** miecz zostaje do końca; przy `protectedNeverDecay = true` zostaje na zawsze, przy `false` wypada jako ostatni.
- [ ] 🚨 **Restart w trakcie rozkładu:** niech wypadną 2–3 przedmioty, od razu zatrzymaj serwer (single player: wyjdź do menu), wczytaj świat. **Oczekiwane:** wyrzucone przedmioty leżą na ziemi i **nie ma ich z powrotem w grobie**. (To jest duplikacja, przed którą chroni `setChanged()`.)
- [ ] Po wczytaniu rozkład kontynuuje od miejsca, w którym stanął.
- [ ] Historia: z właścicielem online w jego danych gracza jest `tombtweaks_decay_history` z wpisami `item` + `count` (nie `minecraft:air`!).

## 3. Cooldown

- [ ] Book of Disenchantment na ozdobnym grobie z duszą — działa.
- [ ] Od razu druga księga tego samego typu — odmowa, na pasku akcji „This book is still recovering — N s left." (Tombstone dorzuca swój komunikat w czacie — to znane.)
- [ ] **Stos złożony z jednej księgi** — cooldown też startuje.
- [ ] Wyjście do menu i powrót — cooldown trwa dalej. Śmierć — cooldown trwa dalej.
- [ ] `books = ["tombstone:book_of_disenchantment;0"]` — cooldown znika od razu, bez restartu.

## 3a. Klucze do grobu (0.2.0)

- [ ] Domyślnie (`graveKeys.survivesDeath = true`): zginij z kluczem do starego grobu w ekwipunku — po respawnie klucz dalej jest przy Tobie (zachowanie Tombstone'a).
- [ ] `survivesDeath = false`, powtórz. **Oczekiwane:** stary klucz leży w **nowym** grobie, a klucz do nowego grobu dostajesz jak zawsze.
- [ ] To samo z kluczem, na który wcześniej nałożono Soulbound (np. przy `true`). **Oczekiwane:** przy `false` też trafia do grobu.
- [ ] Kowadło: klucz + księga z Soulboundem przy `false` — brak wyniku. Przy `true` — działa jak w Tombstonie.
- [ ] W logu linia `Mixing MixinDeathHandlerKeys … into ovh.corail.tombstone.helper.DeathHandler` i `Mixing MixinTombstoneEnchantment … into ovh.corail.tombstone.enchantment.TombstoneEnchantment`.

## 3b. Wyłączone enchanty Tombstone'a (0.2.0)

- [ ] W configu Tombstone'a wyłącz Soulbound (`enableEnchantmentSoulbound = false`).
- [ ] Wygeneruj dużo lootu z księgami `enchant_randomly` (np. `/loot spawn ~ ~ ~ loot minecraft:chests/stronghold_library` kilkadziesiąt razy). **Oczekiwane:** żadna księga z Soulboundem.
- [ ] Nowi bibliotekarze (reroll handlu kilkadziesiąt razy). **Oczekiwane:** żadnej księgi z Soulboundem w ofercie.
- [ ] Kontrola: włącz Soulbound z powrotem — księgi z nim znów się pojawiają (poprawka nie wycina włączonych enchantów).

## 4. Serwer dedykowany

- [ ] Ten sam jar na serwerze dedykowanym startuje bez błędów (statycznie sprawdzone: zero referencji do klas klienckich), a punkt 1 działa dla gracza na serwerze.

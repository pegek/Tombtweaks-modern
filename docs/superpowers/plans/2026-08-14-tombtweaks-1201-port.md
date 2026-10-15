# TombTweaks 1.20.1 — core implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `tombtweaks` on Minecraft 1.20.1 / Forge 47 / Corail Tombstone 9.1.4 with three features: exact-slot grave restore, grave item decay and book cooldowns.

**Architecture:** A new, separate repo (forced by a Gradle wrapper conflict with `modDev`). Three layers in one source set: `core` with not a single Minecraft or Tombstone type (all the decision logic, tested with JUnit on a bare JVM), `platform` (thin adapters), `feature` (handlers and three mixins, kept as dumb as possible). Restore runs on the public `RestoreInventoryEvent` with no mixin; decay and cooldowns go through mixins into Tombstone.

**Tech Stack:** Gradle 8.x, ForgeGradle 6, MixinGradle 0.7, Java 17 (toolchain), mappings `official`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md` (originally in the `modDev` repo, now in this one). Read it before starting — the plan implements its §4, §5, §6, §7.

---

## Rules that apply throughout the plan

1. **`core` must be syntactically Java 8 compatible.** No records, no `var`, no switch expressions, no `List.of`. `core` is the only thing that will carry over copy-paste when porting to 1.16.5 (Java 8) — Java 17 syntax takes away its only reason to exist. The `platform` and `feature` layers may use full Java 17.
2. **`core` imports nothing from `net.minecraft`, `net.minecraftforge` or `ovh.corail`.** Task 2 adds a test that enforces this mechanically.
3. **Refmaps are enabled.** This is the opposite of the rule in `modDev/CLAUDE.md` — there `-proc:none` disables the mixin annotation processor. Here you **must not** add `-proc:none`, because the refmap is needed for the mixin that targets a Minecraft method (`BlockBehaviour.use`).
4. **Every mixin that targets a Tombstone member has `remap = false`.** Tombstone's classes are not obfuscated; its methods have no SRG names.
5. **Commit after every task.** The tasks are chosen so that each one leaves the repo in a state that compiles.

---

## File structure

```
E:\Isuth\tombtweaks-1201\
├─ settings.gradle                     pluginManagement + project name
├─ build.gradle                        FG6 + MixinGradle + toolchain 17 + JUnit 5
├─ gradle.properties
├─ gradle/wrapper/                     Gradle 8.x
├─ libs/tombstone-1.20.1-9.1.4.jar     moved from modDev/TestPort
├─ .gitignore
├─ README.md
├─ src/main/java/com/spege/tombtweaks/
│  ├─ TombTweaks.java                  @Mod: registers config and handlers
│  ├─ core/                            ZERO MC/Forge/Tombstone types, Java 8 syntax
│  │  ├─ ItemKey.java                  stack identity: id + NBT hash
│  │  ├─ SlotEntry.java                one remembered seat
│  │  ├─ SlotSnapshot.java             slot plan from one death + slot encoding constants
│  │  ├─ SlotPlan.java                 three-tier matching, consumes seats
│  │  ├─ SnapshotStore.java            a player's pending snapshots: binding and pruning
│  │  ├─ StackView.java                interface: what core knows about a stack
│  │  ├─ ProtectionRules.java          four protection lists
│  │  ├─ DecaySchedule.java            when a grave loses its next item
│  │  ├─ DecayVictim.java              which slot goes under the knife
│  │  ├─ CooldownRules.java            parser for the "id;minutes" config
│  │  └─ Cooldown.java                 remaining-time arithmetic
│  ├─ platform/
│  │  ├─ Config.java                   ForgeConfigSpec, three categories
│  │  ├─ StackViews.java               ItemStack -> ItemKey / StackView
│  │  ├─ PlayerData.java               access to PlayerPersisted
│  │  └─ SnapshotCodec.java            SnapshotStore <-> CompoundTag
│  ├─ feature/
│  │  ├─ restore/SnapshotCaptureHandler.java   LivingDeathEvent
│  │  ├─ restore/SlotRestoreHandler.java       RestoreInventoryEvent
│  │  ├─ decay/GraveDecayService.java          logic of one grave tick
│  │  ├─ decay/DecayHistory.java               history record keyed by UUID
│  │  └─ cooldown/BookCooldownService.java     cooldown read/write
│  └─ mixin/
│     ├─ MixinBlockEntityPlayerGrave.java      HEAD serverTick -> decay
│     ├─ MixinItemBook.java                    HEAD canEnchant -> block
│     └─ MixinBlockDecorativeGrave.java        REDIRECT setEnchant -> cooldown start
├─ src/main/resources/
│  ├─ META-INF/mods.toml
│  ├─ mixins.tombtweaks.json
│  ├─ pack.mcmeta
│  └─ assets/tombtweaks/lang/en_us.json
└─ src/test/java/com/spege/tombtweaks/core/
   ├─ CoreHasNoForeignTypesTest.java
   ├─ SlotPlanTest.java
   ├─ SnapshotStoreTest.java
   ├─ ProtectionRulesTest.java
   ├─ DecayScheduleTest.java
   ├─ DecayVictimTest.java
   └─ CooldownTest.java
```

---

## Task 1: Repository bootstrap and build

**Files:**
- Create: `E:\Isuth\tombtweaks-1201\settings.gradle`
- Create: `E:\Isuth\tombtweaks-1201\build.gradle`
- Create: `E:\Isuth\tombtweaks-1201\gradle.properties`
- Create: `E:\Isuth\tombtweaks-1201\.gitignore`
- Create: `E:\Isuth\tombtweaks-1201\src\main\java\com\spege\tombtweaks\TombTweaks.java`
- Create: `E:\Isuth\tombtweaks-1201\src\main\resources\META-INF\mods.toml`
- Create: `E:\Isuth\tombtweaks-1201\src\main\resources\pack.mcmeta`
- Move: `E:\Isuth\modDev\TestPort\tombstone-1.20.1-9.1.4.jar` → `E:\Isuth\tombtweaks-1201\libs\`

> **Note on the JDK:** this machine has JDK 8, 18, 21 and 24 — **no 17**, which Forge 47 requires. That is why the `toolchain` block in `build.gradle` is not decoration: Gradle will download JDK 17 itself via the foojay resolver. Do not try to build under 21 or 24.

- [ ] **Step 1: Create the repo and move the Tombstone jar**

```bash
mkdir -p /e/Isuth/tombtweaks-1201/libs
cd /e/Isuth/tombtweaks-1201 && git init
mv /e/Isuth/modDev/TestPort/tombstone-1.20.1-9.1.4.jar /e/Isuth/tombtweaks-1201/libs/
```

- [ ] **Step 2: Generate the Gradle 8.7 wrapper**

```bash
cd /e/Isuth/tombtweaks-1201 && gradle wrapper --gradle-version 8.7
```

If `gradle` is not on PATH, copy the `gradle/wrapper` directory and `gradlew*` from any Gradle 8 project and change `distributionUrl` to `gradle-8.7-bin.zip`.

Expected: `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` with `gradle-8.7-bin.zip` appear.

- [ ] **Step 3: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { name = 'MinecraftForge'; url = 'https://maven.minecraftforge.net/' }
        maven { name = 'Sponge';         url = 'https://repo.spongepowered.org/repository/maven-public/' }
    }
}

plugins {
    // Lets Gradle download JDK 17 by itself. 17 is not installed on this machine.
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'tombtweaks'
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

- [ ] **Step 5: Write `build.gradle`**

```groovy
plugins {
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '[6.0.24,6.2)'
    id 'org.spongepowered.mixin'   version '0.7.+'
}

version = '0.1.0'
group   = 'com.spege.tombtweaks'
base { archivesName = 'tombtweaks-1.20.1' }

// Do NOT add -proc:none. The mixin annotation processor has to run, because
// MixinBlockDecorativeGrave targets a Minecraft method and needs a refmap.
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

minecraft {
    mappings channel: 'official', version: '1.20.1'
    copyIdeResources = true

    runs {
        client {
            workingDirectory project.file('run')
            property 'forge.logging.console.level', 'debug'
            mods { tombtweaks { source sourceSets.main } }
        }
        server {
            workingDirectory project.file('run')
            property 'forge.logging.console.level', 'debug'
            mods { tombtweaks { source sourceSets.main } }
        }
    }
}

mixin {
    add sourceSets.main, 'tombtweaks.refmap.json'
    config 'mixins.tombtweaks.json'
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.2.20'

    // Tombstone: deobf is required, because the MC references in its bytecode are SRG.
    implementation fg.deobf(files('libs/tombstone-1.20.1-9.1.4.jar'))
    // ^ PLAN ERROR: fg.deobf(files(...)) is FG3 syntax, on FG6 it silently does nothing.
    //   In the repo: maven CurseMaven + fg.deobf('curse.maven:corail-tombstone-243707:8606942').

    // MISSING FROM THE PLAN (found 2026-10-02 after Tasks 12-13): MixinGradle 0.7 does NOT add
    // the annotation processor by itself. Without this line the jar has no refmap, and the
    // redirect on `use` breaks the game in a real instance (runClient will not show it).
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging { events 'passed', 'skipped', 'failed' }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

jar {
    manifest {
        attributes([
            'Specification-Title'   : 'TombTweaks',
            'Specification-Vendor'  : 'spege',
            'Specification-Version' : "${version}",
            'Implementation-Title'  : project.name,
            'Implementation-Version': "${version}",
            'Implementation-Vendor' : 'spege'
        ])
    }
}
```

- [ ] **Step 6: Write `.gitignore`**

```gitignore
build/
bin/
run/
.gradle/
.idea/
*.iml
.classpath
.project
.settings/
```

- [ ] **Step 7: Write `src/main/resources/META-INF/mods.toml`**

```toml
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"
issueTrackerURL="https://github.com/spege/tombtweaks"

[[mods]]
modId="tombtweaks"
version="${file.jarVersion}"
displayName="TombTweaks"
authors="spege"
description='''
Tweaks for Corail Tombstone: items return to the slots they came from, unvisited graves slowly decay, and the magic books get a cooldown.
'''

[[dependencies.tombtweaks]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.tombtweaks]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.20.2)"
    ordering="NONE"
    side="BOTH"

[[dependencies.tombtweaks]]
    modId="tombstone"
    mandatory=true
    versionRange="[9.0,)"
    ordering="AFTER"
    side="BOTH"

[[mixins]]
config="mixins.tombtweaks.json"
```

- [ ] **Step 8: Write `src/main/resources/pack.mcmeta`**

```json
{
  "pack": {
    "description": "TombTweaks resources",
    "pack_format": 15
  }
}
```

- [ ] **Step 9: Write the `@Mod` class**

`src/main/java/com/spege/tombtweaks/TombTweaks.java`:

```java
package com.spege.tombtweaks;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        LOGGER.info("[TombTweaks] {} loading", VERSION);
    }
}
```

- [ ] **Step 10: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, `build/libs/tombtweaks-1.20.1-0.1.0.jar` appears. The first run downloads JDK 17 and decompiles Minecraft — it will take a dozen or so minutes.

- [ ] **Step 11: Launch the client and confirm Tombstone is present**

Run: `./gradlew runClient`
Expected: the client starts; the log has `[TombTweaks] 0.1.0 loading` and an entry about loading the `tombstone` mod. Close the client.

- [ ] **Step 12: Commit**

```bash
cd /e/Isuth/tombtweaks-1201
git add -A
git commit -m "chore: bootstrap the tombtweaks project on 1.20.1 (FG6, Java 17, JUnit 5)"
```

---

## Task 2: Test guarding the purity of `core`

`core` only makes sense if it really contains no foreign types — otherwise the port to 1.16.5 stops being copy-paste and nobody finds out until they try. The test scans the **compiled** classes, not the sources, so it also catches use of a fully qualified name without an import.

**Files:**
- Create: `src/test/java/com/spege/tombtweaks/core/CoreHasNoForeignTypesTest.java`
- Create: `src/main/java/com/spege/tombtweaks/core/DecaySchedule.java` (a minimal class, just so that the `core` directory exists at all)
- Create: `src/main/resources/mixins.tombtweaks.json` (with an **empty** `mixins` list)

> **Correction to the plan, found after Task 1.** The jar declares `MixinConfigs: mixins.tombtweaks.json` in its manifest (the MixinGradle plugin does this), and `mods.toml` has `[[mixins]] config=...`. As long as that file does not exist, the build passes, but **the game crashes at startup**. That would block the in-game verification in Tasks 9 and 11, which need no mixins at all. So the file is created here, with an empty `"mixins": []` list — which is a perfectly valid configuration — and Tasks 12 and 13 only add entries to it.

- [ ] **Step 1: Write the test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the one property that makes the core package worth having: no foreign types.
 * Scans the compiled classes, because type names sit in the constant pool as plain text -
 * so it also catches use of a fully qualified name, without an import.
 */
class CoreHasNoForeignTypesTest {

    private static final String[] FORBIDDEN = {
        "net/minecraft", "net/minecraftforge", "ovh/corail"
    };

    @Test
    void coreClassesNameNoForeignTypes() throws IOException {
        Path root = Paths.get("build", "classes", "java", "main",
                              "com", "spege", "tombtweaks", "core");
        assertTrue(Files.isDirectory(root),
                   "no compiled core classes under " + root.toAbsolutePath()
                   + " - run the test via ./gradlew test, not from the IDE without compiling");

        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(root)) {
            classFiles = files.filter(p -> p.toString().endsWith(".class"))
                              .collect(java.util.stream.Collectors.toList());
        }

        List<String> offenders = new ArrayList<String>();
        for (Path file : classFiles) {
            String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String forbidden : FORBIDDEN) {
                if (bytes.contains(forbidden)) {
                    offenders.add(file.getFileName() + " -> " + forbidden);
                }
            }
        }

        assertTrue(offenders.isEmpty(), "core names foreign types: " + offenders);
    }

    @Test
    void theScanWouldActuallyCatchSomething() {
        // Check on the test itself: if the condition were always true, the test guards nothing.
        String pretendClassFile = "some bytes net/minecraft/world/item/ItemStack more bytes";
        boolean caught = false;
        for (String forbidden : FORBIDDEN) {
            if (pretendClassFile.contains(forbidden)) caught = true;
        }
        assertTrue(caught);
        assertFalse("com/spege/tombtweaks/core/ItemKey".contains("net/minecraft"));
    }
}
```

- [ ] **Step 2: Write `DecaySchedule`, so there is something to scan**

`src/main/java/com/spege/tombtweaks/core/DecaySchedule.java`:

```java
package com.spege.tombtweaks.core;

/**
 * When a grave loses its next item.
 *
 * <p>A pure function of the grave's own {@code countTicks} counter, which Tombstone writes to NBT
 * and reads back when the chunk loads. Thanks to that the schedule needs no state of its own
 * and survives a server restart and a chunk unload.
 *
 * <p>Mind the semantics: {@code countTicks} only grows while the grave is loaded,
 * so {@code startTicks} counts LOADED time, not wall-clock time.
 */
public final class DecaySchedule {

    private DecaySchedule() {
    }

    public static boolean shouldDecay(long countTicks, long startTicks, long intervalTicks) {
        if (intervalTicks <= 0L) {
            return false;
        }
        if (countTicks < startTicks) {
            return false;
        }
        return (countTicks - startTicks) % intervalTicks == 0L;
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew test`
Expected: PASS, 2 tests.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: core purity guard + DecaySchedule"
```

---

## Task 3: `core` — stack identity and slot plan

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/ItemKey.java`
- Create: `src/main/java/com/spege/tombtweaks/core/SlotEntry.java`
- Create: `src/main/java/com/spege/tombtweaks/core/SlotSnapshot.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SlotSnapshotTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotSnapshotTest {

    @Test
    void itemKeyDistinguishesByNbt() {
        assertEquals(new ItemKey("minecraft:diamond_sword", 7),
                     new ItemKey("minecraft:diamond_sword", 7));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:diamond_sword", 8));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:iron_sword", 7));
    }

    @Test
    void emptyStackDoesNotEnterSnapshot() {
        SlotSnapshot snapshot = new SlotSnapshot(1000L);
        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.entries().size());
    }

    @Test
    void slotEncodingDoesNotCollide() {
        // Main 0-35 must end before armor, armor before offhand,
        // and offhand before the space reserved for Curios.
        assertTrue(35 < SlotSnapshot.ARMOR_BASE);
        assertTrue(SlotSnapshot.ARMOR_BASE + 3 < SlotSnapshot.OFFHAND_SLOT);
        assertTrue(SlotSnapshot.OFFHAND_SLOT < SlotSnapshot.CURIOS_BASE);
    }

    @Test
    void snapshotRemembersSeatAndCount() {
        SlotSnapshot snapshot = new SlotSnapshot(1234L);
        snapshot.add(4, new ItemKey("minecraft:bread", 0), 12);
        snapshot.add(SlotSnapshot.OFFHAND_SLOT, new ItemKey("minecraft:shield", 99), 1);

        assertEquals(1234L, snapshot.capturedAt());
        assertEquals(2, snapshot.entries().size());
        assertEquals(4, snapshot.entries().get(0).slot());
        assertEquals(12, snapshot.entries().get(0).count());
        assertEquals(SlotSnapshot.OFFHAND_SLOT, snapshot.entries().get(1).slot());
    }
}
```

- [ ] **Step 2: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class ItemKey`.

- [ ] **Step 3: Write `ItemKey`**

```java
package com.spege.tombtweaks.core;

/**
 * Stack identity: just enough to recognise it on its way back from the grave, and not an ounce more.
 *
 * <p>On 1.12.2 the key was (name, metadata, NBT hash). On 1.20.1 there is no metadata,
 * and durability lives in NBT - hence the (name, NBT hash) pair, and hence also the fact that
 * matching has to have a fallback tier: serialising a stack into the grave and back can
 * normalise the tag, and then the hash changes. See {@link SlotPlan}.
 */
public final class ItemKey {

    private final String id;
    private final int nbtHash;

    public ItemKey(String id, int nbtHash) {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        this.id = id;
        this.nbtHash = nbtHash;
    }

    public String id() {
        return id;
    }

    public int nbtHash() {
        return nbtHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey)) {
            return false;
        }
        ItemKey that = (ItemKey) other;
        return this.nbtHash == that.nbtHash && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + nbtHash;
    }

    @Override
    public String toString() {
        return id + "#" + nbtHash;
    }
}
```

- [ ] **Step 4: Write `SlotEntry`**

```java
package com.spege.tombtweaks.core;

/** One remembered seat: what sat where at the moment of death. */
public final class SlotEntry {

    private final int slot;
    private final ItemKey key;
    private final int count;

    public SlotEntry(int slot, ItemKey key, int count) {
        this.slot = slot;
        this.key = key;
        this.count = count;
    }

    public int slot() {
        return slot;
    }

    public ItemKey key() {
        return key;
    }

    /**
     * The count at the moment of death. Stored for diagnostics, NEVER a matching criterion:
     * percentLossOnDeath shrinks stacks inside the grave before anyone sees them.
     */
    public int count() {
        return count;
    }
}
```

- [ ] **Step 5: Write `SlotSnapshot`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The slot plan from one death - not a copy of the items.
 *
 * <p>A full serialised inventory in the player data is tens of kilobytes rewritten
 * on every autosave and, worse, a second instance of the truth about what the player owned.
 * The grave is the only instance of the truth; this is purely the seating chart.
 */
public final class SlotSnapshot {

    /** The main inventory keeps its own indices 0-35. */
    public static final int ARMOR_BASE = 100;
    public static final int OFFHAND_SLOT = 150;
    /** Reserved for Curios. Unused in v1. */
    public static final int CURIOS_BASE = 200;

    private final long capturedAt;
    private final List<SlotEntry> entries;

    public SlotSnapshot(long capturedAt) {
        this(capturedAt, new ArrayList<SlotEntry>());
    }

    public SlotSnapshot(long capturedAt, List<SlotEntry> entries) {
        this.capturedAt = capturedAt;
        this.entries = entries;
    }

    public void add(int slot, ItemKey key, int count) {
        entries.add(new SlotEntry(slot, key, count));
    }

    /** Wall clock in milliseconds - the same scale as the grave's deathDate. */
    public long capturedAt() {
        return capturedAt;
    }

    public List<SlotEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew test`
Expected: PASS, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(core): ItemKey, SlotEntry and SlotSnapshot - a slot plan instead of an inventory copy"
```

---

## Task 4: `core` — three-tier matching

> **Superseded after the `core` review (2026-10-02).** The `claimSeat` below matched greedily, item by item, and an item with a drifted NBT hash could take a seat that a later item would have hit exactly. The real trigger in Tombstone 9.1.4: a second death within 20 blocks of an unclaimed grave **merges into it** (`findExistingGraveLocation` + `resetDeathTime()`), so the grave contains items from two deaths, while it binds only to the snapshot of the last one. The API now in force is the static `SlotPlan.assign(snapshot, graveKeys, seatFree)` — two passes over the whole grave (first all exact matches, then the fallback ones), and occupied player slots do not take part in matching. Task 11 has already been rewritten for the new API. The code below stays as a historical record.

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/SlotPlan.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SlotPlanTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlotPlanTest {

    private static SlotSnapshot snapshotWith(SlotEntry... items) {
        SlotSnapshot snapshot = new SlotSnapshot(0L);
        for (SlotEntry item : items) {
            snapshot.add(item.slot(), item.key(), item.count());
        }
        return snapshot;
    }

    @Test
    void exactHit() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(3, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(3, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 42)));
    }

    @Test
    void fallsBackToNameMatchWhenNbtDrifted() {
        // A round trip through the grave's NBT can normalise the tag and change the hash.
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(7, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(7, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 999)));
    }

    @Test
    void exactTakesPrecedenceOverName() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(1, new ItemKey("minecraft:diamond_sword", 11), 1),
                new SlotEntry(2, new ItemKey("minecraft:diamond_sword", 22), 1)));

        assertEquals(2, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 22)));
        assertEquals(1, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 11)));
    }

    @Test
    void twoIdenticalStacksGetTwoDifferentSeats() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8),
                new SlotEntry(9, new ItemKey("minecraft:bread", 0), 8)));

        int first = plan.claimSeat(new ItemKey("minecraft:bread", 0));
        int second = plan.claimSeat(new ItemKey("minecraft:bread", 0));

        assertNotEquals(first, second);
        assertEquals(13, first + second);
    }

    @Test
    void noMatchMeansGivingUp() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:stone", 0)));
    }

    @Test
    void seatIsConsumedOnce() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(4, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
    }
}
```

- [ ] **Step 2: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class SlotPlan`.

- [ ] **Step 3: Write `SlotPlan`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot issued per grave. Each seat can be taken only once.
 *
 * <p>Matching is three-tier, because the (name, NBT hash) key is brittle: a stack goes
 * into the grave through serialisation to NBT and back, and the round trip can normalise the tag.
 *
 * <ol>
 *   <li>exact - name and NBT hash,</li>
 *   <li>fallback - the name alone, the first entry not yet taken,</li>
 *   <li>giving up - {@link #NO_SEAT}, the item stays in the grave and takes the standard path.</li>
 * </ol>
 *
 * <p>The third tier is more important than it looks: it guarantees that the worst case
 * of the whole feature is Tombstone's current behaviour, never a lost item.
 */
public final class SlotPlan {

    public static final int NO_SEAT = -1;

    private final List<SlotEntry> remaining;

    public SlotPlan(SlotSnapshot snapshot) {
        this.remaining = new ArrayList<SlotEntry>(snapshot.entries());
    }

    public int claimSeat(ItemKey key) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().equals(key)) {
                return remaining.remove(i).slot();
            }
        }
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().id().equals(key.id())) {
                return remaining.remove(i).slot();
            }
        }
        return NO_SEAT;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew test`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core): SlotPlan - three-tier matching with giving up as the third tier"
```

---

## Task 5: `core` — binding a snapshot to a grave

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/SnapshotStore.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SnapshotStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnapshotStoreTest {

    private static final long TOLERANCE = 5_000L;

    private static SlotSnapshot at(long capturedAt) {
        SlotSnapshot snapshot = new SlotSnapshot(capturedAt);
        snapshot.add(0, new ItemKey("minecraft:stone", 0), 1);
        return snapshot;
    }

    @Test
    void bindsByNearestTime() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        SlotSnapshot claimed = store.claimNearest(50_200L, TOLERANCE);

        assertNotNull(claimed);
        assertEquals(50_000L, claimed.capturedAt());
    }

    @Test
    void consumesSnapshotSoSecondGraveGetsItsOwn() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        assertEquals(50_000L, store.claimNearest(50_100L, TOLERANCE).capturedAt());
        assertEquals(1_000L, store.claimNearest(1_100L, TOLERANCE).capturedAt());
        assertNull(store.claimNearest(1_100L, TOLERANCE));
    }

    @Test
    void outsideToleranceMeansNoMatch() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));

        assertNull(store.claimNearest(1_000L + TOLERANCE + 1L, TOLERANCE));
    }

    @Test
    void toleranceWorksBothWays() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(10_000L));

        assertNotNull(store.claimNearest(10_000L - TOLERANCE, TOLERANCE));
    }

    @Test
    void prunesByCount() {
        SnapshotStore store = new SnapshotStore();
        for (int i = 1; i <= 6; i++) {
            store.add(at(i * 1_000L));
        }

        store.prune(100_000L, 3, Long.MAX_VALUE);

        assertEquals(3, store.all().size());
        // the youngest ones stay
        assertEquals(4_000L, store.all().get(0).capturedAt());
        assertEquals(6_000L, store.all().get(2).capturedAt());
    }

    @Test
    void prunesByAge() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(90_000L));

        store.prune(100_000L, 10, 20_000L);

        assertEquals(1, store.all().size());
        assertEquals(90_000L, store.all().get(0).capturedAt());
    }

    @Test
    void emptyStoreDoesNotBlowUp() {
        SnapshotStore store = new SnapshotStore();
        store.prune(100_000L, 3, 1_000L);
        assertNull(store.claimNearest(0L, TOLERANCE));
        assertEquals(0, store.all().size());
    }
}
```

- [ ] **Step 2: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class SnapshotStore`.

- [ ] **Step 3: Write `SnapshotStore`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One player's pending snapshots.
 *
 * <p>A snapshot is created at death, that is before the grave exists - so it can only be associated
 * with a specific grave at recovery time. Binding goes by wall-clock time:
 * the snapshot's {@code capturedAt} against the grave's {@code getOwnerDeathTime()}, which Tombstone
 * sets from {@code System.currentTimeMillis()} - the same scale.
 *
 * <p>A matched snapshot is CONSUMED, so that a player with several unvisited graves gets
 * each one's own seating chart, not the seating chart from the last death.
 */
public final class SnapshotStore {

    private final List<SlotSnapshot> pending = new ArrayList<SlotSnapshot>();

    public void add(SlotSnapshot snapshot) {
        pending.add(snapshot);
    }

    public List<SlotSnapshot> all() {
        return Collections.unmodifiableList(pending);
    }

    /**
     * The snapshot nearest in time, removed from the store.
     *
     * @return null when nothing fits within the tolerance - then the standard path
     */
    public SlotSnapshot claimNearest(long graveDeathTime, long toleranceMillis) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            long distance = Math.abs(pending.get(i).capturedAt() - graveDeathTime);
            if (distance <= toleranceMillis && distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best < 0 ? null : pending.remove(best);
    }

    /**
     * Prunes from two sides: count and age. Without this, one death with no return to the grave
     * would leave an entry behind forever.
     */
    public void prune(long nowMillis, int maxEntries, long maxAgeMillis) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            if (nowMillis - pending.get(i).capturedAt() > maxAgeMillis) {
                pending.remove(i);
            }
        }
        while (pending.size() > maxEntries) {
            int oldest = 0;
            for (int i = 1; i < pending.size(); i++) {
                if (pending.get(i).capturedAt() < pending.get(oldest).capturedAt()) {
                    oldest = i;
                }
            }
            pending.remove(oldest);
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew test`
Expected: PASS, 19 tests.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core): SnapshotStore - binding by wall-clock time, consuming and pruning"
```

---

## Task 6: `core` — the four protection lists

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/StackView.java`
- Create: `src/main/java/com/spege/tombtweaks/core/ProtectionRules.java`
- Test: `src/test/java/com/spege/tombtweaks/core/ProtectionRulesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRulesTest {

    /** A stack stand-in - core does not know ItemStack and has nothing to know about it. */
    private static final class FakeStack implements StackView {
        private final String id;
        private final Set<String> enchants;
        private final Map<String, String> nbt;

        FakeStack(String id) {
            this(id, Collections.<String>emptySet(), Collections.<String, String>emptyMap());
        }

        FakeStack(String id, Set<String> enchants, Map<String, String> nbt) {
            this.id = id;
            this.enchants = enchants;
            this.nbt = nbt;
        }

        public String itemId() { return id; }
        public Set<String> enchantmentIds() { return enchants; }
        public String nbtString(String key) { return nbt.get(key); }
    }

    private static List<String> none() {
        return new ArrayList<String>();
    }

    @Test
    void emptyConfigProtectsNothing() {
        ProtectionRules rules = ProtectionRules.of(none(), none(), none(), none());
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
    }

    @Test
    void fullNameProtectsOnlyItself() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("minecraft:diamond_sword"), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_shovel")));
    }

    @Test
    void prefixCatchesWholeFamily() {
        ProtectionRules rules = ProtectionRules.of(
                none(), Arrays.asList("mymod:relic_"), none(), none());

        assertTrue(rules.isProtected(new FakeStack("mymod:relic_blade")));
        assertTrue(rules.isProtected(new FakeStack("mymod:relic_crown")));
        assertFalse(rules.isProtected(new FakeStack("mymod:common_blade")));
    }

    @Test
    void enchantmentProtectsRegardlessOfItem() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), Arrays.asList("mymod:soulbound"), none());

        Set<String> enchants = new HashSet<String>(Arrays.asList("mymod:soulbound"));
        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", enchants, Collections.<String, String>emptyMap())));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void nbtMatcherComparesValue() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(), Arrays.asList("properties=ashen_legacy"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("properties", "ashen_legacy");
        Map<String, String> different = new HashMap<String, String>();
        different.put("properties", "something_else");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), different)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void malformedNbtEntryIsIgnoredNotFatal() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(),
                Arrays.asList("no_equals", "=no_key", "key=", "good=entry"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("good", "entry");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void whitespaceInConfigIsForgiven() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("  minecraft:stick  ", "", "   "), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:stick")));
    }
}
```

- [ ] **Step 2: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class StackView`.

- [ ] **Step 3: Write `StackView`**

```java
package com.spege.tombtweaks.core;

import java.util.Set;

/**
 * What core knows about one stack. Implemented in the platform layer, so that core
 * does not have to know ItemStack.
 */
public interface StackView {

    /** The registry name, e.g. "minecraft:diamond_sword". Never null. */
    String itemId();

    /** The registry names of the stack's enchantments. An empty set when there are none. */
    Set<String> enchantmentIds();

    /** The value of a top-level string NBT key, or null when it is absent. */
    String nbtString(String key);
}
```

- [ ] **Step 4: Write `ProtectionRules`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a decaying grave has no right to eat.
 *
 * <p>Four independent rules; a stack is protected when ANY of them matches.
 *
 * <p>A rule that must not be broken: <b>an empty list protects nothing</b>. The opposite
 * interpretation would turn the default configuration into a grave that never loses anything,
 * that is, into a feature that looks like it works and does nothing.
 */
public final class ProtectionRules {

    private final Set<String> items;
    private final List<String> prefixes;
    private final Set<String> enchantments;
    private final List<String[]> nbtPairs;

    private ProtectionRules(Set<String> items, List<String> prefixes,
                            Set<String> enchantments, List<String[]> nbtPairs) {
        this.items = items;
        this.prefixes = prefixes;
        this.enchantments = enchantments;
        this.nbtPairs = nbtPairs;
    }

    public static ProtectionRules of(List<String> items, List<String> prefixes,
                                     List<String> enchantments, List<String> nbtStrings) {
        Set<String> parsedItems = new HashSet<String>();
        for (String raw : items) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedItems.add(value);
            }
        }

        List<String> parsedPrefixes = new ArrayList<String>();
        for (String raw : prefixes) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedPrefixes.add(value);
            }
        }

        Set<String> parsedEnchantments = new HashSet<String>();
        for (String raw : enchantments) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedEnchantments.add(value);
            }
        }

        List<String[]> parsedPairs = new ArrayList<String[]>();
        for (String raw : nbtStrings) {
            int equals = raw.indexOf('=');
            if (equals <= 0 || equals == raw.length() - 1) {
                continue; // a broken entry is ignored, never fatal
            }
            String key = raw.substring(0, equals).trim();
            String value = raw.substring(equals + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                parsedPairs.add(new String[] { key, value });
            }
        }

        return new ProtectionRules(parsedItems, parsedPrefixes, parsedEnchantments, parsedPairs);
    }

    public boolean isProtected(StackView stack) {
        String id = stack.itemId();

        if (items.contains(id)) {
            return true;
        }
        for (int i = 0; i < prefixes.size(); i++) {
            if (id.startsWith(prefixes.get(i))) {
                return true;
            }
        }
        if (!enchantments.isEmpty()) {
            for (String enchantment : stack.enchantmentIds()) {
                if (enchantments.contains(enchantment)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < nbtPairs.size(); i++) {
            String[] pair = nbtPairs.get(i);
            String value = stack.nbtString(pair[0]);
            if (value != null && value.equals(pair[1])) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test`
Expected: PASS, 26 tests.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(core): ProtectionRules - four lists, an empty list protects nothing"
```

---

## Task 7: `core` — schedule and victim selection

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/DecayVictim.java`
- Test: `src/test/java/com/spege/tombtweaks/core/DecayScheduleTest.java`
- Test: `src/test/java/com/spege/tombtweaks/core/DecayVictimTest.java`

- [ ] **Step 1: Write the schedule test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DecayScheduleTest {

    private static final long START = 6_000L;
    private static final long INTERVAL = 1_200L;

    @Test
    void nothingBeforeThreshold() {
        assertFalse(DecaySchedule.shouldDecay(0L, START, INTERVAL));
        assertFalse(DecaySchedule.shouldDecay(START - 1L, START, INTERVAL));
    }

    @Test
    void firstFiringExactlyAtThreshold() {
        assertTrue(DecaySchedule.shouldDecay(START, START, INTERVAL));
    }

    @Test
    void exactlyOneFiringPerInterval() {
        int fired = 0;
        for (long tick = START; tick < START + 10L * INTERVAL; tick++) {
            if (DecaySchedule.shouldDecay(tick, START, INTERVAL)) {
                fired++;
            }
        }
        assertEquals(10, fired);
    }

    @Test
    void zeroIntervalDoesNotDivideByZero() {
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, 0L));
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, -5L));
    }

    @Test
    void rewoundCounterStaysSilent() {
        // countTicks can return to zero when the grave is placed anew.
        assertFalse(DecaySchedule.shouldDecay(10L, START, INTERVAL));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests '*DecayScheduleTest*'`
Expected: PASS — `DecaySchedule` was already created in Task 2, so these tests pass right away. That is deliberate: they confirm behaviour nobody has checked yet.

- [ ] **Step 3: Write the failing victim-selection test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayVictimTest {

    /** Deterministic "randomness": always the first element. */
    private static final IntUnaryOperator FIRST = bound -> 0;
    /** Always the last. */
    private static final IntUnaryOperator LAST = bound -> bound - 1;

    private static List<Integer> none() {
        return new ArrayList<Integer>();
    }

    @Test
    void emptyGraveGivesUpNothing() {
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), true, FIRST));
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), false, FIRST));
    }

    @Test
    void unprotectedGoFirst() {
        List<Integer> unprotected = Arrays.asList(3, 4);
        List<Integer> guarded = Arrays.asList(9);

        assertEquals(3, DecayVictim.pick(unprotected, guarded, true, FIRST));
        assertEquals(4, DecayVictim.pick(unprotected, guarded, true, LAST));
    }

    @Test
    void onlyProtectedWithExemptionMeansStop() {
        assertEquals(DecayVictim.NOTHING,
                     DecayVictim.pick(none(), Arrays.asList(9, 10), true, FIRST));
    }

    @Test
    void onlyProtectedWithOrderingMeansProtectedGetsEaten() {
        assertEquals(9, DecayVictim.pick(none(), Arrays.asList(9, 10), false, FIRST));
        assertEquals(10, DecayVictim.pick(none(), Arrays.asList(9, 10), false, LAST));
    }

    @Test
    void pickStaysInRange() {
        List<Integer> unprotected = Arrays.asList(1, 2, 3);
        for (int i = 0; i < 3; i++) {
            final int fixed = i;
            int picked = DecayVictim.pick(unprotected, none(), true, bound -> fixed);
            assertTrue(unprotected.contains(picked));
        }
    }
}
```

- [ ] **Step 4: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class DecayVictim`.

- [ ] **Step 5: Write `DecayVictim`**

```java
package com.spege.tombtweaks.core;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Which occupied slot a decaying grave gives up next.
 *
 * <p>Two candidate lists, not one. Unprotected stacks are eaten first; protected ones
 * are not reachable at all as long as anything else is left. When only they are left, it is decided by
 * {@code neverDecay}: true = protection is an exemption (the grave stops where it is),
 * false = protection was only an ordering.
 */
public final class DecayVictim {

    public static final int NOTHING = -1;

    private DecayVictim() {
    }

    /**
     * @param unprotected indices of slots holding an unprotected stack
     * @param guarded     indices of slots holding a protected stack
     * @param neverDecay  whether protection is an exemption or only an ordering
     * @param random      bound -&gt; a value from the range [0, bound)
     */
    public static int pick(List<Integer> unprotected, List<Integer> guarded,
                           boolean neverDecay, IntUnaryOperator random) {
        List<Integer> pool = unprotected;
        if (pool.isEmpty()) {
            if (guarded.isEmpty() || neverDecay) {
                return NOTHING;
            }
            pool = guarded;
        }
        return pool.get(random.applyAsInt(pool.size())).intValue();
    }
}
```

> `IntUnaryOperator` is in `java.util.function`, present since Java 8 — it does not break rule 1.

- [ ] **Step 6: Run the tests**

Run: `./gradlew test`
Expected: PASS, 36 tests.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(core): DecayVictim + decay schedule tests"
```

---

## Task 8: `core` — cooldowns

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/CooldownRules.java`
- Create: `src/main/java/com/spege/tombtweaks/core/Cooldown.java`
- Test: `src/test/java/com/spege/tombtweaks/core/CooldownTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTest {

    private static final long MINUTE = 60_000L;

    @Test
    void parserReadsNameMinutesPairs() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "tombstone:book_of_disenchantment;6",
                "tombstone:book_of_magic_impregnation;12"));

        assertEquals(6L * MINUTE, rules.millisFor("tombstone:book_of_disenchantment"));
        assertEquals(12L * MINUTE, rules.millisFor("tombstone:book_of_magic_impregnation"));
    }

    @Test
    void unknownBookHasNoCooldown() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;5"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_oblivion"));
    }

    @Test
    void zeroMinutesMeansDisabled() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;0"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void malformedEntriesAreSkippedNotFatal() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "no_semicolon",
                "tombstone:book_of_scribe;not_a_number",
                ";7",
                "tombstone:book_of_scribe;5"));

        assertEquals(5L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void minutesAreClampedToTwelveHours() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;9000"));
        assertEquals(720L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void negativeMinutesMeanNoCooldown() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;-3"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void emptyConfigIsEmpty() {
        assertTrue(CooldownRules.parse(new ArrayList<String>()).isEmpty());
    }

    @Test
    void remainingTimeShrinksToZero() {
        long lastUse = 1_000_000L;
        long cooldown = 10L * MINUTE;

        assertEquals(cooldown, Cooldown.remaining(lastUse, cooldown, lastUse));
        assertEquals(cooldown - MINUTE, Cooldown.remaining(lastUse, cooldown, lastUse + MINUTE));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown + 1L));
    }

    @Test
    void noUseMeansNoCooldown() {
        assertEquals(0L, Cooldown.remaining(0L, 10L * MINUTE, 5_000_000L));
        assertFalse(Cooldown.isActive(0L, 10L * MINUTE, 5_000_000L));
    }

    @Test
    void rewoundClockDoesNotBlockPlayer() {
        // The system clock can go backwards (a time change, NTP synchronisation).
        // That must not lock the player out of the book forever.
        assertEquals(0L, Cooldown.remaining(5_000_000L, 10L * MINUTE, 1_000L));
        assertFalse(Cooldown.isActive(5_000_000L, 10L * MINUTE, 1_000L));
    }

    @Test
    void activeWhileAnyTimeRemains() {
        long lastUse = 1_000_000L;
        assertTrue(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + MINUTE));
        assertFalse(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + 10L * MINUTE));
    }
}
```

- [ ] **Step 2: Run the test, confirm it does not compile**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class CooldownRules`.

- [ ] **Step 3: Write `CooldownRules`**

```java
package com.spege.tombtweaks.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How long each book's cooldown lasts.
 *
 * <p>Entry format: {@code registry_name;minutes}. A broken entry is skipped, never fatal -
 * a config error has no right to bring the server down. Minutes are clamped to 12 hours, because
 * a larger value is almost certainly a mistake, not an intention.
 */
public final class CooldownRules {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MAX_MINUTES = 720L;

    private final Map<String, Long> millisById;

    private CooldownRules(Map<String, Long> millisById) {
        this.millisById = millisById;
    }

    public static CooldownRules parse(List<String> entries) {
        Map<String, Long> parsed = new HashMap<String, Long>();
        for (String raw : entries) {
            int separator = raw.indexOf(';');
            if (separator <= 0 || separator == raw.length() - 1) {
                continue;
            }
            String id = raw.substring(0, separator).trim();
            if (id.isEmpty()) {
                continue;
            }
            long minutes;
            try {
                minutes = Long.parseLong(raw.substring(separator + 1).trim());
            } catch (NumberFormatException malformed) {
                continue;
            }
            if (minutes <= 0L) {
                continue;
            }
            if (minutes > MAX_MINUTES) {
                minutes = MAX_MINUTES;
            }
            parsed.put(id, Long.valueOf(minutes * MILLIS_PER_MINUTE));
        }
        return new CooldownRules(parsed);
    }

    /** @return the cooldown length in ms, or 0 when that book has none */
    public long millisFor(String itemId) {
        Long millis = millisById.get(itemId);
        return millis == null ? 0L : millis.longValue();
    }

    public boolean isEmpty() {
        return millisById.isEmpty();
    }
}
```

- [ ] **Step 4: Write `Cooldown`**

```java
package com.spege.tombtweaks.core;

/** Remaining-time arithmetic. Wall clock in milliseconds. */
public final class Cooldown {

    private Cooldown() {
    }

    /**
     * @return how many ms are left, 0 when it is free
     */
    public static long remaining(long lastUseMillis, long cooldownMillis, long nowMillis) {
        if (cooldownMillis <= 0L || lastUseMillis <= 0L) {
            return 0L;
        }
        if (nowMillis < lastUseMillis) {
            return 0L; // the clock went backwards - we do not lock the player out forever
        }
        long elapsed = nowMillis - lastUseMillis;
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    public static boolean isActive(long lastUseMillis, long cooldownMillis, long nowMillis) {
        return remaining(lastUseMillis, cooldownMillis, nowMillis) > 0L;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test`
Expected: PASS, 47 tests. All of `core` is done.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(core): CooldownRules + Cooldown, robust against a broken config and a rewound clock"
```

---

## Task 9: `platform` — config

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/platform/Config.java`
- Modify: `src/main/java/com/spege/tombtweaks/TombTweaks.java`

- [ ] **Step 1: Write `Config`**

```java
package com.spege.tombtweaks.platform;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Three categories, file config/tombtweaks-common.toml.
 *
 * <p>No master switch: with three features that have their own toggles it was just
 * extra state to get stuck in.
 *
 * <p>The default protection lists are EMPTY. The ones from 1.12.2 belong to a specific pack,
 * and a public mod has no right to assume them.
 */
public final class Config {

    public final ForgeConfigSpec.BooleanValue restoreEnabled;
    public final ForgeConfigSpec.BooleanValue restoreDebugLogging;

    public final ForgeConfigSpec.BooleanValue decayEnabled;
    public final ForgeConfigSpec.IntValue decayStartTicks;
    public final ForgeConfigSpec.IntValue decayIntervalTicks;
    public final ForgeConfigSpec.IntValue decayMaxHistory;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItems;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItemPrefixes;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedEnchantments;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedNbtStrings;
    public final ForgeConfigSpec.BooleanValue decayProtectedNeverDecay;

    public final ForgeConfigSpec.BooleanValue cooldownEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> cooldownBooks;

    private Config(ForgeConfigSpec.Builder builder) {
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
                         "chunk is unloaded. Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
                .defineInRange("startTicks", 6000, 0, Integer.MAX_VALUE);
        decayIntervalTicks = builder
                .comment("Ticks between each item removal. 1200 = 60 seconds. 0 disables decay.")
                .defineInRange("intervalTicks", 1200, 0, Integer.MAX_VALUE);
        decayMaxHistory = builder
                .comment("Max number of decay records kept per player.")
                .defineInRange("maxHistory", 10, 0, 1000);
        decayProtectedItems = builder
                .comment("Full registry names that decay must not eat, e.g. \"mymod:relic_blade\".",
                         "An empty list protects NOTHING.")
                .defineList("protectedItems", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedItemPrefixes = builder
                .comment("Registry-name prefixes, e.g. \"mymod:relic_\".")
                .defineList("protectedItemPrefixes", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedEnchantments = builder
                .comment("Registry names of enchantments that protect whatever carries them.")
                .defineList("protectedEnchantments", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedNbtStrings = builder
                .comment("Top-level string NBT tags, as \"key=value\". A malformed entry is ignored.")
                .defineList("protectedNbtStrings", Collections.<String>emptyList(), o -> o instanceof String);
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
                         "Any Tombstone book works here, not just the two below.")
                .defineList("books", Arrays.asList(
                        "tombstone:book_of_disenchantment;6",
                        "tombstone:book_of_magic_impregnation;6"), o -> o instanceof String);
        builder.pop();
    }

    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
```

- [ ] **Step 2: Register the config in the `@Mod` class**

Replace the contents of `TombTweaks.java`:

```java
package com.spege.tombtweaks;

import com.spege.tombtweaks.platform.Config;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "tombtweaks-common.toml");
        LOGGER.info("[TombTweaks] {} loading", VERSION);
    }
}
```

- [ ] **Step 3: Build and launch the client**

Run: `./gradlew runClient`
Expected: the client starts, and `run/config/` gets a `tombtweaks-common.toml` with three categories and `[decay] enabled = false`. Check the file, close the client.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(platform): ForgeConfigSpec with three categories"
```

---

## Task 10: `platform` — adapters and codec

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/platform/StackViews.java`
- Create: `src/main/java/com/spege/tombtweaks/platform/PlayerData.java`
- Create: `src/main/java/com/spege/tombtweaks/platform/SnapshotCodec.java`

- [ ] **Step 1: Write `StackViews`**

```java
package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.StackView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

    public static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? UNKNOWN : id.toString();
    }

    public static ItemKey keyOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new ItemKey(idOf(stack), tag == null ? 0 : tag.hashCode());
    }

    public static StackView view(final ItemStack stack) {
        return new StackView() {
            @Override
            public String itemId() {
                return idOf(stack);
            }

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
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
                    return null;
                }
                return tag.getString(key);
            }
        };
    }
}
```

- [ ] **Step 2: Write `PlayerData`**

```java
package com.spege.tombtweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * The only player-data sub-tag that Forge carries across death.
 * Everything that has to survive a respawn must live here.
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
```

- [ ] **Step 3: Write `SnapshotCodec`**

```java
package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotEntry;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** SnapshotStore both ways through the player's NBT. */
public final class SnapshotCodec {

    private static final String ROOT = "tombtweaks_slot_snapshots";

    private SnapshotCodec() {
    }

    public static SnapshotStore load(Player player) {
        SnapshotStore store = new SnapshotStore();
        CompoundTag persisted = PlayerData.persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_LIST)) {
            return store;
        }
        ListTag snapshots = persisted.getList(ROOT, Tag.TAG_COMPOUND);
        for (int i = 0; i < snapshots.size(); i++) {
            CompoundTag tag = snapshots.getCompound(i);
            List<SlotEntry> entries = new ArrayList<>();
            ListTag seats = tag.getList("e", Tag.TAG_COMPOUND);
            for (int s = 0; s < seats.size(); s++) {
                CompoundTag seat = seats.getCompound(s);
                entries.add(new SlotEntry(
                        seat.getInt("s"),
                        new ItemKey(seat.getString("i"), seat.getInt("h")),
                        seat.getInt("n")));
            }
            store.add(new SlotSnapshot(tag.getLong("c"), entries));
        }
        return store;
    }

    public static void save(Player player, SnapshotStore store) {
        ListTag snapshots = new ListTag();
        for (SlotSnapshot snapshot : store.all()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("c", snapshot.capturedAt());
            ListTag seats = new ListTag();
            for (SlotEntry entry : snapshot.entries()) {
                CompoundTag seat = new CompoundTag();
                seat.putInt("s", entry.slot());
                seat.putString("i", entry.key().id());
                seat.putInt("h", entry.key().nbtHash());
                seat.putInt("n", entry.count());
                seats.add(seat);
            }
            tag.put("e", seats);
            snapshots.add(tag);
        }
        PlayerData.persisted(player).put(ROOT, snapshots);
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(platform): StackViews, PlayerData and SnapshotCodec"
```

---

## Task 11: Feature — exact-slot restore

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/feature/restore/SnapshotCaptureHandler.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/restore/SlotRestoreHandler.java`
- Modify: `src/main/java/com/spege/tombtweaks/TombTweaks.java`

- [ ] **Step 1: Write `SnapshotCaptureHandler`**

```java
package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Records the inventory layout at the last moment when it is still the truth.
 *
 * <p>{@code Player.die()} empties the inventory right after this event, and everything that reaches
 * {@code LivingDropsEvent} is already a flat pile with no indices.
 *
 * <p>Priority HIGHEST, so that a handler cancelling the death does not get in before us. A snapshot for a
 * death that did not happen costs one unused entry, which the store prunes.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SnapshotCaptureHandler {

    private static final int MAX_PENDING = 5;
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 days

    private SnapshotCaptureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        long now = System.currentTimeMillis();
        SlotSnapshot snapshot = new SlotSnapshot(now);
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size(); i++) {
            record(snapshot, i, inventory.items.get(i));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            record(snapshot, SlotSnapshot.ARMOR_BASE + i, inventory.armor.get(i));
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            record(snapshot, SlotSnapshot.OFFHAND_SLOT + i, inventory.offhand.get(i));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        store.add(snapshot);
        store.prune(now, MAX_PENDING, MAX_AGE_MILLIS);
        SnapshotCodec.save(player, store);

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] recorded {} seats for {} at {}",
                    snapshot.entries().size(), player.getGameProfile().getName(), now);
        }
    }

    private static void record(SlotSnapshot snapshot, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        snapshot.add(slot, StackViews.keyOf(stack), stack.getCount());
    }
}
```

- [ ] **Step 2: Write `SlotRestoreHandler`**

```java
package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotPlan;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.api.event.RestoreInventoryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the contents of a recovered grave back where they came from.
 *
 * <p>No mixin. Tombstone fires {@code RestoreInventoryEvent} at the one useful moment:
 * after chanceLossOnDeath has taken its share, and before auto-equip and the whole rest of the
 * distribution. We get the grave's live ItemStackHandler, so an item taken out here simply
 * does not exist for the standard path.
 *
 * <p>The seating is computed by {@link SlotPlan#assign} for the whole grave at once (two passes), because
 * Tombstone merges graves: a second death within 20 blocks adds items to the old grave,
 * and that one binds only to the snapshot of the last death.
 *
 * <p>Every insertion is conditional on the target slot being empty, re-checked right before
 * inserting. It is that condition - not {@code NO_SEAT} - that guarantees the worst case
 * is the standard behaviour, never a lost or duplicated item.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SlotRestoreHandler {

    /** How many ms may separate the snapshot's capturedAt from the grave's deathDate. */
    private static final long TOLERANCE_MILLIS = 10_000L;

    private SlotRestoreHandler() {
    }

    @SubscribeEvent
    public static void onRestoreInventory(RestoreInventoryEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        SlotSnapshot snapshot = store.claimNearest(event.getOwnerDeathTime(), TOLERANCE_MILLIS);
        if (snapshot == null) {
            if (Config.INSTANCE.restoreDebugLogging.get()) {
                TombTweaks.LOGGER.info("[TombTweaks] no snapshot within {} ms of grave death time {}",
                        TOLERANCE_MILLIS, event.getOwnerDeathTime());
            }
            return;
        }
        SnapshotCodec.save(player, store);

        IItemHandler grave = event.getInventory();
        Inventory inventory = player.getInventory();

        List<ItemKey> graveKeys = new ArrayList<>(grave.getSlots());
        for (int i = 0; i < grave.getSlots(); i++) {
            ItemStack inGrave = grave.getStackInSlot(i);
            graveKeys.add(inGrave.isEmpty() ? null : StackViews.keyOf(inGrave));
        }
        int[] seats = SlotPlan.assign(snapshot, graveKeys, seat -> isSeatFree(inventory, seat));

        int seated = 0;
        for (int i = 0; i < seats.length; i++) {
            int seat = seats[i];
            // The re-check is a condition of the invariant, not extra caution.
            if (seat == SlotPlan.NO_SEAT || !isSeatFree(inventory, seat)) {
                continue;
            }
            ItemStack inGrave = grave.getStackInSlot(i);
            if (inGrave.isEmpty()) {
                continue;
            }
            ItemStack taken = grave.extractItem(i, inGrave.getCount(), false);
            if (taken.isEmpty()) {
                continue;
            }
            place(inventory, seat, taken);
            seated++;
        }

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] seated {} of {} recorded stacks for {}",
                    seated, snapshot.entries().size(), player.getGameProfile().getName());
        }
    }

    private static boolean isSeatFree(Inventory inventory, int seat) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            return inventory.offhand.get(0).isEmpty();
        }
        if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            return inventory.armor.get(seat - SlotSnapshot.ARMOR_BASE).isEmpty();
        }
        if (seat >= 0 && seat < inventory.items.size()) {
            return inventory.items.get(seat).isEmpty();
        }
        return false; // e.g. the reserved Curios space - unsupported in v1
    }

    private static void place(Inventory inventory, int seat, ItemStack stack) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            inventory.offhand.set(0, stack);
        } else if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            inventory.armor.set(seat - SlotSnapshot.ARMOR_BASE, stack);
        } else {
            inventory.items.set(seat, stack);
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game verification**

Run: `./gradlew runClient`

Do this in game:
1. `/gamerule keepInventory false`
2. Set up a distinctive layout: a sword in slot 1, bread in slot 9, something in the offhand, full armor, one empty slot in the middle of the hotbar.
3. Kill yourself (`/kill`).
4. Go back to the grave and recover it.

Expected: everything comes back **to its own place**, including armor and offhand; the empty slot stays empty.

Repeat with `debugLogging = true` and check the log for a pair of lines `recorded N seats` / `seated N of M`.

- [ ] **Step 5: Verify `isReverseInventorySorting`**

In Tombstone's settings GUI, check whether "reverse inventory sorting" is off by default. Turn it on, repeat the test from Step 4 and **note the result in the README** — it is the only open assumption from the spec. Do not fix it now.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(restore): exact-slot grave restore on RestoreInventoryEvent, no mixin"
```

---

## Task 12: Feature — grave decay

**Files:**
- Modify: `src/main/resources/mixins.tombtweaks.json` (add an entry to the empty list created in Task 2)
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinBlockEntityPlayerGrave.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/decay/GraveDecayService.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/decay/DecayHistory.java`

- [ ] **Step 1: Add the mixin to `mixins.tombtweaks.json`**

The file has existed since Task 2 with an empty list. Change only the `mixins` list to:

```json
  "mixins": [
    "MixinBlockEntityPlayerGrave"
  ],
```

> Add **only** the mixin from this task. With `"required": true`, a class that is listed but does not exist is a hard crash at startup — the two cooldown mixins will be added by Task 13 Step 1, once they exist.

- [ ] **Step 2: Write `DecayHistory`**

```java
package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * What the grave has already lost.
 *
 * <p>The 1.12.2 version wrote the history by looking the player up BY NAME, so the grave of
 * an offline player decayed without a trace - exactly the scenario the feature
 * was created for. Here the key is the UUID, and for an offline player the entry goes only to the log for now.
 */
public final class DecayHistory {

    private static final String KEY = "tombtweaks_decay_history";

    private DecayHistory() {
    }

    public static void record(Level level, BlockPos pos, UUID ownerId, ItemStack lost) {
        MinecraftServer server = level.getServer();
        if (server == null || ownerId == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            com.spege.tombtweaks.TombTweaks.LOGGER.info(
                    "[TombTweaks] grave at {} lost {} x{} (owner {} offline)",
                    pos, lost.getDescriptionId(), lost.getCount(), ownerId);
            return;
        }

        CompoundTag persisted = PlayerData.persisted(owner);
        ListTag history = persisted.getList(KEY, Tag.TAG_COMPOUND);

        CompoundTag entry = new CompoundTag();
        entry.putLong("at", System.currentTimeMillis());
        entry.putString("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "," + level.dimension().location());
        entry.put("stack", lost.save(new CompoundTag()));
        history.add(entry);

        int max = Config.INSTANCE.decayMaxHistory.get();
        while (history.size() > max) {
            history.remove(0);
        }
        persisted.put(KEY, history);
    }
}
```

- [ ] **Step 3: Write `GraveDecayService`**

```java
package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.core.DecaySchedule;
import com.spege.tombtweaks.core.DecayVictim;
import com.spege.tombtweaks.core.ProtectionRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

import java.util.ArrayList;
import java.util.List;

/** One tick of one grave. All decisions are made by core; this is only the execution. */
public final class GraveDecayService {

    private GraveDecayService() {
    }

    public static void tick(Level level, BlockPos pos, BlockEntityPlayerGrave grave) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.decayEnabled.get()) {
            return;
        }
        if (!DecaySchedule.shouldDecay(grave.countTicks,
                                       Config.INSTANCE.decayStartTicks.get(),
                                       Config.INSTANCE.decayIntervalTicks.get())) {
            return;
        }

        IItemHandler inventory = grave.getInventory();
        if (inventory == null) {
            return;
        }

        // ProtectionRules.of takes List<?> and skips nulls and non-Strings by itself,
        // so the config values go straight in, without casting.
        ProtectionRules rules = ProtectionRules.of(
                Config.INSTANCE.decayProtectedItems.get(),
                Config.INSTANCE.decayProtectedItemPrefixes.get(),
                Config.INSTANCE.decayProtectedEnchantments.get(),
                Config.INSTANCE.decayProtectedNbtStrings.get());

        List<Integer> unprotected = new ArrayList<>();
        List<Integer> guarded = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (rules.isProtected(StackViews.view(stack))) {
                guarded.add(Integer.valueOf(slot));
            } else {
                unprotected.add(Integer.valueOf(slot));
            }
        }

        int victim = DecayVictim.pick(unprotected, guarded,
                Config.INSTANCE.decayProtectedNeverDecay.get().booleanValue(),
                bound -> level.random.nextInt(bound));
        if (victim == DecayVictim.NOTHING) {
            return;
        }

        ItemStack lost = inventory.extractItem(victim, inventory.getStackInSlot(victim).getCount(), false);
        if (lost.isEmpty()) {
            return;
        }
        // The grave's inventory is a bare ItemStackHandler with no onContentsChanged - the extraction
        // alone does NOT mark the block entity as changed. Without this the chunk may be saved with the old
        // grave state, and the dropped item (an entity, saved separately) survives a restart:
        // after loading it is both on the ground and back in the grave. Duplication.
        // (Added 2026-10-02 after checking the bytecode of the BlockEntityPlayerGrave constructor.)
        grave.setChanged();

        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, lost);
        DecayHistory.record(level, pos, grave.getOwnerId(), lost);
    }
}
```

- [ ] **Step 4: Write the mixin**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.decay.GraveDecayService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

/**
 * Hooks decay into the grave's own ticker.
 *
 * <p>remap = false, because serverTick is a Tombstone method whose name is not obfuscated.
 */
@Mixin(BlockEntityPlayerGrave.class)
public class MixinBlockEntityPlayerGrave {

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void tombtweaks$decay(Level level, BlockPos pos, BlockState state,
                                         BlockEntityPlayerGrave grave, CallbackInfo ci) {
        GraveDecayService.tick(level, pos, grave);
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. If it fails with `Scanned 0 target(s)` — check with `javap -p ovh/corail/tombstone/block/entity/BlockEntityPlayerGrave.class` that the `serverTick` signature has not changed.

- [ ] **Step 6: In-game verification**

Run: `./gradlew runClient`

1. In `run/config/tombtweaks-common.toml` set `[decay] enabled = true`, `startTicks = 100`, `intervalTicks = 40`. Restart the client.
2. Check the log for the line `Mixing MixinBlockEntityPlayerGrave from mixins.tombtweaks.json into ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave`. **No such line = the mixin did not work, even if the game started.**
3. Die with a full inventory, stand by the grave and do not open it.

Expected: roughly every 2 seconds one stack falls out of the grave onto the ground.

4. Add `"minecraft:diamond_sword"` to `protectedItems`, repeat. The sword must be left for last, and with `protectedNeverDecay = true` — be left forever.
5. Log out of the server (single player: exit to the menu), come back — the grave should continue decaying from where it stopped, with no reset.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(decay): grave decay on its own ticker, stateless schedule, history keyed by UUID"
```

---

## Task 13: Feature — book cooldowns

> **Correction 2026-10-02 — the code below is partly outdated.** The cooldown was keyed by `StackViews.idOf(stack)` and started *after* a successful `setEnchant`. The book is consumed on use, and an empty `ItemStack` in 1.20.1 reports its item as `minecraft:air` — for a single book the cooldown would silently not start. In the implementation the key is the book's **`Item` itself**: `(Item) (Object) this` in `MixinItemBook` and the `ISoulConsumer` receiver (checked with `instanceof Item`) in `MixinBlockDecorativeGrave`. `BookCooldownService.remaining/start` take `Item`, and `StackViews` gained an `idOf(Item)` overload.

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/feature/cooldown/BookCooldownService.java`
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinItemBook.java`
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinBlockDecorativeGrave.java`
- Create: `src/main/resources/assets/tombtweaks/lang/en_us.json`
- Modify: `src/main/resources/mixins.tombtweaks.json`

- [ ] **Step 1: Add the two mixins to `mixins.tombtweaks.json`**

The `mixins` list should now contain all three:

```json
  "mixins": [
    "MixinBlockEntityPlayerGrave",
    "MixinItemBook",
    "MixinBlockDecorativeGrave"
  ],
```

- [ ] **Step 2: Write `BookCooldownService`**

```java
package com.spege.tombtweaks.feature.cooldown;

import com.spege.tombtweaks.core.Cooldown;
import com.spege.tombtweaks.core.CooldownRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Reading and writing one book's cooldown. Wall clock, the same scale as Tombstone. */
public final class BookCooldownService {

    private static final String PREFIX = "tombtweaks_cooldown_";

    private BookCooldownService() {
    }

    private static CooldownRules rules() {
        return CooldownRules.parse(Config.INSTANCE.cooldownBooks.get());
    }

    /** @return how many ms are left, 0 when it is free or when the feature is disabled */
    public static long remaining(Player player, ItemStack book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return 0L;
        }
        String id = StackViews.idOf(book);
        long length = rules().millisFor(id);
        if (length <= 0L) {
            return 0L;
        }
        CompoundTag persisted = PlayerData.persisted(player);
        return Cooldown.remaining(persisted.getLong(PREFIX + id), length, System.currentTimeMillis());
    }

    public static void start(Player player, ItemStack book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return;
        }
        String id = StackViews.idOf(book);
        if (rules().millisFor(id) <= 0L) {
            return;
        }
        PlayerData.persisted(player).putLong(PREFIX + id, System.currentTimeMillis());
    }

    public static void tellRemaining(Player player, long remainingMillis) {
        long seconds = (remainingMillis + 999L) / 1000L;
        player.displayClientMessage(
                Component.translatable("message.tombtweaks.book_cooldown", seconds), true);
    }
}
```

- [ ] **Step 3: Write `MixinItemBook` (the block)**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Blocks using a book during its cooldown.
 *
 * <p>It sits on the base class of all the books, so it works regardless of where
 * someone calls it from. remap = false: canEnchant is a Tombstone method.
 */
@Mixin(ItemBook.class)
public class MixinItemBook {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$blockDuringCooldown(Level level, BlockPos pos, Player player,
                                                ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || level.isClientSide() || player == null) {
            return;
        }
        long remaining = BookCooldownService.remaining(player, stack);
        if (remaining > 0L) {
            BookCooldownService.tellRemaining(player, remaining);
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
```

- [ ] **Step 4: Write `MixinBlockDecorativeGrave` (cooldown start)**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.capability.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;

/**
 * Starts the cooldown exactly when the use of the book succeeded.
 *
 * <p>ISoulConsumer.setEnchant is called from one place in all of Tombstone - hence
 * a single redirect covers all the books.
 *
 * <p>Two levels of remapping: the selector method = "use" targets a Minecraft method
 * (BlockBehaviour.use), so it MUST be remapped - and that is the reason why
 * refmaps are enabled in this project. The inner @At targets a Tombstone member
 * and gets its own remap = false.
 *
 * <p>The redirect's receiver is ISoulConsumer, because the call is an invokeinterface on that
 * interface - not on a concrete book.
 */
@Mixin(BlockDecorativeGrave.class)
public class MixinBlockDecorativeGrave {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/capability/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/level/Level;"
                           + "Lnet/minecraft/core/BlockPos;"
                           + "Lnet/minecraft/server/level/ServerPlayer;"
                           + "Lnet/minecraft/world/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/capability/ISoulConsumer$ConsumeResult;",
                    remap = false))
    private ISoulConsumer.ConsumeResult tombtweaks$startCooldown(ISoulConsumer consumer,
                                                                 Level level, BlockPos pos,
                                                                 ServerPlayer player,
                                                                 ItemStack stack, int soulStrength) {
        ISoulConsumer.ConsumeResult result = consumer.setEnchant(level, pos, player, stack, soulStrength);
        if (result != null && result.result() == ISoulConsumer.ConsumeResult.Result.SUCCESS) {
            BookCooldownService.start(player, stack);
        }
        return result;
    }
}
```

- [ ] **Step 5: Write the language file**

`src/main/resources/assets/tombtweaks/lang/en_us.json`:

```json
{
  "message.tombtweaks.book_cooldown": "This book is still recovering — %s s left."
}
```

- [ ] **Step 6: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. If the redirect fails with `Scanned 0 target(s)`, check the descriptor:

```bash
"/c/Program Files/Java/jdk-21.0.11/bin/javap" -p -c -cp libs/tombstone-1.20.1-9.1.4.jar ovh.corail.tombstone.block.BlockDecorativeGrave | grep setEnchant
```

- [ ] **Step 7: In-game verification**

Run: `./gradlew runClient`

1. Check the log for **two** lines, `Mixing MixinItemBook …` and `Mixing MixinBlockDecorativeGrave …`.
2. Get a Book of Disenchantment and a decorative grave with a soul (`/give` + creative mode).
3. Use the book — it must work.
4. Use it a second time immediately — it must refuse, with a message above the hotbar and a sensible number of seconds.
5. Exit to the menu and come back — the cooldown must keep running, not reset.
6. Set `books = ["tombstone:book_of_disenchantment;0"]` — the cooldown must disappear.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(cooldown): block on ItemBook + start on the single setEnchant call"
```

---

## Task 14: README and the 0.1.0 release

**Files:**
- Create: `E:\Isuth\tombtweaks-1201\README.md`
- Create: `E:\Isuth\tombtweaks-1201\docs\superpowers\specs\2026-08-14-tombtweaks-1201-port-design.md` (copy of the spec)

- [ ] **Step 1: Copy the spec into the new repo**

```bash
mkdir -p /e/Isuth/tombtweaks-1201/docs/superpowers/specs
cp /e/Isuth/modDev/docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md /e/Isuth/tombtweaks-1201/docs/superpowers/specs/
```

- [ ] **Step 2: Write `README.md`**

```markdown
# TombTweaks (Minecraft 1.20.1)

Three tweaks for Corail Tombstone 9.1.4, each independently switchable off.

| Feature | What it does | Default |
|---|---|---|
| `[restore]` | A recovered grave gives items back **to the same slots** they died in — including armor and offhand | on |
| `[decay]` | An unvisited grave slowly loses its contents; four protection lists exempt chosen items from this | **off** |
| `[cooldown]` | The magic books get a cooldown, configurable per book | on |

Config: `config/tombtweaks-common.toml`.

## Building

```bash
./gradlew build      # jar in build/libs/
./gradlew test       # tests of the core package on a bare JVM
./gradlew runClient  # development client
```

Requires JDK 17 — Gradle downloads it by itself via the toolchain.

## Architecture

Three layers: `core` (zero Minecraft types, all the decision logic, tested with JUnit),
`platform` (adapters), `feature` (handlers and three mixins). `core` is deliberately written
in Java 8 syntax, so that the port to 1.16.5 is copy-paste.

Full rationale for the decisions: `docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md`.

## Known limitations

- **Curios unsupported** in slot restore (the slot space 200+ is reserved).
  Tombstone has its own `curioAutoEquip`.
- **`reverse inventory sorting`** — a per-player Tombstone preference, run after our
  event; when it is on, it reverses the recovered layout. Verification result: <FILL IN in Task 11 Step 5>.
- The decay history for an **offline** player goes only to the server log for now.
```

- [ ] **Step 3: Fill in the verification result from Task 11 Step 5**

Write into the README what happens with `reverse inventory sorting` turned on. If it turns out to break restore — **do not fix it now**, add it as a known limitation. That is material for a separate task.

- [ ] **Step 4: Full test run and build**

Run: `./gradlew clean build`
Expected: `BUILD SUCCESSFUL`, 47 tests PASS, jar in `build/libs/tombtweaks-1.20.1-0.1.0.jar`.

- [ ] **Step 5: Commit and tag**

```bash
git add -A
git commit -m "docs: README + copy of the spec; release 0.1.0"
git tag v0.1.0
```

---

## What this plan deliberately does NOT do

Everything below is in spec §8 with its rationale. Do not add it along the way:

Curios in slot restore, perk tuning, effect whitelists, a grave_dust nerf, removal of the
grave key recipe, a Curse of Possession patch, first-kill reward, raider alignment, integrations with Enigmatic
Legacy and EBW Redux, the port to 1.16.5, a workaround for `reverse inventory sorting`.

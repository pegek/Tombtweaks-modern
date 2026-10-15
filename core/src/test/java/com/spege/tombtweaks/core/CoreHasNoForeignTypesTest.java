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
 * Guards the one property that makes the core package worth having: the absence of foreign types.
 * It scans the compiled classes, because type names sit in the constant pool as plain text -
 * so it also catches use of a fully qualified name, with no import.
 */
class CoreHasNoForeignTypesTest {

    // core is compiled in the 1.16.5 (Forge), 1.20.1 (Forge) and 1.21.1 (NeoForge) builds.
    private static final String[] FORBIDDEN = {
        "net/minecraft", "net/minecraftforge", "net/neoforged", "ovh/corail"
    };

    /** Forbidden type names that the given class's constant pool contains as plain text. */
    private static List<String> foreignTypesIn(Path classFile) throws IOException {
        String bytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        List<String> hits = new ArrayList<String>();
        for (String forbidden : FORBIDDEN) {
            if (bytes.contains(forbidden)) {
                hits.add(classFile.getFileName() + " -> " + forbidden);
            }
        }
        return hits;
    }

    @Test
    void coreClassesNameNoForeignTypes() throws IOException {
        Path root = Paths.get("build", "classes", "java", "main",
                              "com", "spege", "tombtweaks", "core");
        assertTrue(Files.isDirectory(root),
                   "no compiled core classes under " + root.toAbsolutePath()
                   + " - run the test through ./gradlew test, not from an IDE without compiling");

        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(root)) {
            classFiles = files.filter(p -> p.toString().endsWith(".class"))
                              .collect(java.util.stream.Collectors.toList());
        }

        List<String> offenders = new ArrayList<String>();
        for (Path file : classFiles) {
            offenders.addAll(foreignTypesIn(file));
        }

        assertTrue(offenders.isEmpty(), "core names foreign types: " + offenders);
    }

    @Test
    void theScanWouldActuallyCatchSomething() throws IOException {
        // A check of the scan itself: a mod class (outside core) names net/minecraftforge, so the
        // same scan MUST find something in it. If the scan were broken, the test above would pass
        // vacuously.
        Path modClass = Paths.get("build", "classes", "java", "main",
                                  "com", "spege", "tombtweaks", "TombTweaks.class");
        assertTrue(Files.exists(modClass),
                   "missing " + modClass.toAbsolutePath() + " - the scan check has nothing to work on");

        assertFalse(foreignTypesIn(modClass).isEmpty(),
                    "the scan found no foreign types in a class that certainly uses them");
    }
}

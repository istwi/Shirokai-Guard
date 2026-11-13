package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RegionStoreTest {
  @TempDir Path dir;

  @Test
  void roundTripAndBackup() throws Exception {
    var store = new RegionStore(dir);
    assertTrue(store.load().isEmpty());
    var r =
        Region.create("spawn", "custom:world", new Bounds(-10, -64, -10, 10, 319, 10))
            .withPerson(UUID.randomUUID(), true, true)
            .withFlag(Flag.GREETING, Region.Group.ALL, "Olá ✦");
    store.save(List.of(r));
    assertEquals(List.of(r), store.load());
    store.save(List.of());
    assertTrue(store.load().isEmpty());
    assertTrue(Files.readString(dir.resolve("regions.json.bak")).contains("Olá"));
  }

  @Test
  void malformedFilePreserved() throws Exception {
    var file = dir.resolve("regions.json");
    Files.writeString(file, "{broken}");
    assertThrows(java.io.IOException.class, () -> new RegionStore(dir).load());
    assertEquals("{broken}", Files.readString(file));
  }

  @Test
  void unknownSchemaRejected() throws Exception {
    Files.writeString(dir.resolve("regions.json"), "{\"schema\":2,\"regions\":[]}");
    assertThrows(java.io.IOException.class, () -> new RegionStore(dir).load());
  }

  @Test
  void invalidSnapshotDoesNotOverwrite() throws Exception {
    var store = new RegionStore(dir);
    store.save(List.of());
    String old = Files.readString(dir.resolve("regions.json"));
    var invalid =
        Region.create("a", "minecraft:overworld", new Bounds(0, 0, 0, 1, 1, 1))
            .withParent("missing");
    assertThrows(IllegalArgumentException.class, () -> store.save(List.of(invalid)));
    assertEquals(old, Files.readString(dir.resolve("regions.json")));
  }
}

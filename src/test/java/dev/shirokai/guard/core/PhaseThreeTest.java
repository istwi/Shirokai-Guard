package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhaseThreeTest {
  @TempDir Path dir;

  @Test
  void templatePreservesIdentityMembersGeometryAndUnlistedFlags() {
    UUID owner = UUID.randomUUID();
    var region =
        Region.create("custom", "mod:world", new Bounds(0, 0, 0, 10, 10, 10))
            .withPriority(7)
            .withPerson(owner, true, true)
            .withFlag(Flag.GREETING, Region.Group.ALL, "Bem-vindo")
            .withFlag(Flag.BUILD, Region.Group.OWNERS, "allow");
    var spawn =
        RegionTemplate.defaults().stream()
            .filter(t -> t.id().equals("spawn"))
            .findFirst()
            .orElseThrow();
    var applied = spawn.apply(region, false);
    assertEquals(region.bounds(), applied.bounds());
    assertEquals(7, applied.priority());
    assertEquals(region.owners(), applied.owners());
    assertEquals(region.dimension(), applied.dimension());
    assertEquals("Bem-vindo", applied.flags().get(Flag.GREETING).get(Region.Group.ALL));
    assertEquals("allow", applied.flags().get(Flag.BUILD).get(Region.Group.OWNERS));
    assertEquals("deny", applied.flags().get(Flag.BUILD).get(Region.Group.ALL));
    assertFalse(region.flags().containsKey(Flag.PVP));
  }

  @Test
  void optionalFlagsAreNotAppliedWithoutCobblemon() {
    var gym =
        RegionTemplate.defaults().stream()
            .filter(t -> t.id().equals("ginasio"))
            .findFirst()
            .orElseThrow();
    var r = Region.create("test", "mod:world", new Bounds(0, 0, 0, 1, 1, 1));
    assertFalse(gym.apply(r, false).flags().containsKey(Flag.POKEMON_BATTLE));
    assertEquals(
        "allow", gym.apply(r, true).flags().get(Flag.POKEMON_BATTLE).get(Region.Group.ALL));
  }

  @Test
  void customTemplateIsNotOverwrittenAndBackupIsRetained() throws Exception {
    var templates = RegionTemplate.load(dir);
    assertEquals(5, templates.size());
    var original = templates.get("spawn");
    var custom =
        new RegionTemplate(
            "spawn", "Meu spawn", Map.of(Flag.PVP, Map.of(Region.Group.ALL, "allow")));
    AtomicJson.write(dir.resolve("spawn.json"), custom.json());
    assertEquals(custom, RegionTemplate.load(dir).get("spawn"));
    assertEquals(
        original.description(),
        AtomicJson.read(dir.resolve("spawn.json.bak")).get("description").getAsString());
  }

  @Test
  void malformedTemplateDoesNotDestroyOriginalFile() throws Exception {
    Files.writeString(dir.resolve("broken.json"), "{broken}");
    assertThrows(java.io.IOException.class, () -> RegionTemplate.load(dir));
    assertEquals("{broken}", Files.readString(dir.resolve("broken.json")));
  }

  @Test
  void templatePathTraversalRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new RegionTemplate("../escape", "x", Map.of()));
  }

  @Test
  void cachedSpatialQueriesDoNotCacheAnActorsPermission() {
    var manager = new RegionManager();
    var id = UUID.randomUUID();
    var r =
        Region.create("a", "mod:world", new Bounds(0, 0, 0, 10, 10, 10))
            .withPerson(id, false, true)
            .withFlag(Flag.BUILD, Region.Group.NONMEMBERS, "deny");
    manager.put(r);
    assertFalse(manager.allowed("mod:world", 1, 1, 1, Flag.BUILD, null));
    assertTrue(manager.allowed("mod:world", 1, 1, 1, Flag.BUILD, id));
    assertTrue(manager.metrics().hits() > 0);
    manager.put(r.withPerson(id, false, false));
    assertFalse(manager.allowed("mod:world", 1, 1, 1, Flag.BUILD, id));
    manager.remove("mod:world", "a");
    assertTrue(manager.allowed("mod:world", 1, 1, 1, Flag.BUILD, null));
  }

  @Test
  void cacheIsBoundedAndCanBeDisabled() {
    var m = new RegionManager();
    m.cacheLimit(8);
    for (int i = 0; i < 100; i++) m.at("m:w", i, 0, 0);
    assertEquals(8, m.metrics().cacheEntries());
    m.cacheLimit(0);
    m.at("m:w", 1, 0, 0);
    assertEquals(0, m.metrics().cacheEntries());
  }

  @Test
  void hugeSelectionsHaveBoundedNearbySamples() {
    var b = new Bounds(-30_000_000, -64, -30_000_000, 30_000_000, 319, 30_000_000);
    var points = BoundsVisual.sample(b, -30_000_000, -64, -30_000_000, 128);
    assertFalse(points.isEmpty());
    assertTrue(points.size() <= 128);
    for (var p : points) {
      assertTrue(Math.abs(p.x() + 30_000_000) <= 64);
      assertTrue(Math.abs(p.y() + 64) <= 64);
      assertTrue(Math.abs(p.z() + 30_000_000) <= 64);
    }
    assertTrue(BoundsVisual.sample(b, 0, 64, 0, 128).isEmpty());
  }
}

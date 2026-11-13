package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class RegionManagerTest {
  static final String WORLD = "minecraft:overworld", CUSTOM = "shirokai:arena";
  static final Bounds BOX = new Bounds(-16, -64, -16, 16, 319, 16);
  final RegionManager manager = new RegionManager();

  Region region(String id) {
    return Region.create(id, WORLD, BOX);
  }

  Region flag(String id, Flag flag, String value) {
    return region(id).withFlag(flag, Region.Group.ALL, value);
  }

  boolean allowed(Flag f) {
    return manager.allowed(WORLD, 0, 0, 0, f, null);
  }

  @Test
  void inclusiveBoundsAndNegativeChunks() {
    manager.put(region("a"));
    assertEquals(1, manager.at(WORLD, -16, -64, -16).size());
    assertEquals(1, manager.at(WORLD, 16, 319, 16).size());
    assertTrue(manager.at(WORLD, -17, 0, 0).isEmpty());
    assertTrue(manager.at(WORLD, 0, 320, 0).isEmpty());
  }

  @Test
  void dimensionsAreIsolated() {
    manager.put(region("a"));
    assertTrue(manager.at(CUSTOM, 0, 0, 0).isEmpty());
    manager.put(Region.create("a", CUSTOM, BOX));
    assertEquals(2, manager.all().size());
  }

  @Test
  void unsetAllows() {
    manager.put(region("a"));
    assertTrue(allowed(Flag.BUILD));
  }

  @Test
  void buildFallback() {
    manager.put(flag("a", Flag.BUILD, "deny"));
    assertFalse(allowed(Flag.BLOCK_PLACE));
    assertFalse(allowed(Flag.BLOCK_BREAK));
  }

  @Test
  void specificOverridesBuildInSameRegion() {
    manager.put(
        flag("a", Flag.BUILD, "deny").withFlag(Flag.BLOCK_BREAK, Region.Group.ALL, "allow"));
    assertTrue(allowed(Flag.BLOCK_BREAK));
    assertFalse(allowed(Flag.BLOCK_PLACE));
  }

  @Test
  void denyWinsTiesIndependentOfInsertionOrder() {
    for (boolean reverse : new boolean[] {true, false}) {
      var a = flag("a", Flag.PVP, "allow");
      var b = flag("b", Flag.PVP, "deny");
      manager.replace(reverse ? List.of(b, a) : List.of(a, b));
      assertFalse(allowed(Flag.PVP));
    }
  }

  @Test
  void highPriorityOverridesLowerDeny() {
    manager.put(flag("a", Flag.PVP, "deny"));
    manager.put(flag("b", Flag.PVP, "allow").withPriority(2));
    assertTrue(allowed(Flag.PVP));
  }

  @Test
  void unsetHighPriorityFallsThrough() {
    manager.put(flag("a", Flag.PVP, "deny"));
    manager.put(region("b").withPriority(10));
    assertFalse(allowed(Flag.PVP));
  }

  @Test
  void inheritanceAndUnset() {
    manager.put(flag("a", Flag.BUILD, "deny"));
    manager.put(region("b").withParent("a").withPriority(10));
    assertFalse(allowed(Flag.BLOCK_BREAK));
    manager.put(manager.get(WORLD, "b").withFlag(Flag.BUILD, Region.Group.ALL, "allow"));
    assertTrue(allowed(Flag.BLOCK_BREAK));
    manager.put(manager.get(WORLD, "b").withFlag(Flag.BUILD, Region.Group.ALL, "unset"));
    assertFalse(allowed(Flag.BLOCK_BREAK));
  }

  @Test
  void cyclesRejectedWithoutMutation() {
    manager.put(region("a"));
    manager.put(region("b").withParent("a"));
    assertThrows(IllegalArgumentException.class, () -> manager.put(region("a").withParent("b")));
    assertNull(manager.get(WORLD, "a").parent());
  }

  @Test
  void missingParentRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> manager.put(region("a").withParent("missing")));
    assertTrue(manager.all().isEmpty());
  }

  @Test
  void childBlocksParentRemoval() {
    manager.put(region("a"));
    manager.put(region("b").withParent("a"));
    assertThrows(IllegalArgumentException.class, () -> manager.remove(WORLD, "a"));
    manager.remove(WORLD, "b");
    manager.remove(WORLD, "a");
    assertTrue(manager.all().isEmpty());
  }

  @Test
  void uuidGroupsAndInheritedMembership() {
    UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), other = UUID.randomUUID();
    manager.put(region("a").withPerson(owner, true, true).withPerson(member, false, true));
    var b =
        region("b")
            .withParent("a")
            .withPriority(1)
            .withFlag(Flag.BUILD, Region.Group.NONMEMBERS, "deny");
    manager.put(b);
    assertEquals(Region.Group.OWNERS, manager.group(b, owner));
    assertEquals(Region.Group.MEMBERS, manager.group(b, member));
    assertTrue(manager.allowed(WORLD, 0, 0, 0, Flag.BUILD, owner));
    assertFalse(manager.allowed(WORLD, 0, 0, 0, Flag.BUILD, other));
  }

  @Test
  void globalAndLargeRegions() {
    manager.put(
        Region.create("__global__", CUSTOM, null).withFlag(Flag.PVP, Region.Group.ALL, "deny"));
    assertFalse(manager.allowed(CUSTOM, 20_000_000, 900, 20_000_000, Flag.PVP, null));
    manager.put(
        Region.create("large", CUSTOM, new Bounds(-100000, -64, -100000, 100000, 319, 100000)));
    assertEquals(2, manager.at(CUSTOM, 0, 0, 0).size());
  }

  @Test
  void redefineInvalidatesIndex() {
    manager.put(region("a"));
    manager.put(region("a").withBounds(new Bounds(100, 0, 100, 110, 10, 110)));
    assertTrue(manager.at(WORLD, 0, 0, 0).isEmpty());
    assertEquals(1, manager.at(WORLD, 100, 0, 100).size());
  }

  @Test
  void commandNamespacesAndLists() {
    manager.put(flag("a", Flag.BLOCKED_COMMANDS, "kill, minecraft:op"));
    assertFalse(manager.commandAllowed(WORLD, 0, 0, 0, null, "/minecraft:kill @a"));
    assertFalse(manager.commandAllowed(WORLD, 0, 0, 0, null, "minecraft:op x"));
    assertTrue(manager.commandAllowed(WORLD, 0, 0, 0, null, "help"));
  }

  @Test
  void commandAllowListIsRestrictive() {
    manager.put(flag("a", Flag.ALLOWED_COMMANDS, "help,sg"));
    assertFalse(manager.commandAllowed(WORLD, 0, 0, 0, null, "kill"));
    assertTrue(manager.commandAllowed(WORLD, 0, 0, 0, null, "sg info a"));
  }

  @Test
  void rejectInvalidFlagsAndRegions() {
    assertThrows(IllegalArgumentException.class, () -> flag("a", Flag.BUILD, "yes"));
    assertThrows(IllegalArgumentException.class, () -> region("../bad"));
    assertThrows(IllegalArgumentException.class, () -> Region.create("x", WORLD, null));
  }

  @Test
  void boundsResizeAndContractValidation() {
    assertEquals(20, BOX.resize(4, "east").maxX());
    assertEquals(12, BOX.resize(-4, "east").maxX());
    assertThrows(IllegalArgumentException.class, () -> BOX.resize(-400, "up"));
    assertEquals(-64, BOX.vertical(-64, 319).minY());
  }

  @Test
  void immutableCollections() {
    var r = region("a");
    assertThrows(UnsupportedOperationException.class, () -> r.owners().add(UUID.randomUUID()));
    manager.put(r);
    assertThrows(UnsupportedOperationException.class, () -> manager.all().clear());
  }

  @Test
  void duplicateSnapshotRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> manager.replace(List.of(region("a"), region("a"))));
  }

  @Test
  void movementCannotJumpAcrossNarrowRegion() {
    manager.put(
        Region.create("narrow", WORLD, new Bounds(0, 0, 0, 0, 10, 10))
            .withFlag(Flag.ENTRY, Region.Group.ALL, "deny"));
    assertFalse(manager.movementAllowed(WORLD, -2, 2, 2, 3, 2, 2, null));
    assertTrue(manager.movementAllowed(WORLD, -2, 20, 2, 3, 20, 2, null));
    assertTrue(manager.movementAllowed(WORLD, -2, 2, -2, 3, 2, -2, null));
  }

  @Test
  void movementExitAndHostileCoordinates() {
    manager.put(
        Region.create("narrow", WORLD, new Bounds(0, 0, 0, 0, 10, 10))
            .withFlag(Flag.EXIT, Region.Group.ALL, "deny"));
    assertFalse(manager.movementAllowed(WORLD, -2, 2, 2, 3, 2, 2, null));
    assertFalse(manager.movementAllowed(WORLD, 0.5, 2, 2, 3, 2, 2, null));
    assertFalse(manager.movementAllowed(WORLD, 0, 2, 2, 30_000_000, 2, 2, null));
    assertFalse(manager.movementAllowed(WORLD, 0, 2, 2, Double.NaN, 2, 2, null));
  }
}

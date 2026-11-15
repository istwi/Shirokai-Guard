package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilityRulesTest {
  static final String DIM="minecraft:overworld";
  final RegionManager manager=new RegionManager();
  Region box() { return Region.create("restricted",DIM,new Bounds(0,0,0,3,8,3)); }
  @Test void fastCrossingCannotSkipThinRestrictedArea() {
    manager.put(box().withFlag(Flag.FLY,Region.Group.ALL,"deny"));
    assertFalse(manager.movementAllowed(DIM,-2,2,1,8,2,1,null,Set.of(Flag.FLY)));
    assertTrue(manager.movementAllowed(DIM,-2,2,1,8,2,1,null,Set.of()));
    assertTrue(manager.movementAllowed(DIM,-2,2,1,8,2,1,null,Set.of(Flag.ELYTRA)));
  }
  @Test void groupsInheritanceAndPrioritiesApplyToAllThreeFlags() {
    var owner=UUID.randomUUID();
    for(var flag:List.of(Flag.FLY,Flag.ELYTRA,Flag.POKEMON_MOUNT)) {
      var region=box().withPerson(owner,true,true).withFlag(flag,Region.Group.ALL,"deny")
          .withFlag(flag,Region.Group.OWNERS,"allow");manager.replace(List.of(region));
      assertTrue(manager.allowed(DIM,1,2,1,flag,owner));
      assertFalse(manager.allowed(DIM,1,2,1,flag,UUID.randomUUID()));
      var exception=Region.create("exception",DIM,new Bounds(0,0,0,1,8,1)).withPriority(10).withFlag(flag,Region.Group.ALL,"allow");
      manager.put(exception);assertTrue(manager.allowed(DIM,1,2,1,flag,null));
      assertFalse(manager.movementAllowed(DIM,1,2,1,3,2,1,null,Set.of(flag)));
      manager.replace(List.of(region,Region.create("child",DIM,new Bounds(10,0,0,12,8,3)).withParent(region.id())));
      assertFalse(manager.allowed(DIM,11,2,1,flag,null));
    }
  }
  @Test void exitsDimensionsAndUnconfiguredRegionsAreUnaffected() {
    var region=box().withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny");manager.put(region);
    assertTrue(manager.movementAllowed(DIM,1,2,1,7,2,1,null,Set.of(Flag.POKEMON_MOUNT)));
    assertTrue(manager.allowed("minecraft:the_nether",1,2,1,Flag.POKEMON_MOUNT,null));
    assertTrue(manager.allowed(DIM,1,2,1,Flag.ENTRY,null));
    assertTrue(manager.commandAllowed(DIM,1,2,1,null,"fly"));
  }
  @TempDir java.nio.file.Path directory;
  @Test void mobilityFlagsRoundTripWithExistingRules() throws Exception {
    var region=box().withFlag(Flag.FLY,Region.Group.ALL,"deny")
        .withFlag(Flag.ELYTRA,Region.Group.ALL,"deny")
        .withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny")
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only");
    var store=new RegionStore(directory);store.save(List.of(region));assertEquals(List.of(region),store.load());
    assertTrue(Flag.POKEMON_MOUNT.optional());assertFalse(Flag.FLY.optional());assertFalse(Flag.ELYTRA.optional());
  }
}

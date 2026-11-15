package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EntryAccessTest {
  final RegionManager manager = new RegionManager();
  final String dim = "minecraft:overworld";
  final UUID player = UUID.randomUUID();
  Region arena(String id) {
    return Region.create(id, dim, new Bounds(0,0,0,9,9,9))
        .withFlag(Flag.ENTRY_MODE, Region.Group.ALL,"authorized-only");
  }
  @Test void validatesModesAndPreservesUnset() {
    for (var mode : List.of("normal","deny","authorized-only")) assertEquals(mode,Flag.ENTRY_MODE.validate(mode));
    assertThrows(IllegalArgumentException.class, () -> Flag.ENTRY_MODE.validate("allow"));
    assertTrue(arena("arena").withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"unset").flags().isEmpty());
  }
  @Test void localOnlyAndNoCommandBlocking() {
    manager.put(arena("arena"));
    assertEquals(Set.of("arena"),manager.entryRule(dim,1,1,1,player).regions());
    assertTrue(manager.entryRule(dim,20,1,1,player).regions().isEmpty());
    assertTrue(manager.entryRule("minecraft:the_nether",1,1,1,player).regions().isEmpty());
    for (var command : List.of("back","home","tpa","warp","tp"))
      assertTrue(manager.commandAllowed(dim,1,1,1,player,command));
    assertTrue(manager.allowed(dim,1,1,1,Flag.BUILD,player));
  }
  @Test void overlapPriorityAndTieRequireAllRegions() {
    manager.put(arena("a")); manager.put(arena("b"));
    assertEquals(Set.of("a","b"),manager.entryRule(dim,1,1,1,player).regions());
    manager.put(arena("normal").withPriority(1).withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"normal"));
    assertTrue(manager.entryRule(dim,1,1,1,player).regions().isEmpty());
    manager.put(arena("deny").withPriority(1).withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"deny"));
    assertTrue(manager.entryRule(dim,1,1,1,player).denied());
  }
  @Test void inheritanceAndGroups() {
    manager.put(arena("parent").withBounds(new Bounds(30,0,0,40,9,9)));
    manager.put(Region.create("child",dim,new Bounds(0,0,0,9,9,9)).withParent("parent"));
    assertEquals(Set.of("child"),manager.entryRule(dim,1,1,1,player).regions());
    manager.put(manager.get(dim,"child").withPerson(player,true,true)
        .withFlag(Flag.ENTRY_MODE,Region.Group.OWNERS,"normal"));
    assertTrue(manager.entryRule(dim,1,1,1,player).regions().isEmpty());
  }
  @Test void walkingAcrossAndExiting() {
    manager.put(arena("arena"));
    assertFalse(manager.movementAllowed(dim,-1,1,1,11,1,1,player));
    assertTrue(manager.movementAllowed(dim,1,1,1,11,1,1,player));
    assertTrue(manager.movementAllowed(dim,-3,1,1,-1,1,1,player));
  }
  @Test void occupantsAreNotTrappedByUnrelatedOverlaps() {
    manager.put(arena("arena"));
    manager.put(Region.create("shop",dim,new Bounds(4,0,0,6,9,9)));
    assertTrue(manager.movementAllowed(dim,1,1,1,8,1,1,player));
  }
  @Test void leavingNormalExceptionRequiresEntryAgain() {
    manager.put(arena("arena"));
    manager.put(Region.create("lobby",dim,new Bounds(0,0,0,3,9,9)).withPriority(1)
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"normal"));
    assertFalse(manager.movementAllowed(dim,1,1,1,8,1,1,player));
  }
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
  @Test void persistenceRoundTripsEntryModeAlongsideExistingFlags() throws Exception {
    var region=arena("arena").withFlag(Flag.BUILD,Region.Group.ALL,"deny");
    var store=new RegionStore(directory);
    store.save(List.of(region));
    assertEquals(List.of(region),store.load());
  }
  @Test void onlyOneConcurrentConsumerWins() throws Exception {
    var grant=issue();
    try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var latch=new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> attempt=()->{latch.await();return auth.consume(grant,player,dim,grant.regions(),101);};
      var first=executor.submit(attempt);var second=executor.submit(attempt);latch.countDown();
      assertNotEquals(first.get(),second.get());
    }
  }
  EntryAuthorizations auth = new EntryAuthorizations();
  EntryAuthorizations.Grant issue() { return auth.issue(player,dim,Set.of("arena"),"raids","raid-1",100,40); }
  @Test void singleUseAndExpiry() {
    var grant=issue();
    assertTrue(auth.consume(grant,player,dim,Set.of("arena"),100));
    assertFalse(auth.consume(grant,player,dim,Set.of("arena"),101));
    grant=issue(); assertFalse(auth.consume(grant,player,dim,Set.of("arena"),140));
  }
  @Test void wrongPlayerRegionOrDimensionBurnsGrant() {
    var g=issue();assertFalse(auth.consume(g,UUID.randomUUID(),dim,g.regions(),101));
    assertFalse(auth.consume(g,player,dim,g.regions(),101));
    g=issue();assertFalse(auth.consume(g,player,"minecraft:the_end",g.regions(),101));
    g=issue();assertFalse(auth.consume(g,player,dim,Set.of("other"),101));
  }
  @Test void disconnectRaidEndAndRestartInvalidate() {
    var g=issue();auth.forget(player);assertFalse(auth.consume(g,player,dim,g.regions(),101));
    g=issue();auth.end("raids","raid-1");assertFalse(auth.consume(g,player,dim,g.regions(),101));
    g=issue();auth.clear();assertFalse(auth.consume(g,player,dim,g.regions(),101));
    g=issue();auth.revoke(g.token());assertFalse(auth.consume(g,player,dim,g.regions(),101));
  }
}

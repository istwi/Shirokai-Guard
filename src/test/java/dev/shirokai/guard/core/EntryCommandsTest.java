package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryCommandsTest {
  @TempDir java.nio.file.Path directory;
  Region shop() { return Region.create("loja","minecraft:overworld",new Bounds(0,0,0,10,10,10))
      .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only")
      .withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,"warp loja\nloja"); }
  @Test void exactArgumentsAreRequired() {
    var commands=EntryCommands.list(shop());
    assertTrue(commands.contains(EntryCommands.normalize(" /warp   loja ")));
    for(var input:List.of("warp","warp spawn","warp lojax","warp loja extra","minecraft:warp loja","back","tpaccept"))
      assertFalse(commands.contains(EntryCommands.normalize(input)),input);
  }
  @Test void quotedArgumentsAndCaseArePreserved() {
    assertEquals("warp \"Lo  Ja\"",EntryCommands.normalize(" /WARP  \"Lo  Ja\" "));
    assertNotEquals(EntryCommands.normalize("warp \"lo  ja\""),EntryCommands.normalize("warp \"lo ja\""));
    assertNotEquals(EntryCommands.normalize("warp Loja"),EntryCommands.normalize("warp loja"));
  }
  @Test void malformedAndWildcardRulesAreRejected() {
    for(var input:List.of("", "warp *", "warp loja\nback", "warp \"loja", "//loja"))
      assertThrows(IllegalArgumentException.class,()->EntryCommands.normalize(input));
  }
  @Test void persistenceKeepsIndependentRegionLists() throws Exception {
    var store=new RegionStore(directory);
    var arena=Region.create("raid","minecraft:overworld",new Bounds(20,0,0,30,10,10))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only");
    store.save(List.of(shop(),arena));
    var loaded=store.load();
    assertEquals(Set.of("loja","warp loja"),EntryCommands.list(loaded.stream().filter(r->r.id().equals("loja")).findFirst().orElseThrow()));
    assertTrue(EntryCommands.list(loaded.stream().filter(r->r.id().equals("raid")).findFirst().orElseThrow()).isEmpty());
  }
  @Test void commandOnlyBlocksWalkingButAllowsExit() {
    var manager=new RegionManager(); manager.put(shop());
    assertFalse(manager.movementAllowed("minecraft:overworld",-1,1,1,12,1,1,null));
    assertTrue(manager.movementAllowed("minecraft:overworld",1,1,1,12,1,1,null));
    assertEquals(Set.of("loja"),manager.entryRule("minecraft:overworld",1,1,1,null).commandRegions());
    assertTrue(manager.entryRule("minecraft:overworld",1,1,1,null).regions().isEmpty());
  }
  @Test void prioritiesAndInheritedModeDoNotCopyCommandLists() {
    var manager=new RegionManager();manager.put(shop());
    var lobby=Region.create("lobby","minecraft:overworld",new Bounds(0,0,0,3,10,10)).withPriority(1)
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"normal");manager.put(lobby);
    assertTrue(manager.entryRule("minecraft:overworld",1,1,1,null).commandRegions().isEmpty());
    assertFalse(manager.movementAllowed("minecraft:overworld",1,1,1,7,1,1,null));
    var child=lobby.withPriority(2).withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"unset").withParent("loja");manager.put(child);
    assertEquals(Set.of("lobby"),manager.entryRule("minecraft:overworld",1,1,1,null).commandRegions());
    assertTrue(EntryCommands.list(child).isEmpty());
  }
}

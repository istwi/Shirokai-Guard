package dev.shirokai.guard;

import dev.shirokai.guard.api.GuardEntryApi;
import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class CommandEntryGameTests implements FabricGameTest {
  private record Scene(ServerPlayerEntity player,Region region,Vec3d origin,Vec3d target) {}
  private Scene scene(TestContext ctx) {
    var a=ctx.getAbsolutePos(new BlockPos(2,0,2));
    var region=Region.create("cmd_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+2,a.getY()+6,a.getZ()+2))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only")
        .withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,"warp loja");
    ShiroGuard.REGIONS.put(region);var p=TestPlayers.create(ctx);
    var origin=Vec3d.ofBottomCenter(a.add(-2,2,1));p.setPosition(origin);
    return new Scene(p,region,origin,Vec3d.ofBottomCenter(a.add(1,2,1)));
  }
  private UUID capture(Scene s,String input) {
    var token=new UUID[1];
    CommandEntryAccess.execute(s.player.getCommandSource(),input,()->token[0]=GuardEntryApi.captureCommand(
        s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z));
    return token[0];
  }
  private int run(Scene s,UUID token) {
    return GuardEntryApi.runCommandTeleport(token,s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z,
        ()->s.player.teleport(s.player.getServerWorld(),s.target.x,s.target.y,s.target.z,Set.of(),0,0)?1:0);
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-entry")
  public void exactCommandTicketIsBoundToOneTeleport(TestContext ctx) {
    var s=scene(ctx);
    for(var denied:List.of("warp spawn","warp","warp loja extra","back","tpaccept"))
      ctx.assertTrue(capture(s,denied)==null,"Wrong command captured authorization: "+denied);
    var token=capture(s,"warp loja");ctx.assertTrue(token!=null,"Exact command not captured");
    ctx.assertEquals(run(s,token),1,"Authorized command failed");
    ctx.assertTrue(s.player.teleport(ctx.getWorld(),s.origin.x,s.origin.y,s.origin.z,Set.of(),0,0),"Exit failed");
    ctx.assertEquals(run(s,token),0,"Ticket reused");
    ctx.assertFalse(s.player.teleport(ctx.getWorld(),s.target.x,s.target.y,s.target.z,Set.of(),0,0),"Back inherited permission");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-entry")
  public void changedRulesAndWrongDestinationBurnTickets(TestContext ctx) {
    var s=scene(ctx);var token=capture(s,"warp loja");
    ctx.assertEquals(GuardEntryApi.runCommandTeleport(token,s.player.getUuid(),s.region.dimension(),
        s.target.x+20,s.target.y,s.target.z,()->{throw new AssertionError("Wrong destination callback executed");}),0,"Wrong destination accepted");
    ctx.assertEquals(run(s,token),0,"Mismatch did not burn token");
    token=capture(s,"warp loja");
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,"warp outro"));
    ctx.assertEquals(run(s,token),0,"Removed command still authorized");
    ShiroGuard.REGIONS.put(s.region);token=capture(s,"warp loja");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());
    var other=Region.create(s.region.id()+"_other",s.region.dimension(),s.region.bounds())
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only")
        .withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,"warp loja");
    ShiroGuard.REGIONS.put(other);
    ctx.assertEquals(run(s,token),0,"Token authorized a different region");
    ShiroGuard.REGIONS.remove(other.dimension(),other.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-entry")
  public void raidApiCannotGrantCommandOnlyAndCommandCannotGrantRaid(TestContext ctx) {
    var s=scene(ctx);
    ctx.assertFalse(GuardEntryApi.teleportRaid(s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z,0,0,"raid"),"Raid granted shop access");
    var token=capture(s,"warp loja");
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only"));
    ctx.assertEquals(run(s,token),0,"Command granted raid access");
    ctx.assertTrue(GuardEntryApi.teleportRaid(s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z,0,0,"raid"),"Existing raid API regressed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-entry")
  public void exceptionsAndDisconnectDoNotLeakCommandContext(TestContext ctx) {
    var s=scene(ctx);
    try { CommandEntryAccess.execute(s.player.getCommandSource(),"warp loja",()->{throw new IllegalStateException("expected");}); }
    catch(IllegalStateException expected) {}
    ctx.assertTrue(GuardEntryApi.captureCommand(s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z)==null,"Exception leaked context");
    var token=capture(s,"warp loja");GuardEntryApi.invalidatePlayer(s.player.getUuid());
    ctx.assertEquals(run(s,token),0,"Disconnect left ticket");
    var failing=capture(s,"warp loja");
    try { GuardEntryApi.runCommandTeleport(failing,s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z,
        ()->{throw new IllegalStateException("expected callback failure");}); }
    catch(IllegalStateException expected) {}
    ctx.assertEquals(run(s,failing),0,"Callback failure left reusable authorization");
    ctx.assertFalse(s.player.teleport(ctx.getWorld(),s.target.x,s.target.y,s.target.z,Set.of(),0,0),"Callback failure leaked active scope");
    ctx.assertFalse(EntryAccess.safeLogin(s.player,BlockPos.ofFloored(s.target)),"Login permitted command-only");
    ctx.assertFalse(Protection.transition(s.player,ctx.getWorld(),s.target,false),"Walking allowed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-entry")
  public void sgEntryCommandAddListRemoveClearPersistRegionRules(TestContext ctx) {
    var s=scene(ctx);var source=s.player.getCommandSource().withLevel(4);
    var id=s.region.id();
    ctx.assertEquals(GuardCommands.run(source,"entry-command",id+" add loja"),1,"Add failed");
    ctx.assertTrue(EntryCommands.list(ShiroGuard.REGIONS.get(s.region.dimension(),id)).contains("loja"),"Alias missing");
    ctx.assertEquals(GuardCommands.run(source,"entry-command",id+" list"),1,"List failed");
    GuardCommands.run(source,"entry-command",id+" remove warp loja");
    ctx.assertFalse(EntryCommands.list(ShiroGuard.REGIONS.get(s.region.dimension(),id)).contains("warp loja"),"Exact removal failed");
    GuardCommands.run(source,"entry-command",id+" add warp \"Lo  Ja\"");
    ctx.assertTrue(EntryCommands.list(ShiroGuard.REGIONS.get(s.region.dimension(),id)).contains("warp \"Lo  Ja\""),"Quoted argument whitespace changed");
    GuardCommands.run(source,"entry-command",id+" clear");
    ctx.assertTrue(EntryCommands.list(ShiroGuard.REGIONS.get(s.region.dimension(),id)).isEmpty(),"Clear failed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="command-dispatch")
  public void actualDispatchAndNestedAliasesKeepOriginalCommand(TestContext ctx) {
    var s=scene(ctx);var manager=s.player.getServer().getCommandManager();
    var root="guardwarp_"+s.region.id(); var alias="guardshop_"+s.region.id();
    manager.getDispatcher().register(net.minecraft.server.command.CommandManager.literal(root)
        .then(net.minecraft.server.command.CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
        .executes(c -> {
          var token=GuardEntryApi.captureCommand(s.player.getUuid(),s.region.dimension(),s.target.x,s.target.y,s.target.z);
          return token==null?0:run(s,token);
        })));
    manager.getDispatcher().register(net.minecraft.server.command.CommandManager.literal(alias).executes(c->{
      manager.executeWithPrefix(c.getSource(),root+" loja");return 1;
    }));
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,root+" loja"));
    manager.executeWithPrefix(s.player.getCommandSource(),alias);
    ctx.assertEquals(s.player.getPos(),s.origin,"Alias silently inherited underlying warp rule");
    manager.executeWithPrefix(s.player.getCommandSource(),root+" loja");
    ctx.assertTrue(s.player.getPos().squaredDistanceTo(s.target)<1e-8,"Real dispatch did not capture command");
    s.player.teleport(ctx.getWorld(),s.origin.x,s.origin.y,s.origin.z,Set.of(),0,0);
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,alias));
    manager.executeWithPrefix(s.player.getCommandSource(),alias);
    ctx.assertTrue(s.player.getPos().squaredDistanceTo(s.target)<1e-8,"Explicit alias rule failed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
}

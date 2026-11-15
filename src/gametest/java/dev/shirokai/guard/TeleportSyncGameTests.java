package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Actual native teleport confirmation and movement packets, including chat observations. */
public class TeleportSyncGameTests implements FabricGameTest {
  private int pendingId(ServerPlayerEntity player) throws Exception {
    var field=ServerPlayNetworkHandler.class.getDeclaredField("requestedTeleportId");
    field.setAccessible(true);return field.getInt(player.networkHandler);
  }
  private void confirm(ServerPlayerEntity player) throws Exception {
    player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(pendingId(player)));
  }
  private Region region(TestContext ctx,String suffix,boolean restrictEntry) {
    var a=ctx.getAbsolutePos(new BlockPos(1,0,1));
    var r=Region.create("sync_"+a.getX()+"_"+a.getZ()+suffix,ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+5,a.getY()+7,a.getZ()+5))
        .withFlag(Flag.BUILD,Region.Group.ALL,"deny");
    if(restrictEntry)r=r.withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only");
    ShiroGuard.REGIONS.put(r);return r;
  }
  private Vec3d target(TestContext ctx){return Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3,2,3)));}
  private boolean blocked(List<String> messages){return messages.stream().anyMatch(s->s.contains("Você não pode entrar nesta região"));}

  @GameTest(templateName=EMPTY_STRUCTURE,batchId="teleport-sync")
  public void allowedSpawnTeleportDoesNotWarnForStaleClientPosition(TestContext ctx) throws Exception {
    var r=region(ctx,"spawn",false);var messages=new ArrayList<String>();
    var p=TestPlayers.create(ctx,text->messages.add(text.getString()));var pos=target(ctx);
    p.setPosition(pos.add(256,0,0));
    ctx.assertTrue(p.teleport(ctx.getWorld(),pos.x,pos.y,pos.z,Set.of(),0,0),"Normal spawn teleport denied");
    int id=pendingId(p);messages.clear();
    p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x+256,pos.y,pos.z,false));
    ctx.assertEquals(p.getPos(),pos,"Stale packet moved player after teleport");
    ctx.assertFalse(blocked(messages),"Allowed spawn teleport produced false entry warning");
    ctx.assertEquals(pendingId(p),id,"Ignored packet replaced the pending teleport ID");
    confirm(p);
    ctx.assertTrue(Protection.transition(p,ctx.getWorld(),pos.add(.25,0,0),false),"Confirmed normal movement denied");
    ShiroGuard.REGIONS.remove(r.dimension(),r.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="teleport-sync")
  public void pendingPacketsAreSilentButConfirmedUnauthorizedEntryStillWarns(TestContext ctx) throws Exception {
    var r=region(ctx,"restricted",true);var messages=new ArrayList<String>();
    var p=TestPlayers.create(ctx,text->messages.add(text.getString()));var pos=target(ctx);var origin=pos.add(-4,0,0);
    p.setPosition(origin);p.networkHandler.requestTeleport(origin.x,origin.y,origin.z,0,0);messages.clear();
    int id=pendingId(p);
    p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x,pos.y,pos.z,false));
    ctx.assertEquals(p.getPos(),origin,"Pending packet entered protected region");
    ctx.assertFalse(blocked(messages),"Packet vanilla discards generated warning");
    ctx.assertEquals(pendingId(p),id,"Pending packet reset teleport confirmation");
    confirm(p);
    p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x,pos.y,pos.z,false));
    ctx.assertEquals(p.getPos(),origin,"Confirmed movement bypassed entry protection");
    ctx.assertTrue(blocked(messages),"Real entry refusal no longer warns");
    ctx.assertFalse(p.teleport(ctx.getWorld(),pos.x,pos.y,pos.z,Set.of(),0,0),"Direct protected teleport bypassed checks");
    ShiroGuard.REGIONS.remove(r.dimension(),r.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="teleport-sync")
  public void pendingVehiclePacketsDoNotCreateFalseEntryWarning(TestContext ctx) throws Exception {
    var r=region(ctx,"vehicle",true);var messages=new ArrayList<String>();
    var p=TestPlayers.create(ctx,text->messages.add(text.getString()));var pos=target(ctx);var origin=pos.add(-4,0,0);
    p.setPosition(origin);var boat=new BoatEntity(ctx.getWorld(),origin.x,origin.y,origin.z);
    ctx.getWorld().spawnEntity(boat);ctx.assertTrue(p.startRiding(boat,true),"Boat setup failed");
    p.networkHandler.requestTeleport(origin.x,origin.y,origin.z,0,0);messages.clear();
    var packetEntity=new BoatEntity(ctx.getWorld(),pos.x,pos.y,pos.z);
    var packet=new VehicleMoveC2SPacket(packetEntity);int id=pendingId(p);
    p.networkHandler.onVehicleMove(packet);
    ctx.assertEquals(boat.getPos(),origin,"Ignored vehicle packet moved boat");
    ctx.assertFalse(blocked(messages),"Pending vehicle packet generated warning");
    ctx.assertEquals(pendingId(p),id,"Ignored vehicle packet changed confirmation ID");
    confirm(p);p.networkHandler.onVehicleMove(packet);
    ctx.assertEquals(boat.getPos(),origin,"Confirmed vehicle entered restricted region");
    ctx.assertTrue(blocked(messages),"Real vehicle entry refusal did not warn");
    p.stopRiding();boat.discard();packetEntity.discard();ShiroGuard.REGIONS.remove(r.dimension(),r.id());ctx.complete();
  }
}

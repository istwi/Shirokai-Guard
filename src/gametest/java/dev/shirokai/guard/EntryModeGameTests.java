package dev.shirokai.guard;

import dev.shirokai.guard.api.GuardEntryApi;
import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.MovementType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class EntryModeGameTests implements FabricGameTest {
  private record Scene(ServerPlayerEntity player, Region region, Vec3d outside, Vec3d inside) {}
  private Scene scene(TestContext ctx) {
    var a = ctx.getAbsolutePos(new BlockPos(2,0,2));
    var b = ctx.getAbsolutePos(new BlockPos(4,6,4));
    var r = Region.create("access_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        Bounds.between(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ()))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only");
    ShiroGuard.REGIONS.put(r);
    var player = TestPlayers.create(ctx);
    var outside=Vec3d.ofBottomCenter(a.add(-2,2,1));
    var inside=Vec3d.ofBottomCenter(a.add(1,2,1));
    player.refreshPositionAndAngles(outside,0,0);
    return new Scene(player,r,outside,inside);
  }
  private boolean authorized(Scene s, TestContext ctx) {
    return GuardEntryApi.teleportRaid(s.player.getUuid(),s.region.dimension(),
        s.inside.x,s.inside.y,s.inside.z,0,0,"test-raid");
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void authorizedEntryExitAndBackDenied(TestContext ctx) {
    var s=scene(ctx);var p=s.player;
    ctx.assertTrue(authorized(s,ctx),"Authorized raid entry failed");
    ctx.assertTrue(p.getPos().squaredDistanceTo(s.inside)<1e-8,"Wrong authorized position");
    ctx.assertTrue(p.teleport(ctx.getWorld(),s.outside.x,s.outside.y,s.outside.z,Set.of(),0,0),"Exit denied");
    ctx.assertFalse(p.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,Set.of(),0,0),"Back reused authorization");
    ctx.assertTrue(authorized(s,ctx),"Fresh raid entry failed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void voidAndNetworkTeleportsDenied(TestContext ctx) {
    var s=scene(ctx);var p=s.player;
    p.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,0,0);
    ctx.assertTrue(p.getPos().squaredDistanceTo(s.outside)<1e-8,"Void teleport entered");
    p.networkHandler.requestTeleport(s.inside.x,s.inside.y,s.inside.z,0,0);
    ctx.assertTrue(p.getPos().squaredDistanceTo(s.outside)<1e-8,"Network teleport entered");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void movementAndLoginDenied(TestContext ctx) {
    var s=scene(ctx);var p=s.player;
    ctx.assertFalse(Protection.transition(p,ctx.getWorld(),s.inside,false),"Walking entered");
    p.move(MovementType.SELF,s.inside.subtract(s.outside));
    ctx.assertTrue(p.getPos().squaredDistanceTo(s.outside)<1e-8,"Physical movement entered");
    ctx.assertFalse(EntryAccess.safeLogin(p,BlockPos.ofFloored(s.inside)),"Login/respawn accepted");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void existingEntryAndTeleportDenialsSurviveAuthorization(TestContext ctx) {
    var s=scene(ctx);
    for (var flag : List.of(Flag.ENTRY,Flag.TELEPORT)) {
      ShiroGuard.REGIONS.put(s.region.withFlag(flag,Region.Group.ALL,"deny"));
      ctx.assertFalse(authorized(s,ctx),"Authorization bypassed "+flag);
      ctx.assertTrue(s.player.getPos().squaredDistanceTo(s.outside)<1e-8,"Denied operation moved");
    }
    ShiroGuard.REGIONS.put(s.region);
    ctx.assertFalse(s.player.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,Set.of(),0,0),"Failed teleport leaked grant");
    ctx.assertTrue(authorized(s,ctx),"Recovery failed");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void unconfiguredRegionsAndOrdinaryDestinationsRemainOpen(TestContext ctx) {
    var s=scene(ctx);var p=s.player;
    ctx.assertTrue(p.teleport(ctx.getWorld(),s.outside.x-1,s.outside.y,s.outside.z,Set.of(),0,0),"Ordinary teleport blocked");
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"normal"));
    ctx.assertTrue(p.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,Set.of(),0,0),"Normal region blocked");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void occupantCanMoveInsideAndExit(TestContext ctx) {
    var s=scene(ctx);ctx.assertTrue(authorized(s,ctx),"Entry failed");
    ctx.assertTrue(Protection.transition(s.player,ctx.getWorld(),s.inside.add(.1,0,0),false),"Occupant trapped");
    GuardEntryApi.endRaid("test-raid");GuardEntryApi.invalidatePlayer(s.player.getUuid());
    ctx.assertTrue(s.player.teleport(ctx.getWorld(),s.outside.x,s.outside.y,s.outside.z,Set.of(),0,0),"Raid end trapped occupant");
    ctx.assertFalse(s.player.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,Set.of(),0,0),"Raid end left access");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void relativePacketFlagsCannotEvadeDestination(TestContext ctx) {
    var s=scene(ctx);var p=s.player;
    var flags=Set.of(net.minecraft.network.packet.s2c.play.PositionFlag.X,
        net.minecraft.network.packet.s2c.play.PositionFlag.Y,net.minecraft.network.packet.s2c.play.PositionFlag.Z);
    ctx.assertFalse(p.teleport(ctx.getWorld(),s.inside.x,s.inside.y,s.inside.z,flags,0,0),"Relative teleport evaded destination check");
    p.networkHandler.requestTeleport(s.inside.x,s.inside.y,s.inside.z,0,0,flags);
    ctx.assertTrue(p.getPos().squaredDistanceTo(s.outside)<1e-8,"Relative network teleport evaded check");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void vehicleCannotCarryPlayerIntoAuthorizedRegion(TestContext ctx) {
    var s=scene(ctx);
    var boat=new net.minecraft.entity.vehicle.BoatEntity(ctx.getWorld(),s.outside.x,s.outside.y,s.outside.z);
    ctx.getWorld().spawnEntity(boat); s.player.startRiding(boat,true);
    boat.move(MovementType.SELF,s.inside.subtract(s.outside));
    ctx.assertTrue(boat.getPos().squaredDistanceTo(s.outside)<1e-8,"Vehicle crossed protected border");
    s.player.stopRiding(); boat.discard();
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void portalAndDimensionTargetsCannotEnter(TestContext ctx) {
    var s=scene(ctx);
    var target=new net.minecraft.world.TeleportTarget(ctx.getWorld(),s.inside,Vec3d.ZERO,0,0,
        net.minecraft.world.TeleportTarget.NO_OP);
    ctx.assertTrue(s.player.teleportTo(target)==null,"Portal target entered without authorization");
    var nether=ctx.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);
    var dim=ShiroGuard.dimension(nether);
    var copy=Region.create(s.region.id(),dim,s.region.bounds()).withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only");
    ShiroGuard.REGIONS.put(copy);
    ctx.assertFalse(s.player.teleport(nether,s.inside.x,s.inside.y,s.inside.z,Set.of(),0,0),"Dimension teleport entered");
    ShiroGuard.REGIONS.remove(dim,copy.id());
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="entry-access")
  public void enderPearlCannotEnterWithoutAuthorization(TestContext ctx) throws Exception {
    var s=scene(ctx);
    var type=net.minecraft.entity.projectile.thrown.EnderPearlEntity.class;
    var collision=type.getDeclaredMethod("onCollision",net.minecraft.util.hit.HitResult.class);
    collision.setAccessible(true);
    for(boolean protectedRegion : new boolean[]{true,false}) {
      var pearl=new net.minecraft.entity.projectile.thrown.EnderPearlEntity(ctx.getWorld(),s.player);
      pearl.setPosition(s.inside);
      collision.invoke(pearl,new net.minecraft.util.hit.BlockHitResult(s.inside,Direction.UP,
          BlockPos.ofFloored(s.inside),false));
      if(protectedRegion) {
        ctx.assertTrue(s.player.getPos().squaredDistanceTo(s.outside)<1e-8,"Pearl entered protected arena");
        ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());
      } else ctx.assertTrue(s.player.getPos().squaredDistanceTo(s.inside)<1e-8,"Positive pearl control failed");
    }
    ctx.complete();
  }
}

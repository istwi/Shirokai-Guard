package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public class MobilityGameTests implements FabricGameTest {
  private record Scene(ServerPlayerEntity player,Region region,Vec3d origin,Vec3d target) {}
  private Scene scene(TestContext ctx,Flag flag) {
    var a=ctx.getAbsolutePos(new BlockPos(1,0,1));
    var r=Region.create("mobility_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+5,a.getY()+7,a.getZ()+5))
        .withFlag(flag,Region.Group.ALL,"deny");
    ShiroGuard.REGIONS.put(r);var p=TestPlayers.create(ctx);
    var origin=Vec3d.ofBottomCenter(a.add(-2,2,2));var target=Vec3d.ofBottomCenter(a.add(2,2,2));
    ctx.getWorld().setBlockState(BlockPos.ofFloored(target).down(),Blocks.STONE.getDefaultState());
    p.setPosition(origin);return new Scene(p,r,origin,target);
  }
  private void requestFlight(ServerPlayerEntity p) {
    var abilities=new PlayerAbilities();abilities.allowFlying=true;abilities.flying=true;
    p.networkHandler.onUpdatePlayerAbilities(new UpdatePlayerAbilitiesC2SPacket(abilities));
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility")
  public void flightPacketDeniedInsideWithoutRevokingEntitlement(TestContext ctx) {
    var s=scene(ctx,Flag.FLY);var p=s.player;p.setPosition(s.target);
    p.getAbilities().allowFlying=true;p.getAbilities().flying=false;
    requestFlight(p);
    ctx.assertFalse(p.getAbilities().flying,"Flight packet enabled flight in denied area");
    ctx.assertTrue(p.getAbilities().allowFlying,"Guard removed another mod's flight entitlement");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());requestFlight(p);
    ctx.assertTrue(p.getAbilities().flying,"Outside positive flight control failed");
    p.getAbilities().flying=false;ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility")
  public void arrivingInFlightIsRefusedAtBoundary(TestContext ctx) {
    var s=scene(ctx,Flag.FLY);var p=s.player;p.getAbilities().allowFlying=true;p.getAbilities().flying=true;
    ctx.assertFalse(p.teleport(ctx.getWorld(),s.target.x,s.target.y,s.target.z,Set.of(),0,0),"Flying teleport entered");
    ctx.assertEquals(p.getPos(),s.origin,"Denied teleport moved player");
    ctx.assertTrue(p.getAbilities().flying,"Boundary refusal dropped flying player");
    ctx.assertFalse(Protection.transition(p,ctx.getWorld(),s.target,false),"Flying motion entered");
    p.getAbilities().flying=false;
    ctx.assertTrue(Protection.transition(p,ctx.getWorld(),s.target,false),"Walking into fly-only restriction was blocked");
    ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility")
  public void elytraStartAndIncomingGlideAreProtected(TestContext ctx) {
    var s=scene(ctx,Flag.ELYTRA);var p=s.player;p.setPosition(s.target);p.setOnGround(false);
    p.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));
    ctx.assertFalse(p.checkFallFlying(),"Vanilla elytra check started in denied region");
    p.startFallFlying();ctx.assertFalse(p.isFallFlying(),"Direct start bypassed restriction");
    p.setPosition(s.origin);
    ctx.assertTrue(p.checkFallFlying(),"Positive elytra control failed");
    ctx.assertFalse(p.teleport(ctx.getWorld(),s.target.x,s.target.y,s.target.z,Set.of(),0,0),"Gliding teleport entered");
    ctx.assertTrue(p.isFallFlying(),"Boundary refusal interrupted glide");
    p.stopFallFlying();ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility")
  public void flyAndElytraFlagsAreIndependent(TestContext ctx) {
    var s=scene(ctx,Flag.FLY);var p=s.player;p.setPosition(s.target);p.startFallFlying();
    ctx.assertTrue(p.isFallFlying(),"Fly flag also blocked independent elytra mode");
    ShiroGuard.REGIONS.put(s.region.withFlag(Flag.FLY,Region.Group.ALL,"unset").withFlag(Flag.ELYTRA,Region.Group.ALL,"deny"));
    ctx.assertFalse(p.checkFallFlying(),"Restricted elytra check accepted");
    ctx.assertFalse(p.isFallFlying(),"Existing glide was not stopped");
    ctx.assertTrue(MobilityProtection.protectsLanding(p),"Interrupted glide lacked landing protection");
    p.getAbilities().allowFlying=true;requestFlight(p);
    ctx.assertTrue(p.getAbilities().flying,"Elytra flag blocked ordinary flight");
    MobilityProtection.enforce(p);
    ctx.assertFalse(MobilityProtection.protectsLanding(p),"Resumed flight kept stale landing grace");
    p.getAbilities().flying=false;ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility",tickLimit=140)
  public void forcedEndProtectsOnlyLandingAndExpiresOnGround(TestContext ctx) {
    var s=scene(ctx,Flag.FLY);var p=s.player;p.changeGameMode(GameMode.SURVIVAL);p.setPosition(s.target);
    p.setNoGravity(true);
    // Vanilla protects newly joined players for 60 ticks; wait so the controls can actually take damage.
    ctx.runAtTick(70,()->{
      p.getAbilities().allowFlying=true;p.getAbilities().flying=true;
      MobilityProtection.enforce(p);
      ctx.assertFalse(p.getAbilities().flying,"Existing flight survived local denial");
      ctx.assertTrue(p.getAbilities().allowFlying,"Flight entitlement was lost");
      float health=p.getHealth();p.damage(ctx.getWorld().getDamageSources().fall(),5);
      ctx.assertEquals(p.getHealth(),health,"Forced landing caused fall damage");
      ctx.assertTrue(p.damage(ctx.getWorld().getDamageSources().generic(),2),"Landing grace granted general invulnerability");
      p.setOnGround(true);
      ctx.waitAndRun(5,()->{
        p.setOnGround(true);MobilityProtection.enforce(p);
        ctx.assertFalse(MobilityProtection.protectsLanding(p),"Landing grace did not expire");
        // Prove ordinary fall damage works again after landing (bypass regeneration frames only).
        p.timeUntilRegen=0;
        ctx.assertTrue(p.damage(ctx.getWorld().getDamageSources().fall(),3),"Fall damage stayed disabled after grace expired");
        ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
      });
    });
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="mobility")
  public void spectatorAndExplicitSessionBypassArePreserved(TestContext ctx) {
    var s=scene(ctx,Flag.FLY);var p=s.player;p.setPosition(s.target);p.changeGameMode(GameMode.SPECTATOR);
    p.getAbilities().flying=true;MobilityProtection.enforce(p);
    ctx.assertTrue(p.getAbilities().flying,"Spectator flight disabled");
    p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;
    me.lucko.fabric.api.permissions.v0.PermissionCheckEvent.EVENT.register((source,node)->
        source instanceof net.minecraft.server.command.ServerCommandSource c && c.getPlayer()==p
          && node.equals("shirokaiguard.bypass") ? net.fabricmc.fabric.api.util.TriState.TRUE : net.fabricmc.fabric.api.util.TriState.DEFAULT);
    ShiroGuard.BYPASS.add(p.getUuid());MobilityProtection.enforce(p);
    ctx.assertTrue(p.getAbilities().flying,"Explicit bypass disabled flight");
    ShiroGuard.BYPASS.remove(p.getUuid());MobilityProtection.enforce(p);
    ctx.assertFalse(p.getAbilities().flying,"Bypass revocation ignored");
    MobilityProtection.forget(p.getUuid());ShiroGuard.REGIONS.remove(s.region.dimension(),s.region.id());ctx.complete();
  }
}

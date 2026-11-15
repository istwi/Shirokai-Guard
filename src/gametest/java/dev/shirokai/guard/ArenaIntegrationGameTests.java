package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Actual built optional mods, no replacements for their production service classes. */
public class ArenaIntegrationGameTests implements FabricGameTest {
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="real-arena-integrations")
  public void raidsBridgeGrantsExactlyOneEntry(TestContext ctx) throws Exception {
    ctx.assertTrue(FabricLoader.getInstance().isModLoaded("shirokai_raids"),"Real Raids missing");
    var a=ctx.getAbsolutePos(new BlockPos(2,0,2));
    var r=Region.create("raidabi_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+2,a.getY()+6,a.getZ()+2))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only");
    ShiroGuard.REGIONS.put(r);
    var p=TestPlayers.create(ctx);var origin=Vec3d.ofBottomCenter(a.add(-2,2,1));
    p.setPosition(origin);var dest=Vec3d.ofBottomCenter(a.add(1,2,1));
    var call=Class.forName("com.shirokai.raids.GuardRaidAccess").getDeclaredMethod("teleport",
        ServerPlayerEntity.class,ServerWorld.class,double.class,double.class,double.class,float.class,float.class,String.class);
    call.setAccessible(true);
    ctx.assertTrue((boolean)call.invoke(null,p,ctx.getWorld(),dest.x,dest.y,dest.z,0f,0f,"abi-test"),"Real Raids bridge failed");
    ctx.assertTrue(p.getPos().squaredDistanceTo(dest)<1e-8,"Bridge did not move player");
    ctx.assertTrue(p.teleport(ctx.getWorld(),origin.x,origin.y,origin.z,Set.of(),0,0),"Exit failed");
    ctx.assertFalse(p.teleport(ctx.getWorld(),dest.x,dest.y,dest.z,Set.of(),0,0),"Back inherited raid access");
    ShiroGuard.REGIONS.remove(r.dimension(),r.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="real-arena-integrations")
  public void essentialsRefusalDoesNotCommitHistoryOrCooldown(TestContext ctx) throws Exception {
    var modType=Class.forName("dev.shirokai.essentials.ShirokaiEssentials");
    var mod=modType.getMethod("instance").invoke(null);
    ctx.assertTrue(mod!=null,"Real Essentials missing");
    var service=modType.getField("teleports").get(mod);
    var serviceType=service.getClass();
    var posType=Class.forName("dev.shirokai.essentials.persistence.Position");
    var a=ctx.getAbsolutePos(new BlockPos(0,0,0));
    for(int x=0;x<7;x++) for(int z=0;z<7;z++)
      ctx.getWorld().setBlockState(a.add(x,1,z),Blocks.STONE.getDefaultState());
    var dest=Vec3d.ofBottomCenter(a.add(3,2,3));
    var r=Region.create("essabi_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX()+2,a.getY(),a.getZ()+2,a.getX()+4,a.getY()+6,a.getZ()+4))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"authorized-only");
    ShiroGuard.REGIONS.put(r);
    var p=TestPlayers.create(ctx);var origin=Vec3d.ofBottomCenter(a.add(0,2,3));p.setPosition(origin);
    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
      ctx.getWorld().getChunk((int)Math.floor(dest.x/16)+dx,(int)Math.floor(dest.z/16)+dz);
    var destination=posType.getConstructor(String.class,double.class,double.class,double.class,float.class,float.class,long.class)
        .newInstance(r.dimension(),dest.x,dest.y,dest.z,0f,0f,System.currentTimeMillis());
    ctx.assertTrue((boolean)serviceType.getMethod("safe",net.minecraft.server.MinecraftServer.class,posType)
        .invoke(service,p.getServer(),destination),"Native safety precondition failed");
    var data=modType.getMethod("data").invoke(mod);
    var playerData=data.getClass().getMethod("player",UUID.class).invoke(data,p.getUuid());
    var cooldown=playerData.getClass().getField("nextTeleportAt");
    var back=playerData.getClass().getField("back");
    var oldCooldown=cooldown.get(playerData);var oldBack=back.get(playerData);
    var perform=serviceType.getMethod("systemTeleport",ServerPlayerEntity.class,posType);
    ctx.assertEquals(perform.invoke(service,p,destination),0,"Denied teleport reported success");
    var after=data.getClass().getMethod("player",UUID.class).invoke(data,p.getUuid());
    ctx.assertEquals(cooldown.get(after),oldCooldown,"Denied teleport consumed cooldown");
    ctx.assertTrue(Objects.equals(back.get(after),oldBack),"Denied teleport changed back history");
    ctx.assertEquals(p.getPos(),origin,"Denied teleport moved player");
    ShiroGuard.REGIONS.remove(r.dimension(),r.id());
    perform.invoke(service,p,destination);
    ctx.assertTrue(p.getPos().squaredDistanceTo(dest)<1e-8,"Ordinary Essentials teleport failed");
    ctx.complete();
  }
}

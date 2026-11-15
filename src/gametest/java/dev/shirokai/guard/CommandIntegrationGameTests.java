package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Exercises the actual Essentials command dispatcher, warmup and TPA acceptance. */
public class CommandIntegrationGameTests implements FabricGameTest {
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="real-command-entry",tickLimit=1200)
  @SuppressWarnings("unchecked")
  public void warpArgumentsWarmupAndTpaCannotShareAuthorization(TestContext ctx) throws Exception {
    var modType=Class.forName("dev.shirokai.essentials.ShirokaiEssentials");
    var app=modType.getMethod("instance").invoke(null);
    var settings=modType.getMethod("settings").invoke(app);
    var teleport=settings.getClass().getField("teleport").get(settings);
    var permissions=settings.getClass().getField("permissions").get(settings);
    var defaults=(Map<String,Boolean>)permissions.getClass().getField("defaults").get(permissions);
    var oldDefaults=new HashMap<>(defaults);
    var op=permissions.getClass().getField("opFallback");var oldOp=op.get(permissions);
    var warmup=teleport.getClass().getField("warmupSeconds");var oldWarmup=warmup.get(teleport);
    var cooldown=teleport.getClass().getField("cooldownSeconds");var oldCooldown=cooldown.get(teleport);
    var tolerance=teleport.getClass().getField("cancelOnMovement");var oldMovement=tolerance.get(teleport);
    Runnable restore=()->{
      try {
        defaults.clear();defaults.putAll(oldDefaults);op.set(permissions,oldOp);
        warmup.set(teleport,oldWarmup);cooldown.set(teleport,oldCooldown);tolerance.set(teleport,oldMovement);
      }catch(Exception e){throw new RuntimeException(e);}
    };
    var a=ctx.getAbsolutePos(new BlockPos(0,0,0));
    for(int x=0;x<7;x++)for(int z=0;z<7;z++)ctx.getWorld().setBlockState(a.add(x,1,z),Blocks.STONE.getDefaultState());
    var dest=Vec3d.ofBottomCenter(a.add(3,2,3));var origin=Vec3d.ofBottomCenter(a.add(0,2,3));
    var region=Region.create("realcmd_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX()+2,a.getY(),a.getZ()+2,a.getX()+4,a.getY()+6,a.getZ()+4))
        .withFlag(Flag.ENTRY_MODE,Region.Group.ALL,"command-only")
        .withFlag(Flag.ENTRY_COMMANDS,Region.Group.ALL,"warp loja");
    var p=TestPlayers.create(ctx);var q=TestPlayers.create(ctx);
    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
      ctx.getWorld().getChunk((int)Math.floor(dest.x/16)+dx,(int)Math.floor(dest.z/16)+dz);
    var manager=p.getServer().getCommandManager();
    var service=modType.getField("teleports").get(app);
    var posType=Class.forName("dev.shirokai.essentials.persistence.Position");
    var constructor=posType.getConstructor(String.class,double.class,double.class,double.class,float.class,float.class,long.class);
    var destination=constructor.newInstance(region.dimension(),dest.x,dest.y,dest.z,0f,0f,System.currentTimeMillis());
    var outside=constructor.newInstance(region.dimension(),origin.x,origin.y,origin.z,0f,0f,System.currentTimeMillis());
    var system=service.getClass().getMethod("systemTeleport",ServerPlayerEntity.class,posType);
    try {
      defaults.putAll(Map.of("warp",true,"setwarp",true,"tpa",true,"tpaccept",true,"back",true,"bypass.warmup",false,"bypass.cooldown",false));
      op.set(permissions,false);warmup.set(teleport,0);cooldown.set(teleport,0);tolerance.set(teleport,false);
      p.setPosition(dest);
      manager.executeWithPrefix(p.getCommandSource(),"setwarp loja");
      manager.executeWithPrefix(p.getCommandSource(),"setwarp outra");
      ShiroGuard.REGIONS.put(region);p.setPosition(origin);
      manager.executeWithPrefix(p.getCommandSource(),"warp outra");
      ctx.assertEquals(p.getPos(),origin,"Different warp entered shop");
      warmup.set(teleport,1);
      manager.executeWithPrefix(p.getCommandSource(),"warp loja");
      var pending=service.getClass().getDeclaredField("pending");pending.setAccessible(true);
      ctx.assertTrue(((Map<?,?>)pending.get(service)).containsKey(p.getUuid()),"Warp did not schedule a real warmup");
      system.invoke(service,p,destination);
      ctx.assertEquals(p.getPos(),origin,"Unrelated teleport stole pending warp authorization");
      awaitWarmup(ctx,()->{
        try { return !((Map<?,?>)pending.get(service)).containsKey(p.getUuid()); }
        catch(IllegalAccessException error) { throw new RuntimeException(error); }
      },()->{
        try {
          ctx.assertTrue(p.getPos().squaredDistanceTo(dest)<1e-8,"Authorized warp warmup failed");
          system.invoke(service,p,outside);
          system.invoke(service,p,destination);
          ctx.assertEquals(p.getPos(),origin,"Later teleport reused warp authorization");
          q.setPosition(dest);
          warmup.set(teleport,0);
          manager.executeWithPrefix(p.getCommandSource(),"tpa "+q.getName().getString());
          manager.executeWithPrefix(q.getCommandSource(),"tpaccept");
          ctx.assertEquals(p.getPos(),origin,"Accepted TPA entered command-only shop");
          ShiroGuard.REGIONS.remove(region.dimension(),region.id());
          manager.executeWithPrefix(p.getCommandSource(),"warp outra");
          ctx.assertTrue(p.getPos().squaredDistanceTo(dest)<1e-8,"Ordinary warp regressed");
          ctx.complete();
        }catch(Exception error){throw new RuntimeException(error);}
        finally { restore.run(); }
      });
    }catch(Throwable error){restore.run();throw error;}
  }
  private void awaitWarmup(TestContext ctx,java.util.function.BooleanSupplier completed,Runnable success) {
    if(completed.getAsBoolean()){success.run();return;}
    ctx.assertTrue(ctx.getTick()<1000,"Essentials warmup did not finish");
    ctx.waitAndRun(1,()->awaitWarmup(ctx,completed,success));
  }
}

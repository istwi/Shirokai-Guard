package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class BypassRecoveryGameTests implements FabricGameTest {
  private void verify(TestContext ctx, boolean permission, int op) {
    var player = TestPlayers.create(ctx);
    var pos = ctx.getAbsolutePos(new BlockPos(2,2,2));
    player.setPosition(Vec3d.ofCenter(pos));
    var region = Region.create("rescue_"+pos.getX()+"_"+pos.getZ(), ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1))
        .withFlag(Flag.BLOCKED_COMMANDS,Region.Group.ALL,"*");
    ShiroGuard.REGIONS.put(region);
    PermissionCheckEvent.EVENT.register((source,node) -> source instanceof ServerCommandSource s
        && s.getPlayer()==player && node.equals("shirokaiguard.bypass")
        ? (permission ? TriState.TRUE : TriState.FALSE) : TriState.DEFAULT);
    var server = ctx.getWorld().getServer();
    if (op > 0) server.getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),op,false));
    try {
      server.getCommandManager().executeWithPrefix(player.getCommandSource(),"sg bypass");
      boolean expected = permission || op == 4;
      ctx.assertEquals(ShiroGuard.bypass(player),expected,"Incorrect rescue authorization");
      if (expected) {
        server.getCommandManager().executeWithPrefix(player.getCommandSource(),"sg bypass");
        ctx.assertFalse(ShiroGuard.bypass(player),"Cannot disable bypass");
        ShiroGuard.REGIONS.put(region.withFlag(Flag.BLOCKED_COMMANDS,Region.Group.ALL,"unset")
            .withFlag(Flag.ALLOWED_COMMANDS,Region.Group.ALL,"help"));
        server.getCommandManager().executeWithPrefix(player.getCommandSource(),"sg bypass");
        ctx.assertTrue(ShiroGuard.bypass(player),"Allowlist blocked rescue");
      }
    } finally {
      ShiroGuard.BYPASS.remove(player.getUuid());
      server.getPlayerManager().getOpList().remove(player.getGameProfile());
      ShiroGuard.REGIONS.remove(region.dimension(),region.id());
    }
    ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE) public void permissionRescue(TestContext ctx) { verify(ctx,true,0); }
  @GameTest(templateName=EMPTY_STRUCTURE) public void opFourRescue(TestContext ctx) { verify(ctx,false,4); }
  @GameTest(templateName=EMPTY_STRUCTURE) public void deniedPlayer(TestContext ctx) { verify(ctx,false,0); }
  @GameTest(templateName=EMPTY_STRUCTURE) public void deniedOpTwo(TestContext ctx) { verify(ctx,false,2); }
}

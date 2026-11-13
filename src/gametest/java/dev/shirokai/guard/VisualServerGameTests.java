package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Explicit visualTest profile only: a temporary loopback server for the automated client. */
public class VisualServerGameTests implements FabricGameTest {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 18000, batchId = "visual")
  public void realClientMenu(TestContext ctx) throws Exception {
    var server = ctx.getWorld().getServer();
    server.setOnlineMode(false);
    var done = new AtomicBoolean();
    var target = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    var a = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var b = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    ShiroGuard.REGIONS.put(
        Region.create(
                "visual",
                ShiroGuard.dimension(ctx.getWorld()),
                Bounds.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()))
            .withFlag(Flag.BUILD, Region.Group.ALL, "deny"));
    server
        .getCommandManager()
        .getDispatcher()
        .register(
            CommandManager.literal("sg")
                .then(
                    CommandManager.literal("visualdone")
                        .requires(s -> s.getName().equals("GuardVisual"))
                        .executes(
                            c -> {
                              done.set(true);
                              return 1;
                            })));
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, s) -> {
          var p = handler.player;
          if (!p.getGameProfile().getName().equals("GuardVisual")) {
            p.networkHandler.disconnect(net.minecraft.text.Text.literal("Local test only"));
            return;
          }
          me.lucko.fabric.api.permissions.v0.PermissionCheckEvent.EVENT.register(
              (source, node) ->
                  source instanceof net.minecraft.server.command.ServerCommandSource command
                          && command.getPlayer() == p
                          && node.startsWith("shirokaiguard.")
                      ? net.fabricmc.fabric.api.util.TriState.TRUE
                      : net.fabricmc.fabric.api.util.TriState.DEFAULT);
          ShiroGuard.LOG.info(
              "Visual client admin permission: {}",
              ShiroGuard.permission(p.getCommandSource(), "admin"));
          s.getCommandManager().sendCommandTree(p);
          p.teleport(ctx.getWorld(), target.getX() + .5, target.getY(), target.getZ() + .5, 0, 0);
        });
    server.getNetworkIo().bind(InetAddress.getByName("127.0.0.1"), 25567);
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (done.get()) {
            ctx.complete();
            return;
          }
          try {
            Thread.sleep(20);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    ShiroGuard.LOG.info("Visual test ready at loopback port 25567");
  }
}

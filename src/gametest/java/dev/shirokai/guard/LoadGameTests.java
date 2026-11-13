package dev.shirokai.guard;

import com.google.gson.JsonObject;
import dev.shirokai.guard.core.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** An opt-in, isolated server profile: real server entities, embedded (not remote) connections. */
public class LoadGameTests implements FabricGameTest {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "load")
  public void hundredEmbeddedPlayers(TestContext ctx) {
    long setup = System.nanoTime();
    var world = ctx.getWorld();
    var from = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var to = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    var r =
        Region.create(
                "load",
                "minecraft:overworld",
                Bounds.between(
                    from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()))
            .withFlag(Flag.BUILD, Region.Group.ALL, "deny")
            .withFlag(Flag.INTERACT, Region.Group.ALL, "deny")
            .withFlag(Flag.PVP, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var players = new ArrayList<ServerPlayerEntity>();
    for (int i = 0; i < 100; i++) {
      var p = TestPlayers.create(ctx);
      p.setNoGravity(true);
      p.noClip = true;
      p.setPosition(
          Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(i % 7, 2 + (i / 49) * 2, (i / 7) % 7))));
      players.add(p);
    }
    long setupNanos = System.nanoTime() - setup;
    var nanos = new long[200];
    long[] start = {0};
    int[] count = {0};
    boolean[] active = {true};
    long[] decisions = {0};
    ctx.assertEquals(
        ctx.getWorld().getServer().getPlayerManager().getPlayerList().size(),
        100,
        "The load profile must contain exactly 100 players");
    ServerTickEvents.START_SERVER_TICK.register(
        server -> {
          if (!active[0] || ctx.getTick() < 40) return;
          start[0] = System.nanoTime();
          for (var p : players) {
            var pos = p.getBlockPos();
            ctx.assertFalse(
                ShiroGuard.allowed(world, pos, Flag.BLOCK_BREAK, p),
                "Protected break allowed under load");
            ctx.assertFalse(
                ShiroGuard.allowed(world, pos, Flag.BLOCK_PLACE, p),
                "Protected place allowed under load");
            ctx.assertFalse(
                ShiroGuard.allowed(world, pos, Flag.INTERACT, p), "Interaction allowed under load");
            ctx.assertFalse(
                PlayerBlockBreakEvents.BEFORE
                    .invoker()
                    .beforeBlockBreak(world, p, pos, Blocks.STONE.getDefaultState(), null),
                "Server callback lost protection under load");
            decisions[0] += 4;
          }
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (!active[0] || start[0] == 0) return;
          nanos[count[0]++] = System.nanoTime() - start[0];
          start[0] = 0;
          if (count[0] == nanos.length) {
            active[0] = false;
            Arrays.sort(nanos);
            var json = new JsonObject();
            json.addProperty("players", 100);
            json.addProperty("connections", "embedded; no remote clients or network latency");
            json.addProperty("warmupTicks", 40);
            json.addProperty("measuredTicks", 200);
            json.addProperty("guardDecisions", decisions[0]);
            json.addProperty("setupMillis", setupNanos / 1e6);
            json.addProperty("meanTickMillis", Arrays.stream(nanos).average().orElseThrow() / 1e6);
            json.addProperty("p95TickMillis", nanos[189] / 1e6);
            json.addProperty("maxTickMillis", nanos[199] / 1e6);
            json.addProperty("cache", ShiroGuard.REGIONS.metrics().toString());
            try {
              AtomicJson.write(Path.of("../load-profile.json"), json);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            ShiroGuard.LOG.info("Load profile: {}", json);
            ctx.complete();
          }
        });
  }
}

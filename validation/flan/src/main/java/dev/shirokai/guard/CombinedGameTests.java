package dev.shirokai.guard;

import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.persistence.Position;
import dev.shirokai.guard.compat.CratesCompat;
import dev.shirokai.guard.core.*;
import dev.shirokaicrates.ShirokaiCrates;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.claim.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public class CombinedGameTests implements FabricGameTest {
  private ServerPlayerEntity admin(TestContext ctx) {
    var p = TestPlayers.create(ctx);
    var user = LuckPermsProvider.get().getUserManager().loadUser(p.getUuid()).join();
    for (var node :
        List.of(
            "shirokaiguard.admin",
            "shirokaiguard.region.flag",
            "shirokaiguard.bypass",
            "shirokaicrates.*")) user.data().add(Node.builder(node).value(true).build());
    return p;
  }

  private Region region(TestContext ctx, Flag flag) {
    var a = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var b = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    var r =
        Region.create(
                "combo_" + a.getX() + "_" + a.getZ(),
                ShiroGuard.dimension(ctx.getWorld()),
                Bounds.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()))
            .withFlag(flag, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    return r;
  }

  private BlockPos platform(TestContext ctx) {
    for (int x = 0; x <= 6; x++)
      for (int z = 0; z <= 6; z++)
        ctx.getWorld()
            .setBlockState(
                ctx.getAbsolutePos(new BlockPos(x, 1, z)), Blocks.STONE.getDefaultState());
    return ctx.getAbsolutePos(new BlockPos(3, 2, 3));
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void fullModSetAndCommandsAreLoaded(TestContext ctx) {
    for (var id :
        List.of(
            "shirokai_guard",
            "flan",
            "cobblemon",
            "luckperms",
            "impactor",
            "shirokai_essentials",
            "shirokaispawn",
            "shirokai_raids",
            "shirokaicrates"))
      ctx.assertTrue(FabricLoader.getInstance().isModLoaded(id), "Mod missing: " + id);
    var root = ctx.getWorld().getServer().getCommandManager().getDispatcher().getRoot();
    for (var cmd : List.of("sg", "home", "explorar", "raid", "crates"))
      ctx.assertTrue(root.getChild(cmd) != null, "Command missing: " + cmd);
    ctx.assertTrue(ShirokaiEssentials.instance() != null, "Essentials did not initialize");
    ctx.assertTrue(
        ShirokaiCrates.service() != null && ShirokaiCrates.service().healthy(),
        "Crates service did not initialize healthy");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void actualLuckPermsNegativeWinsOperatorFallback(TestContext ctx) {
    var p = admin(ctx);
    var user = LuckPermsProvider.get().getUserManager().getUser(p.getUuid());
    user.data().remove(Node.builder("shirokaiguard.region.flag").value(true).build());
    user.data().add(Node.builder("shirokaiguard.region.flag").value(false).build());
    ctx.assertFalse(
        user.getCachedData()
            .getPermissionData()
            .checkPermission("shirokaiguard.region.flag")
            .asBoolean(),
        "LuckPerms negative was not installed");
    ctx.assertFalse(
        ShiroGuard.permission(p.getCommandSource().withLevel(4), "region.flag"),
        "OP fallback defeated actual LuckPerms negative");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void essentialsSystemTeleportCannotIgnoreEntryDeny(TestContext ctx) throws Exception {
    var r = region(ctx, Flag.ENTRY);
    var target = platform(ctx);
    var p = admin(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3))));
    var origin = p.getPos();
    var destination =
        new Position(
            r.dimension(),
            target.getX() + .5,
            target.getY(),
            target.getZ() + .5,
            0,
            0,
            System.currentTimeMillis());
    ctx.assertTrue(
        ShirokaiEssentials.instance().teleports.safe(p.getServer(), destination),
        "Essentials target must pass native safety checks");
    ShirokaiEssentials.instance().teleports.systemTeleport(p, destination);
    ctx.assertEquals(p.getPos(), origin, "Essentials system teleport crossed denied entry");
    ShiroGuard.REGIONS.remove(r.dimension(), r.id());
    ShirokaiEssentials.instance().teleports.systemTeleport(p, destination);
    ctx.assertTrue(
        p.squaredDistanceTo(destination.x(), destination.y(), destination.z()) < .01,
        "Positive control: Essentials teleport failed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void spawnExplorarCannotIgnoreEntryDeny(TestContext ctx) throws Exception {
    var r = region(ctx, Flag.ENTRY);
    var target = platform(ctx);
    var p = admin(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3))));
    var origin = p.getPos();
    var spawn = dev.shirokai.spawn.ShirokaiSpawn.INSTANCE;
    var field = spawn.getClass().getDeclaredField("config");
    field.setAccessible(true);
    var config = field.get(spawn);
    var destination = config.getClass().getDeclaredField("explorar");
    destination.setAccessible(true);
    var old = destination.get(config);
    var type = Class.forName("dev.shirokai.spawn.Storage$Destination");
    var constructor =
        type.getDeclaredConstructor(
            String.class, double.class, double.class, double.class, float.class, float.class);
    constructor.setAccessible(true);
    try {
      destination.set(
          config,
          constructor.newInstance(
              r.dimension(),
              target.getX() + .5,
              (double) target.getY(),
              target.getZ() + .5,
              0f,
              0f));
      p.getServer().getCommandManager().getDispatcher().execute("explorar", p.getCommandSource());
      ctx.assertEquals(p.getPos(), origin, "/explorar crossed entry deny");
      ShiroGuard.REGIONS.remove(r.dimension(), r.id());
      p.getServer().getCommandManager().getDispatcher().execute("explorar", p.getCommandSource());
      ctx.assertTrue(
          p.squaredDistanceTo(target.getX() + .5, target.getY(), target.getZ() + .5) < .01,
          "Positive control: /explorar failed");
    } finally {
      destination.set(config, old);
    }
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void raidStyleTeleportReturnValueIsProtected(TestContext ctx) {
    var r = region(ctx, Flag.ENTRY);
    var target = platform(ctx);
    var p = admin(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3))));
    ctx.assertFalse(
        p.teleport(
            ctx.getWorld(), target.getX() + .5, target.getY(), target.getZ() + .5, Set.of(), 0, 0),
        "Seven-argument teleport used by Raids reported success across deny");
    ShiroGuard.REGIONS.remove(r.dimension(), r.id());
    ctx.assertTrue(
        p.teleport(
            ctx.getWorld(), target.getX() + .5, target.getY(), target.getZ() + .5, Set.of(), 0, 0),
        "Positive control: teleport failed");
    ctx.complete();
  }

  private void poll(
      TestContext ctx,
      String stage,
      java.util.function.BooleanSupplier condition,
      Runnable success) {
    if (condition.getAsBoolean()) {
      success.run();
      return;
    }
    ctx.assertTrue(ctx.getTick() < 180, "Timed out awaiting Crates: " + stage);
    ctx.waitAndRun(1, () -> poll(ctx, stage, condition, success));
  }

  // Test-only observation: the disk barrier clears on the writer thread before its completion
  // releases the service's busy flag on the server thread. Never mutate either lock.
  private boolean crateIdle(ServerPlayerEntity p) {
    var service = ShirokaiCrates.service();
    if (!service.healthy()) throw new IllegalStateException("Crates storage failed");
    try {
      var field = service.getClass().getDeclaredField("busy");
      field.setAccessible(true);
      return !service.checkpoints().blocked(p.getUuid())
          && !((java.util.Set<?>) field.get(service)).contains(p.getUuid());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Crates test readiness contract changed", e);
    }
  }

  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
  public void registeredCrateInteractionCannotBypassSplitFlanDeny(TestContext ctx) {
    var r = region(ctx, Flag.INTERACT).withFlag(Flag.BUILD, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = admin(ctx);
    var pos = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    p.setPosition(Vec3d.ofCenter(pos.west(2)));
    ctx.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
    var service = ShirokaiCrates.service();
    var crate = service.snapshot().crates().values().iterator().next();
    ctx.assertTrue(
        service.allowed(p, "shirokaicrates.admin"), "Actual Crates permission was not granted");
    service.register(p, pos, crate.id());
    poll(
        ctx,
        "registry commit",
        () -> service.protectedBlock(ctx.getWorld(), pos) && crateIdle(p),
        () -> {
          ctx.assertTrue(
              CratesCompat.registered(ctx.getWorld(), pos),
              "Guard adapter did not see real Crates registry");
          service.give(p, p, crate.id(), 1, false);
          ctx.assertFalse(p.getMainHandStack().isEmpty(), "Actual Crates key not delivered");
          poll(
              ctx,
              "key checkpoint and server completion",
              () -> crateIdle(p),
              () -> {
                var claim =
                    ClaimStorage.get(ctx.getWorld())
                        .createAdminClaim(
                            ctx.getAbsolutePos(new BlockPos(0, 0, 0)),
                            ctx.getAbsolutePos(new BlockPos(7, 7, 7)),
                            ctx.getWorld(),
                            true);
                ctx.assertTrue(claim != null, "Flan claim setup failed");
                claim.setPlayerGroup(p.getUuid(), "combo", true);
                claim.editPerms(null, "combo", BuiltinPermission.INTERACTBLOCK, 0, true);
                claim.editPerms(null, "combo", BuiltinPermission.OPENCONTAINER, 0, true);
                var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                int keys = p.getMainHandStack().getCount();
                p.interactionManager.interactBlock(
                    p, ctx.getWorld(), p.getMainHandStack(), Hand.MAIN_HAND, hit);
                ctx.assertTrue(
                    p.currentScreenHandler == p.playerScreenHandler,
                    "Crates opened despite independent Flan deny");
                ctx.assertEquals(
                    p.getMainHandStack().getCount(), keys, "Denied interaction consumed a key");
                ctx.assertTrue(crateIdle(p), "Denied interaction started a Crates transaction");
                claim.editPerms(null, "combo", BuiltinPermission.INTERACTBLOCK, 1, true);
                claim.editPerms(null, "combo", BuiltinPermission.OPENCONTAINER, 1, true);
                p.interactionManager.interactBlock(
                    p, ctx.getWorld(), p.getMainHandStack(), Hand.MAIN_HAND, hit);
                poll(
                    ctx,
                    "authorized opening",
                    () -> p.currentScreenHandler != p.playerScreenHandler,
                    () -> {
                      ctx.assertTrue(
                          p.currentScreenHandler
                              .getClass()
                              .getName()
                              .startsWith("dev.shirokaicrates"),
                          "Positive control did not open Crates screen");
                      ctx.complete();
                    });
              });
        });
  }
}

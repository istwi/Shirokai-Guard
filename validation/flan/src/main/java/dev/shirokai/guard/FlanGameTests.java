package dev.shirokai.guard;

import com.google.gson.JsonObject;
import dev.shirokai.guard.core.*;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.claim.*;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** All claims created here belong exclusively to build/gametest's disposable world. */
public class FlanGameTests implements FabricGameTest {
  private record Fixture(Claim claim, Region region, ServerPlayerEntity player, BlockPos pos) {}

  private Fixture setup(TestContext ctx, boolean flanAllows, String guardValue) {
    var world = ctx.getWorld();
    var min = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
    var max = ctx.getAbsolutePos(new BlockPos(6, 6, 6));
    var pos = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    var claim = ClaimStorage.get(world).createAdminClaim(min, max, world, true);
    ctx.assertTrue(claim != null, "Flan claim creation failed");
    var player = TestPlayers.create(ctx);
    player.setPosition(Vec3d.ofCenter(pos.west(2)));
    claim.setPlayerGroup(player.getUuid(), "guard-test", true);
    for (var permission :
        new net.minecraft.util.Identifier[] {
          BuiltinPermission.BREAK,
          BuiltinPermission.PLACE,
          BuiltinPermission.OPENCONTAINER,
          BuiltinPermission.INTERACTBLOCK
        })
      ctx.assertTrue(
          claim.editPerms(null, "guard-test", permission, flanAllows ? 1 : 0, true),
          "Flan fixture permission setup failed");
    var region =
        Region.create(
                "flan_" + pos.getX() + "_" + pos.getZ(),
                ShiroGuard.dimension(world),
                Bounds.between(
                    min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()))
            .withFlag(Flag.BUILD, Region.Group.ALL, guardValue);
    ShiroGuard.REGIONS.put(region);
    world.setBlockState(pos, Blocks.STONE.getDefaultState());
    ctx.assertEquals(
        claim.canInteract(player, BuiltinPermission.BREAK, pos, false),
        flanAllows,
        "Flan permission precondition failed");
    ctx.assertEquals(
        ShiroGuard.allowed(world, pos, Flag.BLOCK_BREAK, player),
        guardValue.equals("allow"),
        "Guard permission precondition failed");
    return new Fixture(claim, region, player, pos);
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void guardAllowDoesNotOverrideFlanDeny(TestContext ctx) {
    var f = setup(ctx, false, "allow");
    var before = f.claim.toJson(new JsonObject()).deepCopy();
    ctx.assertFalse(
        f.player.interactionManager.tryBreakBlock(f.pos), "Guard allow overrode Flan deny");
    ctx.assertTrue(
        ctx.getWorld().getBlockState(f.pos).isOf(Blocks.STONE), "Protected stone was lost");
    ctx.assertEquals(f.claim.toJson(new JsonObject()), before, "Guard changed Flan claim data");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void guardBypassDoesNotOverrideFlanDeny(TestContext ctx) {
    var f = setup(ctx, false, "allow");
    PermissionCheckEvent.EVENT.register(
        (source, node) ->
            source instanceof ServerCommandSource s
                    && s.getPlayer() == f.player
                    && node.equals("shirokaiguard.bypass")
                ? TriState.TRUE
                : TriState.DEFAULT);
    ShiroGuard.BYPASS.add(f.player.getUuid());
    try {
      ctx.assertTrue(ShiroGuard.bypass(f.player), "Guard bypass was not actually enabled");
      ctx.assertFalse(
          f.player.interactionManager.tryBreakBlock(f.pos),
          "Guard bypass bypassed an independent Flan claim");
    } finally {
      ShiroGuard.BYPASS.remove(f.player.getUuid());
    }
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void flanAllowDoesNotOverrideGuardDeny(TestContext ctx) {
    var f = setup(ctx, true, "deny");
    ctx.assertFalse(
        f.player.interactionManager.tryBreakBlock(f.pos), "Flan allow overrode Guard deny");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void bothAllowBreakSucceeds(TestContext ctx) {
    var f = setup(ctx, true, "allow");
    ctx.assertTrue(
        f.player.interactionManager.tryBreakBlock(f.pos),
        "Positive control: jointly allowed break failed");
    ctx.assertTrue(ctx.getWorld().getBlockState(f.pos).isAir(), "Positive control: stone remains");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void flanDeniesContainerDespiteGuardAllow(TestContext ctx) {
    var f = setup(ctx, false, "allow");
    ctx.getWorld().setBlockState(f.pos, Blocks.CHEST.getDefaultState());
    var region =
        f.region
            .withFlag(Flag.INTERACT, Region.Group.ALL, "allow")
            .withFlag(Flag.CHEST_ACCESS, Region.Group.ALL, "allow");
    ShiroGuard.REGIONS.put(region);
    var hit = new BlockHitResult(Vec3d.ofCenter(f.pos), Direction.UP, f.pos, false);
    ctx.assertFalse(
        f.claim.canInteract(f.player, BuiltinPermission.OPENCONTAINER, f.pos, false),
        "Flan must deny opening the container");
    f.player.interactionManager.interactBlock(
        f.player, ctx.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
    ctx.assertTrue(
        f.player.currentScreenHandler == f.player.playerScreenHandler,
        "Guard allow opened a Flan-protected container");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void flanDeniesRealPlacementDespiteGuardAllow(TestContext ctx) {
    var f = setup(ctx, false, "allow");
    var pos = f.pos.up();
    f.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
    var hit = new BlockHitResult(Vec3d.ofCenter(f.pos).add(0, 0.5, 0), Direction.UP, f.pos, false);
    f.player.interactionManager.interactBlock(
        f.player, ctx.getWorld(), f.player.getMainHandStack(), Hand.MAIN_HAND, hit);
    ctx.assertTrue(
        ctx.getWorld().getBlockState(pos).isAir(), "Player placement bypassed Flan protection");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void jointlyAllowedContainerCanOpen(TestContext ctx) {
    var f = setup(ctx, true, "allow");
    ctx.getWorld().setBlockState(f.pos, Blocks.CHEST.getDefaultState());
    var hit = new BlockHitResult(Vec3d.ofCenter(f.pos), Direction.UP, f.pos, false);
    f.player.interactionManager.interactBlock(
        f.player, ctx.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
    ctx.assertTrue(
        f.player.currentScreenHandler != f.player.playerScreenHandler,
        "Positive control: jointly allowed chest did not open");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void flanContainerAllowDoesNotOverrideGuardChestDeny(TestContext ctx) {
    var f = setup(ctx, true, "allow");
    ctx.getWorld().setBlockState(f.pos, Blocks.CHEST.getDefaultState());
    ShiroGuard.REGIONS.put(f.region.withFlag(Flag.CHEST_ACCESS, Region.Group.ALL, "deny"));
    var hit = new BlockHitResult(Vec3d.ofCenter(f.pos), Direction.UP, f.pos, false);
    f.player.interactionManager.interactBlock(
        f.player, ctx.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
    ctx.assertTrue(
        f.player.currentScreenHandler == f.player.playerScreenHandler,
        "Flan allow opened a Guard-protected container");
    ctx.complete();
  }
}

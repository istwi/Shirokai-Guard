package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.explosion.Explosion;

public class GuardGameTests implements FabricGameTest {
  private Region protect(TestContext ctx, Flag flag) {
    var a = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var b = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    var region =
        Region.create(
                "test_" + a.getX() + "_" + a.getZ(),
                ShiroGuard.dimension(ctx.getWorld()),
                Bounds.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()))
            .withFlag(flag, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(region);
    return region;
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void blockBreakDenied(TestContext ctx) {
    protect(ctx, Flag.BLOCK_BREAK);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var w = ctx.getWorld();
    w.setBlockState(pos, Blocks.STONE.getDefaultState());
    var player = TestPlayers.create(ctx);
    ctx.assertFalse(player.interactionManager.tryBreakBlock(pos), "Protected stone was broken");
    ctx.assertTrue(w.getBlockState(pos).isOf(Blocks.STONE), "Stone must survive");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void blockPlacementDenied(TestContext ctx) {
    protect(ctx, Flag.BLOCK_PLACE);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var w = ctx.getWorld();
    w.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
    var player = TestPlayers.create(ctx);
    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
    var hit =
        new BlockHitResult(
            Vec3d.ofCenter(pos.down()).add(0, 0.5, 0), Direction.UP, pos.down(), false);
    var item = (BlockItem) Items.STONE;
    item.place(new ItemPlacementContext(player, Hand.MAIN_HAND, player.getMainHandStack(), hit));
    ctx.assertTrue(w.getBlockState(pos).isAir(), "Stone placement must fail");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void explosionKeepsProtectedBlocks(TestContext ctx) {
    protect(ctx, Flag.OTHER_EXPLOSION);
    var pos = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    var w = ctx.getWorld();
    w.setBlockState(pos, Blocks.STONE.getDefaultState());
    var blast =
        new Explosion(
            w,
            null,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            4,
            false,
            Explosion.DestructionType.DESTROY,
            List.of(pos));
    blast.affectWorld(false);
    ctx.assertTrue(w.getBlockState(pos).isOf(Blocks.STONE), "Explosion removed protected block");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void mobSpawningDenied(TestContext ctx) {
    protect(ctx, Flag.MOB_SPAWNING);
    var w = ctx.getWorld();
    var mob = new ZombieEntity(EntityType.ZOMBIE, w);
    mob.refreshPositionAndAngles(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))), 0, 0);
    ctx.assertFalse(w.spawnEntity(mob), "Zombie spawned inside denied region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void projectileSpawningDenied(TestContext ctx) {
    protect(ctx, Flag.PROJECTILE);
    var w = ctx.getWorld();
    var arrow = new ArrowEntity(EntityType.ARROW, w);
    arrow.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    ctx.assertFalse(w.spawnEntity(arrow), "Arrow spawned inside denied region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void firePlacementDenied(TestContext ctx) {
    protect(ctx, Flag.FIRE_SPREAD);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    ctx.assertFalse(
        ctx.getWorld().setBlockState(pos, Blocks.FIRE.getDefaultState()),
        "Fire placed inside denied region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void iceFormationDenied(TestContext ctx) {
    protect(ctx, Flag.ICE_FORM);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    ctx.assertFalse(
        ctx.getWorld().setBlockState(pos, Blocks.ICE.getDefaultState()),
        "Ice formed inside denied region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void bucketPlacementDenied(TestContext ctx) {
    protect(ctx, Flag.BLOCK_PLACE);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    ctx.assertFalse(
        ((BucketItem) Items.WATER_BUCKET).placeFluid(null, ctx.getWorld(), pos, null),
        "Bucket ignored block-place");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void teleportDenied(TestContext ctx) {
    protect(ctx, Flag.ENTRY);
    var p = TestPlayers.create(ctx);
    var dest = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3))));
    var start = p.getPos();
    p.teleport(ctx.getWorld(), dest.getX(), dest.getY(), dest.getZ(), 0, 0);
    ctx.assertEquals(p.getPos(), start, "Teleport crossed entry deny");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void itemDropPreservesInventory(TestContext ctx) {
    protect(ctx, Flag.ITEM_DROP);
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    p.getInventory().selectedSlot = 0;
    p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 12));
    ctx.assertFalse(p.dropSelectedItem(true), "Drop succeeded");
    ctx.assertEquals(p.getInventory().getStack(0).getCount(), 12, "Drop lost inventory items");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void entityDamageDenied(TestContext ctx) {
    protect(ctx, Flag.ENTITY_DAMAGE);
    var w = ctx.getWorld();
    var mob = new ZombieEntity(EntityType.ZOMBIE, w);
    mob.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    w.spawnEntity(mob);
    float before = mob.getHealth();
    mob.damage(w.getDamageSources().generic(), 5);
    ctx.assertEquals(mob.getHealth(), before, "Damage changed health");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void leafDecayDenied(TestContext ctx) {
    protect(ctx, Flag.LEAF_DECAY);
    var w = ctx.getWorld();
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var state =
        Blocks.OAK_LEAVES
            .getDefaultState()
            .with(LeavesBlock.DISTANCE, 7)
            .with(LeavesBlock.PERSISTENT, false);
    w.setBlockState(pos, state);
    state.randomTick(w, pos, w.random);
    ctx.assertTrue(w.getBlockState(pos).isOf(Blocks.OAK_LEAVES), "Leaves decayed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  public void pistonCannotPushIntoProtectedTarget(TestContext ctx) {
    var w = ctx.getWorld();
    var piston = ctx.getAbsolutePos(new BlockPos(1, 2, 2));
    var stone = piston.east();
    var target = stone.east();
    w.setBlockState(
        piston, Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
    w.setBlockState(stone, Blocks.STONE.getDefaultState());
    var r =
        Region.create(
                "piston_" + target.getX() + "_" + target.getZ(),
                ShiroGuard.dimension(w),
                Bounds.between(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getX(),
                    target.getY(),
                    target.getZ()))
            .withFlag(Flag.PISTON, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    w.setBlockState(piston.west(), Blocks.REDSTONE_BLOCK.getDefaultState());
    ctx.runAtTick(
        8,
        () -> {
          ctx.assertTrue(w.getBlockState(stone).isOf(Blocks.STONE), "Piston moved source");
          ctx.assertTrue(w.getBlockState(target).isAir(), "Piston crossed boundary");
          ShiroGuard.REGIONS.remove(r.dimension(), r.id());
          w.setBlockState(piston.west(), Blocks.AIR.getDefaultState());
          w.setBlockState(piston.west(), Blocks.REDSTONE_BLOCK.getDefaultState());
        });
    ctx.runAtTick(
        16,
        () -> {
          ctx.assertTrue(
              w.getBlockState(target).isOf(Blocks.STONE),
              "Positive control: allowed piston failed");
          ctx.complete();
        });
  }

  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  public void hopperCannotTakeProtectedChest(TestContext ctx) {
    var region = protect(ctx, Flag.HOPPER);
    var w = ctx.getWorld();
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    w.setBlockState(pos, Blocks.HOPPER.getDefaultState());
    w.setBlockState(pos.up(), Blocks.CHEST.getDefaultState());
    var chest = (ChestBlockEntity) w.getBlockEntity(pos.up());
    chest.setStack(0, new ItemStack(Items.DIAMOND, 8));
    ctx.runAtTick(
        16,
        () -> {
          ctx.assertEquals(chest.getStack(0).getCount(), 8, "Hopper stole items");
          ctx.assertTrue(
              ((HopperBlockEntity) w.getBlockEntity(pos)).isEmpty(), "Hopper received items");
          ShiroGuard.REGIONS.remove(region.dimension(), region.id());
        });
    ctx.runAtTick(
        32,
        () -> {
          ctx.assertTrue(
              chest.getStack(0).getCount() < 8, "Positive control: allowed hopper failed");
          ctx.complete();
        });
  }

  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  public void waterCannotFlowAcrossProtectedBoundary(TestContext ctx) {
    var w = ctx.getWorld();
    var source = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var target = source.east();
    for (int x = 0; x <= 6; x++)
      for (int z = 0; z <= 6; z++)
        w.setBlockState(ctx.getAbsolutePos(new BlockPos(x, 1, z)), Blocks.STONE.getDefaultState());
    var r =
        Region.create(
                "water_" + target.getX() + "_" + target.getZ(),
                ShiroGuard.dimension(w),
                Bounds.between(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getX(),
                    target.getY(),
                    target.getZ()))
            .withFlag(Flag.WATER_FLOW, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    w.setBlockState(source, Blocks.WATER.getDefaultState());
    ctx.runAtTick(
        20,
        () -> {
          ctx.assertTrue(w.getFluidState(target).isEmpty(), "Water crossed boundary");
          ShiroGuard.REGIONS.remove(r.dimension(), r.id());
          w.scheduleFluidTick(source, Fluids.WATER, 1);
        });
    ctx.runAtTick(
        35,
        () -> {
          ctx.assertFalse(
              w.getFluidState(target).isEmpty(), "Positive control: allowed water did not flow");
          ctx.complete();
        });
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void explicitPermissionDenialWinsOperatorFallback(TestContext ctx) {
    var p = TestPlayers.create(ctx);
    me.lucko.fabric.api.permissions.v0.PermissionCheckEvent.EVENT.register(
        (source, node) ->
            source instanceof net.minecraft.server.command.ServerCommandSource sc
                    && sc.getPlayer() == p
                ? net.fabricmc.fabric.api.util.TriState.FALSE
                : net.fabricmc.fabric.api.util.TriState.DEFAULT);
    ctx.assertFalse(
        ShiroGuard.permission(p.getCommandSource().withLevel(4), "admin"),
        "Explicit denial lost to operator fallback");
    ShiroGuard.BYPASS.add(p.getUuid());
    ctx.assertFalse(ShiroGuard.bypass(p), "Denied permission still bypassed protection");
    ShiroGuard.BYPASS.remove(p.getUuid());
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void invincibleAndFallDamage(TestContext ctx) {
    var r = protect(ctx, Flag.FALL_DAMAGE).withFlag(Flag.INVINCIBLE, Region.Group.ALL, "allow");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    float health = p.getHealth();
    p.damage(ctx.getWorld().getDamageSources().genericKill(), 10);
    ctx.assertEquals(p.getHealth(), health, "Invincible player took damage");
    ShiroGuard.REGIONS.put(r.withFlag(Flag.INVINCIBLE, Region.Group.ALL, "unset"));
    p.damage(ctx.getWorld().getDamageSources().genericKill(), 5);
    ctx.assertTrue(p.getHealth() < health, "Positive control: survival player did not take damage");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void inventoryThrowDoesNotLoseItems(TestContext ctx) {
    protect(ctx, Flag.ITEM_DROP);
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 12));
    p.playerScreenHandler.onSlotClick(36, 1, net.minecraft.screen.slot.SlotActionType.THROW, p);
    ctx.assertEquals(p.getInventory().getStack(0).getCount(), 12, "Inventory throw lost items");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pickupDenied(TestContext ctx) {
    protect(ctx, Flag.ITEM_PICKUP);
    var w = ctx.getWorld();
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(pos));
    var item = new ItemEntity(w, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND));
    item.resetPickupDelay();
    w.spawnEntity(item);
    item.onPlayerCollision(p);
    ctx.assertFalse(item.isRemoved(), "Item was picked up");
    ctx.assertFalse(
        p.getInventory().contains(new ItemStack(Items.DIAMOND)), "Diamond entered inventory");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pvpDenied(TestContext ctx) {
    var region = protect(ctx, Flag.PVP);
    var w = ctx.getWorld();
    w.getServer().setPvpEnabled(true);
    var a = TestPlayers.create(ctx);
    var b = TestPlayers.create(ctx);
    a.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
    b.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
    a.setNoGravity(true);
    b.setNoGravity(true);
    var position = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2)));
    a.setPosition(position);
    b.setPosition(position);
    ctx.runAtTick(
        70,
        () -> {
          float health = b.getHealth();
          b.damage(w.getDamageSources().playerAttack(a), 4);
          ctx.assertEquals(b.getHealth(), health, "PVP deny ignored");
          ShiroGuard.REGIONS.remove(region.dimension(), region.id());
          b.damage(w.getDamageSources().playerAttack(a), 4);
          ctx.assertTrue(b.getHealth() < health, "Positive control: player attack did not execute");
          ctx.complete();
        });
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void commandsCreateFlagsAndRemove(TestContext ctx) throws Exception {
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    var source = p.getCommandSource().withLevel(4);
    var dispatcher = ctx.getWorld().getServer().getCommandManager().getDispatcher();
    var a = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
    var b = ctx.getAbsolutePos(new BlockPos(5, 5, 5));
    String id = "command_" + a.getX() + "_" + a.getZ();
    ctx.assertEquals(
        dispatcher.execute("sg pos1 " + a.getX() + " " + a.getY() + " " + a.getZ(), source),
        1,
        "pos1 failed");
    dispatcher.execute("sg pos2 " + b.getX() + " " + b.getY() + " " + b.getZ(), source);
    dispatcher.execute("sg define " + id, source);
    dispatcher.execute("sg flag " + id + " build deny @nonmembers", source);
    var r = ShiroGuard.REGIONS.get(ShiroGuard.dimension(ctx.getWorld()), id);
    ctx.assertTrue(r.owners().contains(p.getUuid()), "Creator not owner");
    ctx.assertEquals(
        r.flags().get(Flag.BUILD).get(Region.Group.NONMEMBERS), "deny", "Flag command failed");
    dispatcher.execute("sg remove " + id, source);
    ctx.assertTrue(
        ShiroGuard.REGIONS.all().stream().noneMatch(v -> v.id().equals(id)), "Removal failed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void itemFrameDamageDenied(TestContext ctx) {
    protect(ctx, Flag.ITEM_FRAME);
    var frame =
        new net.minecraft.entity.decoration.ItemFrameEntity(
            ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(2, 2, 2)), Direction.NORTH);
    ctx.assertFalse(
        frame.damage(ctx.getWorld().getDamageSources().generic(), 5),
        "Frame ignored damage protection");
    ctx.assertFalse(frame.isRemoved(), "Frame was removed");
    ctx.complete();
  }

  private static BlockPos fixtureCrate;

  public static boolean fixtureRegistry(net.minecraft.world.World world, BlockPos pos) {
    return pos.equals(fixtureCrate);
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void crateExceptionIsRestrictedToRegisteredBlock(TestContext ctx) throws Exception {
    var r = protect(ctx, Flag.INTERACT).withFlag(Flag.BUILD, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    var w = ctx.getWorld();
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    fixtureCrate = pos;
    w.setBlockState(pos, Blocks.STONE.getDefaultState());
    var field = dev.shirokai.guard.compat.CratesCompat.class.getDeclaredField("predicate");
    field.setAccessible(true);
    var previous = field.get(null);
    try {
      field.set(
          null,
          GuardGameTests.class.getMethod(
              "fixtureRegistry", net.minecraft.world.World.class, BlockPos.class));
      var use = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker();
      var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
      ctx.assertEquals(
          use.interact(p, w, Hand.MAIN_HAND, hit),
          net.minecraft.util.ActionResult.PASS,
          "Registered crate did not defer authorization to Crates");
      var other = new BlockHitResult(Vec3d.ofCenter(pos.east()), Direction.UP, pos.east(), false);
      ctx.assertEquals(
          use.interact(p, w, Hand.MAIN_HAND, other),
          net.minecraft.util.ActionResult.FAIL,
          "Crate exception leaked to neighboring blocks");
      ctx.assertFalse(
          p.interactionManager.tryBreakBlock(pos), "Preview exception enabled destruction");
      var blast =
          new Explosion(
              w,
              null,
              pos.getX(),
              pos.getY(),
              pos.getZ(),
              4,
              false,
              Explosion.DestructionType.DESTROY,
              List.of(pos));
      blast.affectWorld(false);
      ctx.assertTrue(
          w.getBlockState(pos).isOf(Blocks.STONE), "Registered crate was destroyed by explosion");
    } finally {
      field.set(null, previous);
      fixtureCrate = null;
    }
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void doorPlacementCannotCrossVerticalBoundary(TestContext ctx) {
    var w = ctx.getWorld();
    var lower = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    var upper = lower.up();
    w.setBlockState(lower.down(), Blocks.STONE.getDefaultState());
    var r =
        Region.create(
                "doorplace_" + upper.getX() + "_" + upper.getZ(),
                ShiroGuard.dimension(w),
                Bounds.between(
                    upper.getX(),
                    upper.getY(),
                    upper.getZ(),
                    upper.getX(),
                    upper.getY(),
                    upper.getZ()))
            .withFlag(Flag.BLOCK_PLACE, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_DOOR));
    var hit =
        new BlockHitResult(
            Vec3d.ofCenter(lower.down()).add(0, 0.5, 0), Direction.UP, lower.down(), false);
    var item = (BlockItem) Items.OAK_DOOR;
    item.place(new ItemPlacementContext(p, Hand.MAIN_HAND, p.getMainHandStack(), hit));
    ctx.assertTrue(
        w.getBlockState(lower).isAir(), "Door lower half was placed across protected boundary");
    ctx.assertTrue(w.getBlockState(upper).isAir(), "Door upper half bypassed region");
    ShiroGuard.REGIONS.remove(r.dimension(), r.id());
    item.place(new ItemPlacementContext(p, Hand.MAIN_HAND, p.getMainHandStack(), hit));
    ctx.assertTrue(
        w.getBlockState(upper).isOf(Blocks.OAK_DOOR), "Positive control: door placement failed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void doorBreakCannotDestroyProtectedOtherHalf(TestContext ctx) {
    var w = ctx.getWorld();
    var lower = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    var upper = lower.up();
    w.setBlockState(lower.down(), Blocks.STONE.getDefaultState());
    var state = Blocks.OAK_DOOR.getDefaultState();
    w.setBlockState(lower, state, 2);
    w.setBlockState(
        upper,
        state.with(
            net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER),
        2);
    var r =
        Region.create(
                "doorbreak_" + upper.getX() + "_" + upper.getZ(),
                ShiroGuard.dimension(w),
                Bounds.between(
                    upper.getX(),
                    upper.getY(),
                    upper.getZ(),
                    upper.getX(),
                    upper.getY(),
                    upper.getZ()))
            .withFlag(Flag.BLOCK_BREAK, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    ctx.assertFalse(
        p.interactionManager.tryBreakBlock(lower), "Lower half destroyed protected upper half");
    ctx.assertTrue(
        w.getBlockState(upper).isOf(Blocks.OAK_DOOR), "Protected upper half disappeared");
    ctx.complete();
  }
}

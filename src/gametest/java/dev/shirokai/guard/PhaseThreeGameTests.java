package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class PhaseThreeGameTests implements FabricGameTest {
  private ServerPlayerEntity admin(TestContext ctx, AtomicBoolean allowed) {
    var p = TestPlayers.create(ctx);
    PermissionCheckEvent.EVENT.register(
        (s, node) ->
            s instanceof ServerCommandSource source
                    && source.getPlayer() == p
                    && node.startsWith("shirokaiguard.")
                ? (allowed.get() ? TriState.TRUE : TriState.FALSE)
                : TriState.DEFAULT);
    return p;
  }

  private Region region(TestContext ctx, String suffix) {
    var min = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var max = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    var r =
        Region.create(
            "ui_" + min.getX() + "_" + min.getZ() + suffix,
            ShiroGuard.dimension(ctx.getWorld()),
            Bounds.between(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
    ShiroGuard.REGIONS.put(r);
    return r;
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void menuNeverTransfersIconsOrInventoryItems(TestContext ctx) {
    var p = admin(ctx, new AtomicBoolean(true));
    p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 12));
    GuardMenus.home(p);
    var view = (GuardMenus.View) p.currentScreenHandler;
    for (var type : SlotActionType.values()) {
      view.onSlotClick(0, 0, type, p);
      view.onSlotClick(81, 0, type, p);
    }
    ctx.assertTrue(view.quickMove(p, 10).isEmpty(), "Shift-click moved an icon");
    ctx.assertTrue(view.getCursorStack().isEmpty(), "An icon entered the cursor");
    ctx.assertEquals(p.getInventory().getStack(0).getCount(), 12, "GUI changed inventory");
    ctx.assertTrue(
        view.getInventory().getStack(49).isOf(Items.ARROW), "Required back arrow missing");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void permissionRevocationClosesMenuBeforeAction(TestContext ctx) {
    var allowed = new AtomicBoolean(true);
    var p = admin(ctx, allowed);
    GuardMenus.home(p);
    var view = (GuardMenus.View) p.currentScreenHandler;
    allowed.set(false);
    view.onSlotClick(10, 0, SlotActionType.PICKUP, p);
    ctx.assertTrue(
        p.currentScreenHandler == p.playerScreenHandler, "Revoked player retained admin menu");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void flagPermissionDenialWinsEvenInAdminMenu(TestContext ctx) {
    var p = TestPlayers.create(ctx);
    PermissionCheckEvent.EVENT.register(
        (s, node) ->
            s instanceof ServerCommandSource source && source.getPlayer() == p
                ? (node.equals("shirokaiguard.region.flag") ? TriState.FALSE : TriState.TRUE)
                : TriState.DEFAULT);
    var r = region(ctx, "flags");
    GuardMenus.flags(p, r.id(), Region.Group.ALL, 0);
    var view = (GuardMenus.View) p.currentScreenHandler;
    view.onSlotClick(0, 0, SlotActionType.PICKUP, p);
    ctx.assertEquals(
        ShiroGuard.REGIONS.get(r.dimension(), r.id()),
        r,
        "Admin GUI ignored explicit negative flag permission");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void flagMenuChangesSupportedFlagAndGroup(TestContext ctx) {
    var p = admin(ctx, new AtomicBoolean(true));
    var r = region(ctx, "toggle");
    GuardMenus.flags(p, r.id(), Region.Group.MEMBERS, 0);
    var flags =
        Arrays.stream(Flag.values()).filter(f -> !f.optional() || ShiroGuard.COBBLEMON).toList();
    var f = flags.get(0);
    ((GuardMenus.View) p.currentScreenHandler).onSlotClick(0, 0, SlotActionType.PICKUP, p);
    ctx.assertEquals(
        ShiroGuard.REGIONS.get(r.dimension(), r.id()).flags().get(f).get(Region.Group.MEMBERS),
        "allow",
        "Flag menu failed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void templateRequiresConfirmationAndRejectsStalePlan(TestContext ctx) {
    var p = admin(ctx, new AtomicBoolean(true));
    var r = region(ctx, "template");
    var plan = TemplateActions.preview(p, r.id(), "spawn");
    ctx.assertTrue(
        ShiroGuard.REGIONS.get(r.dimension(), r.id()).flags().isEmpty(), "Preview changed region");
    ShiroGuard.REGIONS.put(r.withPriority(4));
    boolean refused = false;
    try {
      TemplateActions.confirm(p, plan.token());
    } catch (IllegalArgumentException expected) {
      refused = true;
    }
    ctx.assertTrue(refused, "Stale preview overwrote a changed region");
    var current = TemplateActions.preview(p, r.id(), "spawn");
    TemplateActions.confirm(p, current.token());
    ctx.assertEquals(
        ShiroGuard.REGIONS.get(r.dimension(), r.id()).flags().get(Flag.BUILD).get(Region.Group.ALL),
        "deny",
        "Confirmed template not applied");
    ctx.assertEquals(
        ShiroGuard.REGIONS.get(r.dimension(), r.id()).priority(),
        4,
        "Template erased custom priority");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void textEditorConsumesPrivateChatAndUpdatesFlag(TestContext ctx) {
    var p = admin(ctx, new AtomicBoolean(true));
    var r = region(ctx, "input");
    GuardMenus.prompt(
        p,
        "Mensagem",
        text -> GuardCommands.run(p.getCommandSource(), "flag", r.id() + " greeting " + text));
    ctx.assertTrue(
        GuardMenus.consume(p, "Bem-vindo ao ginásio"),
        "Private editor message was not intercepted");
    ctx.runAtTick(
        2,
        () -> {
          ctx.assertEquals(
              ShiroGuard.REGIONS
                  .get(r.dimension(), r.id())
                  .flags()
                  .get(Flag.GREETING)
                  .get(Region.Group.ALL),
              "Bem-vindo ao ginásio",
              "Editor did not commit");
          ctx.assertFalse(
              GuardMenus.consume(p, "Mensagem normal"), "Editor consumed subsequent ordinary chat");
          ctx.complete();
        });
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-ui")
  public void regionListHasWorkingPagination(TestContext ctx) {
    var p = admin(ctx, new AtomicBoolean(true));
    for (int i = 0; i < 50; i++) region(ctx, "page" + i);
    GuardMenus.regions(p, 0);
    var first = (GuardMenus.View) p.currentScreenHandler;
    ctx.assertTrue(first.getInventory().getStack(53).isOf(Items.ARROW), "Next page missing");
    first.onSlotClick(53, 0, SlotActionType.PICKUP, p);
    ctx.assertTrue(p.currentScreenHandler != first, "Page did not change");
    ctx.assertTrue(
        ((GuardMenus.View) p.currentScreenHandler).getInventory().getStack(45).isOf(Items.ARROW),
        "Previous page missing");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-physical")
  public void serverDrivenMovementRespectsEntryDeny(TestContext ctx) {
    var r = region(ctx, "physical").withFlag(Flag.ENTRY, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3))));
    var start = p.getPos();
    p.move(net.minecraft.entity.MovementType.SELF, new Vec3d(5, 0, 0));
    ctx.assertEquals(p.getPos(), start, "Server-driven motion crossed entry deny");
    ShiroGuard.REGIONS.remove(r.dimension(), r.id());
    p.move(net.minecraft.entity.MovementType.SELF, new Vec3d(5, 0, 0));
    ctx.assertTrue(p.getX() > start.x, "Positive control: physical movement failed");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-physical")
  public void autonomousVehicleCannotCarryPlayerAcrossEntryDeny(TestContext ctx) {
    var r = region(ctx, "vehicle").withFlag(Flag.ENTRY, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    var start = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(-2, 2, 3)));
    p.setPosition(start);
    var boat =
        new net.minecraft.entity.vehicle.BoatEntity(ctx.getWorld(), start.x, start.y, start.z);
    ctx.getWorld().spawnEntity(boat);
    p.startRiding(boat, true);
    boat.move(net.minecraft.entity.MovementType.SELF, new Vec3d(5, 0, 0));
    ctx.assertEquals(boat.getPos(), start, "Vehicle carried passenger across denied entry");
    ShiroGuard.REGIONS.remove(r.dimension(), r.id());
    boat.move(net.minecraft.entity.MovementType.SELF, new Vec3d(5, 0, 0));
    ctx.assertTrue(boat.getX() > start.x, "Positive control: autonomous vehicle did not move");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-config")
  public void invalidReloadPreservesLiveRulesAndConfiguration(TestContext ctx) throws Exception {
    var original = AtomicJson.read(GuardConfig.DIR.resolve("menus.json"));
    var options = GuardConfig.options();
    var r = region(ctx, "reload").withFlag(Flag.BUILD, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    ShiroGuard.save();
    try {
      var invalid = original.deepCopy();
      invalid.addProperty("pageSize", 999);
      AtomicJson.write(GuardConfig.DIR.resolve("menus.json"), invalid);
      boolean rejected = false;
      try {
        ShiroGuard.reload();
      } catch (Exception expected) {
        rejected = true;
      }
      ctx.assertTrue(rejected, "Invalid config was accepted");
      ctx.assertEquals(GuardConfig.options(), options, "Invalid reload changed live options");
      ctx.assertEquals(
          ShiroGuard.REGIONS.get(r.dimension(), r.id()),
          r,
          "Invalid reload changed protection rules");
    } finally {
      AtomicJson.write(GuardConfig.DIR.resolve("menus.json"), original);
    }
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard-entry")
  public void loginSafetyFindsPermittedLoadedLocation(TestContext ctx) {
    var origin = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
    ctx.getWorld().setBlockState(origin.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
    ctx.getWorld()
        .setBlockState(origin.east().down(), net.minecraft.block.Blocks.STONE.getDefaultState());
    var r =
        Region.create(
                "entrysafe_" + origin.getX() + "_" + origin.getZ(),
                ShiroGuard.dimension(ctx.getWorld()),
                new Bounds(
                    origin.getX(),
                    ctx.getWorld().getBottomY(),
                    origin.getZ(),
                    origin.getX(),
                    ctx.getWorld().getTopY() - 1,
                    origin.getZ()))
            .withFlag(Flag.ENTRY, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(origin));
    ctx.assertTrue(EntrySafety.enforce(p), "Login safety did not find a permitted loaded location");
    ctx.assertTrue(
        ShiroGuard.allowed(ctx.getWorld(), p.getBlockPos(), Flag.ENTRY, p),
        "Login safety left player in denied region");
    ctx.complete();
  }
}

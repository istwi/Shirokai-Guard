package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class Protection {
  private static int ticks;
  private static final Map<UUID, Set<String>> VISITS = new HashMap<>();
  private static final Map<UUID, String> FAREWELLS = new HashMap<>();

  public static void forget(UUID id) {
    VISITS.remove(id);
    FAREWELLS.remove(id);
    EntryAccess.forget(id);
  }

  public static ServerPlayerEntity actor(Entity e) {
    return e instanceof ServerPlayerEntity p ? p : null;
  }

  public static boolean entityAllowed(ServerPlayerEntity p, Entity target, boolean damage) {
    if (ShiroGuard.COBBLEMON
        && dev.shirokai.guard.compat.CobblemonCompat.pokemon(target)
        && !ShiroGuard.allowed(
            target.getWorld(),
            target.getBlockPos(),
            damage ? Flag.POKEMON_DAMAGE : Flag.POKEMON_INTERACT,
            p)) return false;
    var flag =
        target instanceof ItemFrameEntity
            ? Flag.ITEM_FRAME
            : target instanceof ArmorStandEntity
                ? Flag.ARMOR_STAND
                : damage ? Flag.ENTITY_DAMAGE : Flag.ENTITY_INTERACT;
    return ShiroGuard.allowed(target.getWorld(), target.getBlockPos(), flag, p);
  }

  public static boolean transition(
      ServerPlayerEntity p, ServerWorld target, Vec3d pos, boolean teleport) {
    if (ShiroGuard.bypass(p)) return true;
    var from = p.getBlockPos();
    var to = BlockPos.ofFloored(pos);
    var world = p.getServerWorld();
    String a = ShiroGuard.dimension(world), b = ShiroGuard.dimension(target);
    var before = ShiroGuard.REGIONS.at(a, from.getX(), from.getY(), from.getZ());
    var after = ShiroGuard.REGIONS.at(b, to.getX(), to.getY(), to.getZ());
    // Evaluate each boundary, including changes inside overlapping regions.
    boolean leaving = before.stream().anyMatch(r -> !after.contains(r));
    boolean entering = after.stream().anyMatch(r -> !before.contains(r));
    if (leaving && !ShiroGuard.allowed(world, from, Flag.EXIT, p)) return false;
    if (entering && !ShiroGuard.allowed(target, to, Flag.ENTRY, p)) return false;
    if (!EntryAccess.transition(p, target, pos, teleport)) return false;
    if (!teleport
        && a.equals(b)
        && !ShiroGuard.REGIONS.movementAllowed(
            a, p.getX(), p.getY(), p.getZ(), pos.x, pos.y, pos.z, p.getUuid())) {
      EntryAccess.notice(p, "entry-blocked");
      return false;
    }
    return !teleport
        || ShiroGuard.allowed(world, from, Flag.TELEPORT, p)
            && ShiroGuard.allowed(target, to, Flag.TELEPORT, p);
  }

  private static <T> void protectFirst(net.fabricmc.fabric.api.event.Event<T> event) {
    var phase = net.minecraft.util.Identifier.of("shirokai_guard", "protection");
    event.addPhaseOrdering(net.minecraft.util.Identifier.of("flan", "events"), phase);
    event.addPhaseOrdering(phase, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
  }

  public static BlockPos paired(BlockPos pos, net.minecraft.block.BlockState state) {
    var block = state.getBlock();
    if (block instanceof net.minecraft.block.BedBlock) {
      var dir = state.get(net.minecraft.block.BedBlock.FACING);
      return pos.offset(
          state.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT
              ? dir
              : dir.getOpposite());
    }
    if (block instanceof net.minecraft.block.DoorBlock
        || block instanceof net.minecraft.block.TallPlantBlock)
      return state.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
              == net.minecraft.block.enums.DoubleBlockHalf.LOWER
          ? pos.up()
          : pos.down();
    if (ShiroGuard.COBBLEMON) return dev.shirokai.guard.compat.CobblemonCompat.paired(pos, state);
    return pos;
  }

  public static boolean blocksAllowed(
      net.minecraft.world.World w,
      BlockPos pos,
      net.minecraft.block.BlockState state,
      Flag flag,
      ServerPlayerEntity p) {
    return ShiroGuard.allowed(w, pos, flag, p)
        && ShiroGuard.allowed(w, paired(pos, state), flag, p);
  }

  public static void register() {
    protectFirst(AttackBlockCallback.EVENT);
    protectFirst(UseBlockCallback.EVENT);
    protectFirst(UseItemCallback.EVENT);
    protectFirst(AttackEntityCallback.EVENT);
    protectFirst(UseEntityCallback.EVENT);
    var phase = net.minecraft.util.Identifier.of("shirokai_guard", "protection");
    PlayerBlockBreakEvents.BEFORE.register(
        (world, p, pos, state, entity) ->
            blocksAllowed(world, pos, state, Flag.BLOCK_BREAK, actor(p)));
    AttackBlockCallback.EVENT.register(
        phase,
        (p, world, hand, pos, dir) -> {
          if (actor(p) != null && GuardCommands.wand(actor(p), p.getStackInHand(hand))) {
            GuardCommands.position(actor(p), pos, true);
            return ActionResult.FAIL;
          }
          if (dev.shirokai.guard.compat.CratesCompat.interactionException(world, pos))
            return ActionResult.PASS;
          return ShiroGuard.allowed(world, pos, Flag.BLOCK_BREAK, actor(p))
              ? ActionResult.PASS
              : ActionResult.FAIL;
        });
    UseBlockCallback.EVENT.register(
        phase,
        (p, world, hand, hit) -> {
          var player = actor(p);
          var pos = hit.getBlockPos();

          if (player != null && GuardCommands.wand(player, p.getStackInHand(hand))) {
            GuardCommands.position(player, pos, false);
            return ActionResult.FAIL;
          }
          if (ShiroGuard.COBBLEMON
              && dev.shirokai.guard.compat.CobblemonCompat.pasture(world, pos)
              && !ShiroGuard.allowed(world, pos, Flag.POKEMON_PASTURE, player))
            return ActionResult.FAIL;
          if (dev.shirokai.guard.compat.CratesCompat.registered(world, pos)
              && !dev.shirokai.guard.compat.FlanCompat.blockAllowed(player))
            return ActionResult.FAIL;
          if (dev.shirokai.guard.compat.CratesCompat.interactionException(world, pos))
            return ActionResult.PASS;
          if (!ShiroGuard.allowed(world, pos, Flag.USE, player)) return ActionResult.FAIL;
          if (world.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory
              && !ShiroGuard.allowed(world, pos, Flag.CHEST_ACCESS, player))
            return ActionResult.FAIL;
          return ShiroGuard.allowed(world, pos, Flag.INTERACT, player)
              ? ActionResult.PASS
              : ActionResult.FAIL;
        });
    UseItemCallback.EVENT.register(
        phase,
        (p, world, hand) ->
            ShiroGuard.allowed(world, p.getBlockPos(), Flag.USE, actor(p))
                ? TypedActionResult.pass(p.getStackInHand(hand))
                : TypedActionResult.fail(p.getStackInHand(hand)));
    AttackEntityCallback.EVENT.register(
        phase,
        (p, world, hand, target, hit) ->
            entityAllowed(actor(p), target, true) ? ActionResult.PASS : ActionResult.FAIL);
    UseEntityCallback.EVENT.register(
        phase,
        (p, world, hand, target, hit) ->
            entityAllowed(actor(p), target, false) ? ActionResult.PASS : ActionResult.FAIL);
    ServerLivingEntityEvents.ALLOW_DAMAGE.register(
        (entity, source, amount) -> {
          var attacker = actor(source.getAttacker());
          if (entity instanceof ServerPlayerEntity victim && !ShiroGuard.bypass(victim)) {
            var pos = entity.getBlockPos();
            var world = entity.getWorld();
            if ("allow"
                .equals(
                    ShiroGuard.REGIONS.value(
                        ShiroGuard.dimension(world),
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        Flag.INVINCIBLE,
                        victim.getUuid()))) return false;
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)
                && !ShiroGuard.allowed(world, pos, Flag.FALL_DAMAGE, victim)) return false;
            if (source.getAttacker() instanceof net.minecraft.entity.mob.MobEntity
                && !ShiroGuard.allowed(world, pos, Flag.MOB_DAMAGE, victim)) return false;
          }
          if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)
              && !ShiroGuard.allowed(
                  entity.getWorld(), entity.getBlockPos(), Flag.PROJECTILE, attacker)) return false;
          if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) {
            var direct = source.getSource();
            var f =
                direct instanceof net.minecraft.entity.mob.CreeperEntity
                    ? Flag.CREEPER_EXPLOSION
                    : direct instanceof net.minecraft.entity.TntEntity
                        ? Flag.TNT
                        : Flag.OTHER_EXPLOSION;
            if (!ShiroGuard.allowed(entity.getWorld(), entity.getBlockPos(), f, attacker))
              return false;
          }
          if (!entityAllowed(attacker, entity, true)) return false;
          return !(entity instanceof PlayerEntity && attacker != null)
              || ShiroGuard.allowed(entity.getWorld(), entity.getBlockPos(), Flag.PVP, attacker)
                  && ShiroGuard.allowed(
                      attacker.getWorld(), attacker.getBlockPos(), Flag.PVP, attacker);
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (++ticks % 20 != 0) return;
          for (var p : server.getPlayerManager().getPlayerList()) {
            String dim = ShiroGuard.dimension(p.getWorld());
            var pos = p.getBlockPos();
            var current = new HashSet<String>();
            for (var r : ShiroGuard.REGIONS.at(dim, pos.getX(), pos.getY(), pos.getZ()))
              current.add(dim + "#" + r.id());
            var old = VISITS.put(p.getUuid(), current);
            String farewell =
                ShiroGuard.REGIONS.value(
                    dim, pos.getX(), pos.getY(), pos.getZ(), Flag.FAREWELL, p.getUuid());
            String previousFarewell = FAREWELLS.put(p.getUuid(), farewell == null ? "" : farewell);
            if (old != null
                && old.stream().anyMatch(v -> !current.contains(v))
                && previousFarewell != null
                && !previousFarewell.isBlank())
              p.sendMessage(Text.literal(previousFarewell), false);
            if (old == null || !old.equals(current)) {
              String greeting =
                  ShiroGuard.REGIONS.value(
                      dim, pos.getX(), pos.getY(), pos.getZ(), Flag.GREETING, p.getUuid());
              if (greeting != null
                  && (old == null || current.stream().anyMatch(v -> !old.contains(v))))
                p.sendMessage(Text.literal(greeting), false);
            }
            RegionVisuals.tick(p);
          }
        });
  }
}

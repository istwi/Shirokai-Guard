package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/** A consumed grant only lives inside one synchronous API teleport, including its nested hooks. */
public final class EntryAccess {
  private static final EntryAuthorizations AUTH = new EntryAuthorizations();
  private record Operation(UUID player, ServerWorld world, Vec3d destination, Set<String> regions) {}
  private static final ThreadLocal<Operation> ACTIVE = new ThreadLocal<>();
  private static final Map<UUID, Integer> NOTICES = new HashMap<>();

  public static void forget(UUID player) { AUTH.forget(player); NOTICES.remove(player); }
  public static void endRaid(String scope) { AUTH.end("shirokai_raids", scope); }
  public static void clear() { AUTH.clear(); NOTICES.clear(); ACTIVE.remove(); }

  public static boolean safeLogin(ServerPlayerEntity player, BlockPos pos) {
    if (ShiroGuard.bypass(player)) return true;
    var rule = ShiroGuard.REGIONS.entryRule(ShiroGuard.dimension(player.getServerWorld()),
        pos.getX(), pos.getY(), pos.getZ(), player.getUuid());
    return !rule.denied() && rule.regions().isEmpty();
  }

  public static boolean transition(ServerPlayerEntity player, ServerWorld target, Vec3d pos,
                                    boolean teleport) {
    var block = BlockPos.ofFloored(pos);
    var rule = ShiroGuard.REGIONS.entryRule(ShiroGuard.dimension(target),
        block.getX(), block.getY(), block.getZ(), player.getUuid());
    var beforeIds = new HashSet<String>();
    if (player.getServerWorld() == target) {
      var origin = player.getBlockPos();
      var previous = ShiroGuard.REGIONS.entryRule(ShiroGuard.dimension(target),
          origin.getX(), origin.getY(), origin.getZ(), player.getUuid());
      beforeIds.addAll(previous.regions());
      beforeIds.addAll(previous.deniedRegions());
    }
    var entering = new HashSet<>(rule.regions());
    entering.removeAll(beforeIds);
    // deny only affects entry; an occupant must still be able to leave or move inside.
    boolean denied = rule.deniedRegions().stream().anyMatch(id -> !beforeIds.contains(id));
    if (!entering.isEmpty()) {
      var op = ACTIVE.get();
      denied |= !teleport || op == null || !op.player().equals(player.getUuid())
          || op.world() != target || op.destination().squaredDistanceTo(pos) > 1e-10
          || !op.regions().containsAll(entering);
    }
    if (denied) notice(player, !entering.isEmpty() ? "raid-entry-blocked" : teleport ? "teleport-blocked" : "entry-blocked");
    return !denied;
  }

  public static void notice(ServerPlayerEntity player, String message) {
    int now = player.getServer().getTicks();
    if (now - NOTICES.getOrDefault(player.getUuid(), now - 20) < 20) return;
    NOTICES.put(player.getUuid(), now);
    player.sendMessage(Text.literal(GuardConfig.message(message)), false);
  }

  public static boolean raidTeleport(ServerPlayerEntity player, ServerWorld target, Vec3d destination,
                                      float yaw, float pitch, String raid) {
    if (ACTIVE.get() != null) return false;
    if (!GuardConfig.raidAccess().enabled())
      return player.teleport(target, destination.x, destination.y, destination.z, Set.of(), yaw, pitch);
    var block = BlockPos.ofFloored(destination);
    var rule = ShiroGuard.REGIONS.entryRule(ShiroGuard.dimension(target),
        block.getX(), block.getY(), block.getZ(), player.getUuid());
    long now = player.getServer().getTicks();
    var grant = AUTH.issue(player.getUuid(), ShiroGuard.dimension(target), rule.regions(),
        "shirokai_raids", raid, now, GuardConfig.raidAccess().authorizationTimeoutTicks());
    try {
      if (!AUTH.consume(grant, player.getUuid(), ShiroGuard.dimension(target), rule.regions(), now)) return false;
      ACTIVE.set(new Operation(player.getUuid(), target, destination, rule.regions()));
      if (!Protection.transition(player, target, destination, true)) return false;
      boolean moved = player.teleport(target, destination.x, destination.y, destination.z, Set.of(), yaw, pitch);
      return moved && player.getServerWorld() == target && player.getPos().squaredDistanceTo(destination) < 1e-8;
    } finally {
      ACTIVE.remove();
      AUTH.revoke(grant.token());
    }
  }
}

package dev.shirokai.guard.api;

import dev.shirokai.guard.*;
import java.util.UUID;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Optional ABI uses only JDK parameter types, so integrations with Mojang mappings can call it.
 * Trusted server mods only: a JVM mod is not a security sandbox. Never expose this as a command.
 */
public final class GuardEntryApi {
  private GuardEntryApi() {}
  public static boolean teleportRaid(UUID player, String dimension, double x, double y, double z,
                                    float yaw, float pitch, String raid) {
    var server = ShiroGuard.server();
    if (server == null || !server.isOnThread() || raid == null || raid.isBlank()
        || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
        || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return false;
    var p = server.getPlayerManager().getPlayer(player);
    var id = Identifier.tryParse(dimension);
    if (p == null || id == null || !p.isAlive()) return false;
    var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    if (world == null || !world.getWorldBorder().contains(net.minecraft.util.math.BlockPos.ofFloored(x,y,z))) return false;
    return EntryAccess.raidTeleport(p, world, new Vec3d(x,y,z), yaw, pitch, raid);
  }
  /** Pure preflight: no token, history or cooldown is created or consumed. */
  public static boolean canTeleport(UUID player, String dimension, double x, double y, double z) {
    var server = ShiroGuard.server();
    if (server == null || !server.isOnThread() || !Double.isFinite(x+y+z)) return false;
    var p = server.getPlayerManager().getPlayer(player);
    var id = Identifier.tryParse(dimension);
    if (p == null || id == null) return false;
    var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    if (world == null) return false;
    boolean allowed = Protection.transition(p, world, new Vec3d(x,y,z), true);
    if (!allowed) EntryAccess.notice(p, "teleport-blocked");
    return allowed;
  }
  public static void invalidatePlayer(UUID player) { EntryAccess.forget(player); }
  public static void endRaid(String raid) { EntryAccess.endRaid(raid); }
}

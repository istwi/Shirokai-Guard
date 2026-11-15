package dev.shirokai.guard;

import dev.shirokai.guard.core.Flag;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Region restrictions never revoke a flight entitlement owned by another mod or game mode. */
public final class MobilityProtection {
  private static final Map<UUID,Integer> LANDING = new HashMap<>();
  private MobilityProtection() {}

  public static boolean allowed(ServerPlayerEntity player, Flag flag) {
    return flag == Flag.FLY && player.isSpectator()
        || ShiroGuard.allowed(player.getWorld(),player.getBlockPos(),flag,player);
  }
  private static boolean pokemonVehicle(Entity vehicle) {
    if (!ShiroGuard.COBBLEMON) return false;
    for (int depth=0; vehicle!=null && depth<64; depth++,vehicle=vehicle.getVehicle())
      if (dev.shirokai.guard.compat.CobblemonCompat.pokemon(vehicle)) return true;
    return false;
  }
  public static boolean mountAllowed(ServerPlayerEntity player, Entity vehicle) {
    if (!pokemonVehicle(vehicle)) return true;
    boolean allow=allowed(player,Flag.POKEMON_MOUNT)
        && ShiroGuard.allowed(vehicle.getWorld(),vehicle.getBlockPos(),Flag.POKEMON_MOUNT,player);
    if (!allow) EntryAccess.notice(player,"pokemon-mount-denied");
    return allow;
  }
  public static Set<Flag> activities(ServerPlayerEntity player) {
    var flags=EnumSet.noneOf(Flag.class);
    if (player.getAbilities().flying && !player.isSpectator()) flags.add(Flag.FLY);
    if (player.isFallFlying()) flags.add(Flag.ELYTRA);
    if (pokemonVehicle(player.getVehicle())) flags.add(Flag.POKEMON_MOUNT);
    return flags;
  }
  public static boolean transition(ServerPlayerEntity player, ServerWorld target, Vec3d pos) {
    var to=BlockPos.ofFloored(pos);
    for (var flag : activities(player)) {
      if (!ShiroGuard.allowed(target,to,flag,player)
          && (player.getServerWorld()!=target || allowed(player,flag))) {
        EntryAccess.notice(player,message(flag));
        return false;
      }
    }
    return true;
  }
  public static String message(Flag flag) {
    return switch(flag) {
      case FLY -> "fly-denied";
      case ELYTRA -> "elytra-denied";
      default -> "pokemon-mount-denied";
    };
  }
  private static void landing(ServerPlayerEntity player) {
    LANDING.put(player.getUuid(),player.getServer().getTicks());
    player.fallDistance=0;
  }
  public static boolean protectsLanding(ServerPlayerEntity player) {
    return LANDING.containsKey(player.getUuid());
  }
  public static void forget(UUID player) { LANDING.remove(player); }
  public static void clear() { LANDING.clear(); }

  public static void enforce(ServerPlayerEntity player) {
    if (player.getAbilities().flying && !allowed(player,Flag.FLY)) {
      landing(player);
      player.getAbilities().flying=false;
      player.sendAbilitiesUpdate();
      EntryAccess.notice(player,"fly-denied");
    }
    if (player.isFallFlying() && !allowed(player,Flag.ELYTRA)) {
      landing(player);
      player.stopFallFlying();
      EntryAccess.notice(player,"elytra-denied");
    }
    if (pokemonVehicle(player.getVehicle()) && !mountAllowed(player,player.getVehicle())) {
      landing(player);
      player.stopRiding();
    }
    var lastAir=LANDING.get(player.getUuid());
    if (lastAir!=null) {
      if (!player.isAlive() || player.getAbilities().flying || player.isFallFlying() || player.hasVehicle())
        LANDING.remove(player.getUuid());
      else if (!player.isOnGround()) LANDING.put(player.getUuid(),player.getServer().getTicks());
      else if (player.getServer().getTicks()-lastAir>2) LANDING.remove(player.getUuid());
    }
  }
  public static void register() {
    ServerTickEvents.END_SERVER_TICK.register(server->{
      for(var player:server.getPlayerManager().getPlayerList()) enforce(player);
    });
  }
}

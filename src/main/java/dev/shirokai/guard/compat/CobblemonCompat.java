package dev.shirokai.guard.compat;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.block.PastureBlock;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import java.util.function.Consumer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Optional class: only loaded when Cobblemon 1.8.3 is present. */
public final class CobblemonCompat {
  public static void register() {
    CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(
        (Consumer<ThrownPokeballHitEvent>)
            event -> {
              var pokemon = event.getPokemon();
              var p = Protection.actor(event.getPokeBall().getOwner());
              if (!ShiroGuard.allowed(
                  pokemon.getWorld(), pokemon.getBlockPos(), Flag.POKEMON_CAPTURE, p))
                event.cancel();
            });
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(
        (Consumer<BattleStartedEvent.Pre>)
            event -> {
              var participants =
                  new java.util.ArrayList<net.minecraft.server.network.ServerPlayerEntity>();
              var server = ShiroGuard.server();
              for (var actor : event.getBattle().getActors())
                for (var id : actor.getPlayerUUIDs()) {
                  var player = server == null ? null : server.getPlayerManager().getPlayer(id);
                  if (player != null) {
                    participants.add(player);
                    if (!ShiroGuard.allowed(
                        player.getWorld(), player.getBlockPos(), Flag.POKEMON_BATTLE, player)) {
                      event.cancel();
                      return;
                    }
                  }
                }
              for (var actor : event.getBattle().getActors())
                for (var pokemon : actor.getPokemonList()) {
                  var entity = pokemon.getEffectedPokemon().getEntity();
                  if (entity == null) continue;
                  if (participants.isEmpty()) {
                    if (!ShiroGuard.allowed(
                        entity.getWorld(), entity.getBlockPos(), Flag.POKEMON_BATTLE, null)) {
                      event.cancel();
                      return;
                    }
                  } else
                    for (var player : participants) {
                      if (!ShiroGuard.allowed(
                          entity.getWorld(), entity.getBlockPos(), Flag.POKEMON_BATTLE, player)) {
                        event.cancel();
                        return;
                      }
                    }
                }
            });
  }

  public static net.minecraft.server.network.ServerPlayerEntity spawnActor(Entity entity) {
    return entity instanceof PokemonEntity pokemon ? Protection.actor(pokemon.getOwner()) : null;
  }

  public static boolean pokemon(Entity entity) {
    return entity instanceof PokemonEntity;
  }

  public static boolean pasture(World w, BlockPos p) {
    return w.getBlockState(p).getBlock() instanceof PastureBlock;
  }

  public static BlockPos paired(BlockPos pos, net.minecraft.block.BlockState state) {
    return state.getBlock() instanceof PastureBlock pasture
        ? pasture.getPositionOfOtherPart(state, pos)
        : pos;
  }
}

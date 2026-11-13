package dev.shirokai.guard.mixin.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.pasture.SetPastureConflictPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.SetPastureConflictHandler;
import dev.shirokai.guard.*;
import dev.shirokai.guard.core.Flag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = SetPastureConflictHandler.class, remap = false)
public abstract class PastureConflictPacketMixin {
  @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
  private void guard$pasture(
      @Coerce Object raw, MinecraftServer server, ServerPlayerEntity player, CallbackInfo ci) {
    var packet = (SetPastureConflictPacket) raw;
    for (var pokemon :
        player
            .getServerWorld()
            .getEntitiesByClass(
                PokemonEntity.class,
                player.getBoundingBox().expand(64),
                e -> e.getPokemon().getUuid().equals(packet.getPokemonId()))) {
      if (!Protection.entityAllowed(player, pokemon, false)
          || !ShiroGuard.allowed(
              pokemon.getWorld(), pokemon.getBlockPos(), Flag.POKEMON_PASTURE, player)) {
        ci.cancel();
        return;
      }
    }
  }
}

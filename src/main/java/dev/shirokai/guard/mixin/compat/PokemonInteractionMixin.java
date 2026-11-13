package dev.shirokai.guard.mixin.compat;

import com.cobblemon.mod.common.net.messages.server.pokemon.interact.InteractPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pokemon.interact.InteractPokemonHandler;
import dev.shirokai.guard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = InteractPokemonHandler.class, remap = false)
public abstract class PokemonInteractionMixin {
  @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
  private void guard$wheel(
      @Coerce Object raw, MinecraftServer server, ServerPlayerEntity p, CallbackInfo ci) {
    var packet = (InteractPokemonPacket) raw;
    var entity = p.getServerWorld().getEntity(packet.getPokemonID());
    if (entity != null && !Protection.entityAllowed(p, entity, false)) ci.cancel();
  }
}

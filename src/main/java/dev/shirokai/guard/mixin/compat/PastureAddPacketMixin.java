package dev.shirokai.guard.mixin.compat;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.Flag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(
    value = com.cobblemon.mod.common.net.serverhandling.pasture.PasturePokemonHandler.class,
    remap = false)
public abstract class PastureAddPacketMixin {
  @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
  private void guard$pasture(
      @Coerce Object packet, MinecraftServer server, ServerPlayerEntity p, CallbackInfo ci) {
    var link = com.cobblemon.mod.common.api.pasture.PastureLinkManager.getLinkByPlayer(p);
    if (link != null && !ShiroGuard.allowed(p.getWorld(), link.getPos(), Flag.POKEMON_PASTURE, p))
      ci.cancel();
  }
}

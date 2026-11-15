package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.Flag;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class FlightAbilitiesMixin {
  @Shadow public ServerPlayerEntity player;
  // After NetworkThreadUtils.forceMainThread, before vanilla changes the ability.
  @Inject(method="onUpdatePlayerAbilities",at=@At(value="INVOKE",
      target="Lnet/minecraft/server/network/ServerPlayerEntity;getAbilities()Lnet/minecraft/entity/player/PlayerAbilities;",ordinal=0),cancellable=true)
  private void guard$flight(UpdatePlayerAbilitiesC2SPacket packet,CallbackInfo ci) {
    if(packet.isFlying() && !MobilityProtection.allowed(player,Flag.FLY)) {
      MobilityProtection.enforce(player);
      player.sendAbilitiesUpdate();
      EntryAccess.notice(player,"fly-denied");
      ci.cancel();
    }
  }
}

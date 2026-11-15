package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.Flag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PlayerEntity.class)
public abstract class ElytraFlightMixin {
  @Inject(method="checkFallFlying",at=@At("HEAD"),cancellable=true)
  private void guard$check(CallbackInfoReturnable<Boolean> cir) {
    if((Object)this instanceof ServerPlayerEntity player && !MobilityProtection.allowed(player,Flag.ELYTRA)) {
      MobilityProtection.enforce(player);
      EntryAccess.notice(player,"elytra-denied");cir.setReturnValue(false);
    }
  }
  @Inject(method="startFallFlying",at=@At("HEAD"),cancellable=true)
  private void guard$start(CallbackInfo ci) {
    if((Object)this instanceof ServerPlayerEntity player && !MobilityProtection.allowed(player,Flag.ELYTRA)) {
      MobilityProtection.enforce(player);
      EntryAccess.notice(player,"elytra-denied");ci.cancel();
    }
  }
}

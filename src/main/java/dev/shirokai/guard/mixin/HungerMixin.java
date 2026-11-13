package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.player.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(HungerManager.class)
public abstract class HungerMixin {
  @Inject(method = "update", at = @At("HEAD"), cancellable = true)
  private void guard$hunger(PlayerEntity p, CallbackInfo ci) {
    if (!ShiroGuard.allowed(p.getWorld(), p.getBlockPos(), Flag.HUNGER, Protection.actor(p)))
      ci.cancel();
  }
}

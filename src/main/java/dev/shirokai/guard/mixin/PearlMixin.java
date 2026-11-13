package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EnderPearlEntity.class)
public abstract class PearlMixin {
  @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
  private void guard$pearl(HitResult hit, CallbackInfo ci) {
    var e = (EnderPearlEntity) (Object) this;
    var p = Protection.actor(e.getOwner());
    if (!ShiroGuard.allowed(e.getWorld(), e.getBlockPos(), Flag.ENDERPEARL, p)) {
      e.discard();
      ci.cancel();
    }
  }
}

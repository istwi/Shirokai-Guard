package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityDamageMixin {
  @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
  private void guard$damage(
      DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    if (!Protection.entityAllowed(
        Protection.actor(source.getAttacker()), (BlockAttachedEntity) (Object) this, true))
      cir.setReturnValue(false);
  }
}

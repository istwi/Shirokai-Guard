package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChorusFruitItem.class)
public abstract class ChorusMixin {
  @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
  private void guard$chorus(
      ItemStack stack, World w, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
    if (!ShiroGuard.allowed(w, user.getBlockPos(), Flag.CHORUS_FRUIT, Protection.actor(user)))
      cir.setReturnValue(stack);
  }
}

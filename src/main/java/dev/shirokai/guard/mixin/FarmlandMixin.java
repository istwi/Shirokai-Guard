package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandMixin {
  @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
  private void guard$trample(
      World w, BlockState state, BlockPos p, Entity e, float distance, CallbackInfo ci) {
    if (!ShiroGuard.allowed(w, p, Flag.FARMLAND_TRAMPLE, Protection.actor(e))) ci.cancel();
  }
}

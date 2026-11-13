package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(FireBlock.class)
public abstract class FireMixin {
  @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
  private void guard$burn(
      World w, BlockPos pos, int spread, Random random, int age, CallbackInfo ci) {
    if (!ShiroGuard.allowed(w, pos, Flag.FIRE_SPREAD, null)
        || !ShiroGuard.allowed(w, pos, Flag.BUILD, null)) ci.cancel();
  }
}

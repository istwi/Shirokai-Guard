package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PitcherCropBlock.class)
public abstract class PitcherCropBlockGrowthMixin {
  @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
  private void guard$tick(BlockState state, ServerWorld w, BlockPos p, Random r, CallbackInfo ci) {
    if (!ShiroGuard.allowed(w, p, Flag.CROP_GROWTH, null)) ci.cancel();
  }
}

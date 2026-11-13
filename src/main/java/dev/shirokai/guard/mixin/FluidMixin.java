package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(FlowableFluid.class)
public abstract class FluidMixin {
  @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
  private void guard$flow(
      WorldAccess world,
      BlockPos pos,
      BlockState state,
      Direction dir,
      FluidState fluid,
      CallbackInfo ci) {
    if (world instanceof World w) {
      var flag = fluid.isIn(FluidTags.LAVA) ? Flag.LAVA_FLOW : Flag.WATER_FLOW;
      if (!ShiroGuard.allowed(w, pos, flag, null)
          || !Protection.blocksAllowed(w, pos, state, Flag.BUILD, null)
          || (!state.isAir() && !Protection.blocksAllowed(w, pos, state, Flag.BLOCK_BREAK, null)))
        ci.cancel();
    }
  }
}

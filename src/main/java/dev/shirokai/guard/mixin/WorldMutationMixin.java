package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(World.class)
public abstract class WorldMutationMixin {
  @Inject(
      method =
          "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void guard$environment(
      BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
    var w = (World) (Object) this;
    if (w.isClient()) return;
    Flag flag = null;
    if (state.getBlock() instanceof AbstractFireBlock) flag = Flag.FIRE_SPREAD;
    else if (state.isOf(Blocks.ICE) || state.isOf(Blocks.FROSTED_ICE)) flag = Flag.ICE_FORM;
    if (flag != null && !ShiroGuard.allowed(w, pos, flag, null)) cir.setReturnValue(false);
  }
}

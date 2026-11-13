package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BucketItem.class)
public abstract class BucketMixin {
  @Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
  private void guard$bucket(
      PlayerEntity p,
      World w,
      BlockPos pos,
      BlockHitResult hit,
      CallbackInfoReturnable<Boolean> cir) {
    if (!ShiroGuard.allowed(w, pos, Flag.BLOCK_PLACE, Protection.actor(p)))
      cir.setReturnValue(false);
  }
}

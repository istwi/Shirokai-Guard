package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.BlockState;
import net.minecraft.item.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
  @Inject(
      method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void guard$place(
      ItemPlacementContext ctx, BlockState state, CallbackInfoReturnable<Boolean> cir) {
    if (!Protection.blocksAllowed(
        ctx.getWorld(),
        ctx.getBlockPos(),
        state,
        Flag.BLOCK_PLACE,
        Protection.actor(ctx.getPlayer()))) cir.setReturnValue(false);
  }
}

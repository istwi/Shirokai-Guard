package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PlayerInventory.class)
public abstract class InventoryDropMixin {
  @Shadow @Final public PlayerEntity player;

  @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
  private void guard$drop(boolean entire, CallbackInfoReturnable<ItemStack> cir) {
    if (!ShiroGuard.allowed(
        player.getWorld(), player.getBlockPos(), Flag.ITEM_DROP, Protection.actor(player)))
      cir.setReturnValue(ItemStack.EMPTY);
  }
}

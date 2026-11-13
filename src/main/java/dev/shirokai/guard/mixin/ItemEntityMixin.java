package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
  @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
  private void guard$pickup(PlayerEntity p, CallbackInfo ci) {
    var item = (ItemEntity) (Object) this;
    if (!ShiroGuard.allowed(
        item.getWorld(), item.getBlockPos(), Flag.ITEM_PICKUP, Protection.actor(p))) ci.cancel();
  }
}

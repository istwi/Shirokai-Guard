package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ScreenHandler.class)
public abstract class ScreenDropMixin {
  @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
  private void guard$drop(
      int slot, int button, SlotActionType type, PlayerEntity p, CallbackInfo ci) {
    if ((type == SlotActionType.THROW || slot == -999)
        && !ShiroGuard.allowed(p.getWorld(), p.getBlockPos(), Flag.ITEM_DROP, Protection.actor(p)))
      ci.cancel();
  }
}

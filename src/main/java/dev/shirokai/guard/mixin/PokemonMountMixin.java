package dev.shirokai.guard.mixin;

import dev.shirokai.guard.MobilityProtection;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also covers direct or forced vanilla mounting that does not emit Cobblemon's ride event. */
@Mixin(Entity.class)
public abstract class PokemonMountMixin {
  @Inject(method="startRiding(Lnet/minecraft/entity/Entity;Z)Z",at=@At("HEAD"),cancellable=true)
  private void guard$mount(Entity vehicle,boolean force,CallbackInfoReturnable<Boolean> cir) {
    if((Object)this instanceof ServerPlayerEntity player && !MobilityProtection.mountAllowed(player,vehicle))
      cir.setReturnValue(false);
  }
}

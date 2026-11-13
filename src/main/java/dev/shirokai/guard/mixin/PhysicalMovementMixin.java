package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import net.minecraft.entity.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also validates server-driven motion, including passengers of autonomous vehicles. */
@Mixin(Entity.class)
public abstract class PhysicalMovementMixin {
  @Inject(method = "move", at = @At("HEAD"), cancellable = true)
  private void guard$physical(MovementType type, Vec3d movement, CallbackInfo ci) {
    var entity = (Entity) (Object) this;
    if (entity.getWorld().isClient()
        || (!(entity instanceof ServerPlayerEntity) && !entity.hasPassengers())) return;
    if (entity instanceof ServerPlayerEntity p
        && p.networkHandler != null
        && !Protection.transition(p, p.getServerWorld(), p.getPos().add(movement), false)) {
      entity.setVelocity(Vec3d.ZERO);
      ci.cancel();
      return;
    }
    if (entity.hasPassengers())
      for (var passenger : entity.getPassengersDeep())
        if (passenger instanceof ServerPlayerEntity p
            && !Protection.transition(p, p.getServerWorld(), p.getPos().add(movement), false)) {
          entity.setVelocity(Vec3d.ZERO);
          ci.cancel();
          return;
        }
  }
}

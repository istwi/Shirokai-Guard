package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayerEntity.class)
public abstract class TeleportMixin {
  @Inject(
      method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void guard$teleportResult(
      ServerWorld world,
      double x,
      double y,
      double z,
      java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags,
      float yaw,
      float pitch,
      CallbackInfoReturnable<Boolean> cir) {
    if (!Protection.transition((ServerPlayerEntity) (Object) this, world, new Vec3d(x, y, z), true))
      cir.setReturnValue(false);
  }

  @Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
  private void guard$dimension(TeleportTarget target, CallbackInfoReturnable<Entity> cir) {
    if (!Protection.transition(
        (ServerPlayerEntity) (Object) this, target.world(), target.pos(), true))
      cir.setReturnValue(null);
  }

  @Inject(
      method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V",
      at = @At("HEAD"),
      cancellable = true)
  private void guard$teleport(
      ServerWorld world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
    if (!Protection.transition((ServerPlayerEntity) (Object) this, world, new Vec3d(x, y, z), true))
      ci.cancel();
  }
}

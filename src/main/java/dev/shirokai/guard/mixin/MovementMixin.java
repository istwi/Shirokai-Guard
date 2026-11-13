package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MovementMixin {
  @Shadow public ServerPlayerEntity player;

  @Inject(
      method = "onPlayerMove",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/network/ServerPlayNetworkHandler;handlePendingTeleport()Z"),
      cancellable = true)
  private void guard$move(PlayerMoveC2SPacket packet, CallbackInfo ci) {
    var dest =
        new Vec3d(
            packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
    if (!Double.isFinite(dest.x) || !Double.isFinite(dest.y) || !Double.isFinite(dest.z)) return;
    if (!Protection.transition(player, player.getServerWorld(), dest, false)) {
      player.networkHandler.requestTeleport(
          player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
      ci.cancel();
    }
  }

  @Inject(
      method = "onVehicleMove",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/network/ServerPlayNetworkHandler;handlePendingTeleport()Z"),
      cancellable = true)
  private void guard$vehicle(VehicleMoveC2SPacket packet, CallbackInfo ci) {
    var vehicle = player.getRootVehicle();
    if (vehicle == player) return;
    var dest =
        player
            .getPos()
            .add(
                packet.getX() - vehicle.getX(),
                packet.getY() - vehicle.getY(),
                packet.getZ() - vehicle.getZ());
    for (var passenger : vehicle.getPassengersDeep()) {
      if (passenger instanceof ServerPlayerEntity p
          && !Protection.transition(
              p, p.getServerWorld(), p.getPos().add(dest.subtract(player.getPos())), false)) {
        player.networkHandler.sendPacket(
            new net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket(vehicle));
        ci.cancel();
        return;
      }
    }
  }
}

package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class NetworkTeleportMixin {
  @Shadow public ServerPlayerEntity player;

  @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), cancellable = true)
  private void guard$teleport(
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      Set<PositionFlag> flags,
      CallbackInfo ci) {
    var dest = new Vec3d(x, y, z);
    if (dest.squaredDistanceTo(player.getPos()) < 0.000001) return;
    if (!Protection.transition(player, player.getServerWorld(), dest, true)) ci.cancel();
  }
}

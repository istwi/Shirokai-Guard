package dev.shirokai.guard;

import dev.shirokai.guard.core.Flag;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Login/respawn only. Searches a bounded, already-loaded neighborhood; never loads chunks. */
public final class EntrySafety {
  public static void register() {
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> enforce(handler.player));
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      MobilityProtection.forget(newPlayer.getUuid());
      enforce(newPlayer);
    });
  }

  public static boolean enforce(ServerPlayerEntity p) {
    MobilityProtection.enforce(p);
    var world = p.getServerWorld();
    var origin = p.getBlockPos();
    if (ShiroGuard.allowed(world, origin, Flag.ENTRY, p) && EntryAccess.safeLogin(p, origin)) return true;
    for (int radius = 1; radius <= 16; radius++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++) {
          if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
          var column = origin.add(dx, 0, dz);
          if (!world.isChunkLoaded(column)) continue;
          int y =
              world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
          var feet = new BlockPos(column.getX(), y, column.getZ());
          if (y < world.getBottomY() + 1
              || y + 2 >= world.getTopY()
              || !world.getWorldBorder().contains(feet)
              || (!ShiroGuard.allowed(world, feet, Flag.ENTRY, p) || !EntryAccess.safeLogin(p, feet))) continue;
          var floor = world.getBlockState(feet.down());
          if (!floor.isSideSolidFullSquare(world, feet.down(), Direction.UP)
              || !floor.getFluidState().isEmpty()
              || floor.isIn(BlockTags.FIRE)
              || floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
              || floor.isOf(net.minecraft.block.Blocks.CACTUS)) continue;
          if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir())
            continue;
          // The ordinary teleport hooks still apply: this is never an automatic bypass.
          p.teleport(
              world, feet.getX() + .5, feet.getY(), feet.getZ() + .5, p.getYaw(), p.getPitch());
          if (p.getBlockPos().equals(feet)) return true;
        }
    p.networkHandler.disconnect(
        Text.literal(
            "Entrada bloqueada nesta região. Nenhum destino permitido e seguro está carregado;"
                + " contate a administração."));
    return false;
  }
}

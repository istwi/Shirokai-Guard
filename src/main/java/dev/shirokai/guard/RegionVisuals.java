package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RegionVisuals {
  private static final Map<UUID, String> SHOWN = new HashMap<>();

  public static void forget(UUID id) {
    SHOWN.remove(id);
  }

  public static void toggle(ServerPlayerEntity player, String id) {
    if (id.equals("off")) {
      forget(player.getUuid());
      return;
    }
    if (!id.equals("here")) {
      var r = ShiroGuard.REGIONS.get(ShiroGuard.dimension(player.getWorld()), id);
      if (r.bounds() == null) throw new IllegalArgumentException("Região global não tem limites");
    }
    if (id.equals(SHOWN.get(player.getUuid()))) forget(player.getUuid());
    else SHOWN.put(player.getUuid(), id);
  }

  public static void tick(ServerPlayerEntity p) {
    if (!ShiroGuard.permission(p.getCommandSource(), "region.info")) {
      forget(p.getUuid());
      return;
    }
    var boxes = new ArrayList<Bounds>();
    String dim = ShiroGuard.dimension(p.getWorld());
    var selection = GuardCommands.SELECTIONS.get(p.getUuid());
    if (GuardConfig.options().selectionParticles()
        && selection != null
        && selection.first() != null
        && selection.second() != null
        && selection.dimension().equals(dim)) boxes.add(selection.bounds());
    var shown = SHOWN.get(p.getUuid());
    if (shown != null) {
      if (shown.equals("here")) {
        var pos = p.getBlockPos();
        ShiroGuard.REGIONS.at(dim, pos.getX(), pos.getY(), pos.getZ()).stream()
            .map(Region::bounds)
            .filter(Objects::nonNull)
            .limit(4)
            .forEach(boxes::add);
      } else
        try {
          var b = ShiroGuard.REGIONS.get(dim, shown).bounds();
          if (b != null) boxes.add(b);
        } catch (IllegalArgumentException e) {
          forget(p.getUuid());
        }
    }
    int left = GuardConfig.options().particleBudget();
    for (var b : boxes) {
      int allowance = left / Math.max(1, boxes.size());
      var points = BoundsVisual.sample(b, p.getX(), p.getY(), p.getZ(), allowance);
      left -= points.size();
      for (var point : points)
        p.getServerWorld()
            .spawnParticles(
                p, ParticleTypes.END_ROD, false, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0);
    }
  }
}

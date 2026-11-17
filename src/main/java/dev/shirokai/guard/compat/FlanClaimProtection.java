package dev.shirokai.guard.compat;

import dev.shirokai.guard.ShiroGuard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Checks Flan's normalized candidate, before any claim or accounting mutation. */
public final class FlanClaimProtection {
  private FlanClaimProtection() {}

  public static final class Denied extends RuntimeException {
    public Denied() { super(null, null, false, false); }
  }

  public static void check(Object claim, ServerPlayerEntity player) {
    if (ShiroGuard.bypass(player)) return;
    try {
      Object box = claim.getClass().getMethod("getDimensions").invoke(claim);
      Class<?> type = box.getClass();
      int minX = (int) type.getMethod("minX").invoke(box);
      int minY = (int) type.getMethod("minY").invoke(box);
      int minZ = (int) type.getMethod("minZ").invoke(box);
      int maxX = (int) type.getMethod("maxX").invoke(box);
      int maxY = (int) type.getMethod("maxY").invoke(box);
      int maxZ = (int) type.getMethod("maxZ").invoke(box);
      String dimension = ShiroGuard.dimension(player.getWorld());
      for (var region : ShiroGuard.REGIONS.all()) {
        if (!region.dimension().equals(dimension)) continue;
        var b = region.bounds();
        if (b == null || (minX <= b.maxX() && maxX >= b.minX()
            && minY <= b.maxY() && maxY >= b.minY()
            && minZ <= b.maxZ() && maxZ >= b.minZ())) {
          deny(player);
        }
      }
    } catch (ReflectiveOperationException | LinkageError e) {
      ShiroGuard.LOG.error("Flan: não foi possível validar limites do claim", e);
      deny(player);
    }
  }

  private static void deny(ServerPlayerEntity player) {
    player.sendMessage(Text.literal("§c✦ Não é permitido criar ou expandir claims nesta região protegida."), false);
    throw new Denied();
  }
}

package dev.shirokai.guard.compat;

import dev.shirokai.guard.*;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Read-only adapter to the public Shirokai Crates registered-block predicate. */
public final class CratesCompat {
  private static Method predicate;
  private static boolean failed;

  public static void initialize() {
    if (!FabricLoader.getInstance().isModLoaded("shirokaicrates")) return;
    try {
      predicate =
          Class.forName("dev.shirokaicrates.ShirokaiCrates")
              .getMethod("protectedBlock", World.class, BlockPos.class);
    } catch (ReflectiveOperationException e) {
      ShiroGuard.LOG.error(
          "API pública de Crates indisponível; exceção de interação desativada", e);
    }
  }

  public static boolean registered(World world, BlockPos pos) {
    if (predicate == null || failed) return false;
    try {
      return (boolean) predicate.invoke(null, world, pos);
    } catch (ReflectiveOperationException | LinkageError e) {
      failed = true;
      ShiroGuard.LOG.error(
          "Falha ao consultar blocos de Crates; exceção de interação desativada", e);
      return false;
    }
  }

  public static boolean interactionException(World world, BlockPos pos) {
    return GuardConfig.crateInteractions() && registered(world, pos);
  }
}

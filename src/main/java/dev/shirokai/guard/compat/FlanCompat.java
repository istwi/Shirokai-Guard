package dev.shirokai.guard.compat;

import dev.shirokai.guard.ShiroGuard;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

/** Read-only access to the split block/item decision made by Flan's earlier event phase. */
public final class FlanCompat {
  private static boolean present, failed;
  private static Method blockAllowed;

  public static void initialize() {
    present = FabricLoader.getInstance().isModLoaded("flan");
    if (!present) return;
    try {
      blockAllowed =
          Class.forName("io.github.flemmli97.flan.api.fabric.ItemUseBlockFlags")
              .getMethod("flan$allowUseBlocks");
    } catch (ReflectiveOperationException e) {
      failed = true;
      ShiroGuard.LOG.error(
          "Flan: exceção de interação Crates bloqueada porque a API de decisão não está disponível",
          e);
    }
  }

  public static boolean blockAllowed(ServerPlayerEntity player) {
    if (!present) return true;
    if (failed || player == null) return false;
    try {
      return (boolean) blockAllowed.invoke(player.interactionManager);
    } catch (ReflectiveOperationException | LinkageError e) {
      failed = true;
      ShiroGuard.LOG.error(
          "Flan: não foi possível conferir decisão de interação; exceção Crates bloqueada", e);
      return false;
    }
  }
}

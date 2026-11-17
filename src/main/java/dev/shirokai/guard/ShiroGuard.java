package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import java.util.concurrent.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.*;

public final class ShiroGuard implements ModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("ShirokaiGuard");
  public static final RegionManager REGIONS = new RegionManager();
  public static final Set<UUID> BYPASS = new HashSet<>();
  private static final RegionStore STORE =
      new RegionStore(FabricLoader.getInstance().getConfigDir().resolve("shirokai-guard/regions"));
  private static final ExecutorService WRITER =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "ShirokaiGuard-save"));
  private static volatile String saveError;
  private static net.minecraft.server.MinecraftServer server;

  public static net.minecraft.server.MinecraftServer server() {
    return server;
  }

  public static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");

  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTING.register(
        server -> {
          ShiroGuard.server = server;
          try {
            GuardConfig.apply(GuardConfig.read());
            REGIONS.replace(STORE.load());
            REGIONS.cacheLimit(GuardConfig.options().queryCacheEntries());
          } catch (Exception e) {
            throw new IllegalStateException(
                "Shirokai Guard não iniciou: proteção não pode carregar dados inválidos", e);
          }
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          EntryAccess.clear();
          MobilityProtection.clear();
          save();
          WRITER.shutdown();
          try {
            if (!WRITER.awaitTermination(30, TimeUnit.SECONDS))
              LOG.error("Persistência não terminou em 30s");
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          BYPASS.remove(handler.player.getUuid());
          MobilityProtection.forget(handler.player.getUuid());
          EntryAccess.forget(handler.player.getUuid());
          GuardCommands.forget(handler.player.getUuid());
        });
    GuardCommands.register();
    Protection.register();
    GuardMenus.register();
    EntrySafety.register();
    MobilityProtection.register();
    if (COBBLEMON) dev.shirokai.guard.compat.CobblemonCompat.register();
    dev.shirokai.guard.compat.CratesCompat.initialize();
    dev.shirokai.guard.compat.FlanCompat.initialize();
    LOG.info("Shirokai Guard alpha: núcleo experimental; consulte README antes de usar.");
  }

  public static boolean permission(ServerCommandSource source, String node) {
    if (node.equals("bypass") && source.getPlayer() != null
        && source.getPlayer().getCommandSource().hasPermissionLevel(4)) return true;
    int fallback = GuardConfig.fallback(node);
    return fallback < 0
        ? Permissions.check(source, "shirokaiguard." + node, false)
        : Permissions.check(source, "shirokaiguard." + node, fallback);
  }

  public static boolean bypass(ServerPlayerEntity player) {
    return BYPASS.contains(player.getUuid()) && permission(player.getCommandSource(), "bypass");
  }

  public static String dimension(World world) {
    return world.getRegistryKey().getValue().toString();
  }

  public static boolean allowed(World world, BlockPos pos, Flag flag, ServerPlayerEntity player) {
    return player != null && bypass(player)
        || REGIONS.allowed(
            dimension(world),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            flag,
            player == null ? null : player.getUuid());
  }

  public static void save() {
    var snapshot = List.copyOf(REGIONS.all());
    WRITER.submit(
        () -> {
          try {
            STORE.save(snapshot);
            saveError = null;
          } catch (Exception e) {
            saveError = e.getMessage();
            LOG.error("Falha ao salvar regiões; dados em memória preservados", e);
          }
        });
  }

  public static void reload() throws Exception {
    // Drain queued snapshots before reading the same file.
    WRITER.submit(() -> {}).get(30, TimeUnit.SECONDS);
    if (saveError != null)
      throw new IllegalStateException("Salvamento pendente falhou: " + saveError);
    var config = GuardConfig.read();
    var regions = STORE.load();
    REGIONS.replace(regions);
    GuardConfig.apply(config);
    REGIONS.cacheLimit(config.options().queryCacheEntries());
    EntryAccess.clear();
    GuardMenus.invalidate();
    TemplateActions.clear();
  }

  public static String status() {
    return "Regiões: "
        + REGIONS.all().size()
        + " | Cache: "
        + REGIONS.metrics()
        + " | Persistência: "
        + (saveError == null ? "sem falhas registradas" : saveError);
  }
}

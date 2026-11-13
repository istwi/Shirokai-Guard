package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TemplateActions {
  public record Plan(
      UUID player,
      String dimension,
      Region before,
      RegionTemplate template,
      String token,
      long expires) {}

  private static final Map<UUID, Plan> PENDING = new HashMap<>();

  public static void clear() {
    PENDING.clear();
  }

  public static void forget(UUID player) {
    PENDING.remove(player);
  }

  public static Plan preview(ServerPlayerEntity player, String region, String template) {
    if (!ShiroGuard.permission(player.getCommandSource(), "region.flag"))
      throw new IllegalArgumentException(GuardConfig.message("no-permission"));
    var t = GuardConfig.templates().get(template);
    if (t == null) throw new IllegalArgumentException("Template inexistente");
    var dim = ShiroGuard.dimension(player.getWorld());
    var r = ShiroGuard.REGIONS.get(dim, region);
    var plan =
        new Plan(
            player.getUuid(),
            dim,
            r,
            t,
            UUID.randomUUID().toString().substring(0, 8),
            System.nanoTime() + 60_000_000_000L);
    PENDING.put(player.getUuid(), plan);
    return plan;
  }

  public static void confirm(ServerPlayerEntity player, String token) {
    var p = PENDING.remove(player.getUuid());
    if (p == null
        || !p.token().equals(token)
        || System.nanoTime() > p.expires()
        || !p.dimension().equals(ShiroGuard.dimension(player.getWorld())))
      throw new IllegalArgumentException(GuardConfig.message("expired"));
    if (!ShiroGuard.permission(player.getCommandSource(), "region.flag"))
      throw new IllegalArgumentException(GuardConfig.message("no-permission"));
    if (!ShiroGuard.REGIONS.get(p.dimension(), p.before().id()).equals(p.before())
        || !p.template().equals(GuardConfig.templates().get(p.template().id())))
      throw new IllegalArgumentException(GuardConfig.message("changed"));
    ShiroGuard.REGIONS.put(p.template().apply(p.before(), ShiroGuard.COBBLEMON));
    ShiroGuard.save();
    ShiroGuard.LOG.info(
        "{} aplicou template {} à região {} em {}",
        player.getUuid(),
        p.template().id(),
        p.before().id(),
        p.dimension());
    player.sendMessage(
        Text.literal(GuardConfig.message("template-applied", p.template().id())), false);
  }
}

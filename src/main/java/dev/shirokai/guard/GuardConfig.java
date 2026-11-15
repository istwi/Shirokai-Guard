package dev.shirokai.guard;

import com.google.gson.*;
import dev.shirokai.guard.core.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** All files are validated before an immutable configuration becomes live. */
public final class GuardConfig {
  public record Options(
      boolean selectionParticles,
      boolean auditChanges,
      int maxRegionsPerDimension,
      int particleBudget,
      int queryCacheEntries) {
    public Options(boolean particles, boolean audit, int max) {
      this(particles, audit, max, 128, 4096);
    }

    public Options {
      if (maxRegionsPerDimension < 1 || maxRegionsPerDimension > 100_000)
        throw new IllegalArgumentException("maxRegionsPerDimension: 1..100000");
      if (particleBudget < 24 || particleBudget > 512)
        throw new IllegalArgumentException("particleBudget: 24..512");
      if (queryCacheEntries < 0 || queryCacheEntries > 65536)
        throw new IllegalArgumentException("queryCacheEntries: 0..65536");
    }
  }

  public record Menus(String title, int pageSize, boolean lore) {
    public Menus {
      if (title == null || title.isBlank() || title.length() > 80 || pageSize < 9 || pageSize > 45)
        throw new IllegalArgumentException("menus.json inválido");
    }
  }

  public record RaidAccess(boolean enabled, int authorizationTimeoutTicks) {
    public RaidAccess {
      if (authorizationTimeoutTicks < 1 || authorizationTimeoutTicks > 200)
        throw new IllegalArgumentException("authorizationTimeoutTicks: 1..200");
    }
  }

  public record Snapshot(
      Options options,
      Map<String, String> messages,
      boolean crateInteractions,
      RaidAccess raidAccess,
      Menus menus,
      Map<String, Integer> permissions,
      Map<Flag, Map<Region.Group, String>> defaults,
      Map<String, RegionTemplate> templates) {}

  public static final Path DIR =
      FabricLoader.getInstance().getConfigDir().resolve("shirokai-guard");
  private static final List<String> NODES =
      List.of(
          "admin",
          "region.create",
          "region.delete",
          "region.flag",
          "region.member",
          "region.info",
          "bypass");
  private static final Map<String, String> MESSAGES =
      Map.ofEntries(
          Map.entry("entry-blocked", "§c✦ Você não pode entrar nesta região."),
          Map.entry("raid-entry-blocked", "§c✦ Para entrar nesta arena, utilize /raid entrar."),
          Map.entry("teleport-blocked", "§c✦ Este teleporte foi bloqueado pela proteção da região."),
          Map.entry("completed", "Concluído."),
          Map.entry("error", "Shirokai Guard: %s"),
          Map.entry("command-denied", "Comando bloqueado nesta região."),
          Map.entry("wand", "Machado de seleção ativado. /sg sel clear desativa."),
          Map.entry(
              "created",
              "Região criada. Flags unset permitem ações; configure /sg flag %s build deny para"
                  + " proteger construção."),
          Map.entry("no-permission", "Sem permissão."),
          Map.entry("input", "%s — escreva no chat. Use /sg cancel para cancelar (60 segundos)."),
          Map.entry("cancelled", "Operação cancelada."),
          Map.entry("expired", "Operação expirada; abra o menu novamente."),
          Map.entry("changed", "Os dados mudaram. Revise e confirme novamente."),
          Map.entry(
              "template-preview",
              "Template %s: %s. Para aplicar, use /sg template confirm %s (60 segundos)."),
          Map.entry("template-applied", "Template aplicado: %s."),
          Map.entry("visual", "Visualização: %s"),
          Map.entry("position", "%s: %s"),
          Map.entry(
              "save-queued", "Snapshot enfileirado; verifique /sg status e logs para falhas."),
          Map.entry("reloaded", "Configurações e regiões recarregadas e validadas."),
          Map.entry("bypass", "Bypass: %s"));
  private static final Options DEFAULT_OPTIONS = new Options(true, true, 10_000);
  private static final Menus DEFAULT_MENUS = new Menus("✦ SHIROKAI GUARD", 45, true);
  private static volatile Snapshot current =
      new Snapshot(
          DEFAULT_OPTIONS, MESSAGES, true, new RaidAccess(true, 40), DEFAULT_MENUS, defaultPermissions(), Map.of(), Map.of());

  private GuardConfig() {}

  private static Map<String, Integer> defaultPermissions() {
    var p = new HashMap<String, Integer>();
    NODES.forEach(n -> p.put(n, 2));
    return Map.copyOf(p);
  }

  public static Options options() {
    return current.options();
  }

  public static Menus menus() {
    return current.menus();
  }

  public static RaidAccess raidAccess() { return current.raidAccess(); }

  public static boolean crateInteractions() {
    return current.crateInteractions();
  }

  public static int fallback(String node) {
    return current.permissions().getOrDefault(node, 2);
  }

  public static Map<Flag, Map<Region.Group, String>> defaultFlags() {
    return current.defaults();
  }

  public static Map<String, RegionTemplate> templates() {
    return current.templates();
  }

  public static String message(String key, Object... args) {
    return current.messages().getOrDefault(key, key).formatted(args);
  }

  public static void apply(Snapshot snapshot) {
    current = snapshot;
  }

  public static Snapshot read() throws Exception {
    AtomicJson.create(DIR.resolve("config.json"), AtomicJson.object(DEFAULT_OPTIONS));
    AtomicJson.create(DIR.resolve("messages.json"), AtomicJson.object(MESSAGES));
    AtomicJson.create(DIR.resolve("menus.json"), AtomicJson.object(DEFAULT_MENUS));
    var permissions = new JsonObject();
    permissions.addProperty("schema", 1);
    permissions.add("fallbackOpLevels", AtomicJson.object(defaultPermissions()));
    AtomicJson.create(DIR.resolve("permissions.json"), permissions);
    var defaults = new JsonObject();
    defaults.addProperty("schema", 1);
    defaults.add("defaults", new JsonObject());
    AtomicJson.create(DIR.resolve("flags.json"), defaults);
    var integrations = new JsonObject();
    integrations.addProperty("crateInteractions", true);
    integrations.add("raidArenaAccess", AtomicJson.object(new RaidAccess(true, 40)));
    AtomicJson.create(DIR.resolve("integrations.json"), integrations);
    var config = AtomicJson.read(DIR.resolve("config.json"));
    var options =
        new Options(
            bool(config, "selectionParticles", true),
            bool(config, "auditChanges", true),
            integer(config, "maxRegionsPerDimension", 10000),
            integer(config, "particleBudget", 128),
            integer(config, "queryCacheEntries", 4096));
    var messages = new HashMap<>(MESSAGES);
    AtomicJson.read(DIR.resolve("messages.json"))
        .entrySet()
        .forEach(
            e -> {
              if (!MESSAGES.containsKey(e.getKey())
                  || !e.getValue().isJsonPrimitive()
                  || !e.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Mensagem inválida: " + e.getKey());
              var text = e.getValue().getAsString();
              if (text.length() > 4096 || text.replace("%s", "").replace("%%", "").contains("%"))
                throw new IllegalArgumentException("Formato inválido: " + e.getKey());
              if (text.split("%s", -1).length != MESSAGES.get(e.getKey()).split("%s", -1).length)
                throw new IllegalArgumentException("Mantenha %s em " + e.getKey());
              messages.put(e.getKey(), text);
            });
    var menu = AtomicJson.read(DIR.resolve("menus.json"));
    var menus =
        new Menus(
            menu.get("title").getAsString(),
            integer(menu, "pageSize", 45),
            bool(menu, "lore", true));
    var p = AtomicJson.read(DIR.resolve("permissions.json"));
    schema(p);
    var levels = new HashMap<>(defaultPermissions());
    p.getAsJsonObject("fallbackOpLevels")
        .entrySet()
        .forEach(
            e -> {
              if (!NODES.contains(e.getKey()))
                throw new IllegalArgumentException("Permissão desconhecida: " + e.getKey());
              int n = exactInt(e.getValue());
              if (n < -1 || n > 4) throw new IllegalArgumentException("Fallback OP: -1..4");
              levels.put(e.getKey(), n);
            });
    var f = AtomicJson.read(DIR.resolve("flags.json"));
    schema(f);
    var flags = RegionTemplate.parseFlags(f.getAsJsonObject("defaults"));
    var integrationConfig = AtomicJson.read(DIR.resolve("integrations.json"));
    var access = integrationConfig.has("raidArenaAccess")
        ? integrationConfig.getAsJsonObject("raidArenaAccess") : new JsonObject();
    return new Snapshot(
        options,
        Map.copyOf(messages),
        bool(integrationConfig, "crateInteractions", true),
        new RaidAccess(bool(access, "enabled", true), integer(access, "authorizationTimeoutTicks", 40)),
        menus,
        Map.copyOf(levels),
        flags,
        RegionTemplate.load(DIR.resolve("templates")));
  }

  private static void schema(JsonObject o) {
    if (exactInt(o.get("schema")) != 1) throw new IllegalArgumentException("Schema incompatível");
  }

  private static boolean bool(JsonObject o, String key, boolean fallback) {
    if (!o.has(key)) return fallback;
    var p = o.getAsJsonPrimitive(key);
    if (!p.isBoolean()) throw new IllegalArgumentException("Booleano esperado: " + key);
    return p.getAsBoolean();
  }

  private static int exactInt(JsonElement e) {
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Número inteiro esperado");
    return e.getAsBigDecimal().intValueExact();
  }

  private static int integer(JsonObject o, String k, int fallback) {
    return o.has(k) ? exactInt(o.get(k)) : fallback;
  }

  public static void saveOptions(Options value) throws Exception {
    var data = AtomicJson.read(DIR.resolve("config.json"));
    AtomicJson.object(value).entrySet().forEach(e -> data.add(e.getKey(), e.getValue()));
    AtomicJson.write(DIR.resolve("config.json"), data);
    ShiroGuard.reload();
  }

  public static void saveTemplate(RegionTemplate template) throws Exception {
    var file = DIR.resolve("templates").resolve(template.id() + ".json");
    if (Files.exists(file))
      throw new IllegalArgumentException(
          "Template já existe; escolha outro nome para preservar o anterior");
    AtomicJson.create(file, template.json());
    ShiroGuard.reload();
  }
}

package dev.shirokai.guard.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A template merges only its explicit flags. Geometry, identity and people are preserved. */
public record RegionTemplate(
    String id, String description, Map<Flag, Map<Region.Group, String>> flags) {
  public RegionTemplate {
    if (id == null || !id.matches("[a-z0-9_-]{1,64}"))
      throw new IllegalArgumentException("ID de template inválido");
    if (description == null || description.length() > 256)
      throw new IllegalArgumentException("Descrição de template inválida");
    flags =
        Region.create("validation", "guard:template", new Bounds(0, 0, 0, 0, 0, 0))
            .withFlags(flags)
            .flags();
  }

  public Region apply(Region region, boolean optional) {
    var result = region;
    for (var f : flags.entrySet())
      if (optional || !f.getKey().optional())
        for (var group : f.getValue().entrySet())
          result = result.withFlag(f.getKey(), group.getKey(), group.getValue());
    return result;
  }

  public static Map<Flag, Map<Region.Group, String>> parseFlags(JsonObject object) {
    var flags = new EnumMap<Flag, Map<Region.Group, String>>(Flag.class);
    for (var e : object.entrySet()) {
      var f = Flag.parse(e.getKey());
      var groups = new EnumMap<Region.Group, String>(Region.Group.class);
      for (var v : e.getValue().getAsJsonObject().entrySet())
        groups.put(
            Region.Group.valueOf(v.getKey().toUpperCase(Locale.ROOT)),
            f.validate(v.getValue().getAsString()));
      flags.put(f, Map.copyOf(groups));
    }
    return Map.copyOf(flags);
  }

  public JsonObject json() {
    var root = new JsonObject();
    root.addProperty("schema", 1);
    root.addProperty("description", description);
    var values = new JsonObject();
    flags.forEach((f, g) -> values.add(f.id(), AtomicJson.object(g)));
    root.add("flags", values);
    return root;
  }

  public static Map<String, RegionTemplate> load(Path directory) throws IOException {
    Files.createDirectories(directory);
    for (var t : defaults()) AtomicJson.create(directory.resolve(t.id() + ".json"), t.json());
    var result = new TreeMap<String, RegionTemplate>();
    try (var stream = Files.list(directory)) {
      for (var file :
          stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
        var root = AtomicJson.read(file);
        if (root.get("schema").getAsInt() != 1)
          throw new IllegalArgumentException("Schema de template inválido");
        String name = file.getFileName().toString();
        name = name.substring(0, name.length() - 5);
        var t =
            new RegionTemplate(
                name,
                root.get("description").getAsString(),
                parseFlags(root.getAsJsonObject("flags")));
        result.put(name, t);
      }
    }
    return Map.copyOf(result);
  }

  public static List<RegionTemplate> defaults() {
    var common = new EnumMap<Flag, Map<Region.Group, String>>(Flag.class);
    for (var f :
        List.of(
            Flag.BUILD,
            Flag.TNT,
            Flag.CREEPER_EXPLOSION,
            Flag.OTHER_EXPLOSION,
            Flag.PISTON,
            Flag.FIRE_SPREAD)) common.put(f, Map.of(Region.Group.ALL, "deny"));
    var spawn = new EnumMap<>(common);
    spawn.put(Flag.PVP, Map.of(Region.Group.ALL, "deny"));
    var arena = new EnumMap<>(common);
    arena.put(Flag.PVP, Map.of(Region.Group.ALL, "allow"));
    var shop = new EnumMap<>(spawn);
    shop.put(Flag.INTERACT, Map.of(Region.Group.ALL, "allow"));
    shop.put(Flag.CHEST_ACCESS, Map.of(Region.Group.ALL, "deny", Region.Group.OWNERS, "allow"));
    var event = new EnumMap<>(common);
    event.put(Flag.PVP, Map.of(Region.Group.ALL, "deny"));
    var gym = new EnumMap<>(spawn);
    gym.put(Flag.POKEMON_BATTLE, Map.of(Region.Group.ALL, "allow"));
    gym.put(Flag.POKEMON_CAPTURE, Map.of(Region.Group.ALL, "deny"));
    return List.of(
        new RegionTemplate("spawn", "Construção, PvP e explosões bloqueados", spawn),
        new RegionTemplate("arena", "PvP permitido, construção protegida", arena),
        new RegionTemplate("loja", "Interação permitida e baús restritos a owners", shop),
        new RegionTemplate("evento", "Área de evento sem PvP ou construção", event),
        new RegionTemplate("ginasio", "Batalhas Pokémon permitidas; captura bloqueada", gym));
  }
}

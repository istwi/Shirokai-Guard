package dev.shirokai.guard.core;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Versioned snapshot, validated before replacing live data. Never overwrites malformed input on
 * load.
 */
public final class RegionStore {
  private final Path path;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public RegionStore(Path directory) {
    path = directory.resolve("regions.json");
  }

  public List<Region> load() throws IOException {
    if (!Files.exists(path)) return List.of();
    try (var reader = Files.newBufferedReader(path)) {
      var root = JsonParser.parseReader(reader).getAsJsonObject();
      if (root.get("schema").getAsInt() != 1)
        throw new IllegalArgumentException("Versão de dados incompatível");
      var result = new ArrayList<Region>();
      for (var element : root.getAsJsonArray("regions")) {
        var r = element.getAsJsonObject();
        var owners = new HashSet<UUID>();
        var members = new HashSet<UUID>();
        r.getAsJsonArray("owners").forEach(v -> owners.add(UUID.fromString(v.getAsString())));
        r.getAsJsonArray("members").forEach(v -> members.add(UUID.fromString(v.getAsString())));
        var flags = new EnumMap<Flag, Map<Region.Group, String>>(Flag.class);
        r.getAsJsonObject("flags")
            .entrySet()
            .forEach(
                e -> {
                  var values = new EnumMap<Region.Group, String>(Region.Group.class);
                  e.getValue()
                      .getAsJsonObject()
                      .entrySet()
                      .forEach(
                          v ->
                              values.put(
                                  Region.Group.valueOf(v.getKey()), v.getValue().getAsString()));
                  flags.put(Flag.parse(e.getKey()), values);
                });
        result.add(
            new Region(
                r.get("id").getAsString(),
                r.get("dimension").getAsString(),
                gson.fromJson(r.get("bounds"), Bounds.class),
                r.get("priority").getAsInt(),
                r.has("parent") ? r.get("parent").getAsString() : null,
                owners,
                members,
                flags));
      }
      new RegionManager().replace(result);
      return List.copyOf(result);
    } catch (RuntimeException e) {
      throw new IOException("Dados inválidos em " + path + "; arquivo preservado", e);
    }
  }

  public synchronized void save(Collection<Region> regions) throws IOException {
    new RegionManager().replace(regions);
    var root = new JsonObject();
    root.addProperty("schema", 1);
    var array = new JsonArray();
    for (var r : regions) {
      var object = new JsonObject();
      object.addProperty("id", r.id());
      object.addProperty("dimension", r.dimension());
      object.add("bounds", gson.toJsonTree(r.bounds()));
      object.addProperty("priority", r.priority());
      if (r.parent() != null) object.addProperty("parent", r.parent());
      object.add("owners", gson.toJsonTree(r.owners()));
      object.add("members", gson.toJsonTree(r.members()));
      var flags = new JsonObject();
      r.flags().forEach((f, v) -> flags.add(f.id(), gson.toJsonTree(v)));
      object.add("flags", flags);
      array.add(object);
    }
    root.add("regions", array);
    Files.createDirectories(path.getParent());
    Path temp = Files.createTempFile(path.getParent(), "regions-", ".tmp");
    try {
      Files.writeString(temp, gson.toJson(root), StandardCharsets.UTF_8);
      try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      if (Files.exists(path))
        Files.copy(
            path, path.resolveSibling("regions.json.bak"), StandardCopyOption.REPLACE_EXISTING);
      // Atomic rename is required; an unsupported filesystem must report failure.
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}

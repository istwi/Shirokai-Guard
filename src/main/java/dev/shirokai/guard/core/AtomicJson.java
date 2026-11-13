package dev.shirokai.guard.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

public final class AtomicJson {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private AtomicJson() {}

  public static JsonObject read(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new IOException("JSON inválido: " + path, e);
    }
  }

  public static void create(Path path, JsonObject defaults) throws IOException {
    if (!Files.exists(path)) write(path, defaults);
  }

  public static void write(Path path, JsonObject data) throws IOException {
    Files.createDirectories(path.getParent());
    var temp = Files.createTempFile(path.getParent(), "guard-", ".tmp");
    try {
      Files.writeString(temp, GSON.toJson(data));
      try (var file = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        file.force(true);
      }
      if (Files.exists(path))
        Files.copy(
            path,
            path.resolveSibling(path.getFileName() + ".bak"),
            StandardCopyOption.REPLACE_EXISTING);
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static JsonObject object(Object value) {
    return GSON.toJsonTree(value).getAsJsonObject();
  }
}

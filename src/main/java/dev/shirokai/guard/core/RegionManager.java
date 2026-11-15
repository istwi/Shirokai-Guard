package dev.shirokai.guard.core;

import java.util.*;

/** Main-thread mutations; immutable query results. Large regions use a per-dimension fallback. */
public final class RegionManager {
  private Map<String, Region> regions = Map.of();
  private Map<String, Map<Long, List<Region>>> chunks = Map.of();
  private Map<String, List<Region>> wide = Map.of();

  private record Query(String dimension, int x, int y, int z) {}

  private final LinkedHashMap<Query, List<Region>> cache = new LinkedHashMap<>(128, 0.75f, true);
  private int cacheLimit = 4096;
  private long queries, hits;

  public void cacheLimit(int limit) {
    if (limit < 0 || limit > 65536) throw new IllegalArgumentException("Cache: 0..65536");
    cacheLimit = limit;
    cache.clear();
  }

  public record Metrics(
      long queries, long hits, int cacheEntries, int indexedChunks, int largeRegions) {}

  public Metrics metrics() {
    return new Metrics(
        queries,
        hits,
        cache.size(),
        chunks.values().stream().mapToInt(Map::size).sum(),
        wide.values().stream().mapToInt(List::size).sum());
  }

  private static String key(String dim, String id) {
    return dim + "#" + id;
  }

  private static long chunk(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  public Collection<Region> all() {
    return regions.values();
  }

  public Region get(String dimension, String id) {
    var r = regions.get(key(dimension, id));
    if (r == null) throw new IllegalArgumentException("Região inexistente nesta dimensão: " + id);
    return r;
  }

  public void put(Region region) {
    var next = new HashMap<>(regions);
    var previous = next.put(key(region.dimension(), region.id()), region);
    if (previous != null && Objects.equals(previous.bounds(), region.bounds())) {
      var seen = new HashSet<String>();
      var cursor = region;
      while (cursor != null) {
        if (!seen.add(cursor.id())) throw new IllegalArgumentException("Herança circular");
        if (cursor.parent() == null) break;
        cursor = next.get(key(region.dimension(), cursor.parent()));
        if (cursor == null) throw new IllegalArgumentException("Pai inexistente");
      }
      var b = region.bounds();
      if (b == null
          || ((long) (b.maxX() >> 4) - (b.minX() >> 4) + 1)
                  * ((long) (b.maxZ() >> 4) - (b.minZ() >> 4) + 1)
              > 4096)
        wide.getOrDefault(region.dimension(), List.of())
            .replaceAll(r -> r.id().equals(region.id()) ? region : r);
      else
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++)
          for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
            chunks
                .get(region.dimension())
                .get(chunk(x, z))
                .replaceAll(r -> r.id().equals(region.id()) ? region : r);
      regions = Collections.unmodifiableMap(next);
      cache.clear();
    } else replace(next.values());
  }

  public void remove(String dimension, String id) {
    get(dimension, id);
    if (regions.values().stream()
        .anyMatch(r -> r.dimension().equals(dimension) && id.equals(r.parent())))
      throw new IllegalArgumentException("Remova primeiro os vínculos dos filhos");
    var next = new HashMap<>(regions);
    next.remove(key(dimension, id));
    replace(next.values());
  }

  public void replace(Collection<Region> replacement) {
    var next = new TreeMap<String, Region>();
    for (var r : replacement)
      if (next.put(key(r.dimension(), r.id()), r) != null)
        throw new IllegalArgumentException("Região duplicada");
    for (var r : next.values()) {
      var seen = new HashSet<String>();
      var cursor = r;
      while (cursor != null) {
        if (!seen.add(cursor.id())) throw new IllegalArgumentException("Herança circular");
        if (cursor.parent() == null) break;
        cursor = next.get(key(r.dimension(), cursor.parent()));
        if (cursor == null) throw new IllegalArgumentException("Pai inexistente");
      }
    }
    var index = new HashMap<String, Map<Long, List<Region>>>();
    var large = new HashMap<String, List<Region>>();
    for (var r : next.values()) {
      var b = r.bounds();
      if (b == null
          || ((long) (b.maxX() >> 4) - (b.minX() >> 4) + 1)
                  * ((long) (b.maxZ() >> 4) - (b.minZ() >> 4) + 1)
              > 4096) {
        large.computeIfAbsent(r.dimension(), k -> new ArrayList<>()).add(r);
        continue;
      }
      var world = index.computeIfAbsent(r.dimension(), k -> new HashMap<>());
      for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++)
        for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
          world.computeIfAbsent(chunk(x, z), k -> new ArrayList<>()).add(r);
    }
    cache.clear();
    regions = Collections.unmodifiableMap(next);
    chunks = index;
    wide = large;
  }

  public List<Region> at(String dimension, int x, int y, int z) {
    queries++;
    var query = new Query(dimension, x, y, z);
    var cached = cache.get(query);
    if (cached != null) {
      hits++;
      return cached;
    }
    var result = new ArrayList<>(wide.getOrDefault(dimension, List.of()));
    result.addAll(
        chunks.getOrDefault(dimension, Map.of()).getOrDefault(chunk(x >> 4, z >> 4), List.of()));
    result.removeIf(r -> r.bounds() != null && !r.bounds().contains(x, y, z));
    result.sort(Comparator.comparingInt(Region::priority).reversed().thenComparing(Region::id));
    var answer = List.copyOf(result);
    if (cacheLimit > 0) {
      cache.put(query, answer);
      while (cache.size() > cacheLimit) cache.remove(cache.keySet().iterator().next());
    }
    return answer;
  }

  private String inherited(Region region, Flag flag, UUID actor) {
    var seen = new HashSet<String>();
    var r = region;
    while (r != null && seen.add(r.id())) {
      var values = r.flags().getOrDefault(flag, Map.of());
      var group = group(region, actor);
      var value = values.getOrDefault(group, values.get(Region.Group.ALL));
      if (value != null) return value;
      r = r.parent() == null ? null : get(r.dimension(), r.parent());
    }
    return null;
  }

  public Region.Group group(Region region, UUID actor) {
    if (actor == null) return Region.Group.NONMEMBERS;
    var r = region;
    boolean member = false;
    while (r != null) {
      if (r.owners().contains(actor)) return Region.Group.OWNERS;
      member |= r.members().contains(actor);
      r = r.parent() == null ? null : get(r.dimension(), r.parent());
    }
    return member ? Region.Group.MEMBERS : Region.Group.NONMEMBERS;
  }

  public String value(String dim, int x, int y, int z, Flag flag, UUID actor) {
    Integer priority = null;
    String result = null;
    for (var r : at(dim, x, y, z)) {
      if (priority != null && r.priority() < priority) break;
      String v = inherited(r, flag, actor);
      if (v == null && (flag == Flag.BLOCK_BREAK || flag == Flag.BLOCK_PLACE))
        v = inherited(r, Flag.BUILD, actor);
      if (v == null) continue;
      priority = r.priority();
      if ("deny".equals(v)) return v;
      if (result == null || flag == Flag.ENTRY_MODE && "authorized-only".equals(v)) result = v;
    }
    return result;
  }

  /** Highest defined priority wins; deny > authorized-only > normal at equal priority.
   * Inherited flags bind to the destination child region, never a global permission. */
  public record EntryRule(boolean denied, Set<String> regions, Set<String> deniedRegions) {}

  public EntryRule entryRule(String dim, int x, int y, int z, UUID actor) {
    Integer priority = null;
    boolean denied = false;
    var required = new HashSet<String>();
    var deniedRegions = new HashSet<String>();
    for (var r : at(dim, x, y, z)) {
      if (priority != null && r.priority() < priority) break;
      var value = inherited(r, Flag.ENTRY_MODE, actor);
      if (value == null) continue;
      priority = r.priority();
      denied |= value.equals("deny");
      if (value.equals("deny")) deniedRegions.add(r.id());
      if (value.equals("authorized-only")) required.add(r.id());
    }
    return new EntryRule(denied, Set.copyOf(required), Set.copyOf(deniedRegions));
  }

  public boolean allowed(String dim, int x, int y, int z, Flag flag, UUID actor) {
    return !"deny".equals(value(dim, x, y, z, flag, actor));
  }

  public boolean commandAllowed(String dim, int x, int y, int z, UUID actor, String command) {
    String root =
        command.strip().replaceFirst("^/", "").split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    // Namespaced aliases must not evade a block on the unqualified name.
    String bare = root.substring(root.lastIndexOf(':') + 1);
    Integer priority = null;
    for (var r : at(dim, x, y, z)) {
      if (priority != null && r.priority() < priority) break;
      String blocked = inherited(r, Flag.BLOCKED_COMMANDS, actor),
          allowed = inherited(r, Flag.ALLOWED_COMMANDS, actor);
      if (blocked == null && allowed == null) continue;
      priority = r.priority();
      if (blocked != null && matches(blocked, root, bare)) return false;
      if (allowed != null && !matches(allowed, root, bare)) return false;
    }
    return true;
  }

  private boolean matches(String list, String root, String bare) {
    return Arrays.stream(list.toLowerCase(Locale.ROOT).split(","))
        .map(String::strip)
        .anyMatch(s -> s.equals("*") || s.equals(root) || s.equals(bare));
  }

  /**
   * Queries chunks touched by a short physical movement, then checks exact cuboid intersections.
   */
  public boolean movementAllowed(
      String dimension, double x, double y, double z, double a, double b, double c, UUID actor) {
    if (!Double.isFinite(x + y + z + a + b + c)) return false;
    if (Math.abs(a - x) > 128 || Math.abs(b - y) > 128 || Math.abs(c - z) > 128) return false;
    var candidates = new HashSet<>(wide.getOrDefault(dimension, List.of()));
    var index = chunks.getOrDefault(dimension, Map.of());
    for (int cx = (int) Math.floor(Math.min(x, a)) >> 4;
        cx <= ((int) Math.floor(Math.max(x, a)) >> 4);
        cx++)
      for (int cz = (int) Math.floor(Math.min(z, c)) >> 4;
          cz <= ((int) Math.floor(Math.max(z, c)) >> 4);
          cz++) candidates.addAll(index.getOrDefault(chunk(cx, cz), List.of()));
    for (var r : candidates) {
      if (r.bounds() == null) continue;
      var interval = r.bounds().intersection(x, y, z, a, b, c);
      if (interval == null) continue;
      for (int edge = 0; edge < 2; edge++) {
        double t = interval[edge];
        if (edge == 0 ? t <= 0 : t >= 1) continue;
        double inside = t + (edge == 0 ? 1 : -1) * Math.min(1e-7, (interval[1] - interval[0]) / 4);
        int px = (int) Math.floor(x + (a - x) * inside),
            py = (int) Math.floor(y + (b - y) * inside),
            pz = (int) Math.floor(z + (c - z) * inside);
        // Compare effective restrictions on both sides, including leaving a higher-priority
        // normal exception inside a protected region. Do not trap existing occupants.
        double epsilon = Math.min(1e-7, (interval[1] - interval[0]) / 4);
        if (epsilon > 0) {
          double t0 = Math.max(0, t - epsilon), t1 = Math.min(1, t + epsilon);
          var oldRule = entryRule(dimension, (int)Math.floor(x+(a-x)*t0),
              (int)Math.floor(y+(b-y)*t0), (int)Math.floor(z+(c-z)*t0), actor);
          var newRule = entryRule(dimension, (int)Math.floor(x+(a-x)*t1),
              (int)Math.floor(y+(b-y)*t1), (int)Math.floor(z+(c-z)*t1), actor);
          var occupied = new HashSet<>(oldRule.regions()); occupied.addAll(oldRule.deniedRegions());
          if (newRule.regions().stream().anyMatch(id -> !occupied.contains(id))
              || newRule.deniedRegions().stream().anyMatch(id -> !occupied.contains(id))) return false;
        }
        if (!allowed(dimension, px, py, pz, edge == 0 ? Flag.ENTRY : Flag.EXIT, actor))
          return false;
      }
    }
    return true;
  }
}

package dev.shirokai.guard.core;

import java.util.*;

public record Region(
    String id,
    String dimension,
    Bounds bounds,
    int priority,
    String parent,
    Set<UUID> owners,
    Set<UUID> members,
    Map<Flag, Map<Group, String>> flags) {
  public enum Group {
    ALL,
    OWNERS,
    MEMBERS,
    NONMEMBERS
  }

  public Region {
    if (id == null || !id.matches("[a-z0-9_-]{1,64}"))
      throw new IllegalArgumentException("ID: a-z, 0-9, _ ou -, até 64 caracteres");
    if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
      throw new IllegalArgumentException("Dimensão inválida");
    if (bounds == null && !id.equals("__global__"))
      throw new IllegalArgumentException("Somente __global__ pode não ter limites");
    if (bounds != null && id.equals("__global__"))
      throw new IllegalArgumentException("__global__ não pode ter limites");
    owners = Set.copyOf(owners);
    members = Set.copyOf(members);
    var copy = new EnumMap<Flag, Map<Group, String>>(Flag.class);
    flags.forEach(
        (f, v) -> {
          v.values().forEach(f::validate);
          copy.put(f, Map.copyOf(v));
        });
    flags = Collections.unmodifiableMap(copy);
  }

  public static Region create(String id, String dimension, Bounds bounds) {
    return new Region(id, dimension, bounds, 0, null, Set.of(), Set.of(), Map.of());
  }

  public Region withFlags(Map<Flag, Map<Group, String>> values) {
    return new Region(id, dimension, bounds, priority, parent, owners, members, values);
  }

  public Region withBounds(Bounds b) {
    return new Region(id, dimension, b, priority, parent, owners, members, flags);
  }

  public Region withPriority(int p) {
    return new Region(id, dimension, bounds, p, parent, owners, members, flags);
  }

  public Region withParent(String p) {
    return new Region(id, dimension, bounds, priority, p, owners, members, flags);
  }

  public Region withPerson(UUID uuid, boolean owner, boolean add) {
    var set = new HashSet<>(owner ? owners : members);
    if (add) set.add(uuid);
    else set.remove(uuid);
    return new Region(
        id,
        dimension,
        bounds,
        priority,
        parent,
        owner ? set : owners,
        owner ? members : set,
        flags);
  }

  public Region withFlag(Flag f, Group group, String value) {
    var copy = new EnumMap<Flag, Map<Group, String>>(Flag.class);
    copy.putAll(flags);
    var values = new EnumMap<Group, String>(Group.class);
    values.putAll(copy.getOrDefault(f, Map.of()));
    if (value.equals("unset")) values.remove(group);
    else values.put(group, f.validate(value));
    if (values.isEmpty()) copy.remove(f);
    else copy.put(f, values);
    return new Region(id, dimension, bounds, priority, parent, owners, members, copy);
  }
}

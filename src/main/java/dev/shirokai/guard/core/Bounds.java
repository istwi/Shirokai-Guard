package dev.shirokai.guard.core;

public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  public Bounds {
    if (minX > maxX || minY > maxY || minZ > maxZ)
      throw new IllegalArgumentException("Limites invertidos");
    if (minX < -30_000_000 || maxX > 30_000_000 || minZ < -30_000_000 || maxZ > 30_000_000)
      throw new IllegalArgumentException("Fora dos limites do mundo");
  }

  public static Bounds between(int x, int y, int z, int a, int b, int c) {
    return new Bounds(
        Math.min(x, a),
        Math.min(y, b),
        Math.min(z, c),
        Math.max(x, a),
        Math.max(y, b),
        Math.max(z, c));
  }

  public boolean contains(int x, int y, int z) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
  }

  public Bounds vertical(int bottom, int top) {
    return new Bounds(minX, bottom, minZ, maxX, top, maxZ);
  }

  public Bounds resize(int amount, String direction) {
    return switch (direction) {
      case "east" -> new Bounds(minX, minY, minZ, Math.addExact(maxX, amount), maxY, maxZ);
      case "west" -> new Bounds(Math.subtractExact(minX, amount), minY, minZ, maxX, maxY, maxZ);
      case "up" -> new Bounds(minX, minY, minZ, maxX, Math.addExact(maxY, amount), maxZ);
      case "down" -> new Bounds(minX, Math.subtractExact(minY, amount), minZ, maxX, maxY, maxZ);
      case "south" -> new Bounds(minX, minY, minZ, maxX, maxY, Math.addExact(maxZ, amount));
      case "north" -> new Bounds(minX, minY, Math.subtractExact(minZ, amount), maxX, maxY, maxZ);
      default -> throw new IllegalArgumentException("Direção: north/south/east/west/up/down");
    };
  }

  /** Slab intersection against continuous block volume [min, max+1). */
  public double[] intersection(double x, double y, double z, double a, double b, double c) {
    double enter = 0, exit = 1;
    double[] from = {x, y, z},
        delta = {a - x, b - y, c - z},
        low = {minX, minY, minZ},
        high = {(double) maxX + 1, (double) maxY + 1, (double) maxZ + 1};
    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(delta[axis]) < 1e-12) {
        if (from[axis] < low[axis] || from[axis] >= high[axis]) return null;
        continue;
      }
      double t0 = (low[axis] - from[axis]) / delta[axis],
          t1 = (high[axis] - from[axis]) / delta[axis];
      enter = Math.max(enter, Math.min(t0, t1));
      exit = Math.min(exit, Math.max(t0, t1));
      if (enter >= exit) return null;
    }
    return new double[] {enter, exit};
  }
}

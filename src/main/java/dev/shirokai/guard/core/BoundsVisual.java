package dev.shirokai.guard.core;

import java.util.*;

/** Bounded samples of twelve edges, clipped to the observer's nearby view. */
public final class BoundsVisual {
  public record Point(double x, double y, double z) {}

  public static List<Point> sample(Bounds box, double x, double y, double z, int budget) {
    if (budget < 1) return List.of();
    double[] min = {box.minX(), box.minY(), box.minZ()},
        max = {(double) box.maxX() + 1, (double) box.maxY() + 1, (double) box.maxZ() + 1},
        eye = {x, y, z};
    var points = new LinkedHashSet<Point>();
    int perEdge = Math.max(2, budget / 12);
    for (int axis = 0; axis < 3; axis++) {
      int u = (axis + 1) % 3, v = (axis + 2) % 3;
      for (double a : new double[] {min[u], max[u]})
        for (double b : new double[] {min[v], max[v]}) {
          if (Math.abs(a - eye[u]) > 64 || Math.abs(b - eye[v]) > 64) continue;
          double from = Math.max(min[axis], eye[axis] - 64),
              to = Math.min(max[axis], eye[axis] + 64);
          if (from > to) continue;
          int count = Math.min(perEdge, Math.max(2, (int) Math.ceil(to - from) + 1));
          for (int i = 0; i < count && points.size() < budget; i++) {
            double[] p = new double[3];
            p[u] = a;
            p[v] = b;
            p[axis] = from + (to - from) * i / (count - 1);
            points.add(new Point(p[0], p[1], p[2]));
          }
        }
    }
    return List.copyOf(points);
  }
}

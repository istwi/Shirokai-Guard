package dev.shirokai.guard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SpatialIndexTest {
  @Test
  void representativeIndexWorkload() {
    var regions = new ArrayList<Region>();
    for (int x = 0; x < 100; x++)
      for (int z = 0; z < 100; z++)
        regions.add(
            Region.create(
                    "r_" + x + "_" + z,
                    "custom:world",
                    new Bounds(x * 64, -64, z * 64, x * 64 + 31, 319, z * 64 + 31))
                .withFlag(Flag.BUILD, Region.Group.ALL, "deny"));
    var manager = new RegionManager();
    long start = System.nanoTime();
    manager.replace(regions);
    long indexed = System.nanoTime();
    int denies = 0;
    for (int n = 0; n < 100_000; n++)
      if (!manager.allowed(
          "custom:world", (n % 100) * 64, 64, ((n / 100) % 100) * 64, Flag.BLOCK_BREAK, null))
        denies++;
    long end = System.nanoTime();
    assertEquals(100_000, denies);
    System.out.printf(
        Locale.ROOT,
        "10k regions: index %.1f ms; 100k indexed queries %.1f ms (synthetic JVM workload, not"
            + " player capacity)%n",
        (indexed - start) / 1e6,
        (end - indexed) / 1e6);
  }
}

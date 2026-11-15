package dev.shirokai.guard.core;

import java.util.*;

/** Server-memory, single-use capabilities. No serialization and no player-supplied IDs. */
public final class EntryAuthorizations {
  public record Grant(UUID token, UUID player, String dimension, Set<String> regions,
                      String integration, String scope, long expires) {
    public Grant { regions = Set.copyOf(regions); }
  }
  private final Map<UUID, Grant> pending = new HashMap<>();

  public synchronized Grant issue(UUID player, String dimension, Set<String> regions,
                                  String integration, String scope, long now, int lifetime) {
    if (lifetime < 1 || lifetime > 200) throw new IllegalArgumentException("Timeout: 1..200 ticks");
    Objects.requireNonNull(player); Objects.requireNonNull(dimension);
    Objects.requireNonNull(integration); Objects.requireNonNull(scope);
    pending.values().removeIf(g -> g.expires() <= now || g.player().equals(player));
    var grant = new Grant(UUID.randomUUID(), player, dimension, regions, integration, scope, now + lifetime);
    pending.put(grant.token(), grant);
    return grant;
  }

  /** Even a mismatched attempt burns the token. Consumption precedes the operation. */
  public synchronized boolean consume(Grant grant, UUID player, String dimension,
                                      Set<String> regions, long now) {
    var stored = pending.remove(grant.token());
    return grant.equals(stored) && now < stored.expires() && stored.player().equals(player)
        && stored.dimension().equals(dimension) && stored.regions().equals(regions);
  }
  public synchronized void revoke(UUID token) { pending.remove(token); }
  public synchronized void forget(UUID player) { pending.values().removeIf(g -> g.player().equals(player)); }
  public synchronized void end(String integration, String scope) {
    pending.values().removeIf(g -> g.integration().equals(integration) && g.scope().equals(scope));
  }
  public synchronized void clear() { pending.clear(); }
}

package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import java.util.function.IntSupplier;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Captures provenance during command execution. Deferred capabilities bind to one exact destination. */
public final class CommandEntryAccess {
  private record Context(UUID player, String command) {}
  private record Ticket(UUID player, ServerWorld world, Vec3d pos, String command, Set<String> regions, long expires) {}
  private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
  private static final ThreadLocal<Ticket> ACTIVE = new ThreadLocal<>();
  private static final Map<UUID, Ticket> TICKETS = new HashMap<>();
  private CommandEntryAccess() {}

  public static void execute(ServerCommandSource source, String input, Runnable operation) {
    var previous = CONTEXT.get();
    if (previous != null) { operation.run(); return; } // Aliases preserve the outer command.
    var player = source.getPlayer();
    String command;
    try { command = EntryCommands.normalize(input); }
    catch (IllegalArgumentException invalid) { operation.run(); return; }
    if (player == null) { operation.run(); return; }
    CONTEXT.set(new Context(player.getUuid(),command));
    try { operation.run(); } finally { CONTEXT.remove(); }
  }

  private static boolean listed(Ticket ticket, Set<String> regions) {
    for (String id : regions)
      if (!EntryCommands.list(ShiroGuard.REGIONS.get(ShiroGuard.dimension(ticket.world()),id)).contains(ticket.command())) return false;
    return true;
  }
  public static UUID capture(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
    var context = CONTEXT.get();
    if (context == null || !context.player().equals(player.getUuid())) return null;
    var block = BlockPos.ofFloored(pos);
    var rule = ShiroGuard.REGIONS.entryRule(ShiroGuard.dimension(world),block.getX(),block.getY(),block.getZ(),player.getUuid());
    var ticket = new Ticket(player.getUuid(),world,pos,context.command(),rule.commandRegions(),player.getServer().getTicks()+12000L);
    if (rule.commandRegions().isEmpty() || !listed(ticket,rule.commandRegions())) return null;
    TICKETS.values().removeIf(t -> t.expires() <= player.getServer().getTicks());
    var id = UUID.randomUUID(); TICKETS.put(id,ticket); return id;
  }
  public static int run(UUID id, ServerPlayerEntity player, ServerWorld world, Vec3d pos, IntSupplier operation) {
    var ticket = TICKETS.remove(id); // Burn even on a mismatched attempt.
    if (ticket == null || ACTIVE.get() != null || !ticket.player().equals(player.getUuid())
        || ticket.world() != world || ticket.pos().squaredDistanceTo(pos)>1e-10
        || ticket.expires() <= player.getServer().getTicks()) return 0;
    ACTIVE.set(ticket);
    try {
      // Revalidate current policies and all other flags before the integration commits any effects.
      if (!Protection.transition(player,world,pos,true)) return 0;
      return operation.getAsInt();
    } finally { ACTIVE.remove(); }
  }
  public static boolean permits(ServerPlayerEntity player, ServerWorld world, Vec3d pos, Set<String> regions) {
    var ticket = ACTIVE.get();
    return ticket != null && ticket.player().equals(player.getUuid()) && ticket.world()==world
        && ticket.pos().squaredDistanceTo(pos)<1e-10 && ticket.regions().containsAll(regions) && listed(ticket,regions);
  }
  public static void revoke(UUID id) { TICKETS.remove(id); }
  public static void forget(UUID player) { TICKETS.values().removeIf(t -> t.player().equals(player)); }
  public static void clear() { TICKETS.clear(); ACTIVE.remove(); CONTEXT.remove(); }
}

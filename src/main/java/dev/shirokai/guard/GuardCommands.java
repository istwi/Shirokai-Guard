package dev.shirokai.guard;

import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class GuardCommands {
  public record Selection(String dimension, BlockPos first, BlockPos second) {
    public Bounds bounds() {
      if (first == null || second == null) throw new IllegalArgumentException("Defina pos1 e pos2");
      return Bounds.between(
          first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
    }
  }

  public static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
  public static final Set<UUID> WANDS = new HashSet<>();

  public static void forget(UUID id) {
    SELECTIONS.remove(id);
    WANDS.remove(id);
    Protection.forget(id);
    GuardMenus.forget(id);
    TemplateActions.forget(id);
    RegionVisuals.forget(id);
  }

  public static boolean wand(ServerPlayerEntity p, ItemStack stack) {
    return WANDS.contains(p.getUuid())
        && stack.isOf(Items.WOODEN_AXE)
        && ShiroGuard.permission(p.getCommandSource(), "region.create");
  }

  public static void position(ServerPlayerEntity p, BlockPos pos, boolean first) {
    String dim = ShiroGuard.dimension(p.getWorld());
    var old = SELECTIONS.get(p.getUuid());
    if (old == null || !old.dimension().equals(dim)) old = new Selection(dim, null, null);
    SELECTIONS.put(
        p.getUuid(),
        new Selection(
            dim,
            first ? pos.toImmutable() : old.first(),
            first ? old.second() : pos.toImmutable()));
    p.sendMessage(
        Text.literal(GuardConfig.message("position", first ? "Pos1" : "Pos2", pos.toShortString())),
        false);
  }

  private static String node(String command) {
    return switch (command) {
      case "define", "redefine", "wand", "pos1", "pos2", "sel", "expand", "contract", "select" ->
          "region.create";
      case "remove" -> "region.delete";
      case "template", "flag", "flags", "entry-command" -> "region.flag";
      case "addowner", "removeowner", "addmember", "removemember" -> "region.member";
      case "visualize", "info", "list", "help" -> "region.info";
      case "bypass" -> "bypass";
      case "cancel" -> "region.info";
      default -> "admin";
    };
  }

  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> {
          var root = literal("sg").executes(ctx -> run(ctx.getSource(), "help", ""));
          for (String command :
              List.of(
                  "menu",
                  "template",
                  "visualize",
                  "cancel",
                  "wand",
                  "pos1",
                  "pos2",
                  "sel",
                  "expand",
                  "contract",
                  "define",
                  "redefine",
                  "remove",
                  "info",
                  "list",
                  "select",
                  "setpriority",
                  "setparent",
                  "addowner",
                  "removeowner",
                  "addmember",
                  "removemember",
                  "flag",
                  "flags",
                  "entry-command",
                  "reload",
                  "save",
                  "status",
                  "debug",
                  "bypass",
                  "help")) {
            root.then(
                literal(command)
                    .requires(s -> ShiroGuard.permission(s, node(command)))
                    .executes(ctx -> run(ctx.getSource(), command, ""))
                    .then(
                        argument("args", StringArgumentType.greedyString())
                            .executes(
                                ctx ->
                                    run(
                                        ctx.getSource(),
                                        command,
                                        StringArgumentType.getString(ctx, "args")))));
          }
          dispatcher.register(root);
        });
  }

  private static void need(String[] args, int count) {
    if (args.length < count)
      throw new IllegalArgumentException("Argumentos insuficientes; /sg help");
  }

  private static Selection selection(ServerPlayerEntity p) {
    var s = SELECTIONS.get(p.getUuid());
    if (s == null || !s.dimension().equals(ShiroGuard.dimension(p.getWorld())))
      throw new IllegalArgumentException("Seleção ausente nesta dimensão");
    return s;
  }

  private static UUID uuid(ServerCommandSource s, String input) {
    try {
      return UUID.fromString(input);
    } catch (IllegalArgumentException ignored) {
    }
    var p = s.getServer().getPlayerManager().getPlayer(input);
    if (p == null)
      throw new IllegalArgumentException(
          "Jogador offline: informe UUID para evitar identidade ambígua");
    return p.getUuid();
  }

  public static int run(ServerCommandSource s, String command, String input) {
    try {
      if (!ShiroGuard.permission(s, node(command)))
        throw new IllegalArgumentException(GuardConfig.message("no-permission"));
      String[] a = input.isBlank() ? new String[0] : input.split("\\s+");
      var p = s.getPlayer();
      String dim = ShiroGuard.dimension(s.getWorld());
      boolean changed = false;
      String result = GuardConfig.message("completed");
      switch (command) {
        case "entry-command" -> {
          need(a, 2);
          var region = ShiroGuard.REGIONS.get(dim, a[0]);
          var commands = new TreeSet<>(EntryCommands.list(region));
          switch (a[1]) {
            case "list" -> { if (a.length != 2) throw new IllegalArgumentException("Use <região> list"); }
            case "clear" -> { if (a.length != 2) throw new IllegalArgumentException("Use <região> clear"); commands.clear(); changed = true; }
            case "add", "remove" -> {
              need(a, 3);
              String entry = EntryCommands.normalize(input.strip().split("\\s+", 3)[2]);
              if (a[1].equals("add")) commands.add(entry); else commands.remove(entry);
              changed = true;
            }
            default -> throw new IllegalArgumentException("Use entry-command <região> add/remove/list/clear [comando completo]");
          }
          if (changed) ShiroGuard.REGIONS.put(region.withFlag(Flag.ENTRY_COMMANDS, Region.Group.ALL,
              commands.isEmpty() ? "unset" : String.join("\n", commands)));
          result = "Comandos de entrada de " + region.id() + ": " + commands;
        }
        case "menu" -> {
          GuardMenus.home(s.getPlayerOrThrow());
        }
        case "cancel" -> {
          GuardMenus.forget(s.getPlayerOrThrow().getUuid());
          TemplateActions.forget(s.getPlayerOrThrow().getUuid());
          result = GuardConfig.message("cancelled");
        }
        case "visualize" -> {
          need(a, 1);
          RegionVisuals.toggle(s.getPlayerOrThrow(), a[0]);
          result = GuardConfig.message("visual", a[0]);
        }
        case "template" -> {
          need(a, 1);
          switch (a[0]) {
            case "list" ->
                result = String.join(", ", new TreeSet<>(GuardConfig.templates().keySet()));
            case "apply" -> {
              need(a, 3);
              var plan = TemplateActions.preview(s.getPlayerOrThrow(), a[1], a[2]);
              result =
                  GuardConfig.message(
                      "template-preview",
                      plan.template().id(),
                      plan.template().description(),
                      plan.token());
            }
            case "confirm" -> {
              need(a, 2);
              TemplateActions.confirm(s.getPlayerOrThrow(), a[1]);
            }
            case "save" -> {
              need(a, 3);
              if (!ShiroGuard.permission(s, "admin"))
                throw new IllegalArgumentException(GuardConfig.message("no-permission"));
              var r = ShiroGuard.REGIONS.get(dim, a[2]);
              GuardConfig.saveTemplate(
                  new RegionTemplate(a[1], "Modelo salvo de " + r.id(), r.flags()));
            }
            default ->
                throw new IllegalArgumentException(
                    "Use template list | apply <região> <template> | confirm <código> | save <nome>"
                        + " <região>");
          }
        }
        case "help" ->
            result =
                "Shirokai Guard alpha — /sg wand, pos1, pos2, sel clear, expand <n> <direção>|vert,"
                    + " contract <n> <direção>, define <id>|__global__,"
                    + " redefine/remove/info/select/flags <id>, list, setpriority <id> <n>,"
                    + " setparent <id> <pai|none>, addowner/removeowner/addmember/removemember <id>"
                    + " <nome online|UUID>, flag <id> <flag> <allow|deny|normal|authorized-only|command-only|unset|texto|lista>"
                    + " [@all|@owners|@members|@nonmembers], menu, template, visualize"
                    + " <região|off>, entry-command <região> add/remove/list/clear [comando completo], cancel, save, reload, status, debug, bypass";
        case "wand" -> {
          p = s.getPlayerOrThrow();
          WANDS.add(p.getUuid());
          p.giveItemStack(new ItemStack(Items.WOODEN_AXE));
          result = GuardConfig.message("wand");
        }
        case "pos1", "pos2" -> {
          p = s.getPlayerOrThrow();
          BlockPos target = p.getBlockPos();
          if (a.length > 0) {
            need(a, 3);
            target =
                new BlockPos(
                    Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            new Bounds(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getX(),
                target.getY(),
                target.getZ());
          }
          position(p, target, command.equals("pos1"));
        }
        case "sel" -> {
          need(a, 1);
          if (!a[0].equals("clear")) throw new IllegalArgumentException("Use /sg sel clear");
          forget(s.getPlayerOrThrow().getUuid());
        }
        case "expand", "contract" -> {
          p = s.getPlayerOrThrow();
          var old = selection(p);
          var b = old.bounds();
          need(a, 1);
          if (command.equals("expand") && a[0].equals("vert"))
            b = b.vertical(p.getWorld().getBottomY(), p.getWorld().getTopY() - 1);
          else {
            need(a, 2);
            int n = Integer.parseInt(a[0]);
            if (n < 1 || n > 60_000_000) throw new IllegalArgumentException("Quantidade inválida");
            b = b.resize(command.equals("contract") ? -n : n, a[1]);
          }
          SELECTIONS.put(
              p.getUuid(),
              new Selection(
                  dim,
                  new BlockPos(b.minX(), b.minY(), b.minZ()),
                  new BlockPos(b.maxX(), b.maxY(), b.maxZ())));
        }
        case "define" -> {
          need(a, 1);
          if (ShiroGuard.REGIONS.all().stream()
              .anyMatch(r -> r.dimension().equals(dim) && r.id().equals(a[0])))
            throw new IllegalArgumentException("Região já existe");
          if (ShiroGuard.REGIONS.all().stream().filter(r -> r.dimension().equals(dim)).count()
              >= GuardConfig.options().maxRegionsPerDimension())
            throw new IllegalArgumentException("Limite de regiões por dimensão atingido");
          Bounds b = a[0].equals("__global__") ? null : selection(s.getPlayerOrThrow()).bounds();
          var r = Region.create(a[0], dim, b).withFlags(GuardConfig.defaultFlags());
          if (p != null) r = r.withPerson(p.getUuid(), true, true);
          ShiroGuard.REGIONS.put(r);
          changed = true;
          result = GuardConfig.message("created", a[0]);
        }
        case "list" ->
            result =
                ShiroGuard.REGIONS.all().stream()
                    .filter(r -> r.dimension().equals(dim))
                    .map(Region::id)
                    .sorted()
                    .toList()
                    .toString();
        case "status" -> result = ShiroGuard.status();
        case "debug" ->
            result =
                ShiroGuard.status()
                    + " | Dimensão: "
                    + dim
                    + " | Regiões aqui: "
                    + ShiroGuard.REGIONS
                        .at(
                            dim,
                            BlockPos.ofFloored(s.getPosition()).getX(),
                            BlockPos.ofFloored(s.getPosition()).getY(),
                            BlockPos.ofFloored(s.getPosition()).getZ())
                        .stream()
                        .map(Region::id)
                        .toList();
        case "save" -> {
          ShiroGuard.save();
          result = GuardConfig.message("save-queued");
        }
        case "reload" -> {
          ShiroGuard.reload();
          result = GuardConfig.message("reloaded");
        }
        case "bypass" -> {
          p = s.getPlayerOrThrow();
          if (!ShiroGuard.BYPASS.remove(p.getUuid())) ShiroGuard.BYPASS.add(p.getUuid());
          result = GuardConfig.message("bypass", ShiroGuard.BYPASS.contains(p.getUuid()));
          ShiroGuard.LOG.warn(
              "Bypass alterado: {} ({}) {}", p.getName().getString(), p.getUuid(), result);
        }
        default -> {
          need(a, 1);
          var r = ShiroGuard.REGIONS.get(dim, a[0]);
          switch (command) {
            case "info" -> result = r.toString();
            case "flags" ->
                result =
                    "Ativas: "
                        + r.flags()
                        + " | Disponíveis: "
                        + Arrays.stream(Flag.values())
                            .filter(f -> !f.optional() || ShiroGuard.COBBLEMON)
                            .map(Flag::id)
                            .toList();
            case "remove" -> {
              ShiroGuard.REGIONS.remove(dim, r.id());
              changed = true;
            }
            case "select" -> {
              p = s.getPlayerOrThrow();
              var b = r.bounds();
              if (b == null) throw new IllegalArgumentException("Região global não tem seleção");
              SELECTIONS.put(
                  p.getUuid(),
                  new Selection(
                      dim,
                      new BlockPos(b.minX(), b.minY(), b.minZ()),
                      new BlockPos(b.maxX(), b.maxY(), b.maxZ())));
            }
            case "redefine" -> {
              r = r.withBounds(selection(s.getPlayerOrThrow()).bounds());
              changed = true;
            }
            case "setpriority" -> {
              need(a, 2);
              r = r.withPriority(Integer.parseInt(a[1]));
              changed = true;
            }
            case "setparent" -> {
              need(a, 2);
              r = r.withParent(a[1].equals("none") ? null : a[1]);
              changed = true;
            }
            case "addowner", "removeowner", "addmember", "removemember" -> {
              need(a, 2);
              r = r.withPerson(uuid(s, a[1]), command.endsWith("owner"), command.startsWith("add"));
              changed = true;
            }
            case "flag" -> {
              need(a, 3);
              Flag f = Flag.parse(a[1]);
              if (f.optional() && !ShiroGuard.COBBLEMON)
                throw new IllegalArgumentException("Cobblemon não está instalado");
              var group = Region.Group.ALL;
              int end = a.length;
              if (a[end - 1].startsWith("@")) {
                group = Region.Group.valueOf(a[end - 1].substring(1).toUpperCase(Locale.ROOT));
                end--;
              }
              if (end < 3) throw new IllegalArgumentException("Valor ausente");
              r = r.withFlag(f, group, String.join(" ", Arrays.copyOfRange(a, 2, end)));
              changed = true;
            }
            default -> throw new IllegalArgumentException("Comando desconhecido");
          }
          if (changed && !command.equals("remove")) ShiroGuard.REGIONS.put(r);
        }
      }
      if (changed) {
        ShiroGuard.save();
        if (GuardConfig.options().auditChanges())
          ShiroGuard.LOG.info("{} executou /sg {} {}", s.getName(), command, input);
      }
      final String message = result;
      s.sendFeedback(() -> Text.literal(message), false);
      return 1;
    } catch (Exception e) {
      s.sendError(
          Text.literal(
              GuardConfig.message(
                  "error",
                  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
      return 0;
    }
  }
}

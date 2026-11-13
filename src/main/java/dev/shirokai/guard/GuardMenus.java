package dev.shirokai.guard;

import dev.shirokai.guard.core.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Native chest screens. Every slot, including the player inventory, is read-only. */
public final class GuardMenus {
  @FunctionalInterface
  public interface Click {
    void run(int button) throws Exception;
  }

  private record Entry(ItemStack icon, Click action) {}

  private record Input(String dimension, long expires, Consumer<String> action) {}

  private static final Map<UUID, Input> INPUTS = new HashMap<>();
  private static int generation;

  public static void forget(UUID player) {
    INPUTS.remove(player);
  }

  public static void invalidate() {
    generation++;
    INPUTS.clear();
    var server = ShiroGuard.server();
    if (server != null)
      for (var p : server.getPlayerManager().getPlayerList())
        if (p.currentScreenHandler instanceof View) p.closeHandledScreen();
  }

  public static void register() {
    ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
        (message, sender, params) -> !consume(sender, message.getSignedContent()));
  }

  public static boolean consume(ServerPlayerEntity p, String text) {
    var input = INPUTS.remove(p.getUuid());
    if (input == null) return false;
    p.getServer()
        .execute(
            () -> {
              try {
                if (System.nanoTime() > input.expires()
                    || !input.dimension().equals(ShiroGuard.dimension(p.getWorld())))
                  throw new IllegalArgumentException(GuardConfig.message("expired"));
                if (text.equalsIgnoreCase("cancelar")) {
                  tell(p, GuardConfig.message("cancelled"));
                  return;
                }
                require(p, "admin");
                if (text.isBlank() || text.length() > 2048 || text.chars().anyMatch(c -> c < 32))
                  throw new IllegalArgumentException("Texto inválido");
                input.action().accept(text.strip());
              } catch (Exception e) {
                error(p, e);
              }
            });
    return true;
  }

  public static void prompt(ServerPlayerEntity p, String label, Consumer<String> action) {
    require(p, "admin");
    p.closeHandledScreen();
    INPUTS.put(
        p.getUuid(),
        new Input(ShiroGuard.dimension(p.getWorld()), System.nanoTime() + 60_000_000_000L, action));
    tell(p, GuardConfig.message("input", label));
  }

  private static void require(ServerPlayerEntity p, String node) {
    if (!ShiroGuard.permission(p.getCommandSource(), node))
      throw new IllegalArgumentException(GuardConfig.message("no-permission"));
  }

  private static void tell(ServerPlayerEntity p, String text) {
    p.sendMessage(Text.literal(text), false);
  }

  private static void error(ServerPlayerEntity p, Exception e) {
    tell(
        p,
        GuardConfig.message(
            "error", e.getMessage() == null ? "Falha na operação" : e.getMessage()));
  }

  private static void unchanged(Region r) {
    if (!ShiroGuard.REGIONS.get(r.dimension(), r.id()).equals(r))
      throw new IllegalArgumentException(GuardConfig.message("changed"));
  }

  private static Region get(ServerPlayerEntity p, String id) {
    return ShiroGuard.REGIONS.get(ShiroGuard.dimension(p.getWorld()), id);
  }

  private static boolean command(ServerPlayerEntity p, String command, String args) {
    return GuardCommands.run(p.getCommandSource(), command, args) == 1;
  }

  public static ItemStack icon(Item item, String name, String... lore) {
    var stack = new ItemStack(item);
    stack.set(
        DataComponentTypes.CUSTOM_NAME,
        Text.literal(name).formatted(Formatting.AQUA).styled(s -> s.withItalic(false)));
    if (GuardConfig.menus().lore())
      stack.set(
          DataComponentTypes.LORE,
          new LoreComponent(
              Arrays.stream(lore)
                  .<Text>map(
                      s ->
                          Text.literal(s.length() > 150 ? s.substring(0, 147) + "..." : s)
                              .formatted(Formatting.GRAY)
                              .styled(v -> v.withItalic(false)))
                  .toList()));
    return stack;
  }

  public static final class View extends GenericContainerScreenHandler {
    private final ServerPlayerEntity owner;
    private final String dimension;
    private final int epoch;
    private final Map<Integer, Click> actions = new HashMap<>();

    private View(int sync, PlayerInventory inventory, ServerPlayerEntity p) {
      super(ScreenHandlerType.GENERIC_9X6, sync, inventory, new SimpleInventory(54), 6);
      owner = p;
      dimension = ShiroGuard.dimension(p.getWorld());
      epoch = generation;
      for (int i = 0; i < 54; i++)
        getInventory().setStack(i, icon(Items.CYAN_STAINED_GLASS_PANE, " "));
    }

    public void button(int slot, ItemStack stack, Click click) {
      getInventory().setStack(slot, stack);
      actions.put(slot, click);
    }

    @Override
    public boolean canUse(PlayerEntity p) {
      return p == owner
          && p.isAlive()
          && epoch == generation
          && dimension.equals(ShiroGuard.dimension(p.getWorld()))
          && ShiroGuard.permission(owner.getCommandSource(), "admin");
    }

    @Override
    public ItemStack quickMove(PlayerEntity p, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity p) {
      if (p != owner || owner.currentScreenHandler != this) return;
      if (!canUse(p)) {
        owner.closeHandledScreen();
        return;
      }
      if (type != SlotActionType.PICKUP || (button != 0 && button != 1)) return;
      var action = actions.get(slot);
      if (action == null) return;
      try {
        action.run(button);
      } catch (Exception e) {
        error(owner, e);
      }
    }
  }

  private static View open(ServerPlayerEntity p, String title, Runnable back) {
    require(p, "admin");
    forget(p.getUuid());
    if (!p.currentScreenHandler.getCursorStack().isEmpty())
      throw new IllegalArgumentException("Guarde o item do cursor antes de abrir o menu");
    p.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (sync, inventory, player) -> new View(sync, inventory, p),
            Text.literal(title.equals("Administração") ? GuardConfig.menus().title() : "✦ " + title)
                .formatted(Formatting.AQUA)));
    if (!(p.currentScreenHandler instanceof View view))
      throw new IllegalStateException("Não foi possível abrir o menu");
    view.button(46, icon(Items.NETHER_STAR, "INÍCIO"), b -> home(p));
    view.button(48, icon(Items.ARROW, "VOLTAR"), b -> back.run());
    view.button(
        49,
        icon(Items.ARROW, "VOLTAR AO MENU", "Abre /menu"),
        b -> {
          p.closeHandledScreen();
          p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(), "menu");
        });
    return view;
  }

  private static View page(
      ServerPlayerEntity p,
      String title,
      List<Entry> entries,
      int requested,
      Runnable back,
      Consumer<Integer> navigate) {
    int size = GuardConfig.menus().pageSize(),
        pages = Math.max(1, (entries.size() + size - 1) / size),
        page = Math.clamp(requested, 0, pages - 1);
    var view = open(p, title + " " + (page + 1) + "/" + pages, back);
    for (int i = page * size; i < Math.min(entries.size(), (page + 1) * size); i++) {
      var e = entries.get(i);
      view.button(i - page * size, e.icon(), e.action());
    }
    if (page > 0)
      view.button(45, icon(Items.ARROW, "PÁGINA ANTERIOR"), b -> navigate.accept(page - 1));
    if (page + 1 < pages)
      view.button(53, icon(Items.ARROW, "PRÓXIMA PÁGINA"), b -> navigate.accept(page + 1));
    return view;
  }

  public static void home(ServerPlayerEntity p) {
    var view = open(p, "Administração", () -> p.closeHandledScreen());
    view.button(
        10,
        icon(Items.FILLED_MAP, "REGIÕES", "Dimensão: " + ShiroGuard.dimension(p.getWorld())),
        b -> regions(p, 0));
    view.button(
        12,
        icon(Items.WOODEN_AXE, "SELEÇÃO", "Receber ferramenta de seleção"),
        b -> command(p, "wand", ""));
    view.button(14, icon(Items.COMPARATOR, "CONFIGURAÇÕES"), b -> settings(p));
    view.button(
        16,
        icon(Items.CLOCK, "STATUS", ShiroGuard.status()),
        b -> {
          tell(p, ShiroGuard.status());
          home(p);
        });
    view.button(
        28,
        icon(
            Items.ENDER_EYE,
            "VISUALIZAR REGIÕES AQUI",
            "Exibe limites próximos sem carregar chunks"),
        b -> command(p, "visualize", "here"));
    view.button(30, icon(Items.HOPPER, "SALVAR"), b -> command(p, "save", ""));
    view.button(
        32,
        icon(Items.REPEATER, "RECARREGAR"),
        b -> {
          if (command(p, "reload", "")) home(p);
        });
    view.button(
        34,
        icon(
            Items.IRON_CHESTPLATE,
            "BYPASS",
            "Estado: " + ShiroGuard.bypass(p),
            "Exige permissão; alteração registrada"),
        b -> {
          command(p, "bypass", "");
          home(p);
        });
  }

  public static void regions(ServerPlayerEntity p, int page) {
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            icon(Items.EMERALD, "CRIAR REGIÃO", "Usa sua seleção atual"),
            b ->
                prompt(
                    p,
                    "Nome da nova região",
                    id -> {
                      if (command(p, "define", id)) region(p, id);
                    })));
    ShiroGuard.REGIONS.all().stream()
        .filter(r -> r.dimension().equals(ShiroGuard.dimension(p.getWorld())))
        .sorted(Comparator.comparing(Region::id))
        .forEach(
            r ->
                entries.add(
                    new Entry(
                        icon(
                            Items.MAP,
                            r.id(),
                            "Prioridade: " + r.priority(),
                            "Owners: " + r.owners().size() + " · Members: " + r.members().size()),
                        b -> region(p, r.id()))));
    page(p, "Regiões", entries, page, () -> home(p), n -> regions(p, n));
  }

  public static void region(ServerPlayerEntity p, String id) {
    var r = get(p, id);
    var view = open(p, id, () -> regions(p, 0));
    view.button(
        10,
        icon(Items.REDSTONE_TORCH, "FLAGS", "Regras por grupo"),
        b -> flags(p, id, Region.Group.ALL, 0));
    view.button(
        12,
        icon(Items.PLAYER_HEAD, "OWNERS", "Gerenciar responsáveis"),
        b -> people(p, id, true, 0));
    view.button(
        14, icon(Items.PLAYER_HEAD, "MEMBERS", "Gerenciar membros"), b -> people(p, id, false, 0));
    view.button(
        16,
        icon(Items.COMPARATOR, "PRIORIDADE: " + r.priority(), "Esquerdo: +1 · direito: -1"),
        b -> {
          unchanged(r);
          if (command(p, "setpriority", id + " " + Math.addExact(r.priority(), b == 0 ? 1 : -1)))
            region(p, id);
        });
    view.button(
        28,
        icon(Items.CHAIN, "REGIÃO PAI: " + (r.parent() == null ? "nenhuma" : r.parent())),
        b -> parents(p, id, 0));
    view.button(
        30,
        icon(Items.BOOK, "TEMPLATES", "Prévia e confirmação antes de aplicar"),
        b -> templates(p, id, 0));
    view.button(32, icon(Items.ENDER_EYE, "VISUALIZAR LIMITES"), b -> command(p, "visualize", id));
    view.button(
        34,
        icon(
            Items.WOODEN_AXE,
            "SELECIONAR / REDEFINIR",
            "Esquerdo: selecionar região",
            "Direito: usar seleção atual para redefinir"),
        b -> {
          if (b == 0) command(p, "select", id);
          else
            confirm(
                p,
                "Redefinir " + id,
                "Substituir limites pela seleção atual",
                () -> {
                  unchanged(r);
                  command(p, "redefine", id);
                },
                () -> region(p, id));
        });
    view.button(
        40,
        icon(Items.BARRIER, "REMOVER REGIÃO", "Exige confirmação"),
        b ->
            confirm(
                p,
                "Remover " + id,
                "Esta ação remove as regras da região",
                () -> {
                  unchanged(r);
                  if (command(p, "remove", id)) regions(p, 0);
                },
                () -> region(p, id)));
  }

  public static void flags(ServerPlayerEntity p, String id, Region.Group group, int page) {
    var r = get(p, id);
    var entries = new ArrayList<Entry>();
    for (var flag : Flag.values())
      if (!flag.optional() || ShiroGuard.COBBLEMON) {
        String value = r.flags().getOrDefault(flag, Map.of()).getOrDefault(group, "unset");
        entries.add(
            new Entry(
                icon(
                    value.equals("deny")
                        ? Items.RED_DYE
                        : value.equals("allow") ? Items.LIME_DYE : Items.GRAY_DYE,
                    flag.id(),
                    "Grupo: " + group,
                    "Valor local: " + value,
                    flag.text() || flag.list()
                        ? "Esquerdo: editar · direito: unset"
                        : "Clique para alternar unset / allow / deny"),
                button -> {
                  require(p, "region.flag");
                  unchanged(r);
                  if ((flag.text() || flag.list()) && button == 0)
                    prompt(
                        p,
                        "Valor de " + flag.id(),
                        text -> {
                          unchanged(r);
                          if (command(
                              p,
                              "flag",
                              id
                                  + " "
                                  + flag.id()
                                  + " "
                                  + text
                                  + " @"
                                  + group.name().toLowerCase(Locale.ROOT)))
                            flags(p, id, group, page);
                        });
                  else {
                    String next =
                        flag.text() || flag.list()
                            ? "unset"
                            : value.equals("unset")
                                ? "allow"
                                : value.equals("allow") ? "deny" : "unset";
                    if (command(
                        p,
                        "flag",
                        id
                            + " "
                            + flag.id()
                            + " "
                            + next
                            + " @"
                            + group.name().toLowerCase(Locale.ROOT))) flags(p, id, group, page);
                  }
                }));
      }
    var view =
        page(
            p, "Flags · " + group, entries, page, () -> region(p, id), n -> flags(p, id, group, n));
    view.button(
        47,
        icon(Items.NAME_TAG, "GRUPO: " + group, "Clique para trocar"),
        b ->
            flags(
                p,
                id,
                Region.Group.values()[(group.ordinal() + 1) % Region.Group.values().length],
                0));
  }

  public static void people(ServerPlayerEntity p, String id, boolean owner, int page) {
    var r = get(p, id);
    var entries = new ArrayList<Entry>();
    String type = owner ? "owner" : "member";
    entries.add(
        new Entry(
            icon(
                Items.EMERALD, "ADICIONAR " + type.toUpperCase(Locale.ROOT), "Nome online ou UUID"),
            b ->
                prompt(
                    p,
                    "Jogador online ou UUID",
                    text -> {
                      unchanged(r);
                      if (command(p, "add" + type, id + " " + text)) people(p, id, owner, 0);
                    })));
    (owner ? r.owners() : r.members())
        .stream()
            .sorted()
            .forEach(
                uuid -> {
                  var player = p.getServer().getPlayerManager().getPlayer(uuid);
                  var name = player == null ? uuid.toString() : player.getName().getString();
                  entries.add(
                      new Entry(
                          icon(Items.PLAYER_HEAD, name, uuid.toString(), "Clique para remover"),
                          b ->
                              confirm(
                                  p,
                                  "Remover " + type,
                                  name,
                                  () -> {
                                    unchanged(r);
                                    if (command(p, "remove" + type, id + " " + uuid))
                                      people(p, id, owner, 0);
                                  },
                                  () -> people(p, id, owner, page))));
                });
    page(
        p,
        owner ? "Owners" : "Members",
        entries,
        page,
        () -> region(p, id),
        n -> people(p, id, owner, n));
  }

  private static void parents(ServerPlayerEntity p, String id, int page) {
    var r = get(p, id);
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            icon(Items.BARRIER, "SEM PAI"),
            b -> {
              unchanged(r);
              if (command(p, "setparent", id + " none")) region(p, id);
            }));
    ShiroGuard.REGIONS.all().stream()
        .filter(parent -> parent.dimension().equals(r.dimension()) && !parent.id().equals(id))
        .sorted(Comparator.comparing(Region::id))
        .forEach(
            parent ->
                entries.add(
                    new Entry(
                        icon(Items.CHAIN, parent.id()),
                        b -> {
                          unchanged(r);
                          if (command(p, "setparent", id + " " + parent.id())) region(p, id);
                        })));
    page(p, "Herança", entries, page, () -> region(p, id), n -> parents(p, id, n));
  }

  public static void templates(ServerPlayerEntity p, String id, int page) {
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            icon(
                Items.WRITABLE_BOOK,
                "SALVAR REGIÃO COMO TEMPLATE",
                "Não sobrescreve templates existentes"),
            b ->
                prompt(
                    p,
                    "Nome do novo template",
                    text -> {
                      if (command(p, "template", "save " + text + " " + id)) templates(p, id, 0);
                    })));
    GuardConfig.templates().values().stream()
        .sorted(Comparator.comparing(RegionTemplate::id))
        .forEach(
            t ->
                entries.add(
                    new Entry(
                        icon(Items.BOOK, t.id(), t.description(), "Clique para revisar alterações"),
                        b -> {
                          var plan = TemplateActions.preview(p, id, t.id());
                          templateConfirm(p, plan);
                        })));
    page(p, "Templates", entries, page, () -> region(p, id), n -> templates(p, id, n));
  }

  private static void templateConfirm(ServerPlayerEntity p, TemplateActions.Plan plan) {
    var view =
        open(p, "Confirmar " + plan.template().id(), () -> templates(p, plan.before().id(), 0));
    int slot = 0;
    for (var f : plan.template().flags().entrySet())
      if (slot < 36) {
        boolean optional = f.getKey().optional() && !ShiroGuard.COBBLEMON;
        view.button(
            slot++,
            icon(
                Items.PAPER,
                f.getKey().id(),
                optional
                    ? "Não aplicado: Cobblemon ausente"
                    : "Antes: " + plan.before().flags().getOrDefault(f.getKey(), Map.of()),
                "Modelo: " + f.getValue()),
            b -> {});
      }
    view.button(
        40,
        icon(
            Items.LIME_DYE,
            "APLICAR TEMPLATE",
            "As flags listadas serão substituídas",
            "As demais configurações serão preservadas"),
        b -> {
          TemplateActions.confirm(p, plan.token());
          region(p, plan.before().id());
        });
  }

  private static void confirm(
      ServerPlayerEntity p, String title, String description, ClickAction action, Runnable back) {
    var view = open(p, title, back);
    view.button(
        20,
        icon(Items.LIME_DYE, "CONFIRMAR", description),
        b -> {
          action.run();
          if (p.currentScreenHandler == view) back.run();
        });
    view.button(24, icon(Items.RED_DYE, "CANCELAR"), b -> back.run());
  }

  @FunctionalInterface
  private interface ClickAction {
    void run() throws Exception;
  }

  public static void settings(ServerPlayerEntity p) {
    var o = GuardConfig.options();
    var view = open(p, "Configurações", () -> home(p));
    view.button(
        10,
        icon(Items.END_ROD, "PARTÍCULAS: " + o.selectionParticles()),
        b -> {
          GuardConfig.saveOptions(
              new GuardConfig.Options(
                  !o.selectionParticles(),
                  o.auditChanges(),
                  o.maxRegionsPerDimension(),
                  o.particleBudget(),
                  o.queryCacheEntries()));
          settings(p);
        });
    view.button(
        12,
        icon(Items.WRITABLE_BOOK, "AUDITORIA: " + o.auditChanges()),
        b -> {
          GuardConfig.saveOptions(
              new GuardConfig.Options(
                  o.selectionParticles(),
                  !o.auditChanges(),
                  o.maxRegionsPerDimension(),
                  o.particleBudget(),
                  o.queryCacheEntries()));
          settings(p);
        });
    view.button(
        14,
        icon(Items.MAP, "LIMITE DE REGIÕES: " + o.maxRegionsPerDimension()),
        b -> prompt(p, "Limite por dimensão (1..100000)", text -> option(p, 0, text)));
    view.button(
        16,
        icon(Items.REPEATER, "CACHE: " + o.queryCacheEntries()),
        b -> prompt(p, "Entradas de cache (0..65536)", text -> option(p, 1, text)));
    view.button(
        28,
        icon(Items.END_ROD, "ORÇAMENTO DE PARTÍCULAS: " + o.particleBudget()),
        b -> prompt(p, "Partículas por jogador/segundo (24..512)", text -> option(p, 2, text)));
    view.button(
        30,
        icon(Items.IRON_CHESTPLATE, "PERMISSÕES", "Consultar fallback OP"),
        b -> permissions(p));
    view.button(
        32,
        icon(Items.COMPASS, "DIAGNÓSTICO", ShiroGuard.status()),
        b -> tell(p, ShiroGuard.status()));
  }

  private static void option(ServerPlayerEntity p, int index, String text) {
    try {
      require(p, "admin");
      var o = GuardConfig.options();
      int n = Integer.parseInt(text);
      GuardConfig.saveOptions(
          new GuardConfig.Options(
              o.selectionParticles(),
              o.auditChanges(),
              index == 0 ? n : o.maxRegionsPerDimension(),
              index == 2 ? n : o.particleBudget(),
              index == 1 ? n : o.queryCacheEntries()));
      settings(p);
    } catch (Exception e) {
      error(p, e);
    }
  }

  private static void permissions(ServerPlayerEntity p) {
    var entries = new ArrayList<Entry>();
    for (var node :
        List.of(
            "admin",
            "region.create",
            "region.delete",
            "region.flag",
            "region.member",
            "region.info",
            "bypass"))
      entries.add(
          new Entry(
              icon(
                  Items.IRON_NUGGET,
                  "shirokaiguard." + node,
                  "Fallback OP: " + GuardConfig.fallback(node),
                  "Negativas do provedor sempre prevalecem"),
              b ->
                  tell(
                      p,
                      "Edite fallbackOpLevels em permissions.json e use /sg reload. -1 exige"
                          + " permissão explícita; 0..4 define nível OP.")));
    page(p, "Permissões", entries, 0, () -> settings(p), n -> permissions(p));
  }
}

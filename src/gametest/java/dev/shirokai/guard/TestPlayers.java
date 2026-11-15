package dev.shirokai.guard;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;

/** Real game-mode behavior, unlike the vanilla mock which always reports creative. */
final class TestPlayers {
  static ServerPlayerEntity create(TestContext ctx) { return create(ctx,null); }

  static ServerPlayerEntity create(TestContext ctx, java.util.function.Consumer<net.minecraft.text.Text> observer) {
    var profile =
        new GameProfile(
            UUID.randomUUID(), "sg-test-" + UUID.randomUUID().toString().substring(0, 6));
    // A real connection preloads LuckPerms during login. Embedded channels skip that stage.
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")) {
      try {
        var provider =
            Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
        var manager =
            Class.forName("net.luckperms.api.LuckPerms")
                .getMethod("getUserManager")
                .invoke(provider);
        ((java.util.concurrent.CompletableFuture<?>)
                Class.forName("net.luckperms.api.model.user.UserManager")
                    .getMethod("loadUser", java.util.UUID.class)
                    .invoke(manager, profile.getId()))
            .join();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to prepare actual LuckPerms login", e);
      }
    }
    var data = ConnectedClientData.createDefault(profile, false);
    var player =
        observer == null ? new ServerPlayerEntity(
            ctx.getWorld().getServer(), ctx.getWorld(), profile, data.syncedOptions())
        : new ServerPlayerEntity(ctx.getWorld().getServer(),ctx.getWorld(),profile,data.syncedOptions()) {
          @Override public void sendMessage(net.minecraft.text.Text text,boolean overlay) {
            observer.accept(text);
            super.sendMessage(text,overlay);
          }
        };
    var connection = new ClientConnection(NetworkSide.SERVERBOUND);
    new EmbeddedChannel(connection);
    ctx.getWorld().getServer().getPlayerManager().onPlayerConnect(connection, player, data);
    player.changeGameMode(GameMode.CREATIVE);
    return player;
  }
}

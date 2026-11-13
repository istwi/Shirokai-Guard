package dev.shirokai.guard.clientcheck;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Offline loopback-only client fixture, never included in the Guard mod. */
public final class VisualClient implements ClientModInitializer {
  private int ticks, stage, last;
  private boolean connected;

  private void require(boolean ok, String msg) {
    if (!ok) throw new IllegalStateException(msg);
  }

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          try {
            ticks++;
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            client.getToastManager().clear();
            client.mouse.unlockCursor();
            if (ticks > 2400)
              throw new IllegalStateException("Visual test timed out at stage " + stage);
            if (!connected
                && client.getOverlay() == null
                && client.currentScreen != null
                && ticks > 60) {
              connected = true;
              ConnectScreen.connect(
                  client.currentScreen,
                  client,
                  ServerAddress.parse("127.0.0.1:25567"),
                  new ServerInfo(
                      "Guard validation", "127.0.0.1:25567", ServerInfo.ServerType.OTHER),
                  false,
                  null);
            }
            if (stage == 13 && ticks - last > 40) {
              client.scheduleStop();
              return;
            }
            if (client.player == null || client.getNetworkHandler() == null || ticks - last < 20)
              return;
            if (stage == 0) {
              client.getNetworkHandler().sendChatCommand("sg menu");
              stage = 1;
              last = ticks;
              return;
            }
            if (!(client.currentScreen instanceof GenericContainerScreen screen)) return;
            var handler = screen.getScreenHandler();
            if (stage == 1) {
              require(
                  screen.getTitle().getString().contains("SHIROKAI GUARD"),
                  "Admin menu title missing");
              require(
                  handler.getSlot(49).getStack().isOf(Items.ARROW),
                  "Back arrow missing on real client");
              ScreenshotRecorder.saveScreenshot(
                  client.runDirectory, "01-admin.png", client.getFramebuffer(), text -> {});
              client.interactionManager.clickSlot(
                  handler.syncId, 10, 0, SlotActionType.PICKUP, client.player);
              stage = 2;
              last = ticks;
              return;
            }
            if (stage == 2) {
              require(
                  screen.getTitle().getString().contains("Regiões"),
                  "Region list navigation failed");
              ScreenshotRecorder.saveScreenshot(
                  client.runDirectory, "02-regions.png", client.getFramebuffer(), text -> {});
              client.interactionManager.clickSlot(
                  handler.syncId, 1, 0, SlotActionType.PICKUP, client.player);
              stage = 3;
              last = ticks;
              return;
            }
            if (stage == 3) {
              require(
                  screen.getTitle().getString().contains("visual"), "Region detail did not open");
              client.interactionManager.clickSlot(
                  handler.syncId, 10, 0, SlotActionType.PICKUP, client.player);
              stage = 4;
              last = ticks;
              return;
            }
            if (stage == 4) {
              require(
                  screen.getTitle().getString().contains("Flags"), "Flag screen navigation failed");
              ScreenshotRecorder.saveScreenshot(
                  client.runDirectory, "03-flags.png", client.getFramebuffer(), text -> {});
              client.interactionManager.clickSlot(
                  handler.syncId, 0, 0, SlotActionType.THROW, client.player);
              stage = 5;
              last = ticks;
              return;
            }
            if (stage == 5) {
              require(
                  handler.getCursorStack().isEmpty(), "Server left an icon on the client cursor");
              require(
                  !handler.getSlot(0).getStack().isEmpty(),
                  "An icon disappeared on the real client");
              client.interactionManager.clickSlot(
                  handler.syncId, 48, 0, SlotActionType.PICKUP, client.player);
              stage = 6;
              last = ticks;
              return;
            }
            if (stage == 6) {
              require(screen.getTitle().getString().contains("visual"), "Back navigation failed");
              client.interactionManager.clickSlot(
                  handler.syncId, 30, 0, SlotActionType.PICKUP, client.player);
              stage = 7;
              last = ticks;
              return;
            }
            if (stage == 7) {
              require(
                  screen.getTitle().getString().contains("Templates"), "Templates screen failed");
              client.interactionManager.clickSlot(
                  handler.syncId, 1, 0, SlotActionType.PICKUP, client.player);
              stage = 8;
              last = ticks;
              return;
            }
            if (stage == 8) {
              require(
                  screen.getTitle().getString().contains("Confirmar"), "Template preview missing");
              ScreenshotRecorder.saveScreenshot(
                  client.runDirectory,
                  "04-template-preview.png",
                  client.getFramebuffer(),
                  text -> {});
              client.interactionManager.clickSlot(
                  handler.syncId, 40, 0, SlotActionType.PICKUP, client.player);
              stage = 9;
              last = ticks;
              return;
            }
            if (stage == 9) {
              require(
                  screen.getTitle().getString().contains("visual"),
                  "Template confirmation did not return to region");
              client.interactionManager.clickSlot(
                  handler.syncId, 46, 0, SlotActionType.PICKUP, client.player);
              stage = 10;
              last = ticks;
              return;
            }
            if (stage == 10) {
              require(
                  screen.getTitle().getString().contains("SHIROKAI GUARD"),
                  "Home navigation failed");
              client.interactionManager.clickSlot(
                  handler.syncId, 14, 0, SlotActionType.PICKUP, client.player);
              stage = 11;
              last = ticks;
              return;
            }
            if (stage == 11) {
              require(
                  screen.getTitle().getString().contains("Configurações"), "Settings menu missing");
              ScreenshotRecorder.saveScreenshot(
                  client.runDirectory, "05-settings.png", client.getFramebuffer(), text -> {});
              client.interactionManager.clickSlot(
                  handler.syncId, 10, 0, SlotActionType.PICKUP, client.player);
              stage = 12;
              last = ticks;
              return;
            }
            if (stage == 12) {
              require(
                  handler.getSlot(10).getStack().getName().getString().contains("false"),
                  "Settings click did not persist and refresh");
              Files.writeString(
                  Path.of("../client-result.txt"),
                  "PASS: real Minecraft client; admin, regions, flags, template"
                      + " preview/confirmation, settings persistence, navigation, native icon throw"
                      + " rejection; five screenshots saved\n");
              stage = 13;
              last = ticks;
              client.getNetworkHandler().sendChatCommand("sg visualdone");
              return;
            }
          } catch (Throwable e) {
            try {
              Files.writeString(Path.of("../client-result.txt"), "FAIL: " + e);
            } catch (Exception ignored) {
            }
            e.printStackTrace();
            client.scheduleStop();
          }
        });
  }
}

package dev.shirokai.guard.mixin;

import com.mojang.brigadier.ParseResults;
import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.server.command.*;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(CommandManager.class)
public abstract class CommandMixin {
  @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "execute")
  private void guard$context(ParseResults<ServerCommandSource> parsed, String command,
      com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
    CommandEntryAccess.execute(parsed.getContext().getSource(), command, () -> original.call(parsed,command));
  }

  @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
  private void guard$command(
      ParseResults<ServerCommandSource> parsed, String command, CallbackInfo ci) {
    var s = parsed.getContext().getSource();
    var p = s.getPlayer();
    if (p == null || ShiroGuard.bypass(p) || GuardCommands.isBypassRecovery(parsed, command)) return;
    var pos = p.getBlockPos();
    if (!ShiroGuard.REGIONS.commandAllowed(
        ShiroGuard.dimension(p.getWorld()),
        pos.getX(),
        pos.getY(),
        pos.getZ(),
        p.getUuid(),
        command)) {
      s.sendError(Text.literal(GuardConfig.message("command-denied")));
      ci.cancel();
    }
  }
}

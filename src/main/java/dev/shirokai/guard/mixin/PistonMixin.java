package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PistonBlock.class)
public abstract class PistonMixin {
  @Inject(method = "move", at = @At("HEAD"), cancellable = true)
  private void guard$piston(
      World world,
      BlockPos pos,
      Direction dir,
      boolean extend,
      CallbackInfoReturnable<Boolean> cir) {
    if (!permitted(world, pos) || !permitted(world, pos.offset(dir))) {
      cir.setReturnValue(false);
      return;
    }
    var handler = new PistonHandler(world, pos, dir, extend);
    if (!handler.calculatePush()) return;
    Direction movement = extend ? dir : dir.getOpposite();
    for (var p : handler.getMovedBlocks())
      if (!permitted(world, p) || !permitted(world, p.offset(movement))) {
        cir.setReturnValue(false);
        return;
      }
    for (var p : handler.getBrokenBlocks())
      if (!permitted(world, p)) {
        cir.setReturnValue(false);
        return;
      }
  }

  @Unique
  private static boolean permitted(World world, BlockPos pos) {
    return !dev.shirokai.guard.compat.CratesCompat.registered(world, pos)
        && Protection.blocksAllowed(world, pos, world.getBlockState(pos), Flag.PISTON, null)
        && Protection.blocksAllowed(world, pos, world.getBlockState(pos), Flag.BUILD, null);
  }
}

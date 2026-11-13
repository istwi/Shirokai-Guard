package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(HopperBlockEntity.class)
public abstract class HopperMixin {
  @Shadow private Direction facing;

  @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
  private static void guard$insert(
      World w, BlockPos p, HopperBlockEntity be, CallbackInfoReturnable<Boolean> cir) {
    Direction direction = ((HopperMixin) (Object) be).facing;
    if (!permitted(w, p) || !permitted(w, p.offset(direction))) cir.setReturnValue(false);
  }

  @Inject(
      method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private static void guard$extract(World w, Hopper h, CallbackInfoReturnable<Boolean> cir) {
    var p = BlockPos.ofFloored(h.getHopperX(), h.getHopperY(), h.getHopperZ());
    if (!permitted(w, p) || !permitted(w, p.up())) cir.setReturnValue(false);
  }

  @Inject(
      method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private static void guard$loose(
      net.minecraft.inventory.Inventory inventory,
      net.minecraft.entity.ItemEntity item,
      CallbackInfoReturnable<Boolean> cir) {
    if (!permitted(item.getWorld(), item.getBlockPos())) {
      cir.setReturnValue(false);
      return;
    }
    if (inventory instanceof net.minecraft.block.entity.BlockEntity be
        && !permitted(item.getWorld(), be.getPos())) cir.setReturnValue(false);
    if (inventory instanceof net.minecraft.entity.Entity e
        && !permitted(item.getWorld(), e.getBlockPos())) cir.setReturnValue(false);
  }

  @Unique
  private static boolean permitted(World w, BlockPos p) {
    return ShiroGuard.allowed(w, p, Flag.HOPPER, null)
        && ShiroGuard.allowed(w, p, Flag.CHEST_ACCESS, null);
  }
}

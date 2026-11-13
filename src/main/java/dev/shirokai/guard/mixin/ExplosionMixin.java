package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
  @Shadow @Final private World world;
  @Shadow @Final private Entity entity;

  @Inject(method = "affectWorld", at = @At("HEAD"))
  private void guard$blocks(boolean particles, CallbackInfo ci) {
    var explosion = (Explosion) (Object) this;
    var flag =
        entity instanceof CreeperEntity
            ? Flag.CREEPER_EXPLOSION
            : entity instanceof TntEntity ? Flag.TNT : Flag.OTHER_EXPLOSION;
    explosion
        .getAffectedBlocks()
        .removeIf(
            p ->
                dev.shirokai.guard.compat.CratesCompat.registered(world, p)
                    || !Protection.blocksAllowed(world, p, world.getBlockState(p), flag, null)
                    || !Protection.blocksAllowed(
                        world, p, world.getBlockState(p), Flag.BLOCK_BREAK, null));
  }
}

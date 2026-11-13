package dev.shirokai.guard.mixin;

import dev.shirokai.guard.*;
import dev.shirokai.guard.core.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerWorld.class)
public abstract class SpawnMixin {
  @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
  private void guard$spawn(Entity e, CallbackInfoReturnable<Boolean> cir) {
    var w = (ServerWorld) (Object) this;
    var p =
        e instanceof ProjectileEntity projectile
            ? Protection.actor(projectile.getOwner())
            : ShiroGuard.COBBLEMON ? dev.shirokai.guard.compat.CobblemonCompat.spawnActor(e) : null;
    if (ShiroGuard.COBBLEMON
            && dev.shirokai.guard.compat.CobblemonCompat.pokemon(e)
            && !ShiroGuard.allowed(w, e.getBlockPos(), Flag.POKEMON_SPAWN, p)
        || !ShiroGuard.allowed(w, e.getBlockPos(), Flag.ENTITY_SPAWNING, p)
        || e instanceof MobEntity
            && !ShiroGuard.allowed(w, e.getBlockPos(), Flag.MOB_SPAWNING, null)
        || e instanceof ProjectileEntity
            && !ShiroGuard.allowed(w, e.getBlockPos(), Flag.PROJECTILE, p))
      cir.setReturnValue(false);
  }
}

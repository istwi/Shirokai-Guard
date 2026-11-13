package dev.shirokai.guard.mixin.compat;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.shirokai.guard.*;
import dev.shirokai.guard.core.Flag;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = PokemonPastureBlockEntity.class, remap = false)
public abstract class PastureMixin {
  @Inject(method = "tether", at = @At("HEAD"), cancellable = true, remap = false)
  private void guard$tether(
      ServerPlayerEntity p, Pokemon pokemon, Direction dir, CallbackInfoReturnable<Boolean> cir) {
    var be = (BlockEntity) (Object) this;
    if (be.getWorld() != null
        && !ShiroGuard.allowed(be.getWorld(), be.getPos(), Flag.POKEMON_PASTURE, p))
      cir.setReturnValue(false);
  }
}

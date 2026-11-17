package dev.shirokai.guard.mixin.flan;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.shirokai.guard.compat.FlanClaimProtection;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "io.github.flemmli97.flan.claim.ClaimStorage", remap = false)
public abstract class ClaimStorageMixin {
  @WrapMethod(method = "createClaim", remap = false)
  private boolean guard$create(BlockPos first, BlockPos second, ServerPlayerEntity player,
      Operation<Boolean> original) {
    try { return original.call(first, second, player); }
    catch (FlanClaimProtection.Denied denied) { return false; }
  }

  @WrapMethod(method = "resizeClaim", remap = false)
  private boolean guard$resize(@Coerce Object claim, BlockPos from, BlockPos to,
      ServerPlayerEntity player, Operation<Boolean> original) {
    try { return original.call(claim, from, to, player); }
    catch (FlanClaimProtection.Denied denied) { return false; }
  }

  @WrapOperation(method = {"createClaim", "resizeClaim"}, at = @At(value = "INVOKE",
      target = "Lio/github/flemmli97/flan/claim/ClaimStorage;conflicts(Lio/github/flemmli97/flan/claim/Claim;Lio/github/flemmli97/flan/claim/Claim;)Ljava/util/Set;",
      remap = false), remap = false)
  private Set<?> guard$check(@Coerce Object storage, @Coerce Object candidate,
      @Coerce Object except, Operation<Set<?>> original,
      @Local(argsOnly = true) ServerPlayerEntity player) {
    FlanClaimProtection.check(candidate, player);
    return original.call(storage, candidate, except);
  }
}

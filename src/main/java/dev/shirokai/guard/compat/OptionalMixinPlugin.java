package dev.shirokai.guard.compat;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class OptionalMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String p) {}

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    return !mixin.contains(".compat.") || FabricLoader.getInstance().isModLoaded("cobblemon");
  }

  public void acceptTargets(Set<String> a, Set<String> b) {}

  public List<String> getMixins() {
    return null;
  }

  public void preApply(String t, ClassNode n, String m, IMixinInfo i) {}

  public void postApply(String t, ClassNode n, String m, IMixinInfo i) {}
}

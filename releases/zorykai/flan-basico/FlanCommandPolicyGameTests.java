package dev.shirokai.guard;
import com.mojang.brigadier.tree.CommandNode;
import io.github.flemmli97.flan.commands.CommandClaim;
import io.github.flemmli97.flan.config.ConfigHandler;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.*;
import java.util.*;
public class FlanCommandPolicyGameTests implements FabricGameTest {
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void originalCreationPermissionPreserved(TestContext ctx) {
  var player=TestPlayers.create(ctx);
  PermissionCheckEvent.EVENT.register((source,node)->source instanceof ServerCommandSource s
      && s.getPlayer()==player && node.equals("flan.claim.create") ? TriState.FALSE:TriState.DEFAULT);
  var add=ctx.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("flan").getChild("add");
  ctx.assertFalse(add.canUse(player.getCommandSource().withLevel(0)),"Original creation denial was overridden");
  ctx.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void commandPolicy(TestContext ctx) {
  var player=TestPlayers.create(ctx);var normal=player.getCommandSource().withLevel(0);
  var admin=player.getCommandSource().withLevel(ConfigHandler.CONFIG.permissionLevel);
  // Explicit provider grants cannot unlock the admin-only command families.
  PermissionCheckEvent.EVENT.register((source,node)->source instanceof ServerCommandSource s && s.getPlayer()==player ? TriState.TRUE:TriState.DEFAULT);
  var tree=ctx.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("flan");
  ctx.assertTrue(tree!=null,"Flan not registered");
  Set<String> basic=Set.of("claim","add","group","info","switchMode","permission","confirm","delete","deleteAll","deleteSubClaim","deleteAllSubClaims","expand","list","name","menu","personalGroups","help","?");
  int available=0,restricted=0;
  for(var node:tree.getChildren()) {
   boolean isBasic=basic.contains(node.getName());
   System.out.println("POLICY "+node.getName()+" normal="+node.canUse(normal)+" admin="+node.canUse(admin));
   if(isBasic) {ctx.assertTrue(node.canUse(normal),"Basic command lost: "+node.getName());available++;}
   else {ctx.assertFalse(node.canUse(normal),"Extra command exposed: "+node.getName());ctx.assertTrue(node.canUse(admin),"Admin lost command: "+node.getName());restricted++;}
  }
  ctx.assertTrue(available>=10,"Missing basic command registrations");
  ctx.assertTrue(restricted>=10,"Not enough restricted commands tested");
  ctx.complete();
 }
}

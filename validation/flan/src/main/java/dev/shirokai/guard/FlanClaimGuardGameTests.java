package dev.shirokai.guard;
import dev.shirokai.guard.core.*;
import io.github.flemmli97.flan.claim.*;
import io.github.flemmli97.flan.player.PlayerClaimData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import net.minecraft.text.Text;
public class FlanClaimGuardGameTests implements FabricGameTest {
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void containingClaimDenied(TestContext ctx) {
  var messages=new ArrayList<Text>(); var p=TestPlayers.create(ctx,messages::add);
  var a=ctx.getAbsolutePos(new BlockPos(0,1,0)); var b=a.add(20,20,20);
  var r=Region.create("flan_guard_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
   new Bounds(a.getX()+5,a.getY()+5,a.getZ()+5,a.getX()+6,a.getY()+6,a.getZ()+6));
  ShiroGuard.REGIONS.put(r); var data=PlayerClaimData.get(p);data.setClaimBlocks(100000);
  int before=data.remainingClaimBlocks();
  try {
   ctx.assertFalse(ClaimStorage.get(ctx.getWorld()).createClaim(a,b,p),"Overlapping claim created");
   ctx.assertEquals(data.remainingClaimBlocks(),before,"Denied claim spent blocks");
   ctx.assertTrue(messages.stream().anyMatch(t->t.getString().contains("região protegida")),"Guard denial did not run");
  } finally {ShiroGuard.REGIONS.remove(r.dimension(),r.id());}ctx.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void outsideAllowedExpansionDenied(TestContext ctx) {
  var messages=new ArrayList<Text>();var p=TestPlayers.create(ctx,messages::add);
  var a=ctx.getAbsolutePos(new BlockPos(0,1,0));var b=a.add(10,20,10);
  PlayerClaimData.get(p).setClaimBlocks(100000);var storage=ClaimStorage.get(ctx.getWorld());
  ctx.assertTrue(storage.createClaim(a,b,p),"Outside claim positive control failed");
  var claim=storage.getClaimAt(a);var before=claim.getDimensions();
  var r=Region.create("flan_expand_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
   new Bounds(a.getX()+15,a.getY(),a.getZ()+15,a.getX()+16,a.getY()+6,a.getZ()+16));
  ShiroGuard.REGIONS.put(r);
  try {
   ctx.assertFalse(storage.resizeClaim(claim,b,a.add(20,20,20),p),"Expansion entered Guard region");
   ctx.assertEquals(claim.getDimensions(),before,"Denied expansion changed claim");
   ctx.assertTrue(messages.stream().anyMatch(t->t.getString().contains("região protegida")),"Resize guard did not run");
  }finally{ShiroGuard.REGIONS.remove(r.dimension(),r.id());}ctx.complete();
 }
}

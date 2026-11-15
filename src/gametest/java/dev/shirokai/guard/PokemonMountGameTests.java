package dev.shirokai.guard;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.RidePokemonEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class PokemonMountGameTests implements FabricGameTest {
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="pokemon-mount")
  public void realRideEventAndForcedMountAreDeniedForAllStyles(TestContext ctx) {
    var a=ctx.getAbsolutePos(new BlockPos(0,0,0));var pos=Vec3d.ofBottomCenter(a.add(3,2,3));
    var region=Region.create("mount_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+7,a.getY()+7,a.getZ()+7))
        .withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny");
    ShiroGuard.REGIONS.put(region);var player=TestPlayers.create(ctx);player.setPosition(pos);
    for(String species:List.of("arcanine","lapras","charizard")) {
      var pokemon=PokemonProperties.Companion.parse(species).createEntity(ctx.getWorld());pokemon.setPosition(pos);
      var event=new RidePokemonEvent.Pre(player,pokemon);CobblemonEvents.RIDE_EVENT_PRE.emit(event);
      ctx.assertTrue(event.isCanceled(),"Ride event not cancelled for "+species);
      ctx.assertFalse(player.startRiding(pokemon,true),"Forced mounting bypassed guard for "+species);
      ShiroGuard.REGIONS.remove(region.dimension(),region.id());
      ctx.assertTrue(player.startRiding(pokemon,true),"Positive mount control failed for "+species);
      player.stopRiding();pokemon.discard();ShiroGuard.REGIONS.put(region);
    }
    var boat=new BoatEntity(ctx.getWorld(),pos.x,pos.y,pos.z);
    ctx.assertTrue(player.startRiding(boat,true),"Pokemon flag also blocked vanilla boat");
    player.stopRiding();boat.discard();ShiroGuard.REGIONS.remove(region.dimension(),region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="pokemon-mount")
  public void alreadyMountedCannotCrossWithoutBeingThrownOff(TestContext ctx) {
    var a=ctx.getAbsolutePos(new BlockPos(2,0,2));var outside=Vec3d.ofBottomCenter(a.add(-3,2,1));
    var region=Region.create("mounted_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX(),a.getY(),a.getZ(),a.getX()+3,a.getY()+7,a.getZ()+3))
        .withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny");ShiroGuard.REGIONS.put(region);
    var player=TestPlayers.create(ctx);player.setPosition(outside);
    var pokemon=PokemonProperties.Companion.parse("charizard").createEntity(ctx.getWorld());pokemon.setPosition(outside);
    ctx.assertTrue(player.startRiding(pokemon,true),"Outside mount failed");
    pokemon.move(MovementType.SELF,new Vec3d(5,0,0));
    ctx.assertEquals(pokemon.getPos(),outside,"Mounted Pokemon crossed boundary");
    ctx.assertTrue(player.hasVehicle(),"Boundary protection threw player off");
    player.stopRiding();pokemon.discard();ShiroGuard.REGIONS.remove(region.dimension(),region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="pokemon-mount")
  public void existingMountIsEndedWhenRegionBecomesRestricted(TestContext ctx) {
    var pos=Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3,2,3)));
    var player=TestPlayers.create(ctx);player.setPosition(pos);
    var pokemon=PokemonProperties.Companion.parse("arcanine").createEntity(ctx.getWorld());pokemon.setPosition(pos);
    ctx.assertTrue(player.startRiding(pokemon,true),"Initial mount failed");
    var a=BlockPos.ofFloored(pos);
    var region=Region.create("mountreload_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX()-2,a.getY()-2,a.getZ()-2,a.getX()+2,a.getY()+5,a.getZ()+2))
        .withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny");ShiroGuard.REGIONS.put(region);
    MobilityProtection.enforce(player);
    ctx.assertFalse(player.hasVehicle(),"Existing prohibited mount remained");
    ctx.assertTrue(MobilityProtection.protectsLanding(player),"Dismount lacked landing protection");
    MobilityProtection.forget(player.getUuid());pokemon.discard();ShiroGuard.REGIONS.remove(region.dimension(),region.id());ctx.complete();
  }
  @GameTest(templateName=EMPTY_STRUCTURE,batchId="pokemon-mount")
  public void normalCobblemonMountMethodRespectsPreEvent(TestContext ctx) {
    var pos=Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3,2,3)));
    ctx.getWorld().setBlockState(BlockPos.ofFloored(pos).down(),net.minecraft.block.Blocks.STONE.getDefaultState());
    var player=TestPlayers.create(ctx);player.setPosition(pos);
    var pokemon=PokemonProperties.Companion.parse("arcanine").createEntity(ctx.getWorld());pokemon.setPosition(pos);
    com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon.getPokemon());
    pokemon.refreshRiding();
    ctx.assertTrue(pokemon.getRidingController()!=null,"Actual riding controller missing");
    pokemon.getRidingController().changeBehaviour(net.minecraft.util.Identifier.of("cobblemon:land/horse"));
    ctx.assertTrue(pokemon.isRideableFor(player),"Native mounting preconditions failed");
    var a=BlockPos.ofFloored(pos);
    var region=Region.create("nativeride_"+a.getX()+"_"+a.getZ(),ShiroGuard.dimension(ctx.getWorld()),
        new Bounds(a.getX()-2,a.getY()-2,a.getZ()-2,a.getX()+2,a.getY()+5,a.getZ()+2))
        .withFlag(Flag.POKEMON_MOUNT,Region.Group.ALL,"deny");ShiroGuard.REGIONS.put(region);
    ctx.assertFalse(pokemon.tryRidingPokemon(player),"Native mount method ignored cancellation");
    ctx.assertFalse(player.hasVehicle(),"Denied method still mounted player");
    ShiroGuard.REGIONS.remove(region.dimension(),region.id());
    ctx.assertTrue(pokemon.tryRidingPokemon(player),"Native positive mount control failed");
    ctx.assertTrue(player.getVehicle()==pokemon,"Native method did not attach rider");
    player.stopRiding();pokemon.discard();ctx.complete();
  }
}

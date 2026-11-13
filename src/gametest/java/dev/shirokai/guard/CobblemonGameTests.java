package dev.shirokai.guard;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import dev.shirokai.guard.core.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class CobblemonGameTests implements FabricGameTest {
  private Region protect(TestContext ctx, Flag flag) {
    var a = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
    var b = ctx.getAbsolutePos(new BlockPos(7, 7, 7));
    var r =
        Region.create(
                "cobblemon_" + a.getX() + "_" + a.getZ(),
                ShiroGuard.dimension(ctx.getWorld()),
                Bounds.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()))
            .withFlag(flag, Region.Group.ALL, "deny");
    ShiroGuard.REGIONS.put(r);
    return r;
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pokemonSpawningDenied(TestContext ctx) {
    protect(ctx, Flag.POKEMON_SPAWN);
    var pokemon = PokemonProperties.Companion.parse("pikachu").createEntity(ctx.getWorld());
    pokemon.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    ctx.assertFalse(ctx.getWorld().spawnEntity(pokemon), "Pokémon spawned in denied region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void captureEventIsCancelledBeforeCapture(TestContext ctx) {
    protect(ctx, Flag.POKEMON_CAPTURE);
    var pokemon = PokemonProperties.Companion.parse("pikachu").createEntity(ctx.getWorld());
    pokemon.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    var ball = new EmptyPokeBallEntity(ctx.getWorld());
    var event = new ThrownPokeballHitEvent(ball, pokemon);
    CobblemonEvents.THROWN_POKEBALL_HIT.emit(event);
    ctx.assertTrue(event.isCanceled(), "Capture event was not cancelled");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void previousCaptureCancellationIsPreserved(TestContext ctx) {
    var pokemon = PokemonProperties.Companion.parse("pikachu").createEntity(ctx.getWorld());
    var event = new ThrownPokeballHitEvent(new EmptyPokeBallEntity(ctx.getWorld()), pokemon);
    event.cancel();
    CobblemonEvents.THROWN_POKEBALL_HIT.emit(event);
    ctx.assertTrue(event.isCanceled(), "Guard revoked another mod's denial");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void battleStartIsCancelled(TestContext ctx) {
    protect(ctx, Flag.POKEMON_BATTLE);
    var p = TestPlayers.create(ctx);
    p.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    var side = new BattleSide(new PlayerBattleActor(p.getUuid(), List.of()));
    var other = new BattleSide();
    var battle = new PokemonBattle(BattleFormat.Companion.getGEN_9_SINGLES(), side, other);
    var event = new BattleStartedEvent.Pre(battle, null);
    CobblemonEvents.BATTLE_STARTED_PRE.emit(event);
    ctx.assertTrue(event.isCanceled(), "Battle start ignored region");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pokemonInteractionCheckDenied(TestContext ctx) {
    protect(ctx, Flag.POKEMON_INTERACT);
    var pokemon = PokemonProperties.Companion.parse("pikachu").createEntity(ctx.getWorld());
    pokemon.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    ctx.assertFalse(
        Protection.entityAllowed(TestPlayers.create(ctx), pokemon, false),
        "Interaction ignored flag");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pokemonDamageDenied(TestContext ctx) {
    var region = protect(ctx, Flag.POKEMON_DAMAGE);
    var pokemon = PokemonProperties.Companion.parse("pikachu").createEntity(ctx.getWorld());
    pokemon.setPosition(Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))));
    float health = pokemon.getHealth();
    pokemon.damage(ctx.getWorld().getDamageSources().generic(), 5);
    ctx.assertEquals(pokemon.getHealth(), health, "Pokémon took damage");
    ShiroGuard.REGIONS.remove(region.dimension(), region.id());
    pokemon.damage(ctx.getWorld().getDamageSources().generic(), 5);
    ctx.assertTrue(
        pokemon.getHealth() < health, "Positive control: Pokémon damage did not execute");
    ctx.complete();
  }

  @GameTest(templateName = EMPTY_STRUCTURE)
  public void pastureTetherDenied(TestContext ctx) {
    protect(ctx, Flag.POKEMON_PASTURE);
    var pos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
    var be =
        new PokemonPastureBlockEntity(
            pos, com.cobblemon.mod.common.CobblemonBlocks.PASTURE.getDefaultState());
    be.setWorld(ctx.getWorld());
    var player = TestPlayers.create(ctx);
    var pokemon = PokemonProperties.Companion.parse("pikachu").create();
    ctx.assertFalse(be.tether(player, pokemon, Direction.SOUTH), "Pasture tether ignored region");
    ctx.complete();
  }
}

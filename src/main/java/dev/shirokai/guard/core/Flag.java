package dev.shirokai.guard.core;

import java.util.*;

/** Only flags with installed server hooks belong here. */
public enum Flag {
  POKEMON_SPAWN,
  POKEMON_CAPTURE,
  POKEMON_BATTLE,
  POKEMON_INTERACT,
  POKEMON_DAMAGE,
  POKEMON_PASTURE,
  FIRE_SPREAD,
  ICE_FORM,
  ICE_MELT,
  SNOW_MELT,
  LEAF_DECAY,
  CROP_GROWTH,
  FARMLAND_TRAMPLE,
  HUNGER,
  ENTITY_SPAWNING,
  MOB_SPAWNING,
  PROJECTILE,
  MOB_DAMAGE,
  INVINCIBLE,
  FALL_DAMAGE,
  ENDERPEARL,
  CHORUS_FRUIT,
  BUILD,
  BLOCK_BREAK,
  BLOCK_PLACE,
  INTERACT,
  USE,
  CHEST_ACCESS,
  ITEM_FRAME,
  ARMOR_STAND,
  PISTON,
  HOPPER,
  WATER_FLOW,
  LAVA_FLOW,
  TNT,
  CREEPER_EXPLOSION,
  OTHER_EXPLOSION,
  PVP,
  ENTITY_DAMAGE,
  ENTITY_INTERACT,
  ITEM_DROP,
  ITEM_PICKUP,
  ENTRY,
  ENTRY_MODE,
  EXIT,
  TELEPORT,
  GREETING,
  FAREWELL,
  BLOCKED_COMMANDS,
  ALLOWED_COMMANDS;

  public boolean optional() {
    return name().startsWith("POKEMON_");
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  public static Flag parse(String id) {
    return valueOf(id.toUpperCase(Locale.ROOT).replace('-', '_'));
  }

  public boolean text() {
    return this == GREETING || this == FAREWELL;
  }

  public boolean list() {
    return this == BLOCKED_COMMANDS || this == ALLOWED_COMMANDS;
  }

  public String validate(String value) {
    if (value == null || value.isBlank() || value.length() > 2048)
      throw new IllegalArgumentException("Valor inválido");
    if (this == ENTRY_MODE) {
      if (!Set.of("normal", "deny", "authorized-only").contains(value))
        throw new IllegalArgumentException("Use normal, deny, authorized-only ou unset");
      return value;
    }
    if (!text() && !list() && !Set.of("allow", "deny").contains(value))
      throw new IllegalArgumentException("Use allow, deny ou unset");
    return value;
  }
}

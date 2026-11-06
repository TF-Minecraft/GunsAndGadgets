package net.tfminecraft.gunsandgadgets.guns.stats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

@SuppressWarnings("deprecation")
class HandlingTest {
  double sneaking, moving, sprinting, airborne, mounted, maxSpread, falloffStart, falloffMin;

  @BeforeEach
  void configure() {
    sneaking = Cache.spreadSneaking;
    moving = Cache.spreadMoving;
    sprinting = Cache.spreadSprinting;
    airborne = Cache.spreadAirborne;
    mounted = Cache.spreadMounted;
    maxSpread = Cache.maxSpreadDegrees;
    falloffStart = Cache.falloffStartDistance;
    falloffMin = Cache.falloffMinMultiplier;
    Cache.spreadSneaking = 0.8;
    Cache.spreadMoving = 1.5;
    Cache.spreadSprinting = 3.0;
    Cache.spreadAirborne = 4.0;
    Cache.spreadMounted = 2.0;
    Cache.maxSpreadDegrees = 45.0;
    Cache.falloffStartDistance = 24.0;
    Cache.falloffMinMultiplier = 0.5;
  }

  @AfterEach
  void restore() {
    Cache.spreadSneaking = sneaking;
    Cache.spreadMoving = moving;
    Cache.spreadSprinting = sprinting;
    Cache.spreadAirborne = airborne;
    Cache.spreadMounted = mounted;
    Cache.maxSpreadDegrees = maxSpread;
    Cache.falloffStartDistance = falloffStart;
    Cache.falloffMinMultiplier = falloffMin;
  }

  Player grounded() {
    var player = mock(Player.class);
    when(player.isOnGround()).thenReturn(true);
    return player;
  }

  Input input(boolean forward, boolean backward, boolean left, boolean right) {
    var input = mock(Input.class);
    when(input.isForward()).thenReturn(forward);
    when(input.isBackward()).thenReturn(backward);
    when(input.isLeft()).thenReturn(left);
    when(input.isRight()).thenReturn(right);
    return input;
  }

  @Test
  void ridingAnAnimalUsesTheMountedMultiplier() {
    var player = mock(Player.class);
    var horse = mock(org.bukkit.entity.Horse.class);
    when(player.isInsideVehicle()).thenReturn(true);
    when(player.getVehicle()).thenReturn(horse);
    assertEquals(2.0, Handling.spreadMultiplier(player));
  }

  @Test
  void otherSeatsCountAsStandingStill() {
    var player = mock(Player.class);
    when(player.isInsideVehicle()).thenReturn(true);
    var stand = mock(org.bukkit.entity.ArmorStand.class);
    var display = mock(org.bukkit.entity.BlockDisplay.class);
    for (var seat : new org.bukkit.entity.Entity[] {stand, display, null}) {
      when(player.getVehicle()).thenReturn(seat);
      assertEquals(1.0, Handling.spreadMultiplier(player));
    }
  }

  @Test
  void leavingTheGroundUsesTheAirborneMultiplier() {
    assertEquals(4.0, Handling.spreadMultiplier(mock(Player.class)));
  }

  @Test
  void waterClimbingAndFlyingCountAsGrounded() {
    var swimming = mock(Player.class);
    when(swimming.isInWater()).thenReturn(true);
    var climbing = mock(Player.class);
    when(climbing.isClimbing()).thenReturn(true);
    var flying = mock(Player.class);
    when(flying.isFlying()).thenReturn(true);
    for (Player player : new Player[] {swimming, climbing, flying}) {
      assertEquals(1.0, Handling.spreadMultiplier(player));
    }
  }

  @Test
  void standingStillKeepsBaseSpreadAndSneakingTightensIt() {
    var player = grounded();
    assertEquals(1.0, Handling.spreadMultiplier(player));
    when(player.isSneaking()).thenReturn(true);
    assertEquals(0.8, Handling.spreadMultiplier(player), 1e-9);
  }

  @Test
  void sprintingBeatsMovingAndStacksWithSneaking() {
    var player = grounded();
    when(player.isSprinting()).thenReturn(true);
    var forward = input(true, false, false, false);
    when(player.getCurrentInput()).thenReturn(forward);
    assertEquals(3.0, Handling.spreadMultiplier(player));
    when(player.isSneaking()).thenReturn(true);
    assertEquals(2.4, Handling.spreadMultiplier(player), 1e-9);
  }

  @Test
  void anyMovementKeyCountsAsMoving() {
    var player = grounded();
    for (Input input :
        new Input[] {
          input(true, false, false, false),
          input(false, true, false, false),
          input(false, false, true, false),
          input(false, false, false, true)
        }) {
      when(player.getCurrentInput()).thenReturn(input);
      assertEquals(1.5, Handling.spreadMultiplier(player));
    }
    var still = input(false, false, false, false);
    when(player.getCurrentInput()).thenReturn(still);
    assertEquals(1.0, Handling.spreadMultiplier(player));
    assertFalse(Handling.isMoving(null));
  }

  @Test
  void spreadIsClampedBetweenTheFloorAndTheCap() {
    assertEquals(0.02, Handling.clampSpread(0.0));
    assertEquals(45.0, Handling.clampSpread(160.0));
    assertEquals(3.0, Handling.clampSpread(3.0));
  }

  @Test
  void falloffIsFullUpToStartThenLinearToTheMinimumAtRange() {
    assertEquals(1.0, Handling.falloffMultiplier(10, 104));
    assertEquals(1.0, Handling.falloffMultiplier(24, 104));
    assertEquals(0.75, Handling.falloffMultiplier(64, 104), 1e-9);
    assertEquals(0.5, Handling.falloffMultiplier(104, 104));
    assertEquals(0.5, Handling.falloffMultiplier(150, 104));
  }

  @Test
  void falloffIsOffAtAMinimumOfOne() {
    Cache.falloffMinMultiplier = 1.0;
    assertEquals(1.0, Handling.falloffMultiplier(150, 104));
  }
}

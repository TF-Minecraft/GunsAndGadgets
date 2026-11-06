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

  @Nested
  class Seats {
    org.mockbukkit.mockbukkit.ServerMock server;
    Player player;

    @BeforeEach
    void server() {
      server = org.mockbukkit.mockbukkit.MockBukkit.mock();
      player = mock(Player.class);
      when(player.isInsideVehicle()).thenReturn(true);
    }

    @AfterEach
    void unmock() {
      org.mockbukkit.mockbukkit.MockBukkit.unmock();
    }

    <T extends org.bukkit.entity.Entity> T seat(Class<T> type, double x, double y, double z) {
      T seat = mock(type);
      when(seat.getVelocity()).thenReturn(new org.bukkit.util.Vector(x, y, z));
      return seat;
    }

    @Test
    void stillSeatsCountAsStandingStillWithoutVehicleFramework() {
      for (var seat :
          new org.bukkit.entity.Entity[] {
            seat(org.bukkit.entity.ArmorStand.class, 0, 0, 0),
            seat(org.bukkit.entity.BlockDisplay.class, 0.005, -0.5, 0.005),
            null
          }) {
        when(player.getVehicle()).thenReturn(seat);
        assertEquals(1.0, Handling.spreadMultiplier(player));
      }
    }

    @Test
    void travellingBoatsAndMinecartsCountAsMounted() {
      var boat = seat(org.bukkit.entity.Boat.class, 0.3, 0, 0);
      var minecart = seat(org.bukkit.entity.Minecart.class, 0, 0, -0.2);
      when(player.getVehicle()).thenReturn(boat);
      assertEquals(2.0, Handling.spreadMultiplier(player));
      when(player.getVehicle()).thenReturn(minecart);
      assertEquals(2.0, Handling.spreadMultiplier(player));
    }

    @Test
    void vehicleFrameworkSeatsFollowTheAccessPanelSpeed() {
      org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("VehicleFramework");
      var stand = seat(org.bukkit.entity.ArmorStand.class, 0, 0, 0);
      when(player.getVehicle()).thenReturn(stand);
      var manager = mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class);
      var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
      var panel = mock(net.tfminecraft.vehicleframework.vehicles.util.AccessPanel.class);
      try (var framework = mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
        framework
            .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
            .thenReturn(manager);
        // Not a VehicleFramework passenger: a still seat.
        assertEquals(1.0, Handling.spreadMultiplier(player));
        when(manager.getByPassenger(player)).thenReturn(vehicle);
        // A vehicle without an access panel cannot report speed.
        assertEquals(1.0, Handling.spreadMultiplier(player));
        when(vehicle.getAccessPanel()).thenReturn(panel);
        assertEquals(1.0, Handling.spreadMultiplier(player));
        when(panel.getSpeed()).thenReturn(0.4);
        assertEquals(2.0, Handling.spreadMultiplier(player));
        when(panel.getSpeed()).thenReturn(-0.2);
        assertEquals(2.0, Handling.spreadMultiplier(player));
      }
    }

    @Test
    void trainCarriagesMoveWithTheirLocomotive() {
      org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("VehicleFramework");
      var stand = seat(org.bukkit.entity.ArmorStand.class, 0, 0, 0);
      when(player.getVehicle()).thenReturn(stand);
      var manager = mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class);
      var carriage = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
      var locomotive = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
      var carriagePanel = mock(net.tfminecraft.vehicleframework.vehicles.util.AccessPanel.class);
      var locomotivePanel = mock(net.tfminecraft.vehicleframework.vehicles.util.AccessPanel.class);
      when(carriage.hasParent()).thenReturn(true);
      when(carriage.getParent()).thenReturn(locomotive);
      when(carriage.getAccessPanel()).thenReturn(carriagePanel);
      when(locomotive.getAccessPanel()).thenReturn(locomotivePanel);
      when(manager.getByPassenger(player)).thenReturn(carriage);
      try (var framework = mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
        framework
            .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
            .thenReturn(manager);
        assertEquals(1.0, Handling.spreadMultiplier(player));
        when(locomotivePanel.getSpeed()).thenReturn(0.5);
        assertEquals(2.0, Handling.spreadMultiplier(player));
        // A coupling loop stops after the guard instead of spinning forever.
        when(carriage.getParent()).thenReturn(carriage);
        assertEquals(1.0, Handling.spreadMultiplier(player));
        verify(carriage, atLeast(64)).getParent();
      }
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

package net.tfminecraft.gunsandgadgets.shooter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.attributes.AttributeReader;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.data.*;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.util.*;
import net.tfminecraft.vehicleframework.util.LightEffect;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.*;

class ProjectileShooterTest {
  ServerMock server;
  GunsAndGadgets plugin;
  Player player;
  World world;
  ItemStack gun;
  MockedStatic<GunsAndGadgets> plugins;
  MockedStatic<AttributeReader> attributes;
  MockedStatic<SoundPlayer> sounds;
  MockedStatic<ImpactVfx> impacts;
  MockedConstruction<LightEffect> lights;
  boolean oldBlockDamage;
  String oldRocketSound;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    plugin = mock(GunsAndGadgets.class);
    when(plugin.namespace()).thenReturn("gunsandgadgets");
    when(plugin.isEnabled()).thenReturn(true);
    plugins = mockStatic(GunsAndGadgets.class);
    plugins.when(GunsAndGadgets::getInstance).thenReturn(plugin);
    attributes = mockStatic(AttributeReader.class);
    sounds = mockStatic(SoundPlayer.class);
    impacts = mockStatic(ImpactVfx.class);
    impacts
        .when(() -> ImpactVfx.onBlockSurface(any(), any(), any()))
        .thenAnswer(i -> i.getArgument(0));
    lights = mockConstruction(LightEffect.class);
    world = mock(World.class);
    player = mock(Player.class);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenAnswer(i -> new Location(world, 0, 0, 0));
    when(player.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 1.6, 0));
    attributes.when(() -> AttributeReader.getAccuracyMultFromAttributes(player)).thenReturn(1.0);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            i ->
                block(
                    Material.AIR,
                    i.getArgument(0),
                    i.getArgument(1),
                    i.getArgument(2),
                    true,
                    List.of()));
    when(world.getBlockAt(any(Location.class)))
        .thenAnswer(
            i -> {
              Location l = i.getArgument(0);
              return world.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            });
    gun = new ItemStack(Material.STICK);
    put("skin_id", "rifle");
    putInt("stat_value_accuracy", 30);
    putInt("stat_value_damage", 10);
    oldBlockDamage = Cache.blockDamage;
    oldRocketSound = Cache.rocketSound;
    Cache.blockDamage = false;
    Cache.rocketSound = "rocket";
  }

  @AfterEach
  void close() {
    server.getScheduler().cancelTasks(plugin);
    lights.close();
    impacts.close();
    sounds.close();
    attributes.close();
    plugins.close();
    Cache.blockDamage = oldBlockDamage;
    Cache.rocketSound = oldRocketSound;
    MockBukkit.unmock();
  }

  void put(String name, String value) {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, name), PersistentDataType.STRING, value);
    gun.setItemMeta(m);
  }

  void putInt(String name, int value) {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, name), PersistentDataType.INTEGER, value);
    gun.setItemMeta(m);
  }

  Ammunition ammo(String... options) {
    var c = new YamlConfiguration();
    c.set("options", List.of(options));
    return new Ammunition("test", c);
  }

  void terrain(Block b) {
    when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(b);
  }

  Block block(Material type, int x, int y, int z, boolean passable, List<BoundingBox> boxes) {
    var b = mock(Block.class);
    when(b.getType()).thenReturn(type);
    when(b.isPassable()).thenReturn(passable);
    when(b.getLocation()).thenReturn(new Location(world, x, y, z));
    var shape = mock(VoxelShape.class);
    when(shape.getBoundingBoxes()).thenReturn(boxes);
    when(b.getCollisionShape()).thenReturn(shape);
    return b;
  }

  Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var method = ProjectileShooter.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(null, args);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception ex) throw ex;
      throw e;
    }
  }

  boolean hits(Ammunition ammo, int ticks) throws Exception {
    return (boolean)
        call(
            "handleHits",
            new Class<?>[] {
              Player.class,
              Ammunition.class,
              Location.class,
              Location.class,
              double.class,
              int.class,
              int.class
            },
            player,
            ammo,
            new Location(world, 0, 0, 0),
            new Location(world, 0, 0, 2),
            10.0,
            20,
            ticks);
  }

  @Test
  void invalidGunInputsDoNotScheduleShots() {
    new ProjectileShooter();
    ProjectileShooter.shoot(player, null, ammo());
    ProjectileShooter.shoot(player, mock(ItemStack.class), ammo());
    ProjectileShooter.shootBullet(player, null, ammo());
    ProjectileShooter.shootBullet(player, mock(ItemStack.class), ammo());
    verifyNoInteractions(world);
  }

  @Test
  void standardShotProducesSmokeAndThreeTimedLights() {
    ProjectileShooter.shoot(player, gun, ammo());
    server.getScheduler().performTicks(5);
    assertEquals(3, lights.constructed().size());
    verify(lights.constructed().get(0)).createTemporaryLight(any(), eq(10));
    verify(lights.constructed().get(1)).createTemporaryLight(any(), eq(15));
    verify(lights.constructed().get(2)).createTemporaryLight(any(), eq(8));
    verify(world, times(20))
        .spawnParticle(
            eq(Particle.CAMPFIRE_COSY_SMOKE),
            any(Location.class),
            eq(0),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble());
  }

  @Test
  void ammunitionCanSuppressBothMuzzleEffects() {
    ProjectileShooter.shoot(player, gun, ammo("SMOKELESS", "NO_LIGHT"));
    server.getScheduler().performTicks(2);
    assertTrue(lights.constructed().isEmpty());
    verify(world, never())
        .spawnParticle(
            eq(Particle.CAMPFIRE_COSY_SMOKE),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble());
  }

  @Test
  void legacySteamlockSkinsSuppressMuzzleEffects() {
    for (String skin : List.of("rifle_steamlock", "pistol_steamlock")) {
      put("skin_id", skin);
      ProjectileShooter.shoot(player, gun, ammo());
    }
    assertTrue(lights.constructed().isEmpty());
  }

  @Test
  void stampedPartOptionsSuppressMuzzleEffects() {
    var part = mock(GunPart.class);
    when(part.hasOption(any())).thenReturn(true);
    PartLoader.get().put("silenced", part);
    try {
      new GunCraftProvenance(
              Arrays.asList(null, new GGCraftPart("missing", 0), new GGCraftPart("silenced", 1)), 1)
          .applyTo(gun);
      ProjectileShooter.shoot(player, gun, ammo());
      assertTrue(lights.constructed().isEmpty());
    } finally {
      PartLoader.get().remove("silenced");
    }
  }

  @Test
  void malformedAndUnsuppressedProvenanceFallBackToMuzzleEffects() {
    put("gg_craft_parts", "{broken");
    ProjectileShooter.shoot(player, gun, ammo());
    new GunCraftProvenance(List.of(new GGCraftPart("missing", 0)), 0).applyTo(gun);
    ProjectileShooter.shoot(player, gun, ammo());
    assertEquals(2, lights.constructed().size());
  }

  @Test
  void configuredRangeStopsProjectileAfterFirstTick() {
    putInt("stat_value_range", 1);
    ProjectileShooter.shootBullet(player, gun, ammo());
    server.getScheduler().performTicks(1);
    int calls = mockingDetails(world).getInvocations().size();
    server.getScheduler().performTicks(5);
    assertEquals(calls, mockingDetails(world).getInvocations().size());
  }

  @Test
  void projectileCountUsesConfiguredPellets() {
    var config = new YamlConfiguration();
    config.set("amount", 3);
    putInt("stat_value_range", 1);
    ProjectileShooter.shootBullet(player, gun, new Ammunition("buckshot", config));
    server.getScheduler().performTicks(2);
    verify(world, times(3)).rayTraceEntities(any(), any(), anyDouble(), eq(.4), any());
  }

  @Test
  void airAndPassableBlocksDoNotStopProjectiles() throws Exception {
    assertFalse(hits(ammo(), 1));
    terrain(block(Material.GRASS_BLOCK, 0, 0, 0, true, List.of()));
    assertFalse(hits(ammo(), 1));
  }

  @Test
  void glassAndLeavesAllowBulletsThrough() throws Exception {
    for (Material type : List.of(Material.GLASS, Material.OAK_LEAVES)) {
      terrain(block(type, 0, 0, 0, false, List.of(new BoundingBox(0, 0, 0, 1, 1, 1))));
      assertFalse(hits(ammo(), 1));
    }
    verify(world, atLeastOnce())
        .playSound(any(Location.class), eq(Sound.BLOCK_GLASS_BREAK), eq(.8f), eq(1.2f));
  }

  @Test
  void solidCollisionStopsBulletAndEmitsImpact() throws Exception {
    terrain(block(Material.STONE, 0, 0, 0, false, List.of(new BoundingBox(0, 0, 0, 1, 1, 1))));
    assertTrue(hits(ammo(), 1));
    verify(world).playSound(any(Location.class), eq(Sound.BLOCK_STONE_BREAK), eq(1f), eq(2f));
  }

  @Test
  void emptyOrNonIntersectingCollisionShapesDoNotStopBullet() throws Exception {
    for (var boxes :
        List.of(List.<BoundingBox>of(), List.of(new BoundingBox(.5, .5, .5, 1, 1, 1)))) {
      terrain(block(Material.STONE, 0, 0, 0, false, boxes));
      assertFalse(hits(ammo(), 1));
    }
  }

  @Test
  void shooterIsNotDamagedByOwnDirectHit() throws Exception {
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, 1), player));
    assertFalse(hits(ammo(), 1));
    verify(player, never()).damage(anyDouble());
  }

  @Test
  void livingNonPlayerReceivesPiercingHit() throws Exception {
    var target = mock(LivingEntity.class);
    when(target.getHealth()).thenReturn(20.0);
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, 1), target));
    assertTrue(hits(ammo(), 1));
    verify(target).damage(10);
  }

  @Test
  void playerHeadshotHasDamageBonusAndBodyShotDoesNot() throws Exception {
    var target = mock(Player.class);
    when(target.getHealth()).thenReturn(20.0);
    when(target.getBoundingBox()).thenReturn(new BoundingBox(0, 0, 0, 1, 2, 1));
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 1.6, 1), target));
    assertTrue(hits(ammo(), 1));
    verify(target).damage(15);
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, .5, 1), target));
    assertTrue(hits(ammo(), 1));
    verify(target).damage(10);
    verify(player, times(2))
        .playSound(any(Location.class), eq(Sound.ENTITY_ARROW_HIT_PLAYER), eq(1f), eq(1f));
  }

  @Test
  void killingPlayerSchedulesFourConfirmationNotes() throws Exception {
    var target = mock(Player.class);
    when(target.getHealth()).thenReturn(20.0);
    when(target.getBoundingBox()).thenReturn(new BoundingBox(0, 0, 0, 1, 2, 1));
    doAnswer(
            i -> {
              when(target.isDead()).thenReturn(true);
              return null;
            })
        .when(target)
        .damage(anyDouble());
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, 1), target));
    assertTrue(hits(ammo(), 1));
    server.getScheduler().performTicks(9);
    verify(player, times(4))
        .playSound(any(Location.class), eq(Sound.ENTITY_EXPERIENCE_ORB_PICKUP), eq(1f), anyFloat());
  }

  @Test
  void rayPredicateRejectsDeadAndNonLivingEntities() throws Exception {
    hits(ammo(), 1);
    var captor = ArgumentCaptor.forClass(Predicate.class);
    verify(world).rayTraceEntities(any(), any(), anyDouble(), anyDouble(), captor.capture());
    Predicate<Entity> filter = captor.getValue();
    var live = mock(LivingEntity.class);
    assertTrue(filter.test(live));
    when(live.isDead()).thenReturn(true);
    assertFalse(filter.test(live));
    assertFalse(filter.test(mock(Entity.class)));
  }

  @Test
  void rocketCollisionExplodesAndRespectsBlockDamageSetting() throws Exception {
    terrain(block(Material.STONE, 0, 0, 0, false, List.of(new BoundingBox(0, 0, 0, 1, 1, 1))));
    assertTrue(hits(ammo("ROCKET"), 1));
    verify(world, never())
        .createExplosion(any(Location.class), anyFloat(), anyBoolean(), anyBoolean(), any());
    Cache.blockDamage = true;
    assertTrue(hits(ammo("ROCKET"), 30));
    verify(world).createExplosion(any(Location.class), eq(2f), eq(false), eq(true), eq(player));
    server.getScheduler().performTicks(5);
    assertEquals(6, lights.constructed().size());
  }

  @Test
  void rocketEntityCollisionExplodes() throws Exception {
    var target = mock(LivingEntity.class);
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, 1), target));
    assertTrue(hits(ammo("ROCKET"), 10));
    verify(world).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_EXPLODE), eq(8f), eq(1f));
  }

  @Test
  void entityBehindSolidWallIsNotDamaged() throws Exception {
    var target = mock(LivingEntity.class);
    when(target.getHealth()).thenReturn(20.0);
    terrain(block(Material.STONE, 0, 0, 0, false, List.of(new BoundingBox(0, 0, 0, 1, 1, 1))));
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, 1.8), target));
    assertTrue(hits(ammo(), 1));
    verify(target, never()).damage(anyDouble());
  }

  @Test
  void vehicleHitboxCanBeHitByRayPredicate() throws Exception {
    MockBukkit.createMockPlugin("VehicleFramework");
    var hitbox = mock(org.bukkit.entity.ArmorStand.class);
    var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
    var manager = mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class);
    when(manager.get(hitbox)).thenReturn(vehicle);
    try (var framework = mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
      framework
          .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
          .thenReturn(manager);
      when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
          .thenAnswer(
              i -> {
                Predicate<Entity> filter = i.getArgument(4);
                return filter.test(hitbox) ? new RayTraceResult(new Vector(0, 0, 1), hitbox) : null;
              });
      hits(ammo(), 1);
      verify(vehicle).damage("PROJECTILE", 10.0);
    }
  }

  @Test
  void damageSplitsBetweenDirectPiercingAndMythicAttack() {
    var target = mock(LivingEntity.class);
    when(target.getHealth()).thenReturn(100.0);
    var previous = io.lumine.mythic.lib.MythicLib.plugin;
    var mythic = mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    io.lumine.mythic.lib.MythicLib.plugin = mythic;
    var provider = mock(io.lumine.mythic.lib.api.stat.provider.StatProvider.class);
    List<Double> normalAmounts = new ArrayList<>();
    try (var providers = mockStatic(io.lumine.mythic.lib.api.stat.provider.StatProvider.class);
        var attacks =
            mockConstruction(
                io.lumine.mythic.lib.damage.AttackMetadata.class,
                (mock, context) ->
                    normalAmounts.add(
                        ((io.lumine.mythic.lib.damage.DamageMetadata) context.arguments().get(0))
                            .getDamage()))) {
      providers
          .when(
              () ->
                  io.lumine.mythic.lib.api.stat.provider.StatProvider.get(
                      player, io.lumine.mythic.lib.api.player.EquipmentSlot.MAIN_HAND, true))
          .thenReturn(provider);
      ProjectileShooter.applyDamage(player, target, 10, 0);
      ProjectileShooter.applyDamage(player, target, 10, 10);
      ProjectileShooter.applyDamage(player, target, 10, 20);
      ProjectileShooter.applyDamage(player, target, 10, -20);
      ProjectileShooter.applyDamage(player, target, 10, 40);
      assertEquals(List.of(10.0, 5.0, 10.0), normalAmounts);
      verify(target).damage(5);
      verify(target, times(2)).damage(10);
      verify(mythic.getDamage(), times(3)).registerAttack(any(), eq(false), eq(true));
    } finally {
      io.lumine.mythic.lib.MythicLib.plugin = previous;
    }
  }

  @Test
  void overkillPiercingCanBeLethal() {
    var target = mock(LivingEntity.class);
    when(target.getHealth()).thenReturn(5.0);
    ProjectileShooter.applyDamage(player, target, 10, 20);
    verify(target).damage(10);
  }

  @Test
  void explosionScalesDamageByDistanceAndArmingTime() throws Exception {
    var near = mock(LivingEntity.class);
    when(near.getLocation()).thenReturn(new Location(world, 0, 0, 2));
    when(near.getHealth()).thenReturn(100.0);
    var far = mock(LivingEntity.class);
    when(far.getLocation()).thenReturn(new Location(world, 0, 0, 20));
    when(far.getHealth()).thenReturn(100.0);
    var otherPlayer = mock(Player.class);
    when(otherPlayer.getLocation()).thenReturn(new Location(world, 0, 0, 4));
    when(otherPlayer.getHealth()).thenReturn(100.0);
    when(player.getHealth()).thenReturn(1000.0);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(near, far, otherPlayer, player, mock(Entity.class)));
    call(
        "explode",
        new Class<?>[] {Player.class, Location.class, double.class, int.class, int.class},
        player,
        new Location(world, 0, 0, 0),
        10.0,
        20,
        10);
    verify(near).damage(7.5);
    verify(far, never()).damage(anyDouble());
    verify(otherPlayer).damage(5);
    verify(player).damage(20);
  }

  @Test
  void rocketSelfDamageIsLinearAndIgnoresArmingTime() throws Exception {
    when(player.getLocation()).thenReturn(new Location(world, 0, 0, 4));
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(player));
    for (int ticks : new int[] {0, 10, 30}) {
      call(
          "explode",
          new Class<?>[] {Player.class, Location.class, double.class, int.class, int.class},
          player,
          new Location(world, 0, 0, 0),
          10.0,
          20,
          ticks);
    }
    verify(player, times(3)).damage(10.0);
    call(
        "explode",
        new Class<?>[] {Player.class, Location.class, double.class, int.class, int.class},
        player,
        new Location(world, 0, 0, 0),
        20.0,
        20,
        10);
    verify(player).damage(20.0);
  }

  @Test
  void rocketFliesUntilFuseExpiresAndSchedulesExplosion() throws Exception {
    call(
        "startProjectileTask",
        new Class<?>[] {
          Player.class,
          Ammunition.class,
          Location.class,
          Vector.class,
          double.class,
          double.class,
          double.class,
          int.class,
          java.util.function.DoubleSupplier.class
        },
        player,
        ammo("ROCKET"),
        new Location(world, 0, 0, 0),
        new Vector(0, 0, 1),
        0.0,
        100000.0,
        10.0,
        20,
        (java.util.function.DoubleSupplier) () -> 0.5);
    server.getScheduler().performTicks(106);
    verify(world).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_EXPLODE), eq(8f), eq(1f));
    verify(world, atLeastOnce()).playSound(any(Location.class), eq("rocket"), eq(4f), eq(1.3f));
  }

  @Test
  void rocketUsesDefaultRangeAndBulletStopsOnImpact() {
    putInt("stat_value_speed", 1);
    ProjectileShooter.shootBullet(player, gun, ammo("ROCKET"));
    server.getScheduler().performTicks(2);
    server.getScheduler().cancelTasks(plugin);
    clearInvocations(world);
    terrain(
        block(Material.STONE, 0, 1, 1, false, List.of(new BoundingBox(-10, -10, -10, 10, 10, 10))));
    ProjectileShooter.shootBullet(player, gun, ammo());
    server.getScheduler().performTicks(2);
    verify(world).playSound(any(Location.class), eq(Sound.BLOCK_STONE_BREAK), eq(1f), eq(2f));
  }

  @Test
  void entityBeforeWallIsDamagedWithoutHittingFartherWall() throws Exception {
    var target = mock(LivingEntity.class);
    when(target.getHealth()).thenReturn(20.0);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            i -> {
              int z = i.getArgument(2);
              return block(
                  z >= 1 ? Material.STONE : Material.AIR,
                  0,
                  0,
                  z,
                  z < 1,
                  List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
            });
    when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
        .thenReturn(new RayTraceResult(new Vector(0, 0, .8), target));
    assertTrue(hits(ammo(), 1));
    verify(target).damage(10);
    verify(world, never())
        .playSound(any(Location.class), eq(Sound.BLOCK_STONE_BREAK), anyFloat(), anyFloat());
  }

  @Test
  void rocketSpinRemainsFiniteAndExpiresWithDeterministicWobble() throws Exception {
    var count = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.DoubleSupplier random =
        () -> (count.getAndIncrement() / 3) % 2 == 0 ? Math.nextDown(1.0) : 0.0;
    call(
        "startProjectileTask",
        new Class<?>[] {
          Player.class,
          Ammunition.class,
          Location.class,
          Vector.class,
          double.class,
          double.class,
          double.class,
          int.class,
          java.util.function.DoubleSupplier.class
        },
        player,
        ammo("ROCKET"),
        new Location(world, 0, 0, 0),
        new Vector(0, 0, 1),
        20.0,
        100000.0,
        10.0,
        20,
        random);
    server.getScheduler().performTicks(106);
    var locations = ArgumentCaptor.forClass(Location.class);
    verify(world).playSound(locations.capture(), eq(Sound.ENTITY_GENERIC_EXPLODE), eq(8f), eq(1f));
    assertTrue(Double.isFinite(locations.getValue().getX()));
    assertTrue(Double.isFinite(locations.getValue().getY()));
    assertTrue(Double.isFinite(locations.getValue().getZ()));
    assertTrue(count.get() > 6);
  }

  @Test
  void registeredNonlivingVehicleReceivesRocketDamage() throws Exception {
    MockBukkit.createMockPlugin("VehicleFramework");
    var hitbox = mock(org.bukkit.entity.Interaction.class);
    var unrelated = mock(Entity.class);
    var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
    var manager = mock(net.tfminecraft.vehicleframework.managers.VehicleManager.class);
    when(manager.get(hitbox)).thenReturn(vehicle);
    try (var framework = mockStatic(net.tfminecraft.vehicleframework.VehicleFramework.class)) {
      framework
          .when(net.tfminecraft.vehicleframework.VehicleFramework::getVehicleManager)
          .thenReturn(manager);
      when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), any()))
          .thenAnswer(
              i -> {
                Predicate<Entity> filter = i.getArgument(4);
                assertFalse(filter.test(unrelated));
                return filter.test(hitbox) ? new RayTraceResult(new Vector(0, 0, 1), hitbox) : null;
              });
      assertTrue(hits(ammo("ROCKET"), 1));
      verify(vehicle).damage("ROCKET", 10.0);
    }
  }

  @Test
  void stampedOrdinaryPartDoesNotSuppressMuzzle() {
    var part = mock(GunPart.class);
    PartLoader.get().put("ordinary", part);
    try {
      new GunCraftProvenance(List.of(new GGCraftPart("ordinary", 1)), 1).applyTo(gun);
      ProjectileShooter.shoot(player, gun, ammo());
      assertEquals(1, lights.constructed().size());
    } finally {
      PartLoader.get().remove("ordinary");
    }
  }
}

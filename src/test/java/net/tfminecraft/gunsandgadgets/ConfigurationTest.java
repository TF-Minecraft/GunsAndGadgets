package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.utils.RevisionTracker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConfigurationTest extends GunsTestSupport {
  @Test
  void settingsLoadAndResetDefaultsAndIgnoreUnknownGunTypes() throws Exception {
    ConfigLoader loader = new ConfigLoader();
    loader.loadConfig(
        file(
                "config.yml",
                """
outputs: {rifle: v.bow, invalid: v.stick}
required-parts: {rifle: [barrel], invalid: [action]}
attributes:
  dexterity: {accuracy-per-level: 2, reload-reduction-per-level: 3}
require-input: false
output-slot: 5
station: v.anvil
block-damage: true
rocket-sound: boom
stat_refresh_debug: true
handling:
  headshot-multiplier: 1.25
  spread-multipliers: {sneaking: 0.8, moving: 1.5, sprinting: 3, airborne: 4, mounted: 2}
  max-spread-degrees: 30
  falloff: {start-distance: 24, min-multiplier: 0.5}
""")
            .toFile());
    assertEquals(1.25, Cache.headshotMultiplier);
    assertEquals(0.8, Cache.spreadSneaking);
    assertEquals(1.5, Cache.spreadMoving);
    assertEquals(3.0, Cache.spreadSprinting);
    assertEquals(4.0, Cache.spreadAirborne);
    assertEquals(2.0, Cache.spreadMounted);
    assertEquals(30.0, Cache.maxSpreadDegrees);
    assertEquals(24.0, Cache.falloffStartDistance);
    assertEquals(0.5, Cache.falloffMinMultiplier);
    assertEquals("v.bow", Cache.outputItems.get(GunType.RIFLE));
    assertEquals(List.of("barrel"), Cache.requiredParts.get(GunType.RIFLE));
    assertEquals(5, Cache.outputSlot);
    assertFalse(Cache.requireInput);
    assertTrue(Cache.blockDamage);
    assertTrue(Cache.statRefreshDebug);
    assertEquals("boom", Cache.rocketSound);
    assertEquals("v.anvil", Cache.station);
    var attribute = Cache.attributes.getFirst();
    assertEquals("dexterity", attribute.getAttribute());
    assertEquals(2, attribute.getAccuracy());
    assertEquals(3, attribute.getReload());
    loader.loadConfig(file("empty.yml", "{}").toFile());
    assertTrue(Cache.attributes.isEmpty());
    assertTrue(Cache.outputItems.isEmpty());
    assertTrue(Cache.requiredParts.isEmpty());
    assertTrue(Cache.requireInput);
    assertEquals(15, Cache.outputSlot);
    // Without a handling section every value keeps the behaviour from before it existed.
    assertEquals(1.5, Cache.headshotMultiplier);
    assertEquals(1.0, Cache.spreadSneaking);
    assertEquals(1.0, Cache.spreadMoving);
    assertEquals(1.0, Cache.spreadSprinting);
    assertEquals(1.0, Cache.spreadAirborne);
    assertEquals(1.0, Cache.spreadMounted);
    assertEquals(Double.POSITIVE_INFINITY, Cache.maxSpreadDegrees);
    assertEquals(0.0, Cache.falloffStartDistance);
    assertEquals(1.0, Cache.falloffMinMultiplier);
    loader.loadConfig(
        file(
                "bounds.yml",
                """
handling:
  headshot-multiplier: -1
  spread-multipliers: {sneaking: -1, moving: -1, sprinting: -1, airborne: -1, mounted: -1}
  max-spread-degrees: 0
  falloff: {start-distance: -5, min-multiplier: 3}
""")
            .toFile());
    assertEquals(0.0, Cache.headshotMultiplier);
    assertEquals(0.0, Cache.spreadSneaking);
    assertEquals(0.0, Cache.spreadMoving);
    assertEquals(0.0, Cache.spreadSprinting);
    assertEquals(0.0, Cache.spreadAirborne);
    assertEquals(0.0, Cache.spreadMounted);
    assertEquals(0.02, Cache.maxSpreadDegrees);
    assertEquals(0.0, Cache.falloffStartDistance);
    assertEquals(1.0, Cache.falloffMinMultiplier);
    loader.loadConfig(file("negative-falloff.yml", "handling: {falloff: {min-multiplier: -2}}").toFile());
    assertEquals(0.0, Cache.falloffMinMultiplier);
    // NaN or infinity fall back to each key's default instead of reaching damage and spread.
    loader.loadConfig(
        file(
                "nonfinite.yml",
                """
handling:
  headshot-multiplier: .nan
  spread-multipliers: {sneaking: .nan, moving: .inf, sprinting: -.inf, airborne: .nan, mounted: .inf}
  max-spread-degrees: .nan
  falloff: {start-distance: .inf, min-multiplier: .nan}
""")
            .toFile());
    assertEquals(1.5, Cache.headshotMultiplier);
    assertEquals(1.0, Cache.spreadSneaking);
    assertEquals(1.0, Cache.spreadMoving);
    assertEquals(1.0, Cache.spreadSprinting);
    assertEquals(1.0, Cache.spreadAirborne);
    assertEquals(1.0, Cache.spreadMounted);
    assertEquals(Double.POSITIVE_INFINITY, Cache.maxSpreadDegrees);
    assertEquals(0.0, Cache.falloffStartDistance);
    assertEquals(1.0, Cache.falloffMinMultiplier);
    // The shipped config.yml carries the tuned values (a typo or indent slip would fall back).
    loader.loadConfig(
        java.nio.file.Path.of(getClass().getResource("/config.yml").toURI()).toFile());
    assertEquals(1.25, Cache.headshotMultiplier);
    assertEquals(0.8, Cache.spreadSneaking);
    assertEquals(2.0, Cache.spreadMoving);
    assertEquals(4.0, Cache.spreadSprinting);
    assertEquals(5.0, Cache.spreadAirborne);
    assertEquals(2.0, Cache.spreadMounted);
    assertEquals(45.0, Cache.maxSpreadDegrees);
    assertEquals(24.0, Cache.falloffStartDistance);
    assertEquals(0.5, Cache.falloffMinMultiplier);
    assertEquals(26, Cache.drawBaseTicks.get(net.tfminecraft.gunsandgadgets.guns.GunType.RIFLE));
    assertEquals(12, Cache.drawBaseTicks.get(net.tfminecraft.gunsandgadgets.guns.GunType.PISTOL));
    assertEquals(2.0, Cache.drawSprinting);
    assertEquals(0.8, Cache.drawSneaking);
    assertTrue(Cache.aimingEnabled);
    assertEquals(0.5, Cache.aimSlow);
    assertEquals("tfmc_guns:part/", Cache.partIconPrefix);
    assertEquals("tfmc_guns:", Cache.typeIconPrefix);
    assertEquals(12, Cache.scopeSettleTicks);
    assertEquals(1.75, Cache.spreadHip);
    assertEquals(4.0, Cache.rocketSafeDistance);
    assertEquals(0.1, Cache.rocketMinMultiplier);
    assertEquals(40.0, Cache.rocketFullDistance);
    assertFalse(Cache.refundsEnabled);
    assertFalse(Cache.refundsAutomatic);
    // Bad draw entries: an unknown gun type is skipped, a non-number gives no draw time.
    loader.loadConfig(
        file(
                "draw.yml",
                """
handling:
  draw:
    base-seconds: {rifle: .nan, pistol: -2, cannon: 3}
    stance: {moving: .nan, sprinting: 0}
  rockets: {safe-distance: -1, min-multiplier: 3, full-damage-distance: -5}
aiming: {enabled: true, slow: 5}
refunds: {enabled: true, automatic: true}
""")
            .toFile());
    assertEquals(0, Cache.drawBaseTicks.get(net.tfminecraft.gunsandgadgets.guns.GunType.RIFLE));
    assertEquals(0, Cache.drawBaseTicks.get(net.tfminecraft.gunsandgadgets.guns.GunType.PISTOL));
    assertEquals(2, Cache.drawBaseTicks.size());
    assertEquals(1.0, Cache.drawMoving);
    assertEquals(0.1, Cache.drawSprinting);
    assertEquals(0.9, Cache.aimSlow);
    assertTrue(Cache.refundsEnabled && Cache.refundsAutomatic);
    assertEquals(0.0, Cache.rocketSafeDistance);
    assertEquals(1.0, Cache.rocketMinMultiplier);
    assertEquals(0.0, Cache.rocketFullDistance);
    loader.loadConfig(file("empty2.yml", "{}").toFile());
    assertEquals(0, Cache.scopeSettleTicks);
    assertEquals(1.0, Cache.spreadHip);
    assertEquals("", Cache.typeIconPrefix);
    assertTrue(Cache.drawBaseTicks.isEmpty());
    assertFalse(Cache.aimingEnabled);
    loader.loadConfig(temp.resolve("missing").toFile());
    loader.loadConfig(file("bad.yml", "x: [").toFile());
  }

  @Test
  void definitionLoadersReplaceCachesAndRecordStablePartRevisions() throws Exception {
    var data = new PartDataLoader();
    var parts = new PartLoader();
    var ammo = new AmmunitionLoader();
    var skins = new SkinLoader();
    data.load(file("types.yml", "barrel: {slot: 2}").toFile());
    assertEquals(2, PartDataLoader.getByString("barrel").getSlot());
    assertNull(PartDataLoader.getByString("missing"));
    Path partFile = file("parts.yml", "barrel: {stats: ['damage 2']}\nother: {}");
    parts.load(partFile.toFile());
    assertEquals(2, PartLoader.getOrdered().size());
    assertEquals(1, PartLoader.getByString("barrel").getRevision());
    parts.load(partFile.toFile());
    assertEquals(1, PartLoader.getByString("barrel").getRevision());
    Files.writeString(partFile, "barrel: {stats: ['damage 3']}");
    parts.load(partFile.toFile());
    assertEquals(2, PartLoader.getByString("barrel").getRevision());
    assertNull(PartLoader.getByString("other"));
    ammo.load(file("ammo.yml", "ball: {amount: 4}").toFile());
    assertEquals(4, AmmunitionLoader.getByString("ball").getAmount());
    assertNull(AmmunitionLoader.getByString("missing"));
    skins.load(file("skins.yml", "steel: {carry: STICK}").toFile());
    assertEquals("STICK", SkinLoader.getByString("steel").getCarry());
    assertNull(SkinLoader.getByString("missing"));
    for (var loader : List.of(data, parts, ammo, skins)) {
      loader.load(temp.resolve("missing").toFile());
      loader.load(file(UUID.randomUUID() + ".yml", "x: [").toFile());
    }
    assertTrue(PartDataLoader.get().isEmpty());
    // a parts file that can't be read keeps the parts already loaded and pauses missing-part refunds
    assertNotNull(PartLoader.getByString("barrel"));
    assertFalse(PartLoader.isHealthy());
    parts.load(partFile.toFile());
    assertTrue(PartLoader.isHealthy());
    PartLoader.clear();
    assertFalse(PartLoader.isHealthy()); // nothing loaded
    assertTrue(AmmunitionLoader.get().isEmpty());
    assertTrue(SkinLoader.get().isEmpty());
  }

  @Test
  void revisionFilesRoundTripNormalizeIdsAndRecoverFromBadInput() throws Exception {
    RevisionTracker tracker = new RevisionTracker();
    tracker.flush();
    assertEquals(1, tracker.resolvePart("BeforeLoad", "hash"));
    tracker.flush();
    tracker.load(temp.toFile());
    assertEquals(1, tracker.resolvePart("BARREL", "one"));
    assertEquals(1, tracker.resolvePart("barrel", "one"));
    assertEquals(2, tracker.resolvePart("barrel", "two"));
    tracker.resolvePart("action", "a");
    tracker.flush();
    tracker.flush();
    Path path = temp.resolve("data/revisions.json");
    assertTrue(Files.readString(path).indexOf("action") < Files.readString(path).indexOf("barrel"));
    tracker.load(temp.toFile());
    assertEquals(2, tracker.resolvePart("Barrel", "two"));
    for (String json :
        List.of(
            "{}",
            "{\"parts\":4}",
            "{\"parts\":{\"BARREL\":{}}}",
            "{\"parts\":{\"BARREL\":{\"revision\":4,\"hash\":\"four\"}}}",
            "invalid")) {
      Files.writeString(path, json);
      tracker.load(temp.toFile());
    }
    Files.delete(path);
    Files.createDirectory(path);
    tracker.load(temp.toFile());
    tracker.resolvePart("barrel", "x");
    tracker.flush();
    assertTrue(Files.isDirectory(path));
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        RevisionTracker.sha256("abc"));
    try (MockedStatic<MessageDigest> digests = mockStatic(MessageDigest.class)) {
      digests
          .when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("provider absent"));
      assertInstanceOf(
          NoSuchAlgorithmException.class,
          assertThrows(RuntimeException.class, () -> RevisionTracker.sha256("x")).getCause());
    }
  }
}

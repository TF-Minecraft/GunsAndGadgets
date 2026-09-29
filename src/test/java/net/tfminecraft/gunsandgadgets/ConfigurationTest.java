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
""")
            .toFile());
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
    assertTrue(PartLoader.get().isEmpty());
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

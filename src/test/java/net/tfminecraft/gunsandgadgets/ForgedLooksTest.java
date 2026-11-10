package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.SoundType;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.forge.Metal;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart.PartSound;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.shooter.ShotFx;
import net.tfminecraft.gunsandgadgets.util.SoundPlayer;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Forged looks: pack models and icons, layered sounds, ignition, smoke, flash and trail. */
class ForgedLooksTest extends GunsTestSupport {

  static final String TYPES = "barrel: {slot: 10}\nloader: {slot: 11}\nchamber: {slot: 12}\naction: {slot: 13}\nstock: {slot: 14}\n";

  static final String METALS =
      """
steel:
  name: Steel
  flash: "#fff1c4"
  trail: f4f4f4
  sound-pitch: 1.1
  sound-volume: 2
  sounds:
    - shoot minecraft:ring pitch=1.5 volume=0.4
maplewood: {name: Maple, kind: wood}
plain: {name: Plain, flash: "zz", sound-pitch: -3}
""";

  static final String PARTS =
      """
barrel:
  part-type: barrel
  type: [rifle]
  tendencies: [accuracy 1]
  sounds:
    - shoot a,b pitch=1.05
  smoke: 1.5
  smoke-cone: 0.4
  backblast: true
loader: {part-type: loader, type: [rifle], tendencies: [reload 1]}
chamber:
  part-type: chamber
  type: [rifle]
  tendencies: [accuracy 1]
  sound-pitch: 0.5
  sound-volume: 0.5
action:
  part-type: action
  type: [rifle]
  tendencies: [accuracy 1]
  ignition: FLINT
stock: {part-type: stock, type: [rifle], material: wood, tendencies: [accuracy 1]}
old: {part-type: barrel, type: [rifle], smoke: .nan}
""";

  void load() throws Exception {
    new PartDataLoader().load(file("types.yml", TYPES).toFile());
    new MetalLoader().load(file("metals.yml", METALS).toFile());
    new PartLoader().load(file("parts.yml", PARTS).toFile());
  }

  List<GunPart> gun(String... ids) {
    List<GunPart> parts = new ArrayList<>();
    for (String id : ids) parts.add(PartLoader.getByString(id));
    return parts;
  }

  // ---------------------------------------------------------------- sound lines
  @Test
  void soundLinesReadPitchVolumeAndGunType() {
    Map<SoundType, List<PartSound>> target = new HashMap<>();
    PartSound.parseSection(
        List.of(
            "shoot a,b(rifle) pitch=1.2 volume=0.5",
            "reload c(nope) speed=3 pitch=x pitch",
            "bogus d",
            "shoot",
            "shoot e",
            "shoot g,,h"),
        target,
        "test");
    PartSound first = target.get(SoundType.SHOOT).get(0);
    assertEquals(List.of("a", "b"), first.getSoundKeys());
    assertEquals(GunType.RIFLE, first.getRequiredType());
    assertEquals(1.2, first.getPitch(), 1e-9);
    assertEquals(0.5, first.getVolume(), 1e-9);
    assertEquals("|1.2|0.5", first.levelSuffix());
    PartSound reload = target.get(SoundType.RELOAD).get(0);
    assertNull(reload.getRequiredType());
    assertEquals(1.0, reload.getPitch(), 1e-9);
    assertEquals("", reload.levelSuffix());
    assertEquals(3, target.get(SoundType.SHOOT).size());
    assertEquals(List.of("g", "h"), target.get(SoundType.SHOOT).get(2).getSoundKeys());
    assertEquals("|2|1", PartSound.levelSuffix(2, 1));
    assertEquals("|1|2", PartSound.levelSuffix(1, 2));
    assertEquals("|0.125|1", PartSound.levelSuffix(0.125, 1));
    assertEquals(1.0, new PartSound(List.of("x"), null, Double.NaN, -1).getVolume(), 1e-9);
  }

  @Test
  void soundGroupsPlayAtTheirPitchAndVolume() {
    World world = mock(World.class);
    Location loc = new Location(world, 0, 0, 0);
    SoundPlayer.playSounds(loc, "a|1.5|0.5;b|9|x;c|y;d|-1|-2;e", false, 8f);
    verify(world).playSound(loc, "a", SoundCategory.PLAYERS, 4f, 1.5f);
    verify(world).playSound(loc, "b", SoundCategory.PLAYERS, 8f, 2f);
    verify(world).playSound(loc, "c", SoundCategory.PLAYERS, 8f, 1f);
    verify(world).playSound(loc, "d", SoundCategory.PLAYERS, 8f, 1f);
    verify(world).playSound(loc, "e", SoundCategory.PLAYERS, 8f, 1f);
    SoundPlayer.playSounds(loc, "f|NaN|Infinity", false, 1f);
    verify(world).playSound(loc, "f", SoundCategory.PLAYERS, 1f, 1f);
    SoundPlayer.playSounds(loc, "x|0.1|1", false, 1f);
    verify(world).playSound(loc, "x", SoundCategory.PLAYERS, 1f, 0.5f);
  }

  // ---------------------------------------------------------------- parts and materials
  @Test
  void partsAndMaterialsCarryTheirLooks() throws Exception {
    load();
    GunPart barrel = PartLoader.getByString("barrel@steel");
    assertEquals(1.5, barrel.getSmoke(), 1e-9);
    assertEquals(0.4, barrel.getSmokeCone(), 1e-9);
    assertTrue(barrel.hasBackblast());
    assertNull(barrel.getIgnition());
    assertTrue(barrel.buildRevisionContent().contains("fx=null,1.5,0.4,true,1.0,1.0;"));
    assertTrue(barrel.buildRevisionContent().contains("SHOOT::a|b|1.05|1"));
    GunPart action = PartLoader.getByString("action@steel");
    assertEquals("FLINT", action.getIgnition());
    GunPart chamber = PartLoader.getByString("chamber@steel");
    assertEquals(0.5, chamber.getSoundPitch(), 1e-9);
    assertEquals(0.5, chamber.getSoundVolume(), 1e-9);
    assertEquals(1.0, PartLoader.getByString("old").getSmoke(), 1e-9);
    assertEquals(0.0, PartLoader.getByString("old").getSmokeCone(), 1e-9);

    Metal steel = MetalLoader.getByString("steel");
    assertEquals(Color.fromRGB(0xfff1c4), steel.getFlash());
    assertEquals(Color.fromRGB(0xf4f4f4), steel.getTrail());
    assertEquals(1.1, steel.getSoundPitch(), 1e-9);
    assertEquals(2.0, steel.getSoundVolume(), 1e-9);
    assertEquals(List.of("minecraft:ring"), steel.getSounds().get(SoundType.SHOOT).get(0).getSoundKeys());
    Metal plain = MetalLoader.getByString("plain");
    assertNull(plain.getFlash());
    assertNull(plain.getTrail());
    assertEquals(1.0, plain.getSoundPitch(), 1e-9);
    assertTrue(plain.getSounds().isEmpty());
  }

  // ---------------------------------------------------------------- firing effects
  @Test
  void shotLookComesFromTheLockBarrelAndBarrelMaterial() throws Exception {
    load();
    ShotFx fx = ShotFx.of(gun("barrel@steel", "action@steel", "chamber@steel"));
    assertEquals("flint", fx.ignition());
    assertEquals(1.5, fx.smoke(), 1e-9);
    assertEquals(0.4, fx.cone(), 1e-9);
    assertTrue(fx.backblast());
    assertEquals(Color.fromRGB(0xfff1c4), fx.flash());
    assertEquals(Color.fromRGB(0xf4f4f4), fx.trail());
    assertEquals(15, fx.smokeCount());
    assertEquals(fx, ShotFx.decode(fx.encode()));
    ShotFx flashOnly = new ShotFx(null, 1.0, ShotFx.DEFAULT_CONE, false, Color.fromRGB(0xfff1c4), null);
    assertFalse(flashOnly.encode().contains("trail="));
    assertEquals(flashOnly, ShotFx.decode(flashOnly.encode()));

    ShotFx bare = ShotFx.of(gun("old", "loader@steel"));
    assertNull(bare.flash());
    assertEquals(ShotFx.DEFAULT_CONE, bare.cone(), 1e-9);
    assertEquals(ShotFx.NONE, bare);
    assertNull(ShotFx.barrelMetal(gun("old")));
    GunPart loose = mock(GunPart.class);
    assertNull(ShotFx.barrelMetal(List.of(loose)));

    var item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    var key = new NamespacedKey("gunsandgadgets", "shoot_fx");
    ShotFx.stamp(gun("barrel@steel"), meta.getPersistentDataContainer(), key);
    assertTrue(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING).contains("trail=f4f4f4"));
    ShotFx.stamp(gun("old"), meta.getPersistentDataContainer(), key);
    assertFalse(meta.getPersistentDataContainer().has(key));
  }

  @Test
  void brokenStampedLooksKeepTheOldEffects() {
    assertSame(ShotFx.NONE, ShotFx.decode(null));
    assertSame(ShotFx.NONE, ShotFx.decode(" "));
    ShotFx fx = ShotFx.decode("junk;smoke=x;cone=y;flash=red;trail=12;other=1;backblast=true;ignition=arc");
    assertEquals(1.0, fx.smoke(), 1e-9);
    assertEquals(ShotFx.DEFAULT_CONE, fx.cone(), 1e-9);
    assertNull(fx.flash());
    assertNull(fx.trail());
    assertTrue(fx.backblast());
    assertEquals("arc", fx.ignition());
  }

  @Test
  void ignitionAndFlashSpawnAtTheLockAndMuzzle() {
    World world = mock(World.class);
    Location eye = new Location(world, 0, 1.6, 0);
    Vector north = new Vector(0, 0, -1);
    for (String kind : List.of("flint", "ember", "wheel", "steam", "arc", "fuse", "unknown")) {
      new ShotFx(kind, 1, 0.2, false, null, null).spawnIgnition(eye, north);
    }
    ShotFx.NONE.spawnIgnition(eye, north);
    new ShotFx("flint", 1, 0.2, false, null, null).spawnIgnition(eye, new Vector(0, 1, 0));
    verify(world, times(4)).spawnParticle(eq(Particle.CRIT), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    verify(world, times(2)).spawnParticle(eq(Particle.SMALL_FLAME), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    verify(world, times(2)).spawnParticle(eq(Particle.SMOKE), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    verify(world).spawnParticle(eq(Particle.FLAME), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    verify(world).spawnParticle(eq(Particle.CLOUD), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    verify(world).spawnParticle(eq(Particle.ELECTRIC_SPARK), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

    ShotFx.NONE.spawnFlash(eye);
    new ShotFx(null, 1, 0.2, false, Color.RED, null).spawnFlash(eye);
    verify(world).spawnParticle(eq(Particle.DUST), eq(eye), eq(6), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class), eq(true));
  }

  // ---------------------------------------------------------------- pack models and icons
  @Test
  void forgedGunsPickEachPartsModelByString() throws Exception {
    load();
    assertEquals(
        List.of("barrel", "steel", "loader", "steel", "chamber", "steel", "action", "steel", "stock", "maplewood"),
        ForgedLook.modelStrings(gun("barrel@steel", "loader@steel", "chamber@steel", "action@steel", "stock@maplewood")));
    GunPart untyped = mock(GunPart.class);
    assertEquals(
        List.of("old", "none", "none", "none", "none", "none", "none", "none", "none", "barkwood"),
        ForgedLook.modelStrings(List.of(untyped, PartLoader.getByString("old"))));

    new SkinLoader()
        .load(file("skins.yml", "forged_rifle: {forged-model: TFMC_Guns:Rifle, carry: stone_hoe, types: [rifle]}\nold: {carry: stone_hoe.5, types: [rifle]}\n").toFile());
    SkinData forged = SkinLoader.getByString("forged_rifle");
    SkinData old = SkinLoader.getByString("old");
    assertTrue(forged.isForged());
    assertFalse(old.isForged());
    assertEquals(NamespacedKey.fromString("tfmc_guns:rifle_aim"), forged.getForgedModel(SkinState.AIM));
    assertNull(old.getForgedModel(SkinState.CARRY));

    // MockBukkit has no item models: check the calls on a mocked meta
    var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
    var cmd = mock(org.bukkit.inventory.meta.components.CustomModelDataComponent.class);
    when(meta.getCustomModelDataComponent()).thenReturn(cmd);
    ForgedLook.stamp(meta, old, SkinState.CARRY, gun("barrel@steel"));
    verify(meta, never()).setItemModel(any());
    ForgedLook.stamp(meta, forged, SkinState.RELOAD, null);
    verify(meta).setItemModel(NamespacedKey.fromString("tfmc_guns:rifle_reload"));
    verify(cmd, never()).setStrings(any());
    ForgedLook.stamp(meta, forged, SkinState.CARRY, gun("barrel@steel"));
    verify(meta).setItemModel(NamespacedKey.fromString("tfmc_guns:rifle_carry"));
    verify(cmd).setFloats(List.of());
    verify(cmd).setStrings(ForgedLook.modelStrings(gun("barrel@steel")));
    verify(meta).setCustomModelDataComponent(cmd);

    assertNull(ForgedLook.stampedParts(new ItemStack(Material.STICK)));
    var stamped = new ItemStack(Material.STICK);
    var provenance = mock(GunCraftProvenance.class);
    when(provenance.resolveStampedParts()).thenReturn(new GunCraftProvenance.ResolvedParts(gun("barrel@steel"), List.of()));
    try (var reads = mockStatic(GunCraftProvenance.class)) {
      reads.when(() -> GunCraftProvenance.readFrom(stamped)).thenReturn(provenance);
      assertEquals(gun("barrel@steel"), ForgedLook.stampedParts(stamped));
    }
  }

  @Test
  void forgedGunsUseTheForgedSkinOfTheirType() throws Exception {
    load();
    new SkinLoader()
        .load(file("skins.yml", "forged_pistol: {forged-model: tfmc_guns:pistol, types: [pistol]}\nforged_rifle: {forged-model: tfmc_guns:rifle, types: [rifle]}\nrifle_flintlock: {carry: stone_hoe.25, types: [rifle]}\n").toFile());
    SkinResolver resolver = new SkinResolver(SkinLoader.get());
    assertEquals("forged_rifle", resolver.resolve(GunType.RIFLE, gun("barrel@steel", "action@steel")).getId());
    assertNull(resolver.resolve(GunType.SHOTGUN, gun("barrel@steel")));
    assertNull(resolver.resolve(GunType.RIFLE, List.of()));
    // a gun with an unforged part keeps the old designs
    assertNotEquals("forged_rifle", String.valueOf(resolver.resolve(GunType.RIFLE, gun("barrel@steel", "old"))));
  }

  @Test
  void partIconsFollowThePartAndItsMaterial() throws Exception {
    load();
    GunPart barrel = PartLoader.getByString("barrel@steel");
    GunPart old = PartLoader.getByString("old");
    String before = Cache.partIconPrefix;
    try {
      Cache.partIconPrefix = "";
      assertNull(ForgedLook.partIcon(barrel, true));
      Cache.partIconPrefix = null;
      assertNull(ForgedLook.partIcon(barrel, true));
      Cache.partIconPrefix = "tfmc_guns:part/";
      assertNull(ForgedLook.partIcon(old, true));
      assertEquals(NamespacedKey.fromString("tfmc_guns:part/barrel"), ForgedLook.partIcon(barrel, false));
      assertEquals(NamespacedKey.fromString("tfmc_guns:part/barrel_steel"), ForgedLook.partIcon(barrel, true));
      GunPart noMetal = mock(GunPart.class);
      when(noMetal.isForged()).thenReturn(true);
      when(noMetal.getBaseId()).thenReturn("odd");
      assertEquals(NamespacedKey.fromString("tfmc_guns:part/odd"), ForgedLook.partIcon(noMetal, true));
      var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
      ForgedLook.applyIcon(meta, barrel, true);
      verify(meta).setItemModel(NamespacedKey.fromString("tfmc_guns:part/barrel_steel"));
      ForgedLook.applyIcon(meta, old, false);
      verifyNoMoreInteractions(meta);
    } finally {
      Cache.partIconPrefix = before;
    }
  }

  @Test
  void typeButtonsAreSteelGunsAndNamesTakeTheirTiersColour() throws Exception {
    String before = Cache.typeIconPrefix;
    try {
      Cache.typeIconPrefix = null;
      assertFalse(ForgedLook.typeIconsOn());
      Cache.typeIconPrefix = " ";
      var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
      assertFalse(ForgedLook.applyTypeIcon(meta, GunType.RIFLE));
      verifyNoInteractions(meta);
      Cache.typeIconPrefix = "tfmc_guns:";
      var component = mock(org.bukkit.inventory.meta.components.CustomModelDataComponent.class);
      when(meta.getCustomModelDataComponent()).thenReturn(component);
      for (GunType type : GunType.values()) {
        assertTrue(ForgedLook.applyTypeIcon(meta, type));
        verify(meta).setItemModel(NamespacedKey.fromString("tfmc_guns:" + type.name().toLowerCase() + "_carry"));
      }
      verify(component).setStrings(List.of("launcher_barrel", "steel", "muzzle_loader", "steel", "single_shot_launcher", "steel", "rocketlock", "steel", "none", "barkwood"));
    } finally {
      Cache.typeIconPrefix = before;
    }
    new MetalLoader().load(file("tiers.yml", """
iron: {name: "#d8d8d8Iron", tier: 1, color: "#d8d8d8"}
steel: {name: Steel, tier: 2}
maple: {name: Maple, kind: wood, tier: 1, color: b3ad71}
bad: {name: Bad, tier: 3, color: nope}
""").toFile());
    assertEquals("#d8d8d8", ForgedLook.tierColor(1));
    assertEquals("§7", ForgedLook.tierColor(2));
    assertEquals("§7", ForgedLook.tierColor(3));
    assertEquals("#b3ad71", MetalLoader.getByString("maple").getColor());
    // a coloured material writes the whole part name in its colour
    new PartLoader().load(file("named.yml", """
tube: {name: "§7Long Tube", part-type: barrel, type: [rifle], tendencies: [accuracy 1]}
""").toFile());
    assertTrue(PartLoader.getByString("tube@iron").getName().endsWith("Iron Long Tube"));
    assertEquals("Steel §7Long Tube", PartLoader.getByString("tube@steel").getName());
  }
}

package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.forge.*;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.stats.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class ForgeTest extends GunsTestSupport {
  static final String NL = System.lineSeparator();

  static final String METALS =
      """
iron:
  name: "§7Iron"
  tier: 1
  cost: v.iron_ingot
  permission: professions.iron_smith
  description: ["§7Crude"]
  better: 0.8
  worse: 1.25
mythril:
  name: "§bMythril"
  kind: METAL
  tier: 4
  cost: m.materials.mythril_ingot
  permission: professions.mythril_smith
  better: {default: 1.25, speed: 1.6}
  worse: {default: 0.75, reload: 0.5}
oak:
  name: Oak
  kind: wood
  tier: 0
not-a-section: 5
""";

  void loadMetals() throws Exception {
    new MetalLoader().load(file("metals.yml", METALS).toFile());
  }

  void loadAmmo() throws Exception {
    new AmmunitionLoader()
        .load(
            file(
                    "ammo.yml",
                    """
ironshot: {family: shot, tier: 1}
ironshot_powered: {family: SHOT, tier: 1}
mythrilshot: {family: shot, tier: 4, powered: false}
ironrocket: {family: rocket, tier: 1}
loose: {}
""")
                .toFile());
  }

  @Test
  void metalsScaleUpsidesAndDownsidesWithTheirOwnCharacter() throws Exception {
    loadMetals();
    Metal iron = MetalLoader.getByString("IRON");
    assertEquals("iron", iron.getId());
    assertEquals("metal", iron.getKind());
    assertEquals(1, iron.getTier());
    assertEquals("v.iron_ingot", iron.getCost());
    assertEquals("professions.iron_smith", iron.getPermission());
    assertEquals(List.of("§7Crude"), iron.getDescription());
    assertEquals(8.0, iron.scale("accuracy", 10), 1e-9);
    assertEquals(-12.5, iron.scale("reload", -10), 1e-9);
    Metal mythril = MetalLoader.getByString("mythril");
    assertEquals("metal", mythril.getKind());
    assertEquals(16.0, mythril.scale("SPEED", 10), 1e-9);
    assertEquals(12.5, mythril.scale("accuracy", 10), 1e-9);
    assertEquals(-5.0, mythril.scale("reload", -10), 1e-9);
    assertEquals(-7.5, mythril.scale("pierce", -10), 1e-9);
    Metal oak = MetalLoader.getByString("oak");
    assertEquals(1, oak.getTier()); // tiers start at 1
    assertEquals("", oak.getCost());
    assertEquals("", oak.getPermission());
    assertEquals(3.0, oak.scale("accuracy", 3), 1e-9);
    assertNull(MetalLoader.getByString(null));
    assertNull(MetalLoader.getByString("not-a-section"));
    assertEquals(List.of("iron", "mythril"), MetalLoader.ofKind("metal").stream().map(Metal::getId).toList());
    assertEquals(3, MetalLoader.getOrdered().size());
    // Typos never flip or poison a stat: NaN, infinite and negative factors read as 1.
    new MetalLoader()
        .load(file("bad.yml", "steel: {better: -2, worse: {default: .nan, reload: .inf}}").toFile());
    Metal steel = MetalLoader.getByString("steel");
    assertEquals(4.0, steel.scale("accuracy", 4), 1e-9);
    assertEquals(-4.0, steel.scale("reload", -4), 1e-9);
    assertEquals(-4.0, steel.scale("pierce", -4), 1e-9);
    new MetalLoader().load(temp.resolve("missing").toFile());
    assertTrue(MetalLoader.getOrdered().isEmpty());
    new MetalLoader().load(file("broken.yml", "x: [").toFile());
    assertTrue(MetalLoader.getOrdered().isEmpty());
  }

  @Test
  void ammunitionKnowsItsFamilyTierAndCharge() throws Exception {
    loadAmmo();
    Ammunition iron = AmmunitionLoader.getByString("ironshot");
    assertEquals("shot", iron.getFamily());
    assertEquals(1, iron.getTier());
    assertFalse(iron.isPowered());
    assertTrue(AmmunitionLoader.getByString("ironshot_powered").isPowered()); // from the id
    assertFalse(AmmunitionLoader.getByString("mythrilshot").isPowered());
    assertEquals("", AmmunitionLoader.getByString("loose").getFamily());
    assertEquals(List.of("ironshot", "ironshot_powered"), AmmunitionLoader.family("shot", 3));
    assertEquals(List.of("ironshot", "ironshot_powered", "mythrilshot"), AmmunitionLoader.family("shot", 4));
    assertEquals(List.of(), AmmunitionLoader.family("", 4));
  }

  @Test
  void definitionsForgeOnePartPerMaterial() throws Exception {
    loadMetals();
    loadAmmo();
    ConfigurationSection def =
        yaml(
                """
heavy:
  name: "§7Heavy Barrel"
  part-type: barrel
  type: [rifle]
  cost-amount: 6
  cost: ["v.leather(2)"]
  ammo-family: shot
  caliber: [extra]
  caliber-override: [ignored]
  stats: [ignored 1]
  tier: 9
  ammo-filter: powered
  skin-base: flintlock
  tendencies: [accuracy 4, speed 10, reload -2, accuracy 1, bogus 3, accuracy, speed x, damage NaN]
  fixed: [capacity 1]
  by-metal:
    mythril: [capacity 3]
  class: [Musketeer]
""")
            .getConfigurationSection("heavy");
    assertTrue(PartForge.isForged(def));
    assertFalse(PartForge.isForged(null));
    assertFalse(PartForge.isForged(yaml("a: {stats: []}").getConfigurationSection("a")));
    Map<String, ConfigurationSection> variants = PartForge.expand("heavy", def);
    assertEquals(List.of("heavy@iron", "heavy@mythril"), new ArrayList<>(variants.keySet()));

    ConfigurationSection iron = variants.get("heavy@iron");
    assertEquals("§7Iron §7Heavy Barrel", iron.getString("name"));
    assertEquals(1, iron.getInt("tier"));
    // accuracy (4 + 1) * 0.8 = 4, speed 10 * 0.8 = 8, reload -2 * 1.25 = -2.5 rounds away from zero.
    assertEquals(List.of("accuracy 4", "speed 8", "reload -3", "capacity 1"), iron.getStringList("stats"));
    assertEquals(List.of("v.iron_ingot(6)", "v.leather(2)"), iron.getStringList("cost"));
    assertEquals(List.of("professions.iron_smith"), iron.getStringList("permissions"));
    assertEquals(List.of("extra", "ironshot", "ironshot_powered"), iron.getStringList("caliber"));
    assertEquals("powered", iron.getString("ammo-filter"));
    assertEquals(List.of("Musketeer"), iron.getStringList("class"));
    assertFalse(iron.contains("caliber-override"));
    assertFalse(iron.contains("tendencies"));

    ConfigurationSection mythril = variants.get("heavy@mythril");
    assertEquals(List.of("accuracy 6", "speed 16", "reload -1", "capacity 4"), mythril.getStringList("stats"));
    assertEquals(List.of("extra", "ironshot", "ironshot_powered", "mythrilshot"), mythril.getStringList("caliber"));

    // A part limited to some materials, a wood part, and a material without cost or permission.
    ConfigurationSection woodDef =
        yaml(
                """
stock:
  material: wood
  permissions: [own.permission]
  tendencies: [accuracy 2]
""")
            .getConfigurationSection("stock");
    ConfigurationSection oak = PartForge.expand("stock", woodDef).get("stock@oak");
    assertEquals("Oak stock", oak.getString("name"));
    assertEquals(List.of(), oak.getStringList("cost"));
    assertEquals(List.of("own.permission"), oak.getStringList("permissions"));
    assertEquals(List.of(), oak.getStringList("caliber"));
    assertEquals(List.of("accuracy 2"), oak.getStringList("stats"));
    ConfigurationSection onlyMythril =
        yaml("p: {metals: [MYTHRIL], cost-amount: 0, tendencies: [speed 1]}").getConfigurationSection("p");
    var only = PartForge.expand("p", onlyMythril);
    assertEquals(List.of("p@mythril"), new ArrayList<>(only.keySet()));
    assertEquals(List.of("m.materials.mythril_ingot(1)"), only.get("p@mythril").getStringList("cost"));
    assertEquals("a@b", PartForge.variantId("a", "b"));
  }

  @Test
  void forgedPartsKnowTheirMaterialTendenciesAndShotLimit() throws Exception {
    loadMetals();
    loadAmmo();
    new PartDataLoader().load(file("types.yml", "barrel: {slot: 10}").toFile());
    new PartLoader()
        .load(
            file(
                    "parts.yml",
                    """
heavy:
  name: "§7Heavy"
  part-type: barrel
  ammo-family: shot
  ammo-filter: PLAIN
  skin-base: flintlock
  tendencies: [accuracy 2, reload -2]
  fixed: [capacity 6, spread 20]
legacy:
  ammo-filter: sideways
  stats: [damage 1]
""")
                .toFile());
    assertEquals(3, PartLoader.getOrdered().size());
    GunPart part = PartLoader.getByString("heavy@mythril");
    assertTrue(part.isForged());
    assertEquals("heavy", part.getBaseId());
    assertEquals("mythril", part.getMetalId());
    assertEquals("§7Heavy", part.getBaseName());
    assertEquals("flintlock", part.getSkinBase());
    assertEquals(GunPart.AmmoFilter.PLAIN, part.getAmmoFilter());
    assertEquals(Map.of(Stats.ACCURACY, 2.0, Stats.RELOAD, -2.0), part.getTendencies());
    assertEquals(Map.of(Stats.CAPACITY, 6.0, Stats.SPREAD, 20.0), part.getFixedStats());
    assertEquals(4, part.getTier());
    assertEquals(1, part.getRevision());
    assertTrue(part.buildRevisionContent().contains("ammo-filter=PLAIN;"));
    assertTrue(part.buildRevisionContent().contains("skin-base=flintlock;"));
    GunPart legacy = PartLoader.getByString("legacy");
    assertFalse(legacy.isForged());
    assertEquals("legacy", legacy.getBaseId());
    assertEquals("legacy", legacy.getSkinBase());
    assertNull(legacy.getMetalId());
    assertNull(legacy.getAmmoFilter()); // an unknown filter is ignored
    assertTrue(legacy.getTendencies().isEmpty());
    assertFalse(legacy.buildRevisionContent().contains("ammo-filter"));
    assertTrue(GunPart.AmmoFilter.POWERED.allows(true));
    assertFalse(GunPart.AmmoFilter.POWERED.allows(false));
    assertTrue(GunPart.AmmoFilter.PLAIN.allows(false));
    assertFalse(GunPart.AmmoFilter.PLAIN.allows(true));
  }

  @Test
  void loreShowsWhatAPartDoesNotHowMuch() throws Exception {
    loadMetals();
    new PartLoader()
        .load(
            file(
                    "parts.yml",
                    """
wide:
  ammo-filter: powered
  options: [smokeless]
  tendencies: [accuracy 2.5, accuracy 0.1, speed -9, pierce 4, reload 3, fire_rate -3, range 7, falloff 7,
    damage_percent -15, moving 20, mounted -41, sneaking 50, capacity 2]
  fixed: [capacity 6, spread 18, reload -14]
medium:
  ammo-filter: plain
  tendencies: [accuracy 1]
  fixed: [capacity 1, spread 10]
tight:
  tendencies: [accuracy 1]
  fixed: [spread 7, spread -7, capacity 0]
""")
                .toFile());
    List<String> wide = strip(ForgeLore.lines(PartLoader.getByString("wide@iron")));
    assertEquals(
        List.of(
            "■ Holds 6 shots",
            "■ Pellet spread: Wide",
            "■ Accuracy: ▲▲",
            "■ Speed: ▼▼▼",
            "■ Pierce: ▲",
            "■ Reload: ▲",
            "■ Fire Rate: ▼",
            "■ Range: ▲▲▲",
            "■ Full Damage Range: ▲",
            "■ Damage %: ▼",
            "■ Steady on the Move: ▲",
            "■ Steady Mounted: ▼▼▼",
            "■ Steady Crouched: ▲▲▲",
            "■ Capacity: ▲▲",
            "■ Reload: ▼▼▼",
            "■ Loads powered shot only",
            "■ Silent: no smoke or muzzle flash"),
        wide);
    List<String> medium = strip(ForgeLore.lines(PartLoader.getByString("medium@iron")));
    assertEquals(
        List.of("■ Pellet spread: Medium", "■ Accuracy: ▲", "■ Loads plain shot only"), medium);
    // A cone that sums to nothing and an empty magazine show neither line.
    List<String> tight = strip(ForgeLore.lines(PartLoader.getByString("tight@iron")));
    assertEquals(List.of("■ Accuracy: ▲"), tight);
    new PartLoader().load(file("tight.yml", "t: {tendencies: [], fixed: [spread 7]}").toFile());
    assertEquals(List.of("■ Pellet spread: Tight"), strip(ForgeLore.lines(PartLoader.getByString("t@iron"))));
  }

  static List<String> strip(List<String> lines) {
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      out.add(line.replaceAll("§x(§[0-9a-fA-F]){6}", "").replaceAll("§.", "").replaceAll("#[0-9a-fA-F]{6}", ""));
    }
    return out;
  }

  @Test
  void statNamesAccuracyCurveAndConfigSwitches() throws Exception {
    assertEquals("Damage %", Stats.DAMAGE_PERCENT.getDisplayName());
    assertEquals("Pellet Spread", Stats.SPREAD.getDisplayName());
    assertEquals("Steady Crouched", Stats.SNEAKING.getDisplayName());
    assertEquals(Stats.FALLOFF, Stats.fromKey("falloff"));
    boolean old = Cache.smoothAccuracy;
    try {
      Cache.smoothAccuracy = false;
      assertEquals(1.2, StatCalculator.calculateAccuracy(20), 1e-9);
      Cache.smoothAccuracy = true;
      assertEquals(2.5 * Math.pow(0.95, 15), StatCalculator.calculateAccuracy(20), 1e-9);
      assertEquals(2.5, StatCalculator.calculateAccuracy(5), 1e-9); // unchanged up to 5
    } finally {
      Cache.smoothAccuracy = old;
    }
    new ConfigLoader()
        .loadConfig(
            file(
                    "config.yml",
                    """
retire-disabled-part-guns: true
handling:
  smooth-accuracy: true
  pellet-cone: {tighten-per-accuracy: 0.05, min-factor: 0.3}
""")
                .toFile());
    assertTrue(Cache.retireDisabledPartGuns);
    assertTrue(Cache.smoothAccuracy);
    assertEquals(0.05, Cache.coneTightenPerAccuracy);
    assertEquals(0.3, Cache.coneMinFactor);
    new ConfigLoader()
        .loadConfig(
            file("bounds.yml", "handling: {pellet-cone: {tighten-per-accuracy: -1, min-factor: 4}}")
                .toFile());
    assertFalse(Cache.retireDisabledPartGuns);
    assertFalse(Cache.smoothAccuracy);
    assertEquals(0.0, Cache.coneTightenPerAccuracy);
    assertEquals(1.0, Cache.coneMinFactor);
    new ConfigLoader().loadConfig(file("defaults.yml", "{}").toFile());
    assertEquals(0.03, Cache.coneTightenPerAccuracy);
    assertEquals(0.4, Cache.coneMinFactor);
  }

  @Test
  void shippedForgeConfigsLoadEveryPartInEveryMaterial() throws Exception {
    new AmmunitionLoader().load(resource("/ammunition.yml"));
    new MetalLoader().load(resource("/metals.yml"));
    new PartDataLoader().load(resource("/part-types.yml"));
    new PartLoader().load(resource("/parts.yml"));
    assertEquals(4, MetalLoader.ofKind("metal").size());
    assertEquals(4, MetalLoader.ofKind("wood").size());
    // A steel heavy barrel matches the calibrated numbers and loads iron and steel shot only.
    GunPart heavy = PartLoader.getByString("heavy_barrel@steel");
    assertEquals(11, heavy.getStats().get(Stats.SPEED));
    assertEquals(20, heavy.getStats().get(Stats.FALLOFF));
    assertEquals(
        List.of("ironshot", "steelshot", "ironshot_powered", "steelshot_powered"), heavy.getCalibers());
    assertEquals(
        8, PartLoader.getByString("heavy_barrel@mythril").getCalibers().size());
    // The multi-tube holds more rockets the better the metal, and has no iron version.
    assertNull(PartLoader.getByString("multi_tube_chamber@iron"));
    assertEquals(2, PartLoader.getByString("multi_tube_chamber@steel").getStats().get(Stats.CAPACITY));
    assertEquals(4, PartLoader.getByString("multi_tube_chamber@mythril").getStats().get(Stats.CAPACITY));
    assertEquals(GunPart.AmmoFilter.POWERED, PartLoader.getByString("proof_chamber@iron").getAmmoFilter());
    // Shotgun barrels set their pellet cone (the spread stat) whatever the metal.
    assertEquals(14, PartLoader.getByString("spread_barrel@iron").getStats().get(Stats.SPREAD));
    assertEquals(22, PartLoader.getByString("sawn_off_barrel@mythril").getStats().get(Stats.SPREAD));
    assertEquals(7, PartLoader.getByString("fowling_barrel@steel").getStats().get(Stats.SPREAD));
    assertEquals("demonwood", PartLoader.getByString("cheek_rest_stock@demonwood").getMetalId());
    for (GunPart part : PartLoader.getOrdered()) {
      assertTrue(part.isForged(), part.getId());
      assertNotNull(part.getPartType(), part.getId());
      assertFalse(part.getCost().isEmpty(), part.getId());
    }
    assertEquals(33 * 4 - 1, PartLoader.getOrdered().size());
  }

  @Test
  void retiredPartsLoadDisabledBesideTheirForgedReplacements() throws Exception {
    loadMetals();
    var loader = new PartLoader();
    loader.load(file("parts.yml", "matchlock: {tendencies: [accuracy 3]}" + NL + "kept: {stats: [damage 1]}").toFile());
    loader.loadRetired(
        file("retired-parts.yml", "matchlock: {stats: [accuracy 1]}" + NL + "kept: {stats: [damage 9]}" + NL + "loose: 3").toFile());
    // The old id resolves for old guns, disabled; the forged variants are separate parts.
    GunPart old = PartLoader.getByString("matchlock");
    assertTrue(old.isDisabled());
    assertFalse(old.isForged());
    assertFalse(PartLoader.getByString("matchlock@iron").isDisabled());
    // parts.yml wins over a retired part with the same id.
    assertEquals(1, PartLoader.getByString("kept").getStats().get(Stats.DAMAGE));
    assertFalse(PartLoader.getByString("kept").isDisabled());
    assertNull(PartLoader.getByString("loose"));
    int count = PartLoader.getOrdered().size();
    loader.loadRetired(temp.resolve("absent.yml").toFile());
    loader.loadRetired(file("broken-retired.yml", "x: [").toFile());
    assertEquals(count, PartLoader.getOrdered().size());
  }

  java.io.File resource(String name) throws Exception {
    return java.nio.file.Path.of(getClass().getResource(name).toURI()).toFile();
  }
}

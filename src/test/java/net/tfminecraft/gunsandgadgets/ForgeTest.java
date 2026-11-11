package net.tfminecraft.gunsandgadgets;

import net.tfminecraft.gunsandgadgets.guns.stats.Stats;

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
    // A file that cannot be read keeps the materials already loaded, so forged parts survive a typo.
    new MetalLoader().load(temp.resolve("missing").toFile());
    assertEquals(List.of("steel"), MetalLoader.getOrdered().stream().map(Metal::getId).toList());
    new MetalLoader().load(file("broken.yml", "x: [").toFile());
    assertEquals(List.of("steel"), MetalLoader.getOrdered().stream().map(Metal::getId).toList());
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
    assertEquals(List.of("capacity 1"), iron.getStringList("forge.fixed"));
    assertEquals(List.of("v.iron_ingot(6)", "v.leather(2)"), iron.getStringList("cost"));
    assertEquals(List.of(), iron.getStringList("permissions"));
    assertEquals("professions.iron_smith", iron.getString("forge.permission"));
    assertEquals(List.of("extra", "ironshot", "ironshot_powered"), iron.getStringList("caliber"));
    assertEquals("powered", iron.getString("ammo-filter"));
    assertEquals(List.of("Musketeer"), iron.getStringList("class"));
    assertFalse(iron.contains("caliber-override"));
    assertFalse(iron.contains("tendencies"));

    ConfigurationSection mythril = variants.get("heavy@mythril");
    assertEquals(List.of("accuracy 6", "speed 16", "reload -1", "capacity 4"), mythril.getStringList("stats"));
    // the material's own extras show in its lore (the multi-tube's rockets per material)
    assertEquals(List.of("capacity 1", "capacity 3"), mythril.getStringList("forge.fixed"));
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
    assertFalse(oak.contains("forge.permission"));
    assertEquals(List.of(), oak.getStringList("caliber"));
    assertEquals(List.of("accuracy 2"), oak.getStringList("stats"));
    ConfigurationSection onlyMythril =
        yaml("p: {metals: [MYTHRIL], cost-amount: 0, tendencies: [speed 1]}").getConfigurationSection("p");
    var only = PartForge.expand("p", onlyMythril);
    assertEquals(List.of("p@mythril"), new ArrayList<>(only.keySet()));
    assertEquals(List.of("m.materials.mythril_ingot(1)"), only.get("p@mythril").getStringList("cost"));
    assertEquals("a@b", PartForge.variantId("a", "b"));
    // A family with no ammunition, or no material to forge from, still loads (with a warning).
    var nothing = yaml("n: {ammo-family: none, metals: [iron], tendencies: [speed 1]}").getConfigurationSection("n");
    var empty = PartForge.expand("n", nothing).get("n@iron");
    assertEquals(List.of(), empty.getStringList("caliber"));
    assertEquals("none", empty.getString("forge.ammo-family"));
    assertTrue(new GunPart("n@iron", empty).declaresAmmoFamily());
    var orphan = yaml("o: {metals: [unobtainium], tendencies: [speed 1]}").getConfigurationSection("o");
    assertTrue(PartForge.expand("o", orphan).isEmpty());
  }

  static final String ANIMALS =
      """
leather: {name: "§fLeather", kind: animal, type: leather, tier: 1, cost: m.materials.leather, better: 0.75}
rare_leather: {name: "§9Good Leather", kind: ANIMAL, type: LEATHER, tier: 2, cost: m.materials.rare_leather, better: 1}
feather: {name: "§fFeather", kind: animal, type: feather, tier: 1, better: 1.5}
""";

  void loadMetalsAndAnimals() throws Exception {
    new MetalLoader().load(file("metals.yml", METALS + ANIMALS).toFile());
  }

  @Test
  void fittedPartsForgeOneVariantPerMaterialAndFitting() throws Exception {
    loadMetalsAndAnimals();
    String stockYaml =
        """
stock:
  name: "§7Stock"
  metals: [iron]
  cost: ["v.string(1)"]
  fitting: {amount: 3, stats: [MOVING]}
  tendencies: [accuracy 2, moving 20, reload -2]
""";
    ConfigurationSection def = yaml(stockYaml).getConfigurationSection("stock");
    Map<String, ConfigurationSection> variants = PartForge.expand("stock", def);
    assertEquals(
        List.of("stock@iron", "stock@iron+leather", "stock@iron+rare_leather", "stock@iron+feather"),
        new ArrayList<>(variants.keySet()));
    assertEquals("a@b+c", PartForge.variantId("a", "b", "c"));
    // The plain variant keeps the old stats (for guns forged before fittings) and is hidden.
    ConfigurationSection plain = variants.get("stock@iron");
    assertTrue(plain.getBoolean("forge.legacy"));
    assertEquals(List.of("accuracy 2", "moving 16", "reload -3"), plain.getStringList("stats"));
    assertEquals(List.of("v.iron_ingot(2)", "v.string(1)"), plain.getStringList("cost"));
    assertFalse(plain.contains("forge.fitting"));
    // A fitting scales only its stats, after the material, and costs its item.
    ConfigurationSection leather = variants.get("stock@iron+leather");
    assertEquals("§7Iron §7Stock §7(§fLeather§7)", leather.getString("name"));
    assertFalse(leather.contains("forge.legacy"));
    assertEquals(List.of("accuracy 2", "moving 12", "reload -3"), leather.getStringList("stats"));
    assertEquals(
        List.of("v.iron_ingot(2)", "v.string(1)", "m.materials.leather(3)"), leather.getStringList("cost"));
    assertEquals("leather", leather.getString("forge.fitting"));
    assertEquals(List.of("moving"), leather.getStringList("forge.fitting-stats"));
    assertEquals("iron", leather.getString("forge.metal"));
    assertEquals("stock", leather.getString("forge.base"));
    assertEquals("professions.iron_smith", leather.getString("forge.permission"));
    assertEquals(List.of("moving 16"), variants.get("stock@iron+rare_leather").getStringList("stats").subList(1, 2));
    // A fitting without a cost adds none.
    ConfigurationSection feather = variants.get("stock@iron+feather");
    assertEquals(List.of("accuracy 2", "moving 24", "reload -3"), feather.getStringList("stats"));
    assertEquals(List.of("v.iron_ingot(2)", "v.string(1)"), feather.getStringList("cost"));

    // 'materials' limits the fittings; the amount is at least one; no match leaves the plain variant.
    var some = yaml("s: {metals: [iron], fitting: {materials: [RARE_LEATHER], amount: 0}, tendencies: [moving 1]}")
        .getConfigurationSection("s");
    var limited = PartForge.expand("s", some);
    assertEquals(List.of("s@iron", "s@iron+rare_leather"), new ArrayList<>(limited.keySet()));
    assertEquals(List.of("v.iron_ingot(2)", "m.materials.rare_leather(1)"), limited.get("s@iron+rare_leather").getStringList("cost"));
    // 'types' takes the fittings of those types only (each animal material's type).
    assertEquals("leather", MetalLoader.getByString("rare_leather").getType());
    assertEquals("", MetalLoader.getByString("iron").getType());
    var typed = yaml("t: {metals: [iron], fitting: {types: [Feather]}, tendencies: [moving 1]}").getConfigurationSection("t");
    assertEquals(List.of("t@iron", "t@iron+feather"), new ArrayList<>(PartForge.expand("t", typed).keySet()));
    var both = yaml("b: {metals: [iron], fitting: {types: [leather], materials: [leather, feather]}, tendencies: [moving 1]}")
        .getConfigurationSection("b");
    assertEquals(List.of("b@iron", "b@iron+leather"), new ArrayList<>(PartForge.expand("b", both).keySet()));
    // a fitting stat the part doesn't have still loads (with a warning) and scales nothing
    var typo = yaml("y: {metals: [iron], fitting: {stats: [firerate, MOVING]}, tendencies: [moving 4]}").getConfigurationSection("y");
    assertEquals(List.of("moving 2"), PartForge.expand("y", typo).get("y@iron+leather").getStringList("stats"));
    var none = yaml("n: {metals: [iron], fitting: {materials: [silk]}, tendencies: [moving 1]}").getConfigurationSection("n");
    assertEquals(List.of("n@iron"), new ArrayList<>(PartForge.expand("n", none).keySet()));

    // Loaded, the variants know their fitting; the plain one resolves but can't be crafted.
    new PartLoader().load(file("fitted.yml", stockYaml).toFile());
    GunPart fitted = PartLoader.getByString("stock@iron+feather");
    assertEquals("feather", fitted.getFittingId());
    assertEquals(List.of("moving"), fitted.getFittingStats());
    assertFalse(fitted.isLegacy());
    assertTrue(fitted.isEnabledForCrafting());
    GunPart legacy = PartLoader.getByString("stock@iron");
    assertNull(legacy.getFittingId());
    assertTrue(legacy.isLegacy());
    assertFalse(legacy.isDisabled());
    assertFalse(legacy.isEnabledForCrafting());
    // Its lore arrows follow the fitting too: feather 24 shows two, leather 12 one.
    assertTrue(strip(ForgeLore.lines(fitted)).contains("■ Steady on the Move: ▲▲"));
    assertTrue(strip(ForgeLore.lines(PartLoader.getByString("stock@iron+leather"))).contains("■ Steady on the Move: ▲"));
    assertTrue(strip(ForgeLore.lines(fitted, false)).contains("■ Steady on the Move: ▲"));
    // A fitting missing from metals.yml (removed after the gun was made) scales nothing.
    loadMetals();
    assertTrue(strip(ForgeLore.lines(fitted)).contains("■ Steady on the Move: ▲"));
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
    assertEquals("professions.mythril_smith", part.getMaterialPermission());
    assertTrue(part.getPermissions().isEmpty());
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
    assertNull(legacy.getMaterialPermission());
    assertFalse(legacy.declaresAmmoFamily());
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
            "■ Pellet Spread: Wide",
            // in iron: smaller upsides, harsher downsides
            "■ Accuracy: ▲",
            "■ Speed: ▼▼▼",
            "■ Pierce: ▲",
            "■ Reload: ▲",
            "■ Fire Rate: ▼▼",
            "■ Range: ▲▲",
            "■ Full Damage Range: ▲",
            "■ Damage %: ▼▼",
            "■ Steady on the Move: ▲",
            "■ Steady Mounted: ▼▼▼",
            "■ Steady Crouched: ▲▲",
            "■ Capacity: ▲▲",
            "■ Reload: ▼▼▼",
            "■ Loads powered shot only",
            "■ Silent: no smoke or muzzle flash"),
        wide);
    List<String> medium = strip(ForgeLore.lines(PartLoader.getByString("medium@iron")));
    assertEquals(
        List.of("■ Pellet Spread: Medium", "■ Accuracy: ▲", "■ Loads plain shot only"), medium);
    // A cone that sums to nothing and an empty magazine show neither line.
    List<String> tight = strip(ForgeLore.lines(PartLoader.getByString("tight@iron")));
    assertEquals(List.of("■ Accuracy: ▲"), tight);
    new PartLoader().load(file("tight.yml", "t: {tendencies: [], fixed: [spread 7]}").toFile());
    assertEquals(List.of("■ Pellet Spread: Tight"), strip(ForgeLore.lines(PartLoader.getByString("t@iron"))));
  }

  @Test
  void eachMaterialCanHaveItsOwnLoreAndStatNotesStartWithACapital() throws Exception {
    loadMetals();
    GunPart part = org.mockito.Mockito.mock(GunPart.class);
    org.mockito.Mockito.when(part.getTendencies())
        .thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(Stats.ACCURACY, 2.0)));
    assertEquals(1, ForgeLore.lines(part).stream().filter(l -> l.contains("Accuracy")).count());
    assertEquals("", MetalLoader.getByString("mythril").getColor());
    for (Stats stat : Stats.values()) {
      assertTrue(Character.isUpperCase(stat.getHint().charAt(0)), stat.name());
    }
    new PartLoader().load(file("lore.yml", """
tube:
  tendencies: [accuracy 1]
  lore: ["§7A tube."]
  metal-lore: {iron: "§7A crude iron tube."}
""").toFile());
    GunPart iron = PartLoader.getByString("tube@iron");
    assertEquals(List.of("§7A crude iron tube."), iron.getLore());
    assertEquals(List.of("§7A tube."), iron.getBaseLore());
    assertEquals(List.of("§7A tube."), PartLoader.getByString("tube@mythril").getLore());
    new PartLoader().load(file("plain.yml", "rod: {lore: [\"§7A rod.\"]}").toFile());
    assertEquals(List.of("§7A rod."), PartLoader.getByString("rod").getBaseLore());
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
    assertEquals(12, MetalLoader.ofKind(PartForge.FITTING_KIND).size());
    // Good quality keeps the written stats; common 0.75, great 1.25, perfect 1.5 on the part's own stats.
    GunPart saddle = PartLoader.getByString("saddle_stock@maplewood");
    assertTrue(saddle.isLegacy());
    assertEquals(saddle.getStats(), PartLoader.getByString("saddle_stock@maplewood+rare_leather").getStats());
    // maplewood mounted 40 x 1.3 = 52, then the fitting
    assertEquals(52, saddle.getStats().get(Stats.MOUNTED));
    assertEquals(78, PartLoader.getByString("saddle_stock@maplewood+legendary_leather").getStats().get(Stats.MOUNTED));
    assertEquals(39, PartLoader.getByString("saddle_stock@maplewood+leather").getStats().get(Stats.MOUNTED));
    assertEquals(saddle.getStats().get(Stats.ACCURACY),
        PartLoader.getByString("saddle_stock@maplewood+leather").getStats().get(Stats.ACCURACY));
    // Every stock and grip takes one type: leather 3, wool 3, feather 2.
    Map<String, String> types = Map.of("saddle_stock", "leather", "cheek_rest_stock", "leather", "standard_grip", "leather",
        "standard_stock", "wool", "wrapped_grip", "wool", "pommel_grip", "wool", "padded_stock", "feather", "birdshead_grip", "feather");
    for (Map.Entry<String, String> entry : types.entrySet()) {
      String base = entry.getKey();
      assertTrue(PartLoader.getByString(base + "@barkwood").isLegacy(), base);
      for (String type : List.of("leather", "wool", "feather")) {
        for (String quality : List.of("", "rare_", "epic_", "legendary_")) {
          GunPart fitted = PartLoader.getByString(base + "@barkwood+" + quality + type);
          assertEquals(type.equals(entry.getValue()), fitted != null, base + "+" + quality + type);
        }
      }
    }
    // the standard stock's quality scales its accuracy; good quality keeps it
    GunPart standard = PartLoader.getByString("standard_stock@maplewood");
    assertEquals(standard.getStats(), PartLoader.getByString("standard_stock@maplewood+rare_wool").getStats());
    assertEquals(6, PartLoader.getByString("standard_stock@maplewood+legendary_wool").getStats().get(Stats.ACCURACY));
    // Give and take (fran 11-11): at every wood and quality the plain Standard stock and grip stay below
    // the Cheek-rest and Pommel, which pay for their accuracy (Cheek-rest 7, Standard grip 2).
    for (String wood : List.of("barkwood", "maplewood", "elderwood", "demonwood")) {
      int cheek = PartLoader.getByString("cheek_rest_stock@" + wood + "+leather").getStats().get(Stats.ACCURACY);
      int pommel = PartLoader.getByString("pommel_grip@" + wood + "+wool").getStats().get(Stats.ACCURACY);
      for (String quality : List.of("", "rare_", "epic_", "legendary_")) {
        assertTrue(PartLoader.getByString("standard_stock@" + wood + "+" + quality + "wool").getStats().get(Stats.ACCURACY) < cheek, wood + quality);
        assertTrue(PartLoader.getByString("standard_grip@" + wood + "+" + quality + "leather").getStats().get(Stats.ACCURACY) < pommel, wood + quality);
      }
    }
    assertEquals(7, PartLoader.getByString("cheek_rest_stock@maplewood+legendary_leather").getStats().get(Stats.ACCURACY));
    assertEquals(3, PartLoader.getByString("standard_grip@maplewood+legendary_leather").getStats().get(Stats.ACCURACY));
    assertEquals(Map.of("m.materials.refined_barkwood", 2, "v.copper_ingot", 1, "v.white_wool", 2),
        PartLoader.getByString("pommel_grip@barkwood+wool").getCost());
    assertEquals(Map.of("m.materials.refined_maplewood", 2, "m.materials.epic_leather", 2),
        PartLoader.getByString("saddle_stock@maplewood+epic_leather").getCost());
    assertEquals(Map.of("m.materials.refined_barkwood", 2, "v.white_wool", 2),
        PartLoader.getByString("wrapped_grip@barkwood+wool").getCost());
    assertEquals(Map.of("m.materials.refined_elderwood", 2, "v.feather", 2),
        PartLoader.getByString("padded_stock@elderwood+feather").getCost());
    assertEquals(List.of("sneaking"), PartLoader.getByString("cheek_rest_stock@demonwood+leather").getFittingStats());
    // No part costs vanilla leather any more (MIReplacer turns all of it into MMOItems leather).
    for (GunPart part : PartLoader.getOrdered()) {
      assertFalse(part.getCost().containsKey("v.leather"), part.getId());
    }
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
    // 37 parts (4 pistol grips), no iron multi-tube, and 8 stocks and grips fitted 4 ways in each of 4 woods
    assertEquals(37 * 4 - 1 + 8 * 4 * 4, PartLoader.getOrdered().size());
    // The part list shows the steel reference arrows, whichever material a player sees first.
    GunPart ironHeavy = PartLoader.getByString("heavy_barrel@iron");
    assertEquals(ForgeLore.lines(heavy), ForgeLore.lines(ironHeavy, false));
    assertNotEquals(ForgeLore.lines(ironHeavy), ForgeLore.lines(ironHeavy, false));
    // The bundled retired parts (the pre-forging defaults) load disabled beside the forged ones.
    new PartLoader().loadRetired(resource("/retired-parts.yml"));
    for (String old : List.of("flintlock", "matchlock", "rifled_barrel_long", "revolver", "quad_shot")) {
      assertTrue(PartLoader.getByString(old).isRetired(), old);
    }
    assertEquals(37 * 4 - 1 + 8 * 4 * 4 + 21, PartLoader.getOrdered().size());
    assertTrue(PartLoader.isHealthy());
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
    assertTrue(old.isRetired());
    assertFalse(PartLoader.getByString("kept").isRetired());
    assertTrue(PartLoader.isHealthy());
    assertFalse(old.isForged());
    assertFalse(PartLoader.getByString("matchlock@iron").isDisabled());
    // parts.yml wins over a retired part with the same id.
    assertEquals(1, PartLoader.getByString("kept").getStats().get(Stats.DAMAGE));
    assertFalse(PartLoader.getByString("kept").isDisabled());
    assertNull(PartLoader.getByString("loose"));
    int count = PartLoader.getOrdered().size();
    loader.loadRetired(temp.resolve("absent.yml").toFile());
    assertTrue(PartLoader.isHealthy());
    loader.loadRetired(file("broken-retired.yml", "x: [").toFile());
    assertEquals(count, PartLoader.getOrdered().size());
    assertFalse(PartLoader.isHealthy()); // old guns would look like they lost a part
    // a stray line in parts.yml pauses refunds, the rest still loads
    PartLoader.clear();
    loader.load(file("stray.yml", "stray: 3" + NL + "kept: {stats: [damage 1]}").toFile());
    assertNotNull(PartLoader.getByString("kept"));
    assertFalse(PartLoader.isHealthy());
    // retired parts wait for a parts.yml that loads (it decides which ids are still current)
    PartLoader.clear();
    loader.load(file("broken-parts.yml", "x: [").toFile());
    loader.loadRetired(file("retired-again.yml", "old: {stats: [damage 1]}").toFile());
    assertNull(PartLoader.getByString("old"));
  }

  java.io.File resource(String name) throws Exception {
    return java.nio.file.Path.of(getClass().getResource(name).toURI()).toFile();
  }
}

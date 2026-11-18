package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.gunsandgadgets.attributes.AttributeData;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.stats.GunSheet;
import net.tfminecraft.gunsandgadgets.guns.stats.GunStats;
import net.tfminecraft.gunsandgadgets.guns.stats.StatApplier;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class GunSheetTest extends GunsTestSupport {

  @AfterEach
  void resetHandling() {
    Cache.falloffMinMultiplier = 1.0;
    Cache.falloffStartDistance = 0.0;
    Cache.aimingEnabled = false;
    Cache.pierceOverflowFullAt = 0.0;
    Cache.singleShotFireRatePerReload = 0.0;
    Cache.singleShotFireRateMax = 1.0;
    Cache.smoothAccuracy = false;
  }

  static List<String> strip(List<String> lines) {
    return lines.stream().map(l -> l.replaceAll("(?i)§[0-9a-fk-orx]", "")).toList();
  }

  Ammunition shot(String yaml) throws Exception {
    return new Ammunition("shot", yaml(yaml));
  }

  static Map<Stats, Double> stats(Object... pairs) {
    Map<Stats, Double> map = new EnumMap<>(Stats.class);
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((Stats) pairs[i], ((Number) pairs[i + 1]).doubleValue());
    }
    return map;
  }

  @Test
  void aGunListsWhatItDoesInRealUnits() throws Exception {
    Cache.smoothAccuracy = false;
    Cache.falloffMinMultiplier = 0.5;
    Cache.falloffStartDistance = 24;
    Cache.drawBaseTicks.put(GunType.PISTOL, 12);
    Cache.aimingEnabled = true;
    Cache.attributes.add(new AttributeData("charisma", yaml("accuracy-per-level: 1")));
    var totals = stats(Stats.DAMAGE_PERCENT, -50, Stats.ACCURACY, 7, Stats.CAPACITY, 6, Stats.FIRE_RATE, 1,
        Stats.RELOAD, -5, Stats.FALLOFF, -4, Stats.MOVING, 30, Stats.PIERCE, -5, Stats.SPEED, 8, Stats.DRAW, 0);
    var steel = shot("stats: [damage 9, accuracy 2, pierce 3]");
    assertEquals(
        List.of(
            "Gun:",
            "■ Damage: 4.5 per hit (50% of Steel Powered Shot)",
            "■ Armour pierce: 0%",
            "■ Spread: 2.15° aimed",
            "■ Reload: 3.75 s",
            "■ Holds: 6 shots",
            "■ Between shots: 0.95 s",
            "■ Full damage to: 20 blocks",
            "■ Range: 96 blocks",
            "■ Bullet speed: 112 blocks/s",
            "■ Draw: 0.6 s",
            "■ Steady on the move: +30%",
            "Reload and spread before Charisma"),
        strip(GunSheet.of(GunType.PISTOL, totals, steel, "Steel Powered Shot").lines()));
  }

  @Test
  void singleShotGunsLeaveOutTheMagazineAndCountFireRateInTheReload() throws Exception {
    Cache.singleShotFireRatePerReload = 4;
    var plain = strip(GunSheet.of(GunType.RIFLE, stats(Stats.FIRE_RATE, 10), null, null).lines());
    assertTrue(plain.contains("■ Damage: 100% of the shot"));
    // fire rate 10 / 4 = 2.5, capped at 1: 60 × 0.95 = 57 ticks
    assertTrue(plain.contains("■ Reload: 2.85 s"));
    assertTrue(plain.contains("■ Spread: 25°")); // no aiming: no "aimed"
    assertTrue(plain.contains("■ Range: 100 blocks")); // no speed stat: the shot's default 10
    assertFalse(plain.stream().anyMatch(l -> l.startsWith("■ Holds") || l.startsWith("■ Between") || l.startsWith("■ Draw")
        || l.startsWith("■ Full damage") || l.startsWith("■ Steady") || l.startsWith("Reload and spread")));
    assertTrue(strip(GunSheet.of(GunType.RIFLE, stats(Stats.FIRE_RATE, -12), null, null).lines())
        .contains("■ Reload: 3.15 s"));
    // off, or a magazine: fire rate stays out of the reload
    Cache.singleShotFireRatePerReload = 0;
    assertTrue(strip(GunSheet.of(GunType.RIFLE, stats(Stats.FIRE_RATE, 10), null, null).lines()).contains("■ Reload: 3 s"));
    Cache.singleShotFireRatePerReload = 4;
    assertTrue(strip(GunSheet.of(GunType.RIFLE, stats(Stats.FIRE_RATE, 10, Stats.CAPACITY, 2), null, null).lines())
        .contains("■ Reload: 3 s"));
  }

  @Test
  void shotgunsCountPelletsAndTheirConeAndRocketsTheirBlast() throws Exception {
    var spread = shot("amount: 10\nstats: [damage 1, range 20]");
    var shotgun = strip(GunSheet.of(GunType.SHOTGUN, stats(Stats.SPREAD, 14, Stats.ACCURACY, 4, Stats.RANGE, 2), spread, "Shot").lines());
    assertTrue(shotgun.contains("■ Damage: 1 per pellet × 10 (100% of Shot)"));
    assertTrue(shotgun.contains("■ Pellet cone: 12.32°"));
    assertTrue(shotgun.contains("■ Range: 44 blocks"));
    // a single ball has no cone, whatever the barrel
    assertFalse(strip(GunSheet.of(GunType.SHOTGUN, stats(Stats.SPREAD, 14), shot("stats: [damage 3]"), "Ball").lines())
        .stream().anyMatch(l -> l.startsWith("■ Pellet cone")));
    var rocket = shot("options: [rocket]\nstats: [damage 10, pierce 25]");
    var launcher = strip(GunSheet.of(GunType.LAUNCHER, stats(), rocket, "Rocket").lines());
    assertTrue(launcher.contains("■ Damage: 20 at the centre (100% of Rocket)"));
    assertTrue(launcher.contains("■ Range: 160 blocks"));
    assertTrue(launcher.contains("■ Bullet speed: 60 blocks/s"));
    assertTrue(launcher.contains("■ Armour pierce: 100%"));
    Cache.pierceOverflowFullAt = 20;
    assertTrue(strip(GunSheet.of(GunType.LAUNCHER, stats(), rocket, "Rocket").lines())
        .contains("■ Armour pierce: 100%, ignores 25% of damage reduction"));
    assertTrue(strip(GunSheet.of(GunType.LAUNCHER, stats(Stats.PIERCE, 30), rocket, "Rocket").lines())
        .contains("■ Armour pierce: 100%, ignores 100% of damage reduction"));
  }

  @Test
  void steadinessShowsOnlyWhatTheStanceLimitsLetThrough() {
    var lines = strip(GunSheet.of(GunType.RIFLE, stats(Stats.MOVING, 80, Stats.MOUNTED, -30, Stats.SNEAKING, -70), null, null).lines());
    assertTrue(lines.contains("■ Steady on the move: +50%"));
    assertTrue(lines.contains("■ Steady mounted: -30%"));
    assertTrue(lines.contains("■ Steady crouched: -50%"));
  }

  @Test
  void theGunsmithComparesTwoGunsLineByLine() throws Exception {
    var steel = shot("stats: [damage 10, pierce 2]");
    var before = GunSheet.of(GunType.RIFLE, stats(Stats.RELOAD, 0, Stats.ACCURACY, 5), steel, "Steel Shot");
    var after = GunSheet.of(GunType.RIFLE, stats(Stats.RELOAD, -2, Stats.ACCURACY, 5, Stats.DAMAGE_PERCENT, 10), steel, "Steel Shot");
    var lines = GunSheet.compare(before, after);
    assertEquals(
        List.of("■ Damage: 10 per hit → 11 per hit", "■ Reload: 3 s → 3.3 s"),
        strip(lines));
    assertTrue(lines.get(0).endsWith(StringFormatter.formatHex("#76de91") + "11 per hit"), lines.get(0));
    assertTrue(lines.get(1).endsWith(StringFormatter.formatHex("#FF5555") + "3.3 s"), lines.get(1));
    assertEquals(List.of("§7No change to this gun"), GunSheet.compare(before, before));
    // fire rate on a single-shot gun changes nothing it lists (with the reload rule off)
    assertEquals(List.of("§7No change to this gun"), GunSheet.compare(
        GunSheet.of(GunType.RIFLE, stats(), null, null), GunSheet.of(GunType.RIFLE, stats(Stats.FIRE_RATE, 10), null, null)));
    // a line only one gun has: the pellet cone of a scattergun, or the magazine of a revolver
    var scatter = shot("amount: 6\nstats: [damage 1]");
    var single = GunSheet.of(GunType.SHOTGUN, stats(Stats.SPREAD, 10), scatter, "Scatter");
    var revolver = GunSheet.of(GunType.SHOTGUN, stats(Stats.CAPACITY, 6), steel, "Steel Shot");
    var changed = strip(GunSheet.compare(single, revolver));
    assertTrue(changed.contains("■ Pellet cone: 10° → —"), changed.toString());
    assertTrue(changed.contains("■ Holds: 1 shot → 6 shots"), changed.toString());
    assertTrue(strip(GunSheet.compare(revolver, single)).contains("■ Pellet cone: — → 10°"));
    // the cone has no better side
    assertTrue(GunSheet.compare(single, GunSheet.of(GunType.SHOTGUN, stats(Stats.SPREAD, 20), scatter, "Scatter"))
        .stream().anyMatch(l -> l.endsWith("§e20°")));
    for (GunSheet.Metric metric : before.metrics()) {
      assertNotNull(metric.key());
    }
  }

  @Test
  void statsAreStampedExactlyAndOldWholeNumbersStillRead() {
    ItemStack item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    assertEquals(7.0, GunStats.get(pdc, "reload", 7.0));
    pdc.set(GunStats.valueKey("reload"), PersistentDataType.INTEGER, -3);
    assertEquals(-3.0, GunStats.get(pdc, "reload", 0)); // a gun stamped before the decimals
    GunStats.set(pdc, "reload", -2.5);
    assertEquals(-2.5, GunStats.get(pdc, "reload", 0));
    assertEquals(-2, pdc.get(GunStats.valueKey("reload"), PersistentDataType.INTEGER)); // whole for older readers
    GunStats.remove(pdc, "reload");
    assertEquals(0.0, GunStats.get(pdc, "reload", 0));
    // damage percents multiply, and a part can't take more than all of it
    assertEquals(-59.0, GunStats.combinePercent(List.of(-50.0, -18.0)), 1e-9);
    assertEquals(-100.0, GunStats.combinePercent(List.of(-150.0, 20.0)), 1e-9);
    assertEquals(0.0, GunStats.combinePercent(List.of()), 1e-9);
  }

  @Test
  void combinedStatsSumExceptDamagePercentsWhichMultiply() throws Exception {
    GunPart barrel = new GunPart("barrel", yaml("stats: ['damage_percent -18', 'accuracy 1.3', 'reload 2']"));
    GunPart chamber = new GunPart("chamber", yaml("stats: ['damage_percent -50', 'accuracy 2.6', 'speed NaN']"));
    var totals = StatApplier.combineStats(List.of(barrel, chamber));
    assertEquals(-59.0, totals.get(Stats.DAMAGE_PERCENT), 1e-9);
    assertEquals(3.9, totals.get(Stats.ACCURACY)); // not 3.9000000000000004
    assertEquals(2.0, totals.get(Stats.RELOAD));
    assertFalse(totals.containsKey(Stats.SPEED)); // NaN is no number
    assertFalse(StatApplier.combineStats(List.of(barrel)).get(Stats.DAMAGE_PERCENT).isNaN());
    assertFalse(StatApplier.combineStats(List.of(new GunPart("plain", yaml("stats: ['accuracy 1']")))).containsKey(Stats.DAMAGE_PERCENT));
  }
}

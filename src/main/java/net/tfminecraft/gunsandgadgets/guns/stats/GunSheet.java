package net.tfminecraft.gunsandgadgets.guns.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition.AmmoOption;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * What a gun does, in the units players feel: damage per hit, armour pierce %, spread in degrees,
 * seconds to reload, blocks of full damage. The finished gun lists it; the gunsmith shows how a
 * part would change it ({@link #compare}), so both read the same numbers. It works from the same
 * formulas the shot uses, before the shooter's Charisma and stance.
 */
public final class GunSheet {

    private static final String LABEL = "#c2b48a";
    private static final String BETTER = "#76de91";
    private static final String WORSE = "#FF5555";

    /**
     * One line: the full text on the gun, a short text for comparisons, the number that decides
     * which way is better ({@code better} 1 = higher, -1 = lower, 0 = neither), and whether the
     * gun lists it (a single-shot gun leaves out "Holds 1 shot").
     */
    public record Metric(String key, String label, String text, String brief, double value, int better, boolean listed) {}

    private final List<Metric> metrics;

    private GunSheet(List<Metric> metrics) {
        this.metrics = metrics;
    }

    public List<Metric> metrics() {
        return metrics;
    }

    /**
     * The sheet of a gun with these combined stats ({@link StatApplier#combineStats}) firing
     * {@code shot} (its strongest shot; null when it loads none), named {@code shotName}.
     */
    public static GunSheet of(GunType type, Map<Stats, Double> totals, Ammunition shot, String shotName) {
        List<Metric> metrics = new ArrayList<>();
        Map<String, Integer> shotStats = shot != null ? shot.getStats() : Map.of();
        boolean rocket = shot != null && shot.hasOption(AmmoOption.ROCKET);
        int pellets = shot != null ? Math.max(1, shot.getAmount()) : 1;

        double factor = Math.max(0.0, 1.0 + stat(totals, Stats.DAMAGE_PERCENT) / 100.0);
        if (shot == null) {
            metrics.add(new Metric("damage", "Damage", percent(factor) + " of the shot", percent(factor), factor, 1, true));
        } else {
            double perHit = Math.max(0.0, (stat(totals, Stats.DAMAGE) + shotStats.getOrDefault("damage", 0)) * factor);
            String brief = rocket ? decimal(perHit * 2) + " at the centre"
                    : pellets > 1 ? decimal(perHit) + " per pellet × " + pellets
                    : decimal(perHit) + " per hit";
            metrics.add(new Metric("damage", "Damage", brief + " §8(" + percent(factor) + " of " + shotName + ")",
                    brief, perHit * pellets, 1, true));
        }

        double pierce = stat(totals, Stats.PIERCE) + shotStats.getOrDefault("pierce", 0);
        double share = Math.max(0.0, Math.min(1.0, pierce / 20.0));
        String pierceText = percent(share);
        double pierceValue = share;
        if (pierce > 20 && Cache.pierceOverflowFullAt > 0) {
            double ignored = Math.min(1.0, (pierce - 20) / Cache.pierceOverflowFullAt);
            pierceText += ", ignores " + percent(ignored) + " of damage reduction";
            pierceValue += ignored;
        }
        metrics.add(new Metric("pierce", "Armour pierce", pierceText, pierceText, pierceValue, 1, true));

        double accuracy = stat(totals, Stats.ACCURACY) + shotStats.getOrDefault("accuracy", 0);
        double spread = Math.max(0.02, StatCalculator.calculateAccuracy(accuracy));
        String spreadText = degrees(spread) + (Cache.aimingEnabled ? " aimed" : "");
        metrics.add(new Metric("spread", "Spread", spreadText, spreadText, spread, -1, true));

        double cone = stat(totals, Stats.SPREAD);
        if (cone > 0 && pellets > 1) {
            double coneDegrees = Handling.clampSpread(
                    cone * Math.max(Cache.coneMinFactor, 1.0 - Cache.coneTightenPerAccuracy * accuracy));
            metrics.add(new Metric("cone", "Pellet cone", degrees(coneDegrees), degrees(coneDegrees), coneDegrees, 0, true));
        }

        int capacity = Math.max(1, (int) Math.round(totals.getOrDefault(Stats.CAPACITY, 1.0)));
        double reload = StatCalculator.calculateReloadTicks(
                GunStats.effectiveReload(stat(totals, Stats.RELOAD), stat(totals, Stats.FIRE_RATE), capacity)) / 20.0;
        metrics.add(new Metric("reload", "Reload", seconds(reload), seconds(reload), reload, -1, true));
        String holds = capacity + (capacity == 1 ? " shot" : " shots");
        metrics.add(new Metric("capacity", "Holds", holds, holds, capacity, 1, capacity > 1));
        double between = StatCalculator.calculateFireRate(stat(totals, Stats.FIRE_RATE));
        metrics.add(new Metric("between", "Between shots", seconds(between), seconds(between), between, -1, capacity > 1));

        if (Cache.falloffMinMultiplier < 1.0) {
            double full = Handling.falloffStart(stat(totals, Stats.FALLOFF));
            metrics.add(new Metric("falloff", "Full damage to", blocks(full), blocks(full), full, 1, true));
        }

        double speed = totals.getOrDefault(Stats.SPEED, 10.0);
        double range = (stat(totals, Stats.RANGE) + shotStats.getOrDefault("range", 0)) * 2;
        double maxDistance = range > 0 ? range : rocket ? 160 : Math.max(96, Math.min(192, speed * 10));
        metrics.add(new Metric("range", "Range", blocks(maxDistance), blocks(maxDistance), maxDistance, 1, true));
        double velocity = speed * (rocket ? 0.3 : 0.7) * 20;
        String velocityText = Math.round(velocity) + " blocks/s";
        metrics.add(new Metric("speed", "Bullet speed", velocityText, velocityText, velocity, 1, true));

        int drawTicks = type != null ? Cache.drawBaseTicks.getOrDefault(type, 0) : 0;
        if (drawTicks > 0) {
            double draw = drawTicks * Math.min(2.0, Math.max(0.5, 1.0 - stat(totals, Stats.DRAW) / 100.0)) / 20.0;
            metrics.add(new Metric("draw", "Draw", seconds(draw), seconds(draw), draw, -1, true));
        }

        // As much of each stance as the gun takes off (or adds), after Handling's limits.
        Handling.Steadiness steadiness = Handling.Steadiness.of(
                stat(totals, Stats.MOVING), stat(totals, Stats.MOUNTED), stat(totals, Stats.SNEAKING));
        steady(metrics, "moving", "Steady on the move", (1.0 - steadiness.moving()) * 100.0);
        steady(metrics, "mounted", "Steady mounted", (1.0 - steadiness.mounted()) * 100.0);
        steady(metrics, "sneaking", "Steady crouched", (steadiness.sneaking() - 1.0) * 100.0);
        return new GunSheet(metrics);
    }

    private static void steady(List<Metric> metrics, String key, String label, double percent) {
        long rounded = Math.round(percent);
        String text = (rounded > 0 ? "+" : "") + rounded + "%";
        metrics.add(new Metric(key, label, text, text, percent, 1, rounded != 0));
    }

    /** The lines on the gun: a heading, then every listed metric. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(StringFormatter.formatHex("#b38e88§lGun:"));
        for (Metric metric : metrics) {
            if (metric.listed()) {
                lines.add(StringFormatter.formatHex("§7■ " + LABEL + metric.label() + "§e: §f" + metric.text()));
            }
        }
        if (!Cache.attributes.isEmpty()) {
            lines.add("§8Reload and spread before Charisma");
        }
        return lines;
    }

    /**
     * What changes from {@code before} to {@code after}, one line per metric that reads
     * differently: "Reload: 3.45 s → 3 s", green when better and red when worse. A part that
     * changes nothing on this gun says so.
     */
    public static List<String> compare(GunSheet before, GunSheet after) {
        Map<String, Metric> old = new LinkedHashMap<>();
        for (Metric metric : before.metrics) {
            old.put(metric.key(), metric);
        }
        List<String> lines = new ArrayList<>();
        for (Metric metric : after.metrics) {
            Metric was = old.remove(metric.key());
            if (was != null && (was.brief().equals(metric.brief()) || !(was.listed() || metric.listed()))) {
                continue;
            }
            // a share of the shot (no shot fits) and damage per hit can't say which is better
            boolean comparable = was != null && was.brief().endsWith("%") == metric.brief().endsWith("%");
            lines.add(change(metric, was == null ? "—" : was.brief(), metric.brief(),
                    comparable ? Math.signum(metric.value() - was.value()) * metric.better() : 0));
        }
        for (Metric gone : old.values()) {
            lines.add(change(gone, gone.brief(), "—", 0));
        }
        if (lines.isEmpty()) {
            lines.add("§7No change to this gun");
        }
        return lines;
    }

    private static String change(Metric metric, String from, String to, double direction) {
        String colour = direction > 0 ? BETTER : direction < 0 ? WORSE : "§e";
        return StringFormatter.formatHex("§7■ " + LABEL + metric.label() + "§e: §f" + from + " §7→ " + colour + to);
    }

    private static double stat(Map<Stats, Double> totals, Stats stat) {
        return totals.getOrDefault(stat, 0.0);
    }

    static String decimal(double value) {
        return trim(String.format(Locale.ROOT, "%.1f", value));
    }

    static String seconds(double value) {
        return trim(String.format(Locale.ROOT, "%.2f", value)) + " s";
    }

    static String degrees(double value) {
        return trim(String.format(Locale.ROOT, "%.2f", value)) + "°";
    }

    static String blocks(double value) {
        return Math.round(value) + " blocks";
    }

    static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    /** "3.50" → "3.5", "3.00" → "3" (always formatted with a decimal point). */
    private static String trim(String number) {
        number = number.replaceAll("0+$", "");
        return number.endsWith(".") ? number.substring(0, number.length() - 1) : number;
    }
}

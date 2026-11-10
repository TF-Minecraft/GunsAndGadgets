package net.tfminecraft.gunsandgadgets.guns.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.MetalLoader;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * A forged part's lore says what it does, not by how much: each tendency is one to three arrows,
 * and the material decides the numbers (shown on the assembled gun).
 */
public final class ForgeLore {

    private static final String UP = "#76de91▲";
    private static final String DOWN = "#FF5555▼";

    private ForgeLore() {}

    public static List<String> lines(GunPart part) {
        return lines(part, true);
    }

    /** {@code scaled} false shows the tendencies unscaled, as parts.yml writes them (the steel reference); fixed stats are the part's own. */
    public static List<String> lines(GunPart part, boolean scaled) {
        List<String> lines = new ArrayList<>();
        Map<Stats, Double> fixed = part.getFixedStats();
        Double capacity = fixed.get(Stats.CAPACITY);
        if (capacity != null && capacity > 1) {
            lines.add(format("§7■ #c2b48aHolds " + Math.round(capacity) + " shots"));
        }
        Double cone = fixed.get(Stats.SPREAD);
        if (cone != null && cone > 0) {
            lines.add(format("§7■ #c2b48aPellet Spread§e: §f" + coneWord(cone)));
        }
        // In the part's own material: a mythril upside can show more arrows than an iron one.
        Metal metal = !scaled || part.getMetalId() == null ? null : MetalLoader.getByString(part.getMetalId());
        for (Map.Entry<Stats, Double> tendency : part.getTendencies().entrySet()) {
            double value = tendency.getValue();
            if (metal != null) value = metal.scale(tendency.getKey().getKey(), value);
            lines.add(arrowLine(tendency.getKey(), value));
        }
        for (Map.Entry<Stats, Double> entry : fixed.entrySet()) {
            Stats stat = entry.getKey();
            if (stat != Stats.CAPACITY && stat != Stats.SPREAD) {
                lines.add(arrowLine(stat, entry.getValue()));
            }
        }
        if (part.getAmmoFilter() != null) {
            lines.add(format("§7■ #c2b48aLoads " + (part.getAmmoFilter() == GunPart.AmmoFilter.POWERED
                    ? "powered" : "plain") + " shot only"));
        }
        if (part.hasOption(GunPart.PartOption.SMOKELESS)) {
            lines.add(format("§7■ #c2b48aSilent: no smoke or muzzle flash"));
        }
        return lines;
    }

    static String arrowLine(Stats stat, double value) {
        String arrow = value >= 0 ? UP : DOWN;
        return format("§7■ #c2b48a" + stat.getDisplayName() + "§e: " + arrow.repeat(arrows(stat, value)));
    }

    /** One arrow per step of the stat, from one to three. */
    static int arrows(Stats stat, double value) {
        double step = switch (stat) {
            case ACCURACY -> 2.5;
            case SPEED, PIERCE -> 4.0;
            case RELOAD, FIRE_RATE, RANGE -> 3.0;
            case FALLOFF -> 7.0;
            case DAMAGE_PERCENT -> 15.0;
            case MOVING, MOUNTED, SNEAKING, DRAW -> 20.0;
            default -> 1.0;
        };
        return (int) Math.max(1, Math.min(3, Math.ceil(Math.abs(value) / step)));
    }

    static String coneWord(double degrees) {
        if (degrees >= 18) return "Wide";
        if (degrees >= 10) return "Medium";
        return "Tight";
    }

    private static String format(String line) {
        return StringFormatter.formatHex(line);
    }
}

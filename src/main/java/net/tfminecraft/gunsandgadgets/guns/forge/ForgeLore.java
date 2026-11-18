package net.tfminecraft.gunsandgadgets.guns.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * What a forged part always is, whatever gun it goes on: the shots it holds, its pellet cone, the
 * shot it loads and whether it is silent. How much it changes a gun is not written here: the
 * gunsmith compares the gun with and without it ({@link net.tfminecraft.gunsandgadgets.guns.stats.GunSheet}).
 */
public final class ForgeLore {

    private ForgeLore() {}

    public static List<String> facts(GunPart part) {
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
        if (part.getAmmoFilter() != null) {
            lines.add(format("§7■ #c2b48aLoads " + (part.getAmmoFilter() == GunPart.AmmoFilter.POWERED
                    ? "powered" : "plain") + " shot only"));
        }
        if (part.hasOption(GunPart.PartOption.SMOKELESS)) {
            lines.add(format("§7■ #c2b48aSilent: no smoke or muzzle flash"));
        }
        return lines;
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

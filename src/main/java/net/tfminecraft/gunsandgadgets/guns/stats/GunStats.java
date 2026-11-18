package net.tfminecraft.gunsandgadgets.guns.stats;

import java.util.Collection;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;

/**
 * A crafted gun's stats as {@link StatApplier} stamps them. Since forged stats have one decimal,
 * the exact value sits in {@code stat_exact_<stat>}; {@code stat_value_<stat>} keeps the whole
 * number older guns carry, so a gun stamped before the decimals reads exactly as it did until its
 * parts refresh it.
 */
public final class GunStats {

    private GunStats() {}

    public static NamespacedKey valueKey(String stat) {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_" + stat);
    }

    public static NamespacedKey exactKey(String stat) {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "stat_exact_" + stat);
    }

    /** The exact stat, else the whole-number one, else {@code fallback}. */
    public static double get(PersistentDataContainer pdc, String stat, double fallback) {
        Double exact = pdc.get(exactKey(stat), PersistentDataType.DOUBLE);
        if (exact != null) {
            return exact;
        }
        Integer value = pdc.get(valueKey(stat), PersistentDataType.INTEGER);
        return value != null ? value : fallback;
    }

    public static void set(PersistentDataContainer pdc, String stat, double value) {
        pdc.set(valueKey(stat), PersistentDataType.INTEGER, (int) Math.round(value));
        pdc.set(exactKey(stat), PersistentDataType.DOUBLE, value);
    }

    public static void remove(PersistentDataContainer pdc, String stat) {
        pdc.remove(valueKey(stat));
        pdc.remove(exactKey(stat));
    }

    /**
     * The parts' damage percents together: each one multiplies the shot (-50% and -18% leave
     * 41%), so two penalties never add up to more than either allows on its own.
     */
    public static double combinePercent(Collection<Double> percents) {
        double factor = 1.0;
        for (double percent : percents) {
            factor *= Math.max(0.0, 1.0 + percent / 100.0);
        }
        return (factor - 1.0) * 100.0;
    }

    /**
     * The reload stat a gun reloads with. On a single-shot gun the time between shots is the
     * reload, so its fire rate counts there (handling.single-shot-fire-rate): one reload point
     * per {@code per-reload} fire rate, at most {@code max} either way.
     */
    public static double effectiveReload(double reload, double fireRate, int capacity) {
        if (capacity > 1 || Cache.singleShotFireRatePerReload <= 0) {
            return reload;
        }
        double shift = fireRate / Cache.singleShotFireRatePerReload;
        double max = Cache.singleShotFireRateMax;
        return reload + Math.max(-max, Math.min(max, shift));
    }
}

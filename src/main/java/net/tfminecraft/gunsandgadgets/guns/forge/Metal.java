package net.tfminecraft.gunsandgadgets.guns.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * A material forged parts are made from (metals.yml). Part tendencies are written for the
 * reference material (steel, or maplewood for stocks); a material scales a part's upsides by its
 * "better" factors and its downsides by its "worse" factors, so each one has its own character.
 */
public final class Metal {

    private final String id;
    private final String name;
    private final String kind;
    private final int tier;
    private final String cost;
    private final String permission;
    private final List<String> description = new ArrayList<>();
    private final Map<String, Double> better = new HashMap<>();
    private final Map<String, Double> worse = new HashMap<>();
    private final double betterDefault;
    private final double worseDefault;

    public Metal(String id, ConfigurationSection config) {
        this.id = id.toLowerCase();
        this.name = StringFormatter.formatHex(config.getString("name", id));
        this.kind = config.getString("kind", "metal").toLowerCase();
        this.tier = Math.max(1, config.getInt("tier", 1));
        this.cost = config.getString("cost", "");
        this.permission = config.getString("permission", "");
        for (String line : config.getStringList("description")) {
            description.add(StringFormatter.formatHex(line));
        }
        this.betterDefault = readFactors(config, "better", better);
        this.worseDefault = readFactors(config, "worse", worse);
    }

    /** "better: 1.2" sets every stat; a section sets "default" plus per-stat factors. */
    private static double readFactors(ConfigurationSection config, String path, Map<String, Double> target) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return positive(config.getDouble(path, 1.0));
        }
        for (String stat : section.getKeys(false)) {
            if (!stat.equalsIgnoreCase("default")) {
                target.put(stat.toLowerCase(), positive(section.getDouble(stat, 1.0)));
            }
        }
        return positive(section.getDouble("default", 1.0));
    }

    /** NaN, infinite or negative factors fall back to 1 so a typo never flips a stat. */
    private static double positive(double value) {
        return Double.isFinite(value) && value >= 0 ? value : 1.0;
    }

    /** A reference tendency forged in this material. */
    public double scale(String stat, double value) {
        String key = stat.toLowerCase();
        double factor = value >= 0 ? better.getOrDefault(key, betterDefault) : worse.getOrDefault(key, worseDefault);
        return value * factor;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public int getTier() { return tier; }
    public String getCost() { return cost; }
    public String getPermission() { return permission; }
    public List<String> getDescription() { return description; }
}

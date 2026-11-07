package net.tfminecraft.gunsandgadgets.guns.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.gunsandgadgets.guns.SoundType;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart.PartSound;

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
    // Presentation: a barrel forged from this material colours the muzzle flash and the trail,
    // pitches and loudens the report, and may add sound layers (mythril's ring).
    // The material's colour (its ingot or refined wood), for part names and the gun name by tier.
    private final String color;
    private final Color flash;
    private final Color trail;
    private final double soundPitch;
    private final double soundVolume;
    private final Map<SoundType, List<PartSound>> sounds = new HashMap<>();

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
        this.color = color(config.getString("color", null)) == null ? "" : "#" + config.getString("color").trim().replace("#", "");
        this.flash = color(config.getString("flash", null));
        this.trail = color(config.getString("trail", null));
        this.soundPitch = GunPart.factor(config.getDouble("sound-pitch", 1.0));
        this.soundVolume = GunPart.factor(config.getDouble("sound-volume", 1.0));
        PartSound.parseSection(config.getStringList("sounds"), sounds, "material " + this.id);
    }

    /** "#rrggbb" or "rrggbb"; anything else means no colour. */
    static Color color(String hex) {
        if (hex == null) return null;
        String raw = hex.trim().replace("#", "");
        if (!raw.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return Color.fromRGB(Integer.parseInt(raw, 16));
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

    /** How strongly this material scales a stat's upside (or downside). */
    public double factor(String stat, boolean upside) {
        String key = stat.toLowerCase();
        return upside ? better.getOrDefault(key, betterDefault) : worse.getOrDefault(key, worseDefault);
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
    /** "#rrggbb" or empty: the colour this material's names are written in. */
    public String getColor() { return color; }
    public Color getFlash() { return flash; }
    public Color getTrail() { return trail; }
    public double getSoundPitch() { return soundPitch; }
    public double getSoundVolume() { return soundVolume; }
    public Map<SoundType, List<PartSound>> getSounds() { return sounds; }
}

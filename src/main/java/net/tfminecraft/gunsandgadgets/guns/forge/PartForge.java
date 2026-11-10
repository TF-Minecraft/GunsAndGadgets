package net.tfminecraft.gunsandgadgets.guns.forge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.MetalLoader;

/**
 * Expands a forged part definition (one with {@code tendencies}) into one part per material.
 *
 * <p>A definition says which stats go up or down for the reference material; each material in
 * metals.yml scales them ({@link Metal#scale}). {@code fixed} stats never scale (shots held, a
 * shotgun's pellet cone), {@code by-metal} adds per-material extras, and an {@code ammo-family}
 * barrel loads every shot of that family up to its material's tier. The variant id is
 * {@code <part>@<material>}, so a crafted gun records which material each part was forged from.
 */
public final class PartForge {

    public static final String SEPARATOR = "@";

    /** Keys the forge computes or consumes; everything else is copied to each variant as is. */
    private static final Set<String> FORGE_KEYS = Set.of(
            "name", "tier", "stats", "cost", "permissions", "caliber", "caliber-override",
            "tendencies", "fixed", "by-metal", "metals", "material", "cost-amount", "ammo-family",
            "metal-lore");

    private PartForge() {}

    public static boolean isForged(ConfigurationSection definition) {
        return definition != null && definition.contains("tendencies");
    }

    public static String variantId(String baseId, String metalId) {
        return baseId + SEPARATOR + metalId;
    }

    /** One part section per material the definition can be forged from, keyed by variant id. */
    public static Map<String, ConfigurationSection> expand(String key, ConfigurationSection definition) {
        if (definition.contains("caliber-override")) {
            System.out.println("Forged part " + key + ": caliber-override is ignored; use ammo-family and ammo-filter");
        }
        Map<String, ConfigurationSection> variants = new LinkedHashMap<>();
        String kind = definition.getString("material", "metal");
        List<String> only = definition.getStringList("metals");
        for (Metal metal : MetalLoader.ofKind(kind)) {
            if (!only.isEmpty() && only.stream().noneMatch(m -> m.equalsIgnoreCase(metal.getId()))) {
                continue;
            }
            variants.put(variantId(key, metal.getId()), forge(key, definition, metal));
        }
        if (variants.isEmpty()) {
            // Guns already forged from this part would lose it and break: say so loudly.
            System.out.println("Forged part " + key + " has no material to be forged from; check metals.yml and its 'material'/'metals' keys");
        }
        return variants;
    }

    static ConfigurationSection forge(String key, ConfigurationSection definition, Metal metal) {
        MemoryConfiguration variant = new MemoryConfiguration();
        for (Map.Entry<String, Object> entry : definition.getValues(false).entrySet()) {
            if (!FORGE_KEYS.contains(entry.getKey())) {
                variant.set(entry.getKey(), entry.getValue());
            }
        }
        String baseName = definition.getString("name", key);
        // A coloured material writes the whole name in its colour: "Steel Long Rifled Barrel".
        variant.set("name", metal.getName() + " "
                + (metal.getColor().isEmpty() ? baseName : baseName.replaceFirst("^(§[0-9a-fk-or])+", "")));
        variant.set("tier", metal.getTier());
        variant.set("stats", stats(definition, metal));
        variant.set("cost", cost(definition, metal));
        // Both apply: the material's permission (smithing level) and any lock the part sets itself.
        variant.set("permissions", definition.getStringList("permissions"));
        if (!metal.getPermission().isEmpty()) {
            variant.set("forge.permission", metal.getPermission());
        }

        List<String> calibers = new ArrayList<>(definition.getStringList("caliber"));
        String family = definition.getString("ammo-family", "");
        if (!family.isEmpty()) {
            List<String> shot = AmmunitionLoader.family(family.toLowerCase(), metal.getTier());
            if (shot.isEmpty()) {
                System.out.println("Forged part " + key + " (" + metal.getId() + "): no ammunition of family '"
                        + family + "' up to tier " + metal.getTier() + "; check family/tier in ammunition.yml");
            }
            calibers.addAll(shot);
            // Kept so a gun with this barrel is refused when the family matches nothing.
            variant.set("forge.ammo-family", family.toLowerCase());
        }
        variant.set("caliber", calibers);

        // Lore written for this material, if any; the part's own lore stays for the part list.
        String metalLore = definition.getString("metal-lore." + metal.getId(), "");
        if (!metalLore.isEmpty()) {
            variant.set("lore", List.of(metalLore));
        }
        variant.set("forge.lore", definition.getStringList("lore"));
        variant.set("forge.base", key);
        variant.set("forge.metal", metal.getId());
        variant.set("forge.name", baseName);
        variant.set("forge.tendencies", definition.getStringList("tendencies"));
        List<String> fixed = new ArrayList<>(definition.getStringList("fixed"));
        ConfigurationSection byMetal = definition.getConfigurationSection("by-metal");
        if (byMetal != null) {
            fixed.addAll(byMetal.getStringList(metal.getId()));
        }
        variant.set("forge.fixed", fixed);
        return variant;
    }

    /** Scaled tendencies, then fixed and per-material stats, summed per stat. */
    static List<String> stats(ConfigurationSection definition, Metal metal) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<Stats, Double> tendency : parse(definition.getStringList("tendencies")).entrySet()) {
            String stat = tendency.getKey().getKey();
            totals.merge(stat, round(metal.scale(stat, tendency.getValue())), Integer::sum);
        }
        addFixed(totals, definition.getStringList("fixed"));
        ConfigurationSection byMetal = definition.getConfigurationSection("by-metal");
        if (byMetal != null) {
            addFixed(totals, byMetal.getStringList(metal.getId()));
        }
        List<String> lines = new ArrayList<>();
        totals.forEach((stat, value) -> lines.add(stat + " " + value));
        return lines;
    }

    private static void addFixed(Map<String, Integer> totals, List<String> lines) {
        for (Map.Entry<Stats, Double> fixed : parse(lines).entrySet()) {
            totals.merge(fixed.getKey().getKey(), round(fixed.getValue()), Integer::sum);
        }
    }

    /** "stat value" lines; unknown stats and bad numbers are skipped. */
    public static Map<Stats, Double> parse(List<String> lines) {
        Map<Stats, Double> parsed = new LinkedHashMap<>();
        for (String line : lines) {
            String[] split = line.trim().split("\\s+");
            Stats stat = split.length == 2 ? Stats.fromKey(split[0]) : null;
            if (stat == null) {
                System.out.println("Invalid forge stat line: " + line);
                continue;
            }
            try {
                double value = Double.parseDouble(split[1]);
                if (Double.isFinite(value)) {
                    parsed.merge(stat, value, Double::sum);
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid forge stat number: " + line);
            }
        }
        return parsed;
    }

    /** Half away from zero, so a downside rounds as far as the matching upside. */
    static int round(double value) {
        long magnitude = Math.round(Math.abs(value));
        return (int) (value < 0 ? -magnitude : magnitude);
    }

    private static List<String> cost(ConfigurationSection definition, Metal metal) {
        List<String> cost = new ArrayList<>();
        if (!metal.getCost().isEmpty()) {
            cost.add(metal.getCost() + "(" + Math.max(1, definition.getInt("cost-amount", 2)) + ")");
        }
        cost.addAll(definition.getStringList("cost"));
        return cost;
    }
}

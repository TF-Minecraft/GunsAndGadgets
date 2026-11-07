package net.tfminecraft.gunsandgadgets.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gunsandgadgets.attributes.AttributeData;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;


public class ConfigLoader {
    public void loadConfig(File configFile) {
		Cache.clear();
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Cache.givePermission = config.getString("give-permission", "gunsandgadgets.give").trim();
        Cache.outputSlot = config.getInt("output-slot", 15);
        ConfigurationSection section = config.getConfigurationSection("outputs");
        if (section != null) {
            for (String typeKey : section.getKeys(false)) {
                try {
                    GunType type = GunType.valueOf(typeKey.toUpperCase());
                    String item = section.getString(typeKey, "v.stick"); // directly get string
                    Cache.outputItems.put(type, item);
                } catch (IllegalArgumentException e) {
                    System.out.println("Unknown gun type in outputs: " + typeKey);
                }
            }
        }
        Cache.requireInput = config.getBoolean("require-input", true);

        section = config.getConfigurationSection("attributes");
        if (section != null) {
            for (String typeKey : section.getKeys(false)) {
                Cache.attributes.add(new AttributeData(typeKey, section.getConfigurationSection(typeKey)));
            }
        }

        Cache.station = config.getString("station", "v(end_portal_frame)");

        Cache.blockDamage = config.getBoolean("block-damage", false);
        Cache.rocketSound = config.getString("rocket-sound", "none");
        Cache.statRefreshDebug = config.getBoolean("stat_refresh_debug", false);

        Cache.headshotMultiplier = Math.max(0.0, finite(config, "handling.headshot-multiplier", 1.5));
        Cache.spreadSneaking = Math.max(0.0, finite(config, "handling.spread-multipliers.sneaking", 1.0));
        Cache.spreadMoving = Math.max(0.0, finite(config, "handling.spread-multipliers.moving", 1.0));
        Cache.spreadSprinting = Math.max(0.0, finite(config, "handling.spread-multipliers.sprinting", 1.0));
        Cache.spreadAirborne = Math.max(0.0, finite(config, "handling.spread-multipliers.airborne", 1.0));
        Cache.spreadMounted = Math.max(0.0, finite(config, "handling.spread-multipliers.mounted", 1.0));
        Cache.maxSpreadDegrees = Math.max(0.02, finite(config, "handling.max-spread-degrees", Double.POSITIVE_INFINITY));
        Cache.falloffStartDistance = Math.max(0.0, finite(config, "handling.falloff.start-distance", 0.0));
        Cache.falloffMinMultiplier =
                Math.max(0.0, Math.min(1.0, finite(config, "handling.falloff.min-multiplier", 1.0)));

        section = config.getConfigurationSection("required-parts");
        if (section != null) {
            for (String typeKey : section.getKeys(false)) {
                try {
                    GunType type = GunType.valueOf(typeKey.toUpperCase());
                    List<String> parts = section.getStringList(typeKey);
                    Cache.requiredParts.put(type, new ArrayList<>(parts));
                } catch (IllegalArgumentException e) {
                    System.out.println("Unknown gun type in required-parts: " + typeKey);
                }
            }
        }
	}

    /**
     * A handling number, or its default when the value is NaN or infinite: Math.max/min pass NaN
     * through, which would turn damage or spread into NaN.
     */
    private static double finite(FileConfiguration config, String path, double fallback) {
        double value = config.getDouble(path, fallback);
        return Double.isFinite(value) ? value : fallback;
    }
}

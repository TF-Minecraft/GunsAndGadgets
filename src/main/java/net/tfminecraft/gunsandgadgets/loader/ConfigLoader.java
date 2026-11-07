package net.tfminecraft.gunsandgadgets.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
        Cache.smoothAccuracy = config.getBoolean("handling.smooth-accuracy", false);
        Cache.coneTightenPerAccuracy =
                Math.max(0.0, finite(config, "handling.pellet-cone.tighten-per-accuracy", 0.03));
        Cache.coneMinFactor =
                Math.max(0.0, Math.min(1.0, finite(config, "handling.pellet-cone.min-factor", 0.4)));
        Cache.retireDisabledPartGuns = config.getBoolean("retire-disabled-part-guns", false);
        Cache.refundsEnabled = config.getBoolean("refunds.enabled", false);
        Cache.refundsAutomatic = config.getBoolean("refunds.automatic", false);
        Cache.refundFreeGuns = "part-costs".equalsIgnoreCase(config.getString("refunds.free-guns", "keep").trim());
        Cache.partIconPrefix = config.getString("part-icons", "").trim();

        // Draw time: seconds per gun type, slowed by stance. Left out = guns are ready at once.
        Cache.drawBaseTicks = new HashMap<>();
        ConfigurationSection draw = config.getConfigurationSection("handling.draw.base-seconds");
        if (draw != null) {
            for (String key : draw.getKeys(false)) {
                try {
                    GunType type = GunType.valueOf(key.toUpperCase());
                    double seconds = draw.getDouble(key, 0.0);
                    Cache.drawBaseTicks.put(type, Double.isFinite(seconds) ? (int) Math.round(Math.max(0.0, seconds) * 20) : 0);
                } catch (IllegalArgumentException e) {
                    System.out.println("Unknown gun type in handling.draw.base-seconds: " + key);
                }
            }
        }
        Cache.drawMoving = Math.max(0.1, finite(config, "handling.draw.stance.moving", 1.0));
        Cache.drawSprinting = Math.max(0.1, finite(config, "handling.draw.stance.sprinting", 1.0));
        Cache.drawAirborne = Math.max(0.1, finite(config, "handling.draw.stance.airborne", 1.0));
        Cache.drawMounted = Math.max(0.1, finite(config, "handling.draw.stance.mounted", 1.0));
        Cache.drawSneaking = Math.max(0.1, finite(config, "handling.draw.stance.sneaking", 1.0));

        Cache.aimingEnabled = config.getBoolean("aiming.enabled", false);
        Cache.aimSlow = Math.max(0.0, Math.min(0.9, finite(config, "aiming.slow", 0.0)));
        Cache.scopeSettleTicks = (int) Math.round(Math.max(0.0, finite(config, "aiming.scope-settle-seconds", 0.0)) * 20);
        Cache.spreadHip = Math.max(0.0, finite(config, "handling.spread-multipliers.hip", 1.0));
        Cache.typeIconPrefix = config.getString("type-icons", "").trim();

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

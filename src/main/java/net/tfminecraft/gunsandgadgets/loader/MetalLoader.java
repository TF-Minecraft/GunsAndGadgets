package net.tfminecraft.gunsandgadgets.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.gunsandgadgets.guns.forge.Metal;

/** metals.yml: the metals and woods forged parts are made from, in forge order. */
public class MetalLoader implements LoaderInterface {
	private static final Map<String, Metal> metals = new LinkedHashMap<>();

	public static void clear() {
		metals.clear();
	}

	public static Metal getByString(String id) {
		return id == null ? null : metals.get(id.toLowerCase());
	}

	/** The materials of one kind ("metal" or "wood") in config order. */
	public static List<Metal> ofKind(String kind) {
		List<Metal> list = new ArrayList<>();
		for (Metal metal : metals.values()) {
			if (metal.getKind().equalsIgnoreCase(kind)) {
				list.add(metal);
			}
		}
		return list;
	}

	public static List<Metal> getOrdered() {
		return new ArrayList<>(metals.values());
	}

	public void load(File configFile) {
		clear();
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section != null) {
				Metal metal = new Metal(key, section);
				metals.put(metal.getId(), metal);
			}
		}
	}
}

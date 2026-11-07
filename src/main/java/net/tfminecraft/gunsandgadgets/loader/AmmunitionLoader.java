package net.tfminecraft.gunsandgadgets.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;

public class AmmunitionLoader implements LoaderInterface{
	static HashMap<String, Ammunition> oList = new HashMap<>();
	private static final List<Ammunition> ordered = new ArrayList<>();
	public static void clear() {
		oList.clear();
		ordered.clear();
	}
	public static HashMap<String, Ammunition> get() {
		return oList;
	}
	/** Every shot of a family up to a tier, in config order, for a forged barrel's caliber list. */
	public static List<String> family(String family, int maxTier) {
		List<String> keys = new ArrayList<>();
		for (Ammunition ammo : ordered) {
			if (!family.isEmpty() && ammo.getFamily().equals(family) && ammo.getTier() <= maxTier) {
				keys.add(ammo.getKey());
			}
		}
		return keys;
	}
	public static Ammunition getByString(String id) {
		if(oList.containsKey(id)) return oList.get(id);
		return null;
	}
	public void load(File configFile) {
		clear();
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			Ammunition o = new Ammunition(key, config.getConfigurationSection(key));
			oList.put(key, o);
			ordered.add(o);
		}
	}
    
}

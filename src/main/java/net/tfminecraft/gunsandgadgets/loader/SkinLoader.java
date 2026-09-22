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
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;

public class SkinLoader implements LoaderInterface{
	static HashMap<String, SkinData> oList = new HashMap<>();
	public static void clear() {
		oList.clear();
	}
	public static HashMap<String, SkinData> get() {
		return oList;
	}
	public static SkinData getByString(String id) {
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
			SkinData o = new SkinData(key, config.getConfigurationSection(key));
			oList.put(key, o);
		}
	}
}

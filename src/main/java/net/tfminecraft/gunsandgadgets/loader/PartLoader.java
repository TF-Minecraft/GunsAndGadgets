package net.tfminecraft.gunsandgadgets.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.guns.forge.PartForge;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.utils.RevisionTracker;

public class PartLoader implements LoaderInterface{
	static HashMap<String, GunPart> oList = new HashMap<>();
	private static List<GunPart> ordered = new ArrayList<>();
	/** The last parts.yml or retired-parts.yml read failed: guns can't be trusted to be "old". */
	private static boolean loadFailed = false;
	public static void clear() {
		oList.clear();
		ordered.clear();
		loadFailed = false;
	}
	/**
	 * Whether the loaded parts can decide which guns are old: both files read and some parts
	 * loaded. Refunds of guns with missing parts wait while this is false.
	 */
	public static boolean isHealthy() {
		return !loadFailed && !ordered.isEmpty();
	}
	public static List<GunPart> getOrdered() {
		return ordered;
	}
	public static HashMap<String, GunPart> get() {
		return oList;
	}
	public static GunPart getByString(String id) {
		if(oList.containsKey(id)) return oList.get(id);
		return null;
	}
	public void load(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            // keep the parts already loaded (none at startup) rather than breaking every gun
            GunsAndGadgets.getInstance().getLogger().severe(
                    "Could not read " + configFile.getName() + "; kept the parts already loaded, refunds of guns with missing parts paused: " + e.getMessage());
            loadFailed = true;
            return;
        }
        clear();
        loadFailed = false;
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (PartForge.isForged(section)) {
				// One part per material; the material is part of the id the gun stamps.
				for (Map.Entry<String, ConfigurationSection> variant : PartForge.expand(key, section).entrySet()) {
					add(variant.getKey(), variant.getValue());
				}
			} else {
				add(key, section);
			}
		}
	}

	/**
	 * Old parts kept only so guns built from them still resolve (retired-parts.yml). They load
	 * disabled: nobody can craft them, and retire-disabled-part-guns decides whether their guns
	 * still fire. An id parts.yml already defines is skipped.
	 */
	public void loadRetired(File configFile) {
		if (!configFile.isFile()) {
			return;
		}
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			GunsAndGadgets.getInstance().getLogger().severe(
					"Could not read " + configFile.getName() + "; refunds of guns with missing parts paused: " + e.getMessage());
			loadFailed = true;
			return;
		}
		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null || oList.containsKey(key)) {
				continue;
			}
			section.set("disabled", true);
			section.set("retired", true);
			add(key, section);
		}
	}

	private static void add(String key, ConfigurationSection section) {
		GunPart o = new GunPart(key, section);
		String hash = RevisionTracker.sha256(o.buildRevisionContent());
		int revision = GunsAndGadgets.getRevisionTracker().resolvePart(key, hash);
		o.setRevision(revision);
		oList.put(key, o);
		ordered.add(o);
	}
}

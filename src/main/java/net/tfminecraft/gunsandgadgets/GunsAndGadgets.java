package net.tfminecraft.gunsandgadgets;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.ConfigLoader;
import net.tfminecraft.gunsandgadgets.loader.PartDataLoader;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;
import net.tfminecraft.gunsandgadgets.manager.CharacterSessionListener;
import net.tfminecraft.gunsandgadgets.manager.CraftingManager;
import net.tfminecraft.gunsandgadgets.manager.GgCommand;
import net.tfminecraft.gunsandgadgets.manager.GunManager;
import net.tfminecraft.gunsandgadgets.manager.GunRefreshListener;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.utils.RevisionTracker;
import net.tfminecraft.tlibs.character.Characters;

public class GunsAndGadgets extends JavaPlugin {
	private static GunsAndGadgets instance;
	private static final RevisionTracker revisionTracker = new RevisionTracker();
	private final ConfigLoader configLoader = new ConfigLoader();
	private final PartLoader partLoader = new PartLoader();
	private final PartDataLoader partDataLoader = new PartDataLoader();
	private final SkinLoader skinLoader = new SkinLoader();
	private final AmmunitionLoader ammunitionLoader = new AmmunitionLoader();

	private final CraftingManager craftingManager = new CraftingManager();
	private GunManager gunManager;

	@Override
	public void onEnable() {
		try {
			Characters.available();
		} catch (LinkageError error) {
			getLogger().severe("GunsAndGadgets requires TLibs 2.3.0 or newer. Update TLibs and restart the server.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		instance = this;
		gunManager = new GunManager();
		createConfigs();
		revisionTracker.load(getDataFolder());
		loadConfigs();
		registerListeners();
		registerCommands();
	}

	@Override
	public void onDisable() {
		if (gunManager != null) {
			for (var player : getServer().getOnlinePlayers()) gunManager.cancelActiveReload(player);
		}
		revisionTracker.flush();
	}

	public void registerListeners() {
		getServer().getPluginManager().registerEvents(craftingManager, this);
		getServer().getPluginManager().registerEvents(new InventoryManager(), this);
		getServer().getPluginManager().registerEvents(gunManager, this);
		getServer().getPluginManager().registerEvents(new GunRefreshListener(), this);
		getServer().getPluginManager().registerEvents(new CharacterSessionListener(gunManager), this);
	}

	public void registerCommands() {
		GgCommand gg = new GgCommand();
		var cmd = getCommand("gg");
		if (cmd != null) {
			cmd.setExecutor(gg);
			cmd.setTabCompleter(gg);
		} else {
			getLogger().severe("Command 'gg' missing from plugin.yml");
		}
	}

	public static GunsAndGadgets getInstance() {
		return instance;
	}

	public void createConfigs() {
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		new File(getDataFolder(), "data").mkdirs();
		String[] files = {
			"config.yml",
			"part-types.yml",
			"parts.yml",
			"skins.yml",
			"ammunition.yml"
		};
		for (String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
			if (!newConfigFile.exists()) {
				newConfigFile.getParentFile().mkdirs();
				saveResource(s, false);
			}
		}
	}

	public void loadConfigs() {
		configLoader.loadConfig(new File(getDataFolder(), "config.yml"));
		ammunitionLoader.load(new File(getDataFolder(), "ammunition.yml"));
		partDataLoader.load(new File(getDataFolder(), "part-types.yml"));
		partLoader.load(new File(getDataFolder(), "parts.yml"));
		skinLoader.load(new File(getDataFolder(), "skins.yml"));
	}

	/** Reload all YAML configs from disk (including skins.yml). */
	public void reload() {
		loadConfigs();
		revisionTracker.flush();
		getLogger().info("Reloaded configs (skins, parts, ammunition, config).");
	}

	public static RevisionTracker getRevisionTracker() {
		return revisionTracker;
	}

	public GunManager getGunManager() {
		return gunManager;
	}

	public SkinData getSkin(String id) {
		return SkinLoader.getByString(id);
	}
}

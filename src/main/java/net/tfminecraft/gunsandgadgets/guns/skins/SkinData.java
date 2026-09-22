package net.tfminecraft.gunsandgadgets.guns.skins;

import net.tfminecraft.gunsandgadgets.util.LegacyModelData;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.tlibs.TLibs;

public class SkinData {

    private final String id;
    private final String carry;
    private final String reload;
    private final String aim;
    private final List<String> types;
    private final List<String> options;

    public SkinData(String key, ConfigurationSection config) {
        this.id = key;

        this.carry = config.getString("carry", "");
        this.reload = config.getString("reload", "");
        this.aim = config.getString("aim", "");

        this.types = new ArrayList<>(config.getStringList("types"));
        this.options = config.contains("options")
                ? new ArrayList<>(config.getStringList("options"))
                : new ArrayList<>();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getCarry() {
        return carry;
    }

    public String getReload() {
        return reload;
    }

    public String getAim() {
        return aim;
    }

    public List<String> getTypes() {
        return types;
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean hasOption(String option) {
        return options.contains(option.toLowerCase());
    }

    public ItemStack parseModel(SkinState state) {
        String path = null;
        switch (state) {
            case AIM:
                path = aim;
                break;
            case CARRY:
                path = carry;
                break;
            case RELOAD:
                path = reload;
                break;
            default:
                break;
        }
        if (path == null || path.isEmpty()) {
            return new ItemStack(Material.BARRIER);
        }

        String trimmed = path.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("ia.")) {
            ItemStack ia = TLibs.getItemAPI().getCreator().getItemFromPath(trimmed);
            if (ia == null || ia.getType().isAir()) {
                return new ItemStack(Material.BARRIER);
            }
            return ia;
        }

        String[] split = trimmed.split("\\.");
        Material mat = Material.matchMaterial(split[0].toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = Material.BARRIER;
        }

        ItemStack item = new ItemStack(mat);
        if (split.length > 1) {
            try {
                int data = Integer.parseInt(split[1]);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    LegacyModelData.set(meta, data);
                    item.setItemMeta(meta);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return item;
    }
}

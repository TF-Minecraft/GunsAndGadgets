package net.tfminecraft.gunsandgadgets.guns.skins;

import net.tfminecraft.gunsandgadgets.util.LegacyModelData;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
    private final String ads;
    // The aimed pose of a scoped gun (a spyglass); empty: scoped guns use the ads pose.
    private final String scope;
    private final List<String> types;
    private final List<String> options;
    // Forged guns: the item model prefix; the pose is appended (_carry, _reload, _aim).
    private final String forgedModel;

    public SkinData(String key, ConfigurationSection config) {
        this.id = key;

        this.carry = config.getString("carry", "");
        this.reload = config.getString("reload", "");
        this.aim = config.getString("aim", "");
        this.ads = config.getString("ads", aim);
        this.scope = config.getString("scope", "").trim();

        this.types = new ArrayList<>(config.getStringList("types"));
        this.options = config.contains("options")
                ? new ArrayList<>(config.getStringList("options"))
                : new ArrayList<>();
        this.forgedModel = config.getString("forged-model", "").trim().toLowerCase(Locale.ROOT);
    }

    /** A skin drawn by the pack's forged gun models (see ForgedLook) rather than one fixed model. */
    public boolean isForged() {
        return !forgedModel.isEmpty();
    }

    /** The item model for this pose, e.g. tfmc_guns:rifle_aim; null when this isn't a forged skin. */
    public NamespacedKey getForgedModel(SkinState state) {
        if (!isForged()) {
            return null;
        }
        return NamespacedKey.fromString(forgedModel + "_" + state.name().toLowerCase(Locale.ROOT));
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

    /** Whether scoped guns of this skin show their own scope pose instead of the ads pose. */
    public boolean hasScopePose() {
        return !scope.isEmpty();
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
        String path = switch (state) {
            case AIM -> aim;
            case ADS -> ads;
            case SCOPE -> scope;
            case CARRY -> carry;
            case RELOAD -> reload;
        };
        if (path.isEmpty()) {
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

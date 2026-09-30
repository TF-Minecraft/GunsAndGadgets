package net.tfminecraft.gunsandgadgets.guns.data;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.gunsandgadgets.utils.GGCraftKeys;

/**
 * Materials actually taken when a gun was crafted, as item path to amount.
 * Empty when staff bypassed the cost or inputs were not required.
 */
public final class GunCraftInputs {

    private static final Gson GSON = new Gson();
    private static final Type INPUTS_TYPE = new TypeToken<LinkedHashMap<String, Integer>>() {}.getType();

    private GunCraftInputs() {}

    public static void applyTo(ItemStack item, Map<String, Integer> used) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(GGCraftKeys.craftInputs(), GGCraftKeys.STRING, GSON.toJson(used));
        item.setItemMeta(meta);
    }

    /**
     * @return the recorded inputs, or null for guns crafted before inputs were recorded
     */
    public static Map<String, Integer> readFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String json = item.getItemMeta().getPersistentDataContainer().get(GGCraftKeys.craftInputs(), GGCraftKeys.STRING);
        if (json == null) {
            return null;
        }
        try {
            return GSON.fromJson(json, INPUTS_TYPE);
        } catch (JsonParseException ex) {
            return null;
        }
    }

    /** Carries the recorded inputs onto a rebuilt gun. */
    public static void copy(ItemStack from, ItemStack to) {
        Map<String, Integer> used = readFrom(from);
        if (used != null) {
            applyTo(to, used);
        }
    }
}

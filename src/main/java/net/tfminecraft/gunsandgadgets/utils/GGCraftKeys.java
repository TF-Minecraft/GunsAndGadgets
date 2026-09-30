package net.tfminecraft.gunsandgadgets.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;

/**
 * Persistent data keys for gun craft provenance and broken state.
 */
public final class GGCraftKeys {

    private GGCraftKeys() {}

    public static NamespacedKey craftParts() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_craft_parts");
    }

    public static NamespacedKey craftInputs() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_craft_inputs");
    }

    public static NamespacedKey partsRevision() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_parts_revision");
    }

    public static NamespacedKey broken() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_broken");
    }

    public static NamespacedKey majorityTier() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_majority_tier");
    }

    public static NamespacedKey tierLoreStart() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "gg_tier_lore_start");
    }

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Integer, Integer> INTEGER = PersistentDataType.INTEGER;
    public static final PersistentDataType<Byte, Boolean> BOOLEAN = PersistentDataType.BOOLEAN;
}

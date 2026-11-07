package net.tfminecraft.gunsandgadgets.guns.skins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;

/**
 * How a gun built only from forged parts looks: the resource pack's item model for its type and pose
 * (a skin with {@code forged-model}) assembles one sub-model per part, picked by these
 * custom_model_data strings, in this order:
 * <pre>
 * 0 barrel, 1 barrel material, 2 loader, 3 its material, 4 chamber, 5 its material,
 * 6 action, 7 its material, 8 stock, 9 wood (the stock's, or barkwood without one)
 * </pre>
 * A missing slot is "none". Part items in the menus show the pack's icon for the part (and material).
 */
public final class ForgedLook {

    static final String[] SLOTS = {"barrel", "loader", "chamber", "action", "stock"};
    public static final String NONE = "none";
    /** Pistols have no stock to choose: their grip is barkwood. */
    public static final String DEFAULT_WOOD = "barkwood";

    private ForgedLook() {}

    public static List<String> modelStrings(Collection<GunPart> parts) {
        List<String> out = new ArrayList<>();
        for (String slot : SLOTS) {
            GunPart part = find(parts, slot);
            out.add(part == null ? NONE : part.getBaseId());
            String material = part == null ? null : part.getMetalId();
            if (slot.equals("stock")) {
                out.add(material == null ? DEFAULT_WOOD : material);
            } else {
                out.add(material == null ? NONE : material);
            }
        }
        return out;
    }

    /**
     * Gives a forged skin's model for this pose; with parts, also rewrites the strings that pick
     * each part's model (old integer model data is dropped: the item model doesn't read it).
     */
    public static void stamp(ItemMeta meta, SkinData skin, SkinState state, Collection<GunPart> parts) {
        NamespacedKey model = skin.getForgedModel(state);
        if (model == null) {
            return;
        }
        meta.setItemModel(model);
        if (parts == null) {
            return;
        }
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of());
        component.setFlags(List.of());
        component.setColors(List.of());
        component.setStrings(modelStrings(parts));
        meta.setCustomModelDataComponent(component);
    }

    /** The parts a crafted gun was stamped with that still exist, or null for an unstamped item. */
    public static List<GunPart> stampedParts(ItemStack gun) {
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(gun);
        if (provenance == null) {
            return null;
        }
        return provenance.resolveStampedParts().live();
    }

    /**
     * The pack icon for a forged part: {@code <part-icons><part>} in the part list,
     * {@code <part-icons><part>_<material>} once the material is chosen. Null when part-icons is off.
     */
    public static NamespacedKey partIcon(GunPart part, boolean withMaterial) {
        String prefix = Cache.partIconPrefix;
        if (prefix == null || prefix.isBlank() || !part.isForged()) {
            return null;
        }
        String id = withMaterial && part.getMetalId() != null
                ? part.getBaseId() + "_" + part.getMetalId()
                : part.getBaseId();
        return NamespacedKey.fromString(prefix + id);
    }

    /** Shows the part's pack icon on a menu item when part-icons is set. */
    public static void applyIcon(ItemMeta meta, GunPart part, boolean withMaterial) {
        NamespacedKey icon = partIcon(part, withMaterial);
        if (icon != null) {
            meta.setItemModel(icon);
        }
    }

    private static GunPart find(Collection<GunPart> parts, String slot) {
        for (GunPart part : parts) {
            if (part.getPartType() != null && part.getPartType().getId().equalsIgnoreCase(slot)) {
                return part;
            }
        }
        return null;
    }
}

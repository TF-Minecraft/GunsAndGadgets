package net.tfminecraft.gunsandgadgets.guns.skins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.Map;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.forge.Metal;
import net.tfminecraft.gunsandgadgets.loader.MetalLoader;
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
    /** The wood string when a gun has no stock or grip (launchers). */
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

    /** A steel build of each gun type, for its button in the gunsmith (type-icons). */
    static final Map<GunType, List<String>> TYPE_BUILDS = Map.of(
            GunType.RIFLE, List.of("rifled_barrel_long", "steel", "muzzle_loader", "steel", "single_shot", "steel", "flintlock", "steel", "standard_stock", "maplewood"),
            GunType.PISTOL, List.of("smoothbore_barrel_short", "steel", "muzzle_loader", "steel", "single_shot", "steel", "flintlock", "steel", "standard_grip", "maplewood"),
            GunType.SHOTGUN, List.of("spread_barrel", "steel", "muzzle_loader", "steel", "single_shot", "steel", "flintlock", "steel", "standard_stock", "maplewood"),
            GunType.LAUNCHER, List.of("launcher_barrel", "steel", "muzzle_loader", "steel", "single_shot_launcher", "steel", "rocketlock", "steel", NONE, DEFAULT_WOOD));

    public static boolean typeIconsOn() {
        return Cache.typeIconPrefix != null && !Cache.typeIconPrefix.isBlank();
    }

    /** Draws a gun type's button as a steel gun of that type (type-icons); false when off. */
    public static boolean applyTypeIcon(ItemMeta meta, GunType type) {
        if (!typeIconsOn()) {
            return false;
        }
        meta.setItemModel(NamespacedKey.fromString(Cache.typeIconPrefix + type.name().toLowerCase() + "_carry"));
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(TYPE_BUILDS.get(type));
        meta.setCustomModelDataComponent(component);
        return true;
    }

    /** The colour of the metal of this tier (metals.yml color), or plain grey. */
    public static String tierColor(int tier) {
        for (Metal metal : MetalLoader.ofKind("metal")) {
            if (metal.getTier() == tier && !metal.getColor().isEmpty()) {
                return metal.getColor();
            }
        }
        return "§7";
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

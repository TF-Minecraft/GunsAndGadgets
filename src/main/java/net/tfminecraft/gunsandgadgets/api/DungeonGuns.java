package net.tfminecraft.gunsandgadgets.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.data.DungeonStamp;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.utils.GGCraftKeys;

/**
 * Dungeon guns, for plugins that hand them out: forged guns with a colour per part, that hit
 * {@code power} times as hard as the same parts crafted and never break. Everything else (handling,
 * reload, spread, shot) is the crafted gun's. Slots are barrel, loader, chamber, action and stock.
 */
public final class DungeonGuns {

    /** A dungeon gun taken apart: pass it back to {@link #build} (with a part swapped, say). */
    public record Parts(String gunType, Map<String, String> partIds, Map<String, Integer> colors, double power, String marker) {}

    private static BinaryOperator<ItemStack> finisher;

    private DungeonGuns() {}

    /**
     * For each slot the gun type needs, the forged parts it can take in this metal or wood: one id per
     * part (with its good quality fitting, where it takes one). Any mix of them agrees on a class, but
     * not on shot (a plain-shot lock with a powered-shot chamber), which {@link #build} answers with null.
     * Empty for an unknown gun type.
     */
    public static Map<String, List<String>> slots(String gunType, String metal, String wood) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<GunPart> kept = new ArrayList<>();
        GunType type = type(gunType);
        for (String slot : required(type)) {
            Map<String, List<GunPart>> variants = new LinkedHashMap<>();
            for (GunPart part : PartLoader.getOrdered()) {
                if (part.isForged() && part.isEnabledForCrafting() && fits(part, type, slot)
                        && List.of(metal, wood).contains(part.getMetalId())) {
                    variants.computeIfAbsent(part.getBaseId(), id -> new ArrayList<>()).add(part);
                }
            }
            List<String> ids = out.computeIfAbsent(slot, s -> new ArrayList<>());
            for (List<GunPart> options : variants.values()) {
                GunPart part = InventoryManager.goodQuality(options);
                kept.add(part);
                if (InventoryManager.resolveClasses(kept) == null) {
                    kept.removeLast();
                } else {
                    ids.add(part.getId());
                }
            }
        }
        return out;
    }

    /**
     * A finished dungeon gun, or null when it can't be built: a slot missing or too many, a part that is
     * unknown, not forged, disabled or made for another slot or gun type, parts for different classes or
     * without a shot in common, or a power that isn't above 0. A slot without a colour is white; the marker
     * (may be null) stays on the gun for its maker to recognise.
     */
    public static ItemStack build(String gunType, Map<String, String> partIdBySlot, Map<String, Integer> rgbBySlot,
            double power, String marker) {
        GunType type = type(gunType);
        List<String> slots = required(type);
        if (slots.isEmpty() || slots.size() != partIdBySlot.size() || !(power > 0)) {
            return null;
        }
        List<GunPart> parts = new ArrayList<>();
        for (String slot : slots) {
            GunPart part = PartLoader.getByString(partIdBySlot.get(slot));
            if (part == null || !part.isForged() || !fits(part, type, slot)) {
                return null;
            }
            parts.add(part);
        }
        if (InventoryManager.resolveClasses(parts) == null) {
            return null;
        }
        List<Integer> colors = ForgedLook.SLOTS.stream()
                .map(slot -> rgbBySlot.getOrDefault(slot, 0xFFFFFF) & 0xFFFFFF).toList();
        ItemStack gun = new InventoryManager().createOutputItem(type, parts, false, null,
                new DungeonStamp(power, colors, marker));
        if (gun == null || gun.getType().isAir() || gun.getType() == Material.BARRIER) {
            return null;
        }
        GunCraftInputs.applyTo(gun, Map.of()); // nothing was paid for it
        return gun;
    }

    /** The parts, colours and power of a dungeon gun; empty for anything else, or when a part no longer exists. */
    public static Optional<Parts> read(ItemStack item) {
        DungeonStamp stamp = DungeonStamp.readFrom(item);
        if (stamp == null) {
            return Optional.empty();
        }
        GunCraftProvenance.ResolvedParts parts = GunCraftProvenance.readFrom(item).resolveStampedParts();
        if (!parts.missingIds().isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (GunPart part : parts.live()) {
            String slot = part.getPartType().getId().toLowerCase(Locale.ROOT);
            ids.put(slot, part.getId());
            colors.put(slot, stamp.colors().get(ForgedLook.SLOTS.indexOf(slot)));
        }
        String type = item.getPersistentDataContainer().get(GGCraftKeys.gunType(), PersistentDataType.STRING);
        return Optional.of(new Parts(type.toLowerCase(Locale.ROOT), ids, colors, stamp.power(), stamp.marker()));
    }

    public static boolean isDungeonGun(ItemStack item) {
        return DungeonStamp.readFrom(item) != null;
    }

    /**
     * What to do with a dungeon gun whose stats were brought up to date: given the old gun and the
     * rebuilt one (which already has the old one's name and other plugins' data, but plain gun lore),
     * returns the gun to keep. Null goes back to keeping the rebuilt gun.
     */
    public static void onRefresh(BinaryOperator<ItemStack> finish) {
        finisher = finish;
    }

    /** For the stat refresh: finishes a rebuilt gun when the old one is a dungeon gun. */
    public static ItemStack refreshed(ItemStack old, ItemStack rebuilt) {
        if (!isDungeonGun(old)) {
            return rebuilt;
        }
        DungeonStamp.carryOver(old, rebuilt);
        try {
            return finisher == null ? rebuilt : finisher.apply(old, rebuilt);
        } catch (RuntimeException e) { // one bad gun never stops the others' refresh
            GunsAndGadgets.getInstance().getLogger().log(Level.WARNING, "Finishing a dungeon gun failed", e);
            return rebuilt;
        }
    }

    private static boolean fits(GunPart part, GunType type, String slot) {
        return part.getGunTypes().contains(type) && part.getPartType().getId().equalsIgnoreCase(slot);
    }

    private static List<String> required(GunType type) {
        return Cache.requiredParts.getOrDefault(type, List.of()).stream()
                .map(slot -> slot.toLowerCase(Locale.ROOT)).toList();
    }

    /** Null for an unknown type (which nothing is required for). */
    private static GunType type(String name) {
        for (GunType type : GunType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}

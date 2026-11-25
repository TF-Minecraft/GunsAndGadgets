package net.tfminecraft.gunsandgadgets.guns.stats;

import java.util.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;

public class StatApplier {
    /** As {@link #apply(ItemStack, Collection, boolean, GunType, Ammunition, String, double)} for a gun of unknown type and shot. */
    public static ItemStack apply(ItemStack item, Collection<GunPart> parts, boolean gui) {
        return apply(item, parts, gui, null, null, null, 1.0);
    }

    /**
     * Stamps the parts' combined stats on the item (exact and whole, see {@link GunStats}) and
     * lists what they do ({@link GunSheet}), firing {@code shot}, the gun's strongest shot. A dungeon
     * gun hits {@code power} times as hard (1 = as crafted).
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static ItemStack apply(ItemStack item, Collection<GunPart> parts, boolean gui, GunType type,
            Ammunition shot, String shotName, double power) {
        Map<Stats, Double> totals = combineStats(parts, power);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        lore.add("");

        for (Map.Entry<Stats, Double> total : totals.entrySet()) {
            GunStats.set(pdc, total.getKey().getKey().toLowerCase(), total.getValue());
        }
        List<String> sheet = GunSheet.of(type, totals, shot, shotName).lines();
        // applied twice to one item, the stats refresh but the sheet is listed once
        if (!lore.contains(sheet.getFirst())) {
            lore.addAll(sheet);
        }

        if(gui) {
            lore.add("");
            Map<String, Integer> cost = new HashMap<>();
            for(GunPart part : parts) {
                if(!part.hasCost()) continue;
                for(Map.Entry<String, Integer> entry : part.getCost().entrySet()) {
                    cost.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
            if(cost.size() > 0) {
                lore.add(StringFormatter.formatHex("#76de91§lInput:"));
                lore.addAll(CostFormatter.getCostsFormatted(cost));
                lore.add("");
            }
            lore.add("§e§lClick to craft");
        }
        Multimap<Attribute, AttributeModifier> empty = HashMultimap.create();
        meta.setAttributeModifiers(empty);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * As {@link #combineStats(Collection)}, hitting {@code power} times as hard: the damage percent is
     * the one stat that scales the whole hit (gun plus shot) and nothing else.
     */
    static Map<Stats, Double> combineStats(Collection<GunPart> parts, double power) {
        Map<Stats, Double> totals = combineStats(parts);
        if (power != 1.0) {
            double percent = GunStats.combinePercent(List.of(
                    totals.getOrDefault(Stats.DAMAGE_PERCENT, 0.0), (power - 1.0) * 100.0));
            totals.put(Stats.DAMAGE_PERCENT, Math.round(percent * 1e6) / 1e6);
        }
        return totals;
    }

    /**
     * The gun's stats from its parts: summed, except damage percents, which multiply
     * ({@link GunStats#combinePercent}).
     */
    public static Map<Stats, Double> combineStats(Collection<GunPart> parts) {
        Map<Stats, Double> totals = new HashMap<>();
        List<Double> percents = new ArrayList<>();
        for (GunPart part : parts) {
            for (Map.Entry<Stats, Double> entry : part.getStats().entrySet()) {
                if (entry.getKey() == Stats.DAMAGE_PERCENT) {
                    percents.add(entry.getValue());
                } else {
                    totals.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }
        if (!percents.isEmpty()) {
            totals.put(Stats.DAMAGE_PERCENT, GunStats.combinePercent(percents));
        }
        // 1.3 + 2.6 is 3.9000000000000004 in doubles
        totals.replaceAll((stat, value) -> Math.round(value * 1e6) / 1e6);
        return totals;
    }
}

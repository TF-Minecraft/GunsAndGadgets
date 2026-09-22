package net.tfminecraft.gunsandgadgets.guns.stats;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;

public class StatApplier {
    /**
     * Apply stats to the item from the given parts.
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static ItemStack apply(ItemStack item, Collection<GunPart> parts, boolean gui) {
        Map<Stats, Integer> totals = combineStats(parts);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        lore.add("");

        // remove old stat indices if reapplying
        for (Stats stat : totals.keySet()) {
            NamespacedKey valKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_" + stat.getKey().toLowerCase());
            NamespacedKey idxKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_index_" + stat.getKey().toLowerCase());
            pdc.remove(valKey);
            pdc.remove(idxKey);
        }

        // Append a "Stats:" header if not already present
        if (lore.stream().anyMatch(l -> l.contains("Stats:"))) {
            lore.add("");
            lore.add(StringFormatter.formatHex("#b38e88§lStats:"));
        }

        for (Stats stat : new TreeSet<>(totals.keySet())) {
            int value = totals.get(stat);

            String valueColor;
            if (value < 0) {
                valueColor = "#FF5555";
            } else if (value == 0) {
                valueColor = "#AAAAAA";
            } else if (value < 4) {
                valueColor = "#FFD700";
            } else if (value < 7) {
                valueColor = "#FFFF55";
            } else {
                valueColor = "#55FF55";
            }

            String statColor = "#c2b48a";

            String line = "§7■ " + statColor + stat.getDisplayName() + "§e: " + valueColor + value;
            line = StringFormatter.formatHex(line);

            lore.add(line);

            // Save to PDC
            NamespacedKey valKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_" + stat.getKey().toLowerCase());
            NamespacedKey idxKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_index_" + stat.getKey().toLowerCase());

            pdc.set(valKey, PersistentDataType.INTEGER, value);
            pdc.set(idxKey, PersistentDataType.INTEGER, lore.size()-1);
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
            lore.add("§e§lClick to Craft");
        }
        Multimap<Attribute, AttributeModifier> empty = HashMultimap.create();
        meta.setAttributeModifiers(empty);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Map<Stats, Integer> combineStats(Collection<GunPart> parts) {
        Map<Stats, Integer> totals = new HashMap<>();
        for (GunPart part : parts) {
            for (Map.Entry<Stats, Integer> entry : part.getStats().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }
}


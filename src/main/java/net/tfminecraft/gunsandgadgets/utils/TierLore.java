package net.tfminecraft.gunsandgadgets.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public final class TierLore {

    private TierLore() {}

    public static String formatComponentLine(int tier) {
        return StringFormatter.formatHex("§e[#ebd05bTier " + toRoman(tier) + " Component§e]");
    }

    public static String formatTierLine(int tier) {
        return StringFormatter.formatHex("§e[#ebd05bTier " + toRoman(tier) + "§e]");
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void applyTo(ItemStack item, int tier) {
        if (item == null || !item.hasItemMeta() || tier <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        int index = insertTierLine(lore, tier);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(GGCraftKeys.tierLoreStart(), PersistentDataType.INTEGER, index);
        meta.getPersistentDataContainer().set(GGCraftKeys.majorityTier(), PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
    }

    private static int insertTierLine(List<String> lore, int tier) {
        String tierLine = formatTierLine(tier);
        int index = findFirstFreeLine(lore);
        if (index < lore.size()) {
            lore.set(index, tierLine);
        } else {
            lore.add(tierLine);
        }
        return index;
    }

    private static int findFirstFreeLine(List<String> lore) {
        if (lore.isEmpty()) {
            return 0;
        }
        if (isBlankLoreLine(lore.get(0))) {
            return 0;
        }
        for (int i = 0; i < lore.size(); i++) {
            if (isBlankLoreLine(lore.get(i))) {
                return i;
            }
        }
        lore.add(0, "");
        return 0;
    }

    private static boolean isBlankLoreLine(String line) {
        if (line == null) {
            return true;
        }
        return line.replaceAll("§.", "").trim().isEmpty();
    }

    public static String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(tier);
        };
    }
}

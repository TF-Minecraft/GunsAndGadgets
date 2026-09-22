package net.tfminecraft.gunsandgadgets.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;

/**
 * Marks guns with missing stamped parts as BROKEN and blocks use.
 */
public final class GunBrokenMarker {

    private static final Set<String> WARNED_GUN_IDS = ConcurrentHashMap.newKeySet();

    private GunBrokenMarker() {}

    public static boolean isBroken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean broken = item.getItemMeta().getPersistentDataContainer().get(GGCraftKeys.broken(), GGCraftKeys.BOOLEAN);
        return broken != null && broken;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void markBroken(ItemStack item, List<String> missingIds) {
        if (item == null || missingIds == null || missingIds.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(GGCraftKeys.broken(), GGCraftKeys.BOOLEAN, true);
        meta.setDisplayName("§c§lBROKEN");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line != null && line.contains("Missing parts:"));
        lore.add("§7Missing parts: " + String.join(", ", missingIds));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static void clearBroken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(GGCraftKeys.broken());
        item.setItemMeta(meta);
    }

    /**
     * Console log + one chat warning per gun_id per server session.
     */
    public static void notifyBroken(Player player, ItemStack gun, String gunId, List<String> missingIds) {
        if (missingIds == null || missingIds.isEmpty()) {
            return;
        }
        String missing = String.join(", ", missingIds);
        GunsAndGadgets.getInstance().getLogger().warning(
                "[GunsAndGadgets] Gun " + (gunId != null ? gunId : "unknown")
                        + " has missing parts: " + missing
                        + (player != null ? " (holder: " + player.getName() + ")" : ""));
        if (player != null && gunId != null && !WARNED_GUN_IDS.contains(gunId)) {
            WARNED_GUN_IDS.add(gunId);
            player.sendMessage("§cThis weapon is broken (missing parts: " + missing + "). It cannot be used.");
        }
    }

    public static void clearWarningSession(String gunId) {
        if (gunId != null) {
            WARNED_GUN_IDS.remove(gunId);
        }
    }
}

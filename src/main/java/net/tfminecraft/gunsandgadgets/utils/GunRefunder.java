package net.tfminecraft.gunsandgadgets.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;
import net.tfminecraft.tlibs.TLibs;

/**
 * Old guns are taken apart (config.yml {@code refunds}) so none stay on the server: a gun built
 * with a retired (disabled) or removed part, or from before guns recorded their parts (2.0.0). The
 * gun is removed and the player gets the materials its crafting took (recorded since 2.0.6), or for
 * older stamped guns the costs of their parts. Guns with no price (crafted for free, unstamped, or
 * with a removed part and nothing recorded) give nothing back.
 */
public final class GunRefunder {

    private GunRefunder() {}

    /** A gun with no record of its parts, or stamped with a part that is retired or no longer exists. */
    public static boolean isOld(ItemStack item) {
        if (!GunStatRefresher.isGun(item)) {
            return false;
        }
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        if (provenance == null) {
            return true;
        }
        GunCraftProvenance.ResolvedParts parts = provenance.resolveStampedParts();
        return !parts.missingIds().isEmpty() || parts.live().stream().anyMatch(GunPart::isDisabled);
    }

    /**
     * What an old gun gives back (item path to amount; empty when it has no price), or null when
     * refunds are off or the gun is not old.
     */
    public static Map<String, Integer> refundFor(ItemStack item) {
        if (!Cache.refundsEnabled || !isOld(item)) {
            return null;
        }
        Map<String, Integer> inputs = GunCraftInputs.readFrom(item);
        if (inputs != null) {
            // what the crafting took; nothing for a gun crafted for free
            return scaled(inputs, item.getAmount());
        }
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        if (provenance == null || !provenance.resolveStampedParts().missingIds().isEmpty()) {
            return Map.of();
        }
        GunCraftProvenance.ResolvedParts parts = provenance.resolveStampedParts();
        Map<String, Integer> cost = new LinkedHashMap<>();
        for (GunPart part : parts.live()) {
            part.getCost().forEach((path, amount) -> cost.merge(path, amount, Integer::sum));
        }
        return scaled(cost, item.getAmount());
    }

    private static Map<String, Integer> scaled(Map<String, Integer> cost, int guns) {
        Map<String, Integer> out = new TreeMap<>();
        cost.forEach((path, amount) -> {
            if (amount != null && amount > 0) {
                out.put(path, amount * guns);
            }
        });
        return out;
    }

    /**
     * Takes an old gun apart for the player: {@code remove} takes the gun away, then the materials
     * go into their inventory (dropped at their feet when full). False, and nothing changes, when
     * the gun is not old (or refunds are off) or a material can't be made.
     */
    public static boolean takeApart(Player player, ItemStack gun, Runnable remove) {
        Map<String, Integer> refund = refundFor(gun);
        if (refund == null) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : refund.entrySet()) {
            ItemStack made = TLibs.getItemAPI().getCreator().getItemFromPath(entry.getKey());
            if (made == null || made.getType().isAir()) {
                GunsAndGadgets.getInstance().getLogger().warning(
                        "[Refund] Could not make " + entry.getKey() + " for " + player.getName() + "'s old gun; kept the gun.");
                return false;
            }
            int perStack = Math.max(1, made.getMaxStackSize());
            for (int left = entry.getValue(); left > 0; left -= perStack) {
                ItemStack stack = made.clone();
                stack.setAmount(Math.min(left, perStack));
                items.add(stack);
            }
        }
        String name = gun.getItemMeta().hasDisplayName() ? gun.getItemMeta().getDisplayName() : "gun";
        String gunId = gun.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(GunsAndGadgets.getInstance(), "gun_id"), PersistentDataType.STRING);
        remove.run();
        for (ItemStack stack : items) {
            for (ItemStack over : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), over);
            }
        }
        if (refund.isEmpty()) {
            player.sendMessage("§eYour old " + name + "§e was retired and removed.");
        } else {
            player.sendMessage("§eYour old " + name + "§e was taken apart for its materials:");
            CostFormatter.getCostsFormatted(refund).forEach(player::sendMessage);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.8f);
        GunsAndGadgets.getInstance().getLogger().info(
                "[Refund] " + player.getName() + " gun_id=" + gunId + " returned " + refund);
        return true;
    }
}

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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;
import net.tfminecraft.tlibs.TLibs;

/**
 * Old guns are taken apart (config.yml {@code refunds}) so none stay on the server: a gun built
 * with a part from retired-parts.yml or a removed part, or from before guns recorded their parts
 * (2.0.0). A part only disabled in parts.yml stops new crafts and leaves its guns alone. The
 * gun is removed and the player gets the materials its crafting took (recorded since 2.0.6), or for
 * older stamped guns the costs of their parts. Guns with no price (crafted for free, unstamped, or
 * with a removed part and nothing recorded) give nothing back.
 */
public final class GunRefunder {

    private GunRefunder() {}

    /**
     * A gun with no record of its parts, or stamped with a retired part or one that no longer
     * exists. Missing parts only count while the part files loaded cleanly, and never for forged
     * ids ({@code part@material}): those go missing only by a config mistake (a material removed
     * from metals.yml or a part's metals list), so their guns stay broken until it is fixed.
     */
    public static boolean isOld(ItemStack item) {
        if (!GunStatRefresher.isGun(item)) {
            return false;
        }
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        if (provenance == null) {
            return true;
        }
        GunCraftProvenance.ResolvedParts parts = provenance.resolveStampedParts();
        if (parts.live().stream().anyMatch(GunPart::isRetired)) {
            return true;
        }
        return !parts.missingIds().isEmpty() && PartLoader.isHealthy()
                && parts.missingIds().stream().noneMatch(id -> id.contains("@"));
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
        ItemStack shots = loadedShots(gun);
        if (shots != null) {
            items.add(shots);
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
        if (shots != null) {
            player.sendMessage("§7Its loaded shots were returned too.");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.8f);
        GunsAndGadgets.getInstance().getLogger().info(
                "[Refund] " + player.getName() + " gun_id=" + gunId + " returned " + refund
                        + (shots == null ? "" : " + " + shots.getAmount() + " loaded shots"));
        return true;
    }

    /** The shots loaded in the gun as items, or null when it is empty or they can't be made. */
    public static ItemStack loadedShots(ItemStack gun) {
        PersistentDataContainer pdc = gun.getItemMeta().getPersistentDataContainer();
        int bullets = pdc.getOrDefault(new NamespacedKey(GunsAndGadgets.getInstance(), "bullets_loaded"),
                PersistentDataType.INTEGER, 0);
        Ammunition ammo = AmmunitionLoader.getByString(
                pdc.get(new NamespacedKey(GunsAndGadgets.getInstance(), "ammo_loaded"), PersistentDataType.STRING));
        if (bullets <= 0 || ammo == null) {
            return null;
        }
        ItemStack made = TLibs.getItemAPI().getCreator().getItemFromPath(ammo.getInput());
        if (made == null || made.getType().isAir()) {
            return null;
        }
        made.setAmount(bullets * gun.getAmount());
        return made;
    }
}

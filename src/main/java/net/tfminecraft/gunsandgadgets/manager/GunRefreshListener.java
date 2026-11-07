package net.tfminecraft.gunsandgadgets.manager;

import org.bukkit.Bukkit;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.utils.GunRefunder;
import net.tfminecraft.gunsandgadgets.utils.GunStatRefresher;

public class GunRefreshListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (!GunStatRefresher.isManaged(dropped)) {
                return;
            }
            GunStatRefresher.RefreshResult result = GunStatRefresher.refreshIfOutdated(dropped);
            if (!result.isChanged()) {
                return;
            }
            event.getItemDrop().setItemStack(result.getItem());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        int slot = event.getSlot();
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            if (clicked != null) {
                tryRefresh(player, clicked.getItem(slot), item -> clicked.setItem(slot, item));
            }
            tryRefresh(player, player.getItemOnCursor(), player::setItemOnCursor);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHotbarSelect(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int slot = event.getNewSlot();
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            ItemStack held = player.getInventory().getItem(slot);
            tryRefresh(player, held, item -> player.getInventory().setItem(slot, item));
        });
    }

    /** After a restart every player rejoins, so this brings their carried guns up to date without anyone acting. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }
            sweep(player, player.getInventory());
            sweep(player, player.getEnderChest());
        });
    }

    /** Chests, barrels and storage entities are checked when opened (refunds go to the opener); plugin menus are left alone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!isWorldStorage(inventory.getHolder(false))) {
            return;
        }
        Player player = event.getPlayer() instanceof Player opener ? opener : null;
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> sweep(player, inventory));
    }

    /** Players are entities too, but a menu owned by a player is not storage. */
    public static boolean isWorldStorage(InventoryHolder holder) {
        return holder instanceof BlockInventoryHolder || holder instanceof DoubleChest
                || (holder instanceof Entity && !(holder instanceof HumanEntity));
    }

    private void sweep(Player player, Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            int target = slot;
            tryRefresh(player, contents[slot], item -> inventory.setItem(target, item));
        }
    }

    /** Brings a gun up to date; with automatic refunds an old gun is taken apart for {@code player}. */
    private void tryRefresh(Player player, ItemStack item, ItemConsumer writer) {
        if (!GunStatRefresher.isGun(item)) {
            return;
        }
        // old guns, including unstamped ones the refresh can't read, are taken apart first
        if (Cache.refundsAutomatic && player != null && GunRefunder.takeApart(player, item, () -> writer.accept(null))) {
            return;
        }
        if (!GunStatRefresher.isManaged(item)) {
            return;
        }
        GunStatRefresher.RefreshResult result = GunStatRefresher.refreshIfOutdated(item);
        if (!result.isChanged()) {
            return;
        }
        writer.accept(result.getItem());
    }

    @FunctionalInterface
    private interface ItemConsumer {
        void accept(ItemStack item);
    }
}

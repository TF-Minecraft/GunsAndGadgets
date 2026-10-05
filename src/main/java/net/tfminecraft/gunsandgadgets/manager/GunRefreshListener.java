package net.tfminecraft.gunsandgadgets.manager;

import org.bukkit.Bukkit;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
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
                tryRefresh(clicked.getItem(slot), item -> clicked.setItem(slot, item));
            }
            tryRefresh(player.getItemOnCursor(), player::setItemOnCursor);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHotbarSelect(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int slot = event.getNewSlot();
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            ItemStack held = player.getInventory().getItem(slot);
            tryRefresh(held, item -> player.getInventory().setItem(slot, item));
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
            sweep(player.getInventory());
            sweep(player.getEnderChest());
        });
    }

    /** Chests, barrels and storage entities are checked when opened; plugin menus are left alone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!isWorldStorage(inventory.getHolder(false))) {
            return;
        }
        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> sweep(inventory));
    }

    public static boolean isWorldStorage(InventoryHolder holder) {
        return holder instanceof BlockInventoryHolder || holder instanceof DoubleChest || holder instanceof Entity;
    }

    private void sweep(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            int target = slot;
            tryRefresh(contents[slot], item -> inventory.setItem(target, item));
        }
    }

    private void tryRefresh(ItemStack item, ItemConsumer writer) {
        if (item == null || item.getType().isAir() || !GunStatRefresher.isManaged(item)) {
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

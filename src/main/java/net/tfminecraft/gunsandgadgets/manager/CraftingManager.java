package net.tfminecraft.gunsandgadgets.manager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.PartData;
import net.tfminecraft.gunsandgadgets.manager.inventory.AssemblyHolder;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.manager.inventory.TypeSelectionManager;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;

public class CraftingManager implements Listener {

    private InventoryManager inv = new InventoryManager();

    /**
     * Listens for right–clicks on End Portal Frames
     */
    @EventHandler
    public void onStationOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        if(!TLibs.getBlockAPI().getChecker().checkBlock(event.getClickedBlock(), Cache.station)) return;
        if (event.getPlayer().isSneaking()) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        inv.openCraftingInventory(player);
    }

    @EventHandler
    public void onCraft(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // ASSEMBLY GUI
        if (e.getInventory().getHolder() instanceof AssemblyHolder) {
            e.setCancelled(true); // fully managed GUI

            ItemStack current = e.getCurrentItem();
            if (current == null) return;
            if (current.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            // Only output slot is craftable
            if (e.getSlot() != Cache.outputSlot) return;
            if(current.getType().equals(Material.BARRIER)) return;

            // Collect parts again (based on player’s selections)
            GunType chosenType = TypeSelectionManager.getSelectedType(player);
            Collection<GunPart> parts = inv.collectPartsForPlayer(player, chosenType);
            if (hasDisabledPart(parts)) {
                player.sendMessage("§cOne or more parts are no longer available for crafting.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            // Check for class conflicts before crafting
            if (inv.hasClassConflict(player, parts)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            // Rebuild the final item (no GUI lines)
            ItemStack crafted = inv.createOutputItem(chosenType, parts, false);

            if(!hasInputs(player, parts)) {
                player.sendMessage("§cLacking inputs");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            boolean staffBypass = player.hasPermission("gg.bypass_crafting_cost");
            if (!staffBypass) {
                takeInputs(player, parts);
            }

            // Give to player
            player.getInventory().addItem(crafted);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            if (staffBypass) {
                player.sendMessage("§eMaterials were bypassed due to staff.");
            }

            // Optionally close GUI
            player.closeInventory();
        }
    }

    private boolean hasInputs(Player p, Collection<GunPart> parts) {
        if(p.hasPermission("gg.bypass_crafting_cost") || !Cache.requireInput) return true;
        Map<String, Integer> costs = new HashMap<>();

        // Collect total costs
        for (GunPart part : parts) {
            if (!part.hasCost()) continue;
            for (Map.Entry<String, Integer> entry : part.getCost().entrySet()) {
                costs.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        // Subtract items from inventory
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int remaining = entry.getValue();
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null) continue;
                if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, entry.getKey())) {
                    remaining -= item.getAmount();
                    if (remaining <= 0) break;
                }
            }
            entry.setValue(remaining);
        }

        // ✅ Check if all requirements satisfied
        return costs.values().stream().allMatch(v -> v <= 0);
    }

    private void takeInputs(Player p, Collection<GunPart> parts) {
        Map<String, Integer> costs = new HashMap<>();

        // Collect total costs for all parts
        for (GunPart part : parts) {
            if (!part.hasCost()) continue;
            for (Map.Entry<String, Integer> entry : part.getCost().entrySet()) {
                costs.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        // Remove items from inventory
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int toRemove = entry.getValue();

            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null) continue;

                if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, entry.getKey())) {
                    int remove = Math.min(item.getAmount(), toRemove);
                    item.setAmount(item.getAmount() - remove);
                    toRemove -= remove;

                    if (toRemove <= 0) break;
                }
            }
        }

        p.updateInventory();
    }

    private boolean hasDisabledPart(Collection<GunPart> parts) {
        for (GunPart part : parts) {
            if (part.isDisabled()) {
                return true;
            }
        }
        return false;
    }
}

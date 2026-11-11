package net.tfminecraft.gunsandgadgets.manager.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The animal fitting picker for one material of a part (e.g. a maplewood Saddle Stock in rare leather). */
public class FittingSelectionHolder implements InventoryHolder {
    private final String partId; // part category, e.g. "stock"
    private final String baseId; // forged part definition, e.g. "saddle_stock"
    private final String metalId; // the chosen material, e.g. "maplewood"

    public FittingSelectionHolder(String partId, String baseId, String metalId) {
        this.partId = partId;
        this.baseId = baseId;
        this.metalId = metalId;
    }

    public String getPartId() {
        return partId;
    }

    public String getBaseId() {
        return baseId;
    }

    public String getMetalId() {
        return metalId;
    }

    @Override public Inventory getInventory() { return null; }
}

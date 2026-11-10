package net.tfminecraft.gunsandgadgets.manager.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The material picker for one forged part (e.g. a steel or mythril Heavy Barrel). */
public class MetalSelectionHolder implements InventoryHolder {
    private final String partId; // part category, e.g. "barrel"
    private final String baseId; // forged part definition, e.g. "heavy_barrel"

    public MetalSelectionHolder(String partId, String baseId) {
        this.partId = partId;
        this.baseId = baseId;
    }

    public String getPartId() {
        return partId;
    }

    public String getBaseId() {
        return baseId;
    }

    @Override public Inventory getInventory() { return null; }
}

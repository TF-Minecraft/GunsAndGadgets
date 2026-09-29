package net.tfminecraft.gunsandgadgets.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;

public final class GunStatRefresher {

    private GunStatRefresher() {}

    public static RefreshResult refresh(ItemStack item) {
        return refresh(item, false);
    }

    public static RefreshResult refresh(ItemStack item, boolean force) {
        if (item == null || item.getType().isAir()) {
            return RefreshResult.unchanged();
        }
        if (GunBrokenMarker.isBroken(item)) {
            return RefreshResult.unchanged();
        }

        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        if (provenance == null) {
            return RefreshResult.unchanged();
        }
        if (!force && !provenance.isOutdated()) {
            return RefreshResult.unchanged();
        }

        GunCraftProvenance.ResolvedParts resolved = provenance.resolveStampedParts();
        if (!resolved.missingIds().isEmpty()) {
            return RefreshResult.failed("missing parts: " + String.join(", ", resolved.missingIds()));
        }

        GunType type = readGunType(item);
        if (type == null) {
            return RefreshResult.failed("invalid or missing gun_type");
        }

        List<GGCraftPart> outdatedBefore = new ArrayList<>(provenance.getOutdatedParts());
        String gunId = readGunId(item);

        if (Cache.statRefreshDebug) {
            String outdatedIds = outdatedBefore.stream()
                    .map(GGCraftPart::getId)
                    .collect(Collectors.joining(", "));
            GunsAndGadgets.getInstance().getLogger().info(
                    "[GG][StatRefresh] gun_id=" + (gunId != null ? gunId : "unknown")
                            + " outdatedParts=[" + outdatedIds + "]");
        }

        ItemStack rebuilt = new InventoryManager().rebuildFromParts(item, type, resolved.live());
        if (rebuilt == null || rebuilt.getType().isAir() || rebuilt.getType() == Material.BARRIER) {
            return RefreshResult.failed("gun rebuild produced no usable item; check skin and item configuration");
        }
        rebuilt.setAmount(item.getAmount());
        provenance.syncRevisions();
        provenance.applyTo(rebuilt);

        return RefreshResult.updated(rebuilt, outdatedBefore);
    }

    public static RefreshResult refreshIfOutdated(ItemStack item) {
        if (item == null || item.getType().isAir() || !isManaged(item)) {
            return RefreshResult.unchanged();
        }
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        // isManaged already read valid provenance from this unchanged item.
        if (!provenance.isOutdated()) {
            return RefreshResult.unchanged();
        }
        return refresh(item, false);
    }

    public static boolean isManaged(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey gunIdKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_id");
        if (!pdc.has(gunIdKey, PersistentDataType.STRING)) {
            return false;
        }
        return GunCraftProvenance.readFrom(item) != null;
    }

    private static String readGunId(ItemStack item) {
        // Called only after refresh has successfully read item provenance.
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(GunsAndGadgets.getInstance(), "gun_id"),
                PersistentDataType.STRING);
    }

    private static GunType readGunType(ItemStack item) {
        // Called only after refresh has successfully read item provenance.
        ItemMeta meta = item.getItemMeta();
        NamespacedKey typeKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_type");
        String typeStr = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (typeStr == null || typeStr.isBlank()) {
            return null;
        }
        try {
            return GunType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static final class RefreshResult {
        private final boolean changed;
        private final ItemStack item;
        private final String error;
        private final List<GGCraftPart> outdatedParts;

        private RefreshResult(boolean changed, ItemStack item, String error, List<GGCraftPart> outdatedParts) {
            this.changed = changed;
            this.item = item;
            this.error = error;
            this.outdatedParts = outdatedParts != null ? outdatedParts : List.of();
        }

        public static RefreshResult unchanged() {
            return new RefreshResult(false, null, null, List.of());
        }

        public static RefreshResult failed(String error) {
            return new RefreshResult(false, null, error, List.of());
        }

        public static RefreshResult updated(ItemStack item, List<GGCraftPart> outdatedParts) {
            return new RefreshResult(true, item, null, outdatedParts);
        }

        public boolean isChanged() {
            return changed;
        }

        public ItemStack getItem() {
            return item;
        }

        public String getError() {
            return error;
        }

        public List<GGCraftPart> getOutdatedParts() {
            return outdatedParts;
        }
    }
}

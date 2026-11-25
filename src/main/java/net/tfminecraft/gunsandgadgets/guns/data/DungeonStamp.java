package net.tfminecraft.gunsandgadgets.guns.data;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.persistence.PersistentDataContainerView;

import net.tfminecraft.gunsandgadgets.utils.GGCraftKeys;

/**
 * What a dungeon gun carries on top of a crafted one: how many times as hard it hits, one RGB colour
 * per part slot (in {@code ForgedLook.SLOTS} order) and its maker's marker (may be null).
 */
public record DungeonStamp(double power, List<Integer> colors, String marker) {

    /** Null for anything that isn't a dungeon gun. */
    public static DungeonStamp readFrom(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : readFrom(item.getPersistentDataContainer());
    }

    public static DungeonStamp readFrom(PersistentDataContainerView pdc) {
        Double power = pdc.get(GGCraftKeys.dungeonPower(), PersistentDataType.DOUBLE);
        if (power == null) {
            return null;
        }
        int[] colors = pdc.get(GGCraftKeys.dungeonColors(), PersistentDataType.INTEGER_ARRAY);
        return new DungeonStamp(power, Arrays.stream(colors).boxed().toList(),
                pdc.get(GGCraftKeys.dungeonMarker(), PersistentDataType.STRING));
    }

    public void applyTo(PersistentDataContainer pdc) {
        pdc.set(GGCraftKeys.dungeonPower(), PersistentDataType.DOUBLE, power);
        pdc.set(GGCraftKeys.dungeonColors(), PersistentDataType.INTEGER_ARRAY,
                colors.stream().mapToInt(Integer::intValue).toArray());
        if (marker != null) {
            pdc.set(GGCraftKeys.dungeonMarker(), PersistentDataType.STRING, marker);
        }
    }

    public List<Color> tints() {
        return colors.stream().map(Color::fromRGB).toList();
    }

    /** Keeps the name and other plugins' data of a dungeon gun that was rebuilt; its lore is the new gun's. */
    public static void carryOver(ItemStack from, ItemStack to) {
        ItemMeta old = from.getItemMeta();
        ItemMeta meta = to.getItemMeta();
        meta.displayName(old.displayName());
        PersistentDataContainer others = old.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        old.getPersistentDataContainer().copyTo(others, true);
        String own = GGCraftKeys.dungeonPower().getNamespace();
        others.getKeys().stream().filter(key -> key.getNamespace().equals(own)).toList().forEach(others::remove);
        others.copyTo(meta.getPersistentDataContainer(), false);
        to.setItemMeta(meta);
    }
}

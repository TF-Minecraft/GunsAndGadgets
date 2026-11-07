package net.tfminecraft.gunsandgadgets.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart.PartOption;
import net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;

/**
 * Aiming (config.yml {@code aiming}): crouching with a loaded, drawn gun raises it to the eye. The
 * gun shows its aiming pose and the player walks slower, which also narrows the field of view (the
 * zoom). A scoped gun (a part with the {@code scoped} option, the arclock) becomes a spyglass while
 * aimed: hold right-click to look through the scope, release to fire.
 */
public final class Aiming {

    static final NamespacedKey ZOOM = NamespacedKey.fromString("gunsandgadgets:aim_zoom");

    private final GunManager guns;
    private final Map<UUID, String> aimed = new HashMap<>();

    Aiming(GunManager guns) {
        this.guns = guns;
    }

    public boolean isAiming(Player player) {
        return aimed.containsKey(player.getUniqueId());
    }

    /** Looking through a scope: the aimed gun is a spyglass, so right-click scopes in instead of firing. */
    public boolean isScoped(Player player, ItemStack item) {
        return isAiming(player) && item != null && item.getType() == Material.SPYGLASS;
    }

    void onSneak(Player player, boolean sneaking) {
        if (sneaking) {
            start(player);
        } else {
            stop(player);
        }
    }

    /** Ends the aim when the player stops crouching, changes item, or the gun runs empty. */
    void tick(Player player) {
        String gunId = aimed.get(player.getUniqueId());
        if (gunId == null) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !gunId.equals(DrawTimer.gunId(held)) || guns.loadedBullets(held) <= 0) {
            stop(player);
        }
    }

    void start(Player player) {
        if (!Cache.aimingEnabled || isAiming(player)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String gunId = DrawTimer.gunId(held);
        SkinData skin = guns.skinOf(held);
        if (gunId == null || skin == null || guns.loadedBullets(held) <= 0
                || guns.isReloading(player) || !guns.isDrawn(player, gunId)) {
            return;
        }
        aimed.put(player.getUniqueId(), gunId);
        zoom(player, true);
        ItemStack item = guns.applyModel(held, skin, SkinState.ADS);
        if (scoped(item)) {
            item.setType(Material.SPYGLASS);
        }
        player.getInventory().setItemInMainHand(item);
    }

    void stop(Player player) {
        String gunId = aimed.remove(player.getUniqueId());
        if (gunId == null) {
            return;
        }
        zoom(player, false);
        ItemStack held = player.getInventory().getItemInMainHand();
        SkinData skin = guns.skinOf(held);
        if (gunId.equals(DrawTimer.gunId(held)) && skin != null && guns.loadedBullets(held) > 0) {
            player.getInventory().setItemInMainHand(guns.showLoaded(held, skin));
        }
    }

    /** Lower the walking speed while aiming; the client narrows the field of view with it. */
    static void zoom(Player player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(ZOOM);
        if (on && Cache.aimSlow > 0) {
            speed.addTransientModifier(new AttributeModifier(ZOOM, -Cache.aimSlow,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    static boolean scoped(ItemStack gun) {
        List<GunPart> parts = ForgedLook.stampedParts(gun);
        return parts != null && parts.stream().anyMatch(p -> p.hasOption(PartOption.SCOPED));
    }
}

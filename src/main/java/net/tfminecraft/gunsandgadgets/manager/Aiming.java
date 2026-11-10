package net.tfminecraft.gunsandgadgets.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * aimed: hold right-click to look through the scope, and release once it is steady
 * ({@code aiming.scope-settle-seconds}) to fire; an earlier release lowers it without a shot.
 */
public final class Aiming {

    static final NamespacedKey ZOOM = NamespacedKey.fromString("gunsandgadgets:aim_zoom");

    private final GunManager guns;
    private final Map<UUID, String> aimed = new HashMap<>();
    /** Players told their scope is steady during this hold. */
    private final Set<UUID> steadied = new HashSet<>();

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

    /**
     * Ends the aim when the player stops crouching, changes item, or the gun runs empty; starts it
     * while crouched with a loaded gun (crouching before the reload finished, then waiting, aims too).
     */
    void tick(Player player) {
        String gunId = aimed.get(player.getUniqueId());
        if (gunId == null) {
            if (player.isSneaking()) {
                start(player);
            }
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !gunId.equals(DrawTimer.gunId(held)) || guns.loadedBullets(held) <= 0) {
            stop(player);
            return;
        }
        settle(player, held);
    }

    /** Once per hold, a click and a word when the scope has been up long enough to fire on release. */
    void settle(Player player, ItemStack held) {
        if (!isScoped(player, held) || !player.isHandRaised()) {
            steadied.remove(player.getUniqueId());
            return;
        }
        if (player.getActiveItemUsedTime() >= Cache.scopeSettleTicks && steadied.add(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.6f, 1.6f);
            guns.sendActionBar(player, "§aSteady. §7Release to fire.");
        }
    }

    void start(Player player) {
        if (!Cache.aimingEnabled || isAiming(player)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String gunId = DrawTimer.gunId(held);
        if (gunId == null) {
            return;
        }
        SkinData skin = guns.skinOf(held);
        if (skin == null || guns.loadedBullets(held) <= 0
                || guns.isReloading(player) || !guns.isDrawn(player, gunId)) {
            return;
        }
        aimed.put(player.getUniqueId(), gunId);
        zoom(player, true);
        ItemStack item = guns.applyModel(held, skin, SkinState.ADS);
        if (scoped(item)) {
            item.setType(Material.SPYGLASS);
            guns.sendActionBar(player, Component.text("Hold ", NamedTextColor.GRAY)
                    .append(Component.keybind("key.use", NamedTextColor.YELLOW))
                    .append(Component.text(" to look through the scope, release when steady to fire.", NamedTextColor.GRAY)));
        }
        player.getInventory().setItemInMainHand(item);
    }

    void stop(Player player) {
        String gunId = aimed.remove(player.getUniqueId());
        steadied.remove(player.getUniqueId());
        if (gunId == null) {
            return;
        }
        zoom(player, false);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (gunId.equals(DrawTimer.gunId(held))) {
            // an emptied gun already shows its carry or reload pose
            SkinData skin = guns.skinOf(held);
            if (skin != null && guns.loadedBullets(held) > 0) {
                player.getInventory().setItemInMainHand(guns.showLoaded(held, skin));
            }
            return;
        }
        // moved or swapped away while aimed: it must not stay a spyglass in the aiming pose
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (gunId.equals(DrawTimer.gunId(contents[slot]))) {
                ItemStack lowered = lowered(contents[slot]);
                if (lowered != null) {
                    player.getInventory().setItem(slot, lowered);
                }
                return;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (gunId.equals(DrawTimer.gunId(cursor))) {
            ItemStack lowered = lowered(cursor);
            if (lowered != null) {
                player.setItemOnCursor(lowered);
            }
        }
    }

    /** Whether this item is the gun the player is aiming. */
    boolean isAimedGun(Player player, ItemStack item) {
        String gunId = aimed.get(player.getUniqueId());
        return gunId != null && gunId.equals(DrawTimer.gunId(item));
    }

    /** The gun out of its aiming look: the loaded pose, or carried when empty; null without a skin. */
    ItemStack lowered(ItemStack gun) {
        SkinData skin = guns.skinOf(gun);
        if (skin == null) {
            return null;
        }
        return guns.loadedBullets(gun) > 0 ? guns.showLoaded(gun, skin) : guns.applyModel(gun, skin, SkinState.CARRY);
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

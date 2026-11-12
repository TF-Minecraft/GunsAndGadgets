package net.tfminecraft.gunsandgadgets.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
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
 * zoom). A scoped gun (a part with the {@code scoped} option, the arclock) is aimed like any gun, and
 * right-click raises the scope: the gun becomes a spyglass, which the held key's repeat (every 4 ticks)
 * starts looking through. Release once it is steady ({@code aiming.scope-settle-seconds}) to fire; an
 * earlier release, or a tap that never looks through it, lowers it back to the aimed gun without a shot.
 * Raised, it shows its skin's scope pose when there is one (skins.yml {@code scope}), else the ads pose.
 * (Only a spyglass gets the game's scope zoom, but a spyglass is held in one hand, so the gun is one
 * only while raised: aimed and not looking through, it keeps the two-handed aim.)
 */
public final class Aiming {

    static final NamespacedKey ZOOM = NamespacedKey.fromString("gunsandgadgets:aim_zoom");

    private final GunManager guns;
    private final Map<UUID, String> aimed = new HashMap<>();
    /** Players told their scope is steady during this hold. */
    private final Set<UUID> steadied = new HashSet<>();
    /** The server tick each player raised their scope (the aimed gun became a spyglass). */
    private final Map<UUID, Integer> raised = new HashMap<>();
    /** A raised scope nobody looks through (a tap) is lowered after this many ticks. */
    static final int SCOPE_GRACE_TICKS = 10;
    /**
     * Until when (server tick) a click counts as right-click still held after the aim ended with the
     * scope raised. The held key repeats every 4 ticks, and each swallowed repeat extends it, so it ends
     * once the key has been let go for {@link #SCOPE_GRACE_TICKS}.
     */
    private final Map<UUID, Integer> heldAfterScope = new HashMap<>();

    Aiming(GunManager guns) {
        this.guns = guns;
    }

    public boolean isAiming(Player player) {
        return aimed.containsKey(player.getUniqueId());
    }

    /** A raised scope: the aimed gun is a spyglass, so right-click looks through it instead of firing. */
    public boolean isScoped(Player player, ItemStack item) {
        return isAiming(player) && item != null && item.getType() == Material.SPYGLASS;
    }

    /**
     * Whether this click is right-click still held after the aim ended while the scope was raised (an
     * un-crouch while looking through): it is swallowed rather than fired from the hip.
     */
    boolean holdsAfterScope(Player player) {
        Integer until = heldAfterScope.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        int now = Bukkit.getCurrentTick();
        if (now > until) {
            heldAfterScope.remove(player.getUniqueId());
            return false;
        }
        heldAfterScope.put(player.getUniqueId(), now + SCOPE_GRACE_TICKS);
        return true;
    }

    /** An aimed scoped gun whose scope isn't raised: a right-click raises it instead of firing. */
    boolean canRaiseScope(Player player, ItemStack item) {
        return isAimedGun(player, item) && item.getType() != Material.SPYGLASS && scoped(item);
    }

    /** Raises the scope of the aimed gun in hand: it becomes a spyglass, in its scope pose. */
    void raiseScope(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        SkinData skin = guns.skinOf(held);
        if (skin == null) {
            return;
        }
        ItemStack item = guns.applyModel(held, skin, skin.hasScopePose() ? SkinState.SCOPE : SkinState.ADS);
        // looking through needs a spyglass whatever the pose's material
        item.setType(Material.SPYGLASS);
        raised.put(player.getUniqueId(), Bukkit.getCurrentTick());
        player.getInventory().setItemInMainHand(item);
    }

    /** Lowers a raised scope back to the aimed gun (a tap, an early release, a shot with shots left). */
    void lowerScope(Player player) {
        raised.remove(player.getUniqueId());
        steadied.remove(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isScoped(player, held) || guns.loadedBullets(held) <= 0) {
            return;
        }
        SkinData skin = guns.skinOf(held);
        if (skin != null) {
            player.getInventory().setItemInMainHand(guns.showAimed(held, skin));
        }
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
        Integer up = raised.get(player.getUniqueId());
        if (up != null && !player.isHandRaised() && Bukkit.getCurrentTick() - up > SCOPE_GRACE_TICKS) {
            lowerScope(player);
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
        if (guns.loadedBullets(held) <= 0 || guns.isReloading(player) || !guns.isDrawn(player, gunId)) {
            return;
        }
        SkinData skin = guns.skinOf(held);
        if (skin == null) {
            return;
        }
        aimed.put(player.getUniqueId(), gunId);
        zoom(player, true);
        ItemStack item = guns.applyModel(held, skin, SkinState.ADS);
        if (scoped(item)) {
            guns.sendActionBar(player, Component.text("Hold ", NamedTextColor.GRAY)
                    .append(Component.keybind("key.use", NamedTextColor.YELLOW))
                    .append(Component.text(" to look through the scope, release when steady to fire.", NamedTextColor.GRAY)));
        }
        player.getInventory().setItemInMainHand(item);
    }

    void stop(Player player) {
        String gunId = aimed.remove(player.getUniqueId());
        steadied.remove(player.getUniqueId());
        if (raised.remove(player.getUniqueId()) != null) {
            heldAfterScope.put(player.getUniqueId(), Bukkit.getCurrentTick() + SCOPE_GRACE_TICKS);
        }
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

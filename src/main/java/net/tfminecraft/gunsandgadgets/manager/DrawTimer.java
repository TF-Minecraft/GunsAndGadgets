package net.tfminecraft.gunsandgadgets.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.stats.Handling;

/**
 * Draw time (config.yml {@code handling.draw}): a gun taken into the hand can't fire or reload until
 * it is drawn. Each gun type has a base time; the gun's {@code draw} stat makes it quicker (positive)
 * or slower, and the stance slows the draw while the player moves, sprints, jumps or rides. The
 * gun's hotbar slot shows the vanilla cooldown sweep for the time left. Without the section there
 * is no draw time.
 */
public final class DrawTimer {

    /** How often the cooldown sweep is refreshed while the stance changes the time left. */
    static final int REFRESH_TICKS = 4;

    private final Map<UUID, State> states = new HashMap<>();

    private static final class State {
        final String gunId;
        final int slot;
        final double needed;
        double progress;
        int age;
        boolean ready;

        State(String gunId, int slot, double needed) {
            this.gunId = gunId;
            this.slot = slot;
            this.needed = needed;
        }
    }

    /** Once a tick per player: starts a draw when a different gun (or slot) comes into the hand. */
    public void tick(Player player) {
        if (Cache.drawBaseTicks.isEmpty()) {
            states.remove(player.getUniqueId());
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String gunId = gunId(held);
        UUID id = player.getUniqueId();
        if (gunId == null) {
            states.remove(id);
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        State state = states.get(id);
        if (state == null || !state.gunId.equals(gunId) || state.slot != slot) {
            state = new State(gunId, slot, neededTicks(held));
            states.put(id, state);
            if (state.needed <= 0) {
                state.ready = true;
                return;
            }
            player.setCooldown(held, (int) Math.ceil(state.needed * Handling.drawMultiplier(player)));
            return;
        }
        if (state.ready) {
            return;
        }
        double multiplier = Handling.drawMultiplier(player);
        state.progress += 1.0 / multiplier;
        if (state.progress >= state.needed) {
            state.ready = true;
            player.setCooldown(held, 0);
            return;
        }
        if (++state.age % REFRESH_TICKS == 0) {
            player.setCooldown(held, (int) Math.ceil((state.needed - state.progress) * multiplier));
        }
    }

    /** Whether this gun is drawn; always true without draw times. */
    public boolean isReady(Player player, String gunId) {
        if (Cache.drawBaseTicks.isEmpty()) {
            return true;
        }
        State state = states.get(player.getUniqueId());
        return state != null && state.ready && state.gunId.equals(gunId);
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    /** The gun type's base draw time scaled by its draw stat (+40 = 40% quicker), within half to double. */
    static double neededTicks(ItemStack gun) {
        PersistentDataContainer pdc = gun.getItemMeta().getPersistentDataContainer();
        GunType type;
        try {
            type = GunType.valueOf(pdc.getOrDefault(key("gun_type"), PersistentDataType.STRING, "").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return 0;
        }
        int base = Cache.drawBaseTicks.getOrDefault(type, 0);
        int stat = pdc.getOrDefault(key("stat_value_draw"), PersistentDataType.INTEGER, 0);
        double factor = Math.min(2.0, Math.max(0.5, 1.0 - stat / 100.0));
        return base * factor;
    }

    static String gunId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key("gun_id"), PersistentDataType.STRING);
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(GunsAndGadgets.getInstance(), name);
    }
}

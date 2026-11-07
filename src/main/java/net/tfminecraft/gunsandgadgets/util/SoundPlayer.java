package net.tfminecraft.gunsandgadgets.util;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Utility for playing semicolon-separated custom sounds
 * stored on guns (reload, shoot, etc.).
 */
public class SoundPlayer {

    private static float level(String raw) {
        try {
            float value = Float.parseFloat(raw.trim());
            return Float.isFinite(value) && value >= 0 ? value : 1f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    /**
     * Play all sounds from a semicolon-separated list at a location.
     *
     * @param loc   Location to play sounds at
     * @param list  Semicolon-separated sound keys (e.g. "minecraft:gun.shoot1;minecraft:gun.shoot2")
     * @param fallback If true, play a fallback vanilla sound when no custom sounds are found
     */
    public static void playSounds(Location loc, String list, boolean fallback, float volume) {
        if (loc == null) return;

        boolean playedAny = false;
        if (list != null && !list.isBlank()) {
            String[] groups = list.split(";"); // each group
            Random rand = new Random();

            for (String group : groups) {
                // "key,key" or "key,key|pitch|volume" (pitch and volume scale this group)
                String[] levels = group.split("\\|");
                String[] keys = levels[0].split(",");
                if (keys.length == 0) continue;

                // pick one at random
                String chosen = keys[rand.nextInt(keys.length)].trim();
                if (chosen.isEmpty()) continue;

                float pitch = levels.length > 1 ? level(levels[1]) : 1f;
                float scale = levels.length > 2 ? level(levels[2]) : 1f;
                playedAny = true;
                loc.getWorld().playSound(loc, chosen, SoundCategory.PLAYERS, volume * scale,
                        Math.max(0.5f, Math.min(2f, pitch)));
            }
        }

        if (!playedAny && fallback) {
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume, 1.2f);
        }
    }
}



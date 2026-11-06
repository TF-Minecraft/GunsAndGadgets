package net.tfminecraft.gunsandgadgets.guns.stats;

import org.bukkit.Input;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.tfminecraft.gunsandgadgets.cache.Cache;

/**
 * Shot handling from {@code handling} in config.yml: spread by stance, damage falloff over
 * distance and the headshot bonus. The code defaults keep the old behaviour, so a server that
 * has not added the section plays exactly as before.
 */
public final class Handling {
    private Handling() {}

    /**
     * Spread multiplier for the shooter's stance when the shot leaves the barrel. Riding an animal
     * uses the mounted value and any other seat (chairs, vehicle seats) counts as standing still;
     * otherwise airborne beats sprinting, sprinting beats moving, and sneaking scales whatever
     * applies on the ground.
     */
    public static double spreadMultiplier(Player player) {
        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            return vehicle instanceof LivingEntity && !(vehicle instanceof ArmorStand) ? Cache.spreadMounted : 1.0;
        }
        if (isAirborne(player)) {
            return Cache.spreadAirborne;
        }
        double multiplier = 1.0;
        if (player.isSprinting()) {
            multiplier = Cache.spreadSprinting;
        } else if (isMoving(player.getCurrentInput())) {
            multiplier = Cache.spreadMoving;
        }
        if (player.isSneaking()) {
            multiplier *= Cache.spreadSneaking;
        }
        return multiplier;
    }

    // The server only knows the ground state the client reports, which is all a shot needs.
    @SuppressWarnings("deprecation")
    private static boolean isAirborne(Player player) {
        return !player.isOnGround() && !player.isInWater() && !player.isClimbing() && !player.isFlying();
    }

    static boolean isMoving(Input input) {
        return input != null && (input.isForward() || input.isBackward() || input.isLeft() || input.isRight());
    }

    /** Final spread in degrees: never below the old 0.02 floor, never above the configured cap. */
    public static double clampSpread(double degrees) {
        return Math.min(Cache.maxSpreadDegrees, Math.max(0.02, degrees));
    }

    /**
     * Damage multiplier for a hit {@code distance} blocks from the muzzle. Full damage up to the
     * start distance, then linear down to the minimum at the shot's maximum range.
     */
    public static double falloffMultiplier(double distance, double maxDistance) {
        double min = Cache.falloffMinMultiplier;
        if (min >= 1.0 || distance <= Cache.falloffStartDistance) {
            return 1.0;
        }
        if (distance >= maxDistance) {
            return min;
        }
        double t = (distance - Cache.falloffStartDistance) / (maxDistance - Cache.falloffStartDistance);
        return 1.0 - t * (1.0 - min);
    }
}

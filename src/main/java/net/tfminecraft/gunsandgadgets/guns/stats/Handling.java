package net.tfminecraft.gunsandgadgets.guns.stats;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/**
 * Shot handling from {@code handling} in config.yml: spread by stance, damage falloff over
 * distance and the headshot bonus. The code defaults keep the old behaviour, so a server that
 * has not added the section plays exactly as before.
 */
public final class Handling {
    /** 0.01 blocks a tick horizontally: anything slower is drift, not travel. */
    private static final double MOVING_SEAT_SPEED_SQUARED = 0.01 * 0.01;

    /** Longest coupling walked to find a train's lead vehicle (guards against a coupling loop). */
    private static final int MAX_COUPLING = 64;

    private Handling() {}

    /**
     * Spread multiplier for the shooter's stance when the shot leaves the barrel. A seat counts as
     * mounted while its vehicle moves (riding an animal always does) and as standing still
     * otherwise; on foot, airborne beats sprinting, sprinting beats moving, and sneaking scales
     * whatever applies on the ground.
     */
    public static double spreadMultiplier(Player player) {
        ActiveVehicle seat = vehicleFrameworkSeat(player);
        if (seat != null) {
            return isTravelling(seat) ? Cache.spreadMounted : 1.0;
        }
        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            boolean animal = vehicle instanceof LivingEntity && !(vehicle instanceof ArmorStand);
            return animal || isSeatMoving(vehicle) ? Cache.spreadMounted : 1.0;
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

    /**
     * The VehicleFramework vehicle the player sits in, or null. VehicleFramework seats riders
     * through ModelEngine's mount manager, so they have no Bukkit vehicle and are found here.
     */
    static ActiveVehicle vehicleFrameworkSeat(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) {
            return null;
        }
        return VehicleFramework.getVehicleManager().getByPassenger(player);
    }

    /**
     * A VehicleFramework vehicle reports motion through its access panel speed (engines, geared
     * engines and harnessed carts all set it). Coupled cars such as train carriages move with the
     * vehicle at the front of the coupling, so that one's speed decides.
     */
    static boolean isTravelling(ActiveVehicle vehicle) {
        ActiveVehicle lead = vehicle;
        for (int guard = 0; lead.hasParent() && guard < MAX_COUPLING; guard++) {
            lead = lead.getParent();
        }
        return lead.getAccessPanel() != null && lead.getAccessPanel().getSpeed() != 0.0;
    }

    /**
     * Minecarts are simulated on the server, so their velocity shows travel. A player-driven boat
     * moves on its driver's client and reads zero velocity on the server, so it counts as moving
     * while the driver (first passenger) holds forward or back.
     */
    static boolean isSeatMoving(Entity seat) {
        if (seat == null) {
            return false;
        }
        Vector velocity = seat.getVelocity();
        if (velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > MOVING_SEAT_SPEED_SQUARED) {
            return true;
        }
        if (!(seat instanceof Boat)) {
            return false;
        }
        List<Entity> passengers = seat.getPassengers();
        if (passengers.isEmpty() || !(passengers.get(0) instanceof Player driver)) {
            return false;
        }
        Input input = driver.getCurrentInput();
        return input != null && (input.isForward() || input.isBackward());
    }

    // The server only knows the ground state the client reports, which is all a shot needs.
    @SuppressWarnings("deprecation")
    private static boolean isAirborne(Player player) {
        return !player.isOnGround() && !player.isInWater() && !player.isClimbing() && !player.isFlying();
    }

    static boolean isMoving(Input input) {
        return input != null && (input.isForward() || input.isBackward() || input.isLeft() || input.isRight());
    }

    /** Final launch spread in degrees: never below the old 0.02 floor, never above the configured cap. */
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

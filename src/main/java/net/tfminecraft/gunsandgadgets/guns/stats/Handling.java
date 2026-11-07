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
        return spreadMultiplier(player, vehicleFrameworkSeat(player));
    }

    /** As {@link #spreadMultiplier(Player)}, with the VehicleFramework seat already looked up. */
    public static double spreadMultiplier(Player player, ActiveVehicle seat) {
        return spreadMultiplier(player, seat, Steadiness.NONE);
    }

    /** Shots fired without aiming spread wider (handling.spread-multipliers.hip), when aiming is on. */
    public static double hipMultiplier(boolean aimed) {
        return !Cache.aimingEnabled || aimed ? 1.0 : Cache.spreadHip;
    }

    /**
     * As {@link #spreadMultiplier(Player, ActiveVehicle)}, with the gun's parts deciding how much of
     * each stance's penalty or bonus applies.
     */
    public static double spreadMultiplier(Player player, ActiveVehicle seat, Steadiness steadiness) {
        if (seat != null) {
            return isTravelling(seat) ? steadiness.mounted(Cache.spreadMounted) : 1.0;
        }
        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            boolean animal = vehicle instanceof LivingEntity && !(vehicle instanceof ArmorStand);
            return animal || isSeatMoving(vehicle) ? steadiness.mounted(Cache.spreadMounted) : 1.0;
        }
        if (isAirborne(player)) {
            return steadiness.moving(Cache.spreadAirborne);
        }
        double multiplier = 1.0;
        if (player.isSprinting()) {
            multiplier = steadiness.moving(Cache.spreadSprinting);
        } else if (isMoving(player.getCurrentInput())) {
            multiplier = steadiness.moving(Cache.spreadMoving);
        }
        if (player.isSneaking()) {
            multiplier *= steadiness.sneaking(Cache.spreadSneaking);
        }
        return multiplier;
    }

    /**
     * How much of each stance's effect a gun's parts let through: 1 is the configured effect,
     * 0.5 half of it, 2 double. The moving factor covers walking, sprinting and jumping; a
     * steadier gun shrinks those penalties, and a steadier crouch deepens the sneaking bonus.
     */
    public record Steadiness(double moving, double mounted, double sneaking) {
        public static final Steadiness NONE = new Steadiness(1.0, 1.0, 1.0);
        static final double MIN = 0.5;
        static final double MAX = 2.0;

        /** From the gun's stats: positive means steadier (moving 40 lets 60% of the penalty through). */
        public static Steadiness of(int movingStat, int mountedStat, int sneakingStat) {
            return new Steadiness(clamp(1.0 - movingStat / 100.0), clamp(1.0 - mountedStat / 100.0),
                    clamp(1.0 + sneakingStat / 100.0));
        }

        private static double clamp(double factor) {
            return Math.min(MAX, Math.max(MIN, factor));
        }

        double moving(double stance) {
            return scale(stance, moving);
        }

        double mounted(double stance) {
            return scale(stance, mounted);
        }

        double sneaking(double stance) {
            return scale(stance, sneaking);
        }

        private static double scale(double stance, double factor) {
            return Math.max(0.0, 1.0 + (stance - 1.0) * factor);
        }
    }

    /**
     * The VehicleFramework vehicle the player sits in, or null. VehicleFramework seats riders
     * through ModelEngine's mount manager, so they have no Bukkit vehicle and are found here.
     */
    public static ActiveVehicle vehicleFrameworkSeat(Player player) {
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

    /**
     * How much longer drawing a gun takes in the player's stance (handling.draw.stance): riding a
     * moving seat, airborne, sprinting or walking, and crouched (which may speed it up).
     */
    public static double drawMultiplier(Player player) {
        ActiveVehicle seat = vehicleFrameworkSeat(player);
        if (seat != null) {
            return isTravelling(seat) ? Cache.drawMounted : 1.0;
        }
        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            boolean animal = vehicle instanceof LivingEntity && !(vehicle instanceof ArmorStand);
            return animal || isSeatMoving(vehicle) ? Cache.drawMounted : 1.0;
        }
        if (isAirborne(player)) {
            return Cache.drawAirborne;
        }
        double multiplier = 1.0;
        if (player.isSprinting()) {
            multiplier = Cache.drawSprinting;
        } else if (isMoving(player.getCurrentInput())) {
            multiplier = Cache.drawMoving;
        }
        if (player.isSneaking()) {
            multiplier *= Cache.drawSneaking;
        }
        return multiplier;
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
        return falloffMultiplier(distance, maxDistance, Cache.falloffStartDistance);
    }

    /** As {@link #falloffMultiplier(double, double)}, from a gun's own start distance. */
    public static double falloffMultiplier(double distance, double maxDistance, double startDistance) {
        double min = Cache.falloffMinMultiplier;
        if (min >= 1.0 || distance <= startDistance) {
            return 1.0;
        }
        if (distance >= maxDistance) {
            return min;
        }
        double t = (distance - startDistance) / (maxDistance - startDistance);
        return 1.0 - t * (1.0 - min);
    }

    /** The gun's full-damage distance: the configured start plus its parts' falloff stat. */
    public static double falloffStart(int falloffStat) {
        return Math.max(0.0, Cache.falloffStartDistance + falloffStat);
    }
}

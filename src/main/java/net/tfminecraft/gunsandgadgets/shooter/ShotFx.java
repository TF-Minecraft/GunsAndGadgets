package net.tfminecraft.gunsandgadgets.shooter;

import java.util.Collection;
import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import net.tfminecraft.gunsandgadgets.guns.forge.Metal;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.MetalLoader;

/**
 * A gun's firing look, stamped when it is built: the lock's ignition effect (parts.yml
 * {@code ignition}), the barrel's smoke amount, fan and back-blast ({@code smoke},
 * {@code smoke-cone}, {@code backblast}) and the barrel material's flash and trail colours
 * (metals.yml {@code flash}, {@code trail}). Stored as "ignition=flint;smoke=1;..." in the gun.
 */
public record ShotFx(String ignition, double smoke, double cone, boolean backblast, Color flash, Color trail) {

    /** The fan of the old muzzle smoke; a part's smoke-cone replaces it. */
    public static final double DEFAULT_CONE = 0.2;
    public static final ShotFx NONE = new ShotFx(null, 1.0, DEFAULT_CONE, false, null, null);

    /** The material the barrel was forged from, or null. */
    public static Metal barrelMetal(Collection<GunPart> parts) {
        for (GunPart part : parts) {
            if (part.getPartType() != null && part.getPartType().getId().equalsIgnoreCase("barrel")
                    && part.getMetalId() != null) {
                return MetalLoader.getByString(part.getMetalId());
            }
        }
        return null;
    }

    public static ShotFx of(Collection<GunPart> parts) {
        String ignition = null;
        double smoke = 1.0;
        double cone = 0.0;
        boolean backblast = false;
        for (GunPart part : parts) {
            if (part.getIgnition() != null) ignition = part.getIgnition().toLowerCase(Locale.ROOT);
            smoke *= part.getSmoke();
            cone = Math.max(cone, part.getSmokeCone());
            backblast |= part.hasBackblast();
        }
        Metal metal = barrelMetal(parts);
        return new ShotFx(ignition, smoke, cone > 0 ? cone : DEFAULT_CONE, backblast,
                metal == null ? null : metal.getFlash(), metal == null ? null : metal.getTrail());
    }

    /** Writes the gun's look, or removes it when nothing differs from the old effects. */
    public static void stamp(Collection<GunPart> parts, PersistentDataContainer pdc, NamespacedKey key) {
        ShotFx fx = of(parts);
        if (fx.equals(NONE)) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, fx.encode());
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        if (ignition != null) sb.append("ignition=").append(ignition).append(';');
        sb.append("smoke=").append(smoke).append(';');
        sb.append("cone=").append(cone).append(';');
        sb.append("backblast=").append(backblast).append(';');
        if (flash != null) sb.append("flash=").append(hex(flash)).append(';');
        if (trail != null) sb.append("trail=").append(hex(trail)).append(';');
        return sb.toString();
    }

    /** Reads a stamped look; unknown or broken entries keep the old effects. */
    public static ShotFx decode(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        String ignition = null;
        double smoke = 1.0;
        double cone = DEFAULT_CONE;
        boolean backblast = false;
        Color flash = null;
        Color trail = null;
        for (String entry : raw.split(";")) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "ignition" -> ignition = kv[1];
                case "smoke" -> smoke = number(kv[1], 1.0);
                case "cone" -> cone = number(kv[1], DEFAULT_CONE);
                case "backblast" -> backblast = Boolean.parseBoolean(kv[1]);
                case "flash" -> flash = color(kv[1]);
                case "trail" -> trail = color(kv[1]);
                default -> { }
            }
        }
        return new ShotFx(ignition, smoke, cone, backblast, flash, trail);
    }

    private static double number(String raw, double fallback) {
        try {
            return GunPart.factor(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Color color(String raw) {
        return raw.matches("[0-9a-fA-F]{6}") ? Color.fromRGB(Integer.parseInt(raw, 16)) : null;
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "%06x", color.asRGB());
    }

    /** Sparks, embers, steam or arcs at the lock, a little right of and below the line of sight. */
    public void spawnIgnition(Location eye, Vector forward) {
        if (ignition == null) return;
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        Location lock = eye.clone().add(forward.clone().multiply(0.6)).add(right.normalize().multiply(0.3)).add(0, -0.2, 0);
        World world = lock.getWorld();
        switch (ignition) {
            case "flint" -> world.spawnParticle(Particle.CRIT, lock, 8, 0.05, 0.05, 0.05, 0.15);
            case "ember" -> {
                world.spawnParticle(Particle.SMALL_FLAME, lock, 3, 0.03, 0.03, 0.03, 0.01);
                world.spawnParticle(Particle.SMOKE, lock, 2, 0.03, 0.03, 0.03, 0.01);
            }
            case "wheel" -> {
                world.spawnParticle(Particle.CRIT, lock, 6, 0.05, 0.05, 0.05, 0.2);
                world.spawnParticle(Particle.FLAME, lock, 2, 0.03, 0.03, 0.03, 0.02);
            }
            case "steam" -> world.spawnParticle(Particle.CLOUD, lock, 6, 0.08, 0.05, 0.08, 0.02);
            case "arc" -> world.spawnParticle(Particle.ELECTRIC_SPARK, lock, 12, 0.08, 0.08, 0.08, 0.1);
            case "fuse" -> {
                world.spawnParticle(Particle.SMALL_FLAME, lock, 2, 0.03, 0.03, 0.03, 0.01);
                world.spawnParticle(Particle.CRIT, lock, 4, 0.04, 0.04, 0.04, 0.1);
                world.spawnParticle(Particle.SMOKE, lock, 3, 0.04, 0.04, 0.04, 0.01);
            }
            default -> { }
        }
    }

    /** A burst in the barrel material's flash colour at the muzzle. */
    public void spawnFlash(Location muzzle) {
        if (flash == null) return;
        muzzle.getWorld().spawnParticle(Particle.DUST, muzzle, 6, 0.08, 0.08, 0.08, 0,
                new Particle.DustOptions(flash, 1.6f), true);
    }

    /** How many smoke particles each of the two muzzle smoke waves spawns. */
    public int smokeCount() {
        return (int) Math.round(10 * smoke);
    }
}

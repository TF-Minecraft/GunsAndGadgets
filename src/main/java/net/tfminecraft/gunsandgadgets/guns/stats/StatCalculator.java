package net.tfminecraft.gunsandgadgets.guns.stats;

public class StatCalculator {

    public static int calculateReloadTicks(int reloadStat) {
        int base = 60; // 3 seconds
        int modifier = Math.max(-20, Math.min(20, reloadStat));

        double factor = 1.0 - (modifier * 0.05);
        int ticks = (int) Math.round(base * factor);

        return Math.max(10, Math.min(200, ticks)); // clamp
    }

    public static double calculateAccuracy(int accuracyStat) {
        if (accuracyStat <= 0) {
            // Bad accuracy → exponential penalty
            double maxSpread = 25.0;
            double penaltyFactor = Math.pow(1.1, -(double) accuracyStat); // grows fast as negative
            return Math.min(maxSpread * penaltyFactor, 40.0);    // hard cap
        }

        if (accuracyStat <= 5) {
            // Continue smoothly from 0 → 25° down to 5 → 2.5°
            return lerp(accuracyStat, 0, 5, 25.0, 2.5);
        } else if (accuracyStat <= 20) {
            // 5 → 2.5° down to 20 → 1.2°
            return lerp(accuracyStat, 5, 20, 2.5, 1.2);
        } else {
            // 20 → 1.2° down to 30 → 0.07°
            return lerp(Math.min(30, accuracyStat), 20, 30, 1.2, 0.07);
        }
    }

    public static double calculateFireRate(int fireRateStat) {
        if (fireRateStat <= 0) {
            return lerp(fireRateStat, -30, 0, 4.0, 1.0); // -30 → 4s, 0 → 2s
        } else if (fireRateStat <= 10) {
            return lerp(fireRateStat, 0, 10, 1.0, 0.5); // 0 → 2s, 10 → 1s
        } else if (fireRateStat <= 20) {
            return lerp(fireRateStat, 10, 20, 0.5, 0.1); // 10 → 1s, 20 → 0.3s
        } else {
            return 0.1; // cap
        }
    }


    private static double lerp(double x, double x0, double x1, double y0, double y1) {
        double t = (x - x0) / (x1 - x0);
        return y0 + t * (y1 - y0);
    }


}


package net.tfminecraft.gunsandgadgets.guns.stats;

public enum Stats {
    ACCURACY("accuracy"),
    SPEED("speed"),
    RELOAD("reload"),
    FIRE_RATE("fire_rate"),
    CAPACITY("capacity"),
    DAMAGE("damage"),
    SPREAD("spread", "Pellet Spread"),
    RANGE("range"),
    PIERCE("pierce"),
    // Forged parts (see PartForge): percent change to shot damage, blocks added to the full-damage
    // range, and how much of each stance penalty or bonus applies (positive = steadier).
    DAMAGE_PERCENT("damage_percent", "Damage %"),
    FALLOFF("falloff", "Full Damage Range"),
    MOVING("moving", "Steady on the Move"),
    MOUNTED("mounted", "Steady Mounted"),
    SNEAKING("sneaking", "Steady Crouched");

    private final String key;
    private final String displayName;

    Stats(String key) {
        this(key, null);
    }

    Stats(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * The config key (used in parts.yml).
     */
    public String getKey() {
        return key;
    }

    /**
     * Capitalized display name (e.g. "Fire_rate" -> "Fire Rate").
     */
    public String getDisplayName() {
        if (displayName != null) return displayName;
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0)))
              .append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Find enum constant by config key (case-insensitive).
     */
    public static Stats fromKey(String key) {
        for (Stats s : values()) {
            if (s.key.equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }
}


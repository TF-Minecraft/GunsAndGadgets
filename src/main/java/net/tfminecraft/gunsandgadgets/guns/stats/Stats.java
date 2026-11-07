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
    SNEAKING("sneaking", "Steady Crouched"),
    // Percent quicker (positive) or slower to draw when the gun is taken into the hand.
    DRAW("draw", "Draw Speed");

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

    /** What the stat does, in a few words, for the gun's lore. */
    public String getHint() {
        return switch (this) {
            case ACCURACY -> "tighter aim";
            case SPEED -> "faster shot";
            case RELOAD -> "quicker reload";
            case FIRE_RATE -> "quicker follow-up shots";
            case CAPACITY -> "shots per load";
            case DAMAGE -> "damage per hit";
            case SPREAD -> "pellet cone";
            case RANGE -> "reach";
            case PIERCE -> "gets through armour";
            case DAMAGE_PERCENT -> "damage bonus";
            case FALLOFF -> "full damage further out";
            case MOVING -> "less spread on the move";
            case MOUNTED -> "less spread riding";
            case SNEAKING -> "less spread crouched";
            case DRAW -> "quicker to ready";
        };
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


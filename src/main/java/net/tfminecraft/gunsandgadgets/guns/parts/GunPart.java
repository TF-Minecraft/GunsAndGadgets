package net.tfminecraft.gunsandgadgets.guns.parts;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.SoundType;
import net.tfminecraft.gunsandgadgets.guns.forge.PartForge;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.PartDataLoader;

public class GunPart {

    private final String id;
    private final String name;
    private PartData partType;
    private Set<GunType> gunTypes = new HashSet<>();
    private final String itemKey;
    private final Map<Stats, Integer> stats;
    private final List<String> calibers;
    private List<String> caliberOverrides;
    private final EnumSet<PartOption> options = EnumSet.noneOf(PartOption.class);
    private List<String> lore = new ArrayList<>();
    private final boolean isTwoHanded;

    private final Map<SoundType, List<PartSound>> sounds = new HashMap<>();
    private final Map<SoundType, List<PartSound>> soundOverrides = new HashMap<>();

    private final Map<String, Integer> cost = new HashMap<>();

    // NEW: name-impact entries
    private final List<NameImpact> nameImpacts = new ArrayList<>();
    private final List<SkinImpact> skinImpacts = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<String> classRequirements = new ArrayList<>();

    private int revision = 0;
    private final boolean disabled;
    private final int tier;

    // Forged parts (PartForge): the definition and material this variant was forged from, the
    // reference tendencies its lore shows as arrows, and an optional powered/plain shot limit.
    private final String forgeBase;
    private final String forgeMetal;
    private final String forgeName;
    private final Map<Stats, Double> tendencies;
    private final Map<Stats, Double> fixedStats;
    private final AmmoFilter ammoFilter;
    private final String skinBase;
    private final String materialPermission;
    private final boolean declaresAmmoFamily;

    // Firing presentation (forged looks): the lock's ignition effect, the barrel's smoke and
    // back-blast, and how this part tunes the gun's report.
    private final String ignition;
    private final double smoke;
    private final double smokeCone;
    private final boolean backblast;
    private final double soundPitch;
    private final double soundVolume;

    public GunPart(String key, ConfigurationSection config) {
        this.id = key;
        this.name = StringFormatter.formatHex(config.getString("name", key));
        this.disabled = config.getBoolean("disabled", false);
        this.tier = config.getInt("tier", 0);

        PartData data = PartDataLoader.getByString(config.getString("part-type", "barrel"));
        if (data != null) partType = data;

        this.itemKey = config.getString("item", "v.stone");
        this.isTwoHanded = config.getBoolean("two-handed", false);

        for (String s : config.getStringList("type")) {
            try {
                GunType g = GunType.valueOf(s.toUpperCase());
                gunTypes.add(g);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Stats
        this.stats = new HashMap<>();
        List<String> statList = config.getStringList("stats");
        for (String s : statList) {
            String[] split = s.split(" ");
            if (split.length == 2) {
                Stats stat = Stats.fromKey(split[0]);
                if (stat != null) {
                    try {
                        stats.put(stat, Integer.parseInt(split[1]));
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid stat value for part " + id + ": " + s);
                    }
                }
            }

        }

        if(config.contains("cost")) {
            List<String> costList = config.getStringList("cost");
            for (String c : costList) {
                try {
                    // e.g. "v.iron_ingot(4)"
                    int start = c.indexOf("(");
                    int end = c.indexOf(")");
                    if (start > 0 && end > start) {
                        String ckey = c.substring(0, start).trim(); // "v.iron_ingot"
                        int amount = Integer.parseInt(c.substring(start + 1, end));
                        cost.put(ckey, amount);
                    } else {
                        // fallback: no () found, assume amount = 1
                        cost.put(c.trim(), 1);
                    }
                } catch (Exception e) {
                    System.out.println("Invalid cost entry for part " + id + ": " + c);
                }
            }
        }

        if(config.contains("permissions")) this.permissions = config.getStringList("permissions");
        if(config.contains("class")) this.classRequirements = config.getStringList("class");

        this.calibers = config.getStringList("caliber");
        if (config.contains("caliber-override")) {
            this.caliberOverrides = config.getStringList("caliber-override");
        }

        for (String opt : config.getStringList("options")) {
            try {
                options.add(PartOption.valueOf(opt.toUpperCase()));
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid part option for " + id + ": " + opt);
            }
        }

        for (String s : config.getStringList("lore")) {
            lore.add(StringFormatter.formatHex(s));
        }

        // Parse name-impacts
        for (String s : config.getStringList("name-impact")) {
            // "<fragment> <index> <weight>": the fragment may have spaces ("Rail Launcher 2 100").
            String[] split = s.trim().split(" ");
            if (split.length >= 3) {
                try {
                    int index = Integer.parseInt(split[split.length - 2]);
                    int weight = Integer.parseInt(split[split.length - 1]);
                    String fragment = StringFormatter.formatHex(String.join(" ", java.util.Arrays.copyOf(split, split.length - 2)));
                    nameImpacts.add(new NameImpact(fragment, index, weight));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid name-impact entry for part " + key + ": " + s);
                }
            }
        }

        for (String s : config.getStringList("skin-impact")) {
            String[] split = s.split(" ");
            if (split.length >= 2) {
                try {
                    String skinId = split[0].toLowerCase();
                    int weight = Integer.parseInt(split[1]);
                    skinImpacts.add(new SkinImpact(skinId, weight));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid skin-impact entry for part " + key + ": " + s);
                }
            }
        }

        this.forgeBase = config.getString("forge.base", null);
        this.forgeMetal = config.getString("forge.metal", null);
        this.forgeName = StringFormatter.formatHex(config.getString("forge.name", name));
        this.tendencies = PartForge.parse(config.getStringList("forge.tendencies"));
        this.fixedStats = PartForge.parse(config.getStringList("forge.fixed"));
        this.ammoFilter = AmmoFilter.parse(config.getString("ammo-filter", null));
        this.skinBase = config.getString("skin-base", null);
        this.materialPermission = config.getString("forge.permission", null);
        this.declaresAmmoFamily = config.contains("forge.ammo-family");

        this.ignition = config.getString("ignition", null);
        this.smoke = factor(config.getDouble("smoke", 1.0));
        this.smokeCone = factor(config.getDouble("smoke-cone", 0.0));
        this.backblast = config.getBoolean("backblast", false);
        this.soundPitch = factor(config.getDouble("sound-pitch", 1.0));
        this.soundVolume = factor(config.getDouble("sound-volume", 1.0));

        // Parse normal sounds
        PartSound.parseSection(config.getStringList("sounds"), sounds, "part " + id);

        // Parse overrides
        PartSound.parseSection(config.getStringList("sound-overrides"), soundOverrides, "part " + id);
    }

    /** NaN, infinite or negative values fall back to 1 so a typo never mutes or flips anything. */
    public static double factor(double value) {
        return Double.isFinite(value) && value >= 0 ? value : 1.0;
    }


    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public PartData getPartType() { return partType; }
    public String getItemKey() { return itemKey; }
    public Map<Stats, Integer> getStats() { return stats; }
    public List<String> getCalibers() { return calibers; }
    public boolean hasCaliberOverrides() { return caliberOverrides != null; }
    public List<String> getCaliberOverrides() { return caliberOverrides; }
    public boolean hasOption(PartOption option) { return options.contains(option); }
    public List<String> getLore() { return lore; }
    public Set<GunType> getGunTypes() { return gunTypes; }
    public Map<String, Integer> getCost() { return cost; }
    public boolean hasCost() { return cost.size() > 0; }
    public List<NameImpact> getNameImpacts() { return nameImpacts; }
    public List<SkinImpact> getSkinImpacts() { return skinImpacts; }
    public Map<SoundType, List<PartSound>> getSounds() { return sounds; }
    public Map<SoundType, List<PartSound>> getSoundOverrides() { return soundOverrides; }
    public List<String> getPermissions() { return permissions; }
    public List<String> getClassRequirements() { return classRequirements; }
    public boolean isTwoHanded() { return isTwoHanded; }

    public int getRevision() { return revision; }

    public void setRevision(int revision) { this.revision = revision; }

    public boolean isDisabled() { return disabled; }

    public boolean isEnabledForCrafting() { return !disabled; }

    public int getTier() { return tier; }

    public boolean isForged() { return forgeBase != null; }

    /** The forged part this variant belongs to, or the part's own id when it is not forged. */
    public String getBaseId() { return forgeBase != null ? forgeBase : id; }

    public String getMetalId() { return forgeMetal; }

    /** The part's name without its material. */
    public String getBaseName() { return forgeName; }

    public Map<Stats, Double> getTendencies() { return tendencies; }

    public Map<Stats, Double> getFixedStats() { return fixedStats; }

    public AmmoFilter getAmmoFilter() { return ammoFilter; }

    /** A forged part's material permission, required on top of any of the part's own permissions. */
    public String getMaterialPermission() { return materialPermission; }

    /** A forged barrel that loads a shot family, even when ammunition.yml has none of it. */
    public boolean declaresAmmoFamily() { return declaresAmmoFamily; }

    /** The id gun designs are matched against when no skin-impact names one: skin-base, else the base id. */
    public String getSkinBase() { return skinBase != null ? skinBase : getBaseId(); }

    public boolean hasTier() { return tier > 0; }

    /** The lock's ignition effect (flint, ember, wheel, steam, arc, fuse), or null. */
    public String getIgnition() { return ignition; }
    /** Muzzle smoke multiplier (1 = the old cloud). */
    public double getSmoke() { return smoke; }
    /** How wide the muzzle smoke fans out; 0 = not set by this part. */
    public double getSmokeCone() { return smokeCone; }
    /** Smoke also blows out of the back of the tube. */
    public boolean hasBackblast() { return backblast; }
    /** Multiplies the pitch of the gun's report. */
    public double getSoundPitch() { return soundPitch; }
    /** Multiplies the volume of the gun's report. */
    public double getSoundVolume() { return soundVolume; }

    /**
     * Canonical gameplay content for revision hashing. Excludes name, lore, permissions, disabled.
     */
    public String buildRevisionContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("item=").append(itemKey).append(';');
        sb.append("two-handed=").append(isTwoHanded).append(';');
        sb.append("part-type=").append(partType != null ? partType.getId() : "").append(';');
        sb.append("tier=").append(tier).append(';');

        List<String> types = gunTypes.stream()
                .map(GunType::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        sb.append("types=").append(String.join(",", types)).append(';');

        List<String> statParts = stats.entrySet().stream()
                .map(e -> e.getKey().getKey() + "(" + e.getValue() + ")")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        sb.append("stats=").append(String.join(",", statParts)).append(';');

        List<String> costParts = cost.entrySet().stream()
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        sb.append("cost=").append(String.join(",", costParts)).append(';');

        List<String> caliberCopy = new ArrayList<>(calibers);
        Collections.sort(caliberCopy, String.CASE_INSENSITIVE_ORDER);
        sb.append("caliber=").append(String.join(",", caliberCopy)).append(';');

        if (caliberOverrides != null) {
            List<String> overrideCopy = new ArrayList<>(caliberOverrides);
            Collections.sort(overrideCopy, String.CASE_INSENSITIVE_ORDER);
            sb.append("caliber-override=").append(String.join(",", overrideCopy)).append(';');
        }

        if (!options.isEmpty()) {
            List<String> optionNames = options.stream().map(Enum::name).sorted().toList();
            sb.append("options=").append(String.join(",", optionNames)).append(';');
        }

        if (ammoFilter != null) {
            sb.append("ammo-filter=").append(ammoFilter.name()).append(';');
        }
        if (skinBase != null) {
            sb.append("skin-base=").append(skinBase).append(';');
        }

        List<String> classCopy = new ArrayList<>(classRequirements);
        Collections.sort(classCopy, String.CASE_INSENSITIVE_ORDER);
        sb.append("class=").append(String.join(",", classCopy)).append(';');

        sb.append("name-impact=").append(buildNameImpactContent()).append(';');
        sb.append("skin-impact=").append(buildSkinImpactContent()).append(';');
        sb.append("fx=").append(ignition).append(',').append(smoke).append(',').append(smokeCone).append(',')
                .append(backblast).append(',').append(soundPitch).append(',').append(soundVolume).append(';');
        sb.append("sounds=").append(buildSoundContent(sounds)).append(';');
        sb.append("sound-overrides=").append(buildSoundContent(soundOverrides));
        return sb.toString();
    }

    private String buildNameImpactContent() {
        List<String> parts = nameImpacts.stream()
                .map(ni -> ni.getIndex() + ":" + ni.getWeight() + ":" + ni.getFragment())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return String.join(",", parts);
    }

    private String buildSkinImpactContent() {
        List<String> parts = skinImpacts.stream()
                .map(si -> si.getSkinId() + ":" + si.getWeight())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return String.join(",", parts);
    }

    private String buildSoundContent(Map<SoundType, List<PartSound>> source) {
        List<String> parts = new ArrayList<>();
        for (SoundType type : EnumSet.allOf(SoundType.class)) {
            List<PartSound> list = source.get(type);
            if (list == null) {
                continue;
            }
            for (PartSound sound : list) {
                String gunType = sound.getRequiredType() != null ? sound.getRequiredType().name() : "";
                List<String> keys = new ArrayList<>(sound.getSoundKeys());
                Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
                parts.add(type.name() + ":" + gunType + ":" + String.join("|", keys) + sound.levelSuffix());
            }
        }
        Collections.sort(parts, String.CASE_INSENSITIVE_ORDER);
        return String.join(",", parts);
    }

    // Inner class for name-impact
    public static class NameImpact {
        private final String fragment;
        private final int index;
        private final int weight;

        public NameImpact(String fragment, int index, int weight) {
            this.fragment = fragment;
            this.index = index;
            this.weight = weight;
        }

        public String getFragment() { return fragment; }
        public int getIndex() { return index; }
        public int getWeight() { return weight; }
    }

    public static class SkinImpact {
        private final String skinId;
        private final int weight;

        public SkinImpact(String skinId, int weight) {
            this.skinId = skinId;
            this.weight = weight;
        }

        public String getSkinId() { return skinId; }
        public int getWeight() { return weight; }
    }

    /**
     * Firing presentation owned by the part. Steamlocks are airguns, so muzzle
     * smoke and flash live here rather than on a shot type.
     */
    public enum PartOption {
        SMOKELESS, NO_LIGHT,
        /** Aiming looks through a scope (spyglass zoom): hold right-click to scope in, release to fire. */
        SCOPED
    }

    /** A part that only loads powered shot (a proofed chamber) or plain shot (an airgun lock). */
    public enum AmmoFilter {
        POWERED, PLAIN;

        public boolean allows(boolean powered) {
            return powered == (this == POWERED);
        }

        static AmmoFilter parse(String raw) {
            if (raw == null) return null;
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid ammo-filter: " + raw);
                return null;
            }
        }
    }

    public static class PartSound {
        private final List<String> soundKeys; // multiple options
        private final GunType requiredType; // may be null
        private final double pitch;
        private final double volume;

        public PartSound(List<String> soundKeys, GunType requiredType) {
            this(soundKeys, requiredType, 1.0, 1.0);
        }

        public PartSound(List<String> soundKeys, GunType requiredType, double pitch, double volume) {
            this.soundKeys = soundKeys;
            this.requiredType = requiredType;
            this.pitch = factor(pitch);
            this.volume = factor(volume);
        }

        /**
         * Reads "<shoot|reload> <key>,<key>[(gun type)] [pitch=<n>] [volume=<n>]" lines into {@code target}.
         * One key is picked at random per group; pitch and volume scale that group.
         */
        public static void parseSection(List<String> entries, Map<SoundType, List<PartSound>> target, String owner) {
            for (String s : entries) {
                String[] split = s.trim().split("\\s+");
                if (split.length < 2) continue;

                SoundType type;
                try {
                    type = SoundType.valueOf(split[0].toUpperCase());
                } catch (IllegalArgumentException ex) {
                    System.out.println("Invalid sound type for " + owner + ": " + split[0]);
                    continue;
                }

                String soundKeys = split[1];
                GunType requiredType = null;
                if (soundKeys.contains("(") && soundKeys.endsWith(")")) {
                    int start = soundKeys.indexOf("(");
                    String typeStr = soundKeys.substring(start + 1, soundKeys.length() - 1);
                    soundKeys = soundKeys.substring(0, start);
                    try {
                        requiredType = GunType.valueOf(typeStr.toUpperCase());
                    } catch (Exception e) {
                        System.out.println("Invalid gun type for sound in " + owner + ": " + typeStr);
                    }
                }

                double pitch = 1.0;
                double volume = 1.0;
                for (int i = 2; i < split.length; i++) {
                    String[] kv = split[i].split("=", 2);
                    try {
                        double value = Double.parseDouble(kv.length == 2 ? kv[1] : "");
                        if (kv[0].equalsIgnoreCase("pitch")) {
                            pitch = value;
                        } else if (kv[0].equalsIgnoreCase("volume")) {
                            volume = value;
                        } else {
                            System.out.println("Unknown sound setting for " + owner + ": " + split[i]);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid sound setting for " + owner + ": " + split[i]);
                    }
                }

                List<String> keys = Arrays.stream(soundKeys.split(","))
                        .map(String::trim)
                        .filter(k -> !k.isEmpty())
                        .toList();

                target.computeIfAbsent(type, t -> new ArrayList<>())
                    .add(new PartSound(keys, requiredType, pitch, volume));
            }
        }

        public double getPitch() { return pitch; }
        public double getVolume() { return volume; }

        /** "|pitch|volume" when either differs from 1, so plain sounds keep their old stored form. */
        public String levelSuffix() {
            return levelSuffix(pitch, volume);
        }

        public static String levelSuffix(double pitch, double volume) {
            if (pitch == 1.0 && volume == 1.0) return "";
            return "|" + trim(pitch) + "|" + trim(volume);
        }

        private static String trim(double value) {
            String text = String.format(java.util.Locale.ROOT, "%.3f", value);
            return text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        public GunType getRequiredType() { return requiredType; }

        public boolean matches(GunType type) {
            return requiredType == null || requiredType == type;
        }

        public List<String> getSoundKeys() { return soundKeys; }

        /** Pick one random key */
        public String pickRandomKey(Random rand) {
            if (soundKeys.isEmpty()) return null;
            return soundKeys.get(rand.nextInt(soundKeys.size()));
        }
    }
}

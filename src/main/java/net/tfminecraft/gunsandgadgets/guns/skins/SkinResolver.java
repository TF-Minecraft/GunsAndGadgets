package net.tfminecraft.gunsandgadgets.guns.skins;

import java.util.*;

import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;

public class SkinResolver {

    private final Map<String, SkinData> skins;

    public SkinResolver(Map<String, SkinData> skins) {
        this.skins = skins;
    }

    /**
     * Resolve the correct skin.
     * @param type   The final gun type (rifle/pistol/shotgun).
     * @param parts  All selected gun parts (at least action + barrel).
     * @return SkinData or null if nothing matched.
     */
    public SkinData resolve(GunType type, Collection<GunPart> parts) {
        // 1. Collect all skin-impact votes
        Map<String, Integer> votes = new HashMap<>();
        for (GunPart part : parts) {
            if (part.getSkinImpacts() == null) continue;
            for (GunPart.SkinImpact impact : part.getSkinImpacts()) {
                votes.merge(impact.getSkinId().toLowerCase(), impact.getWeight(), Integer::sum);
            }
        }

        // 2. If we got any votes, pick the highest
        if (!votes.isEmpty()) {
            String bestId = votes.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElseThrow();

            if (skins.containsKey(bestId)) {
                return skins.get(bestId);
            }
        }

        // 3. Fallback: use action + type scheme
        GunPart action = parts.stream()
                .filter(p -> p.getPartType().getId().equalsIgnoreCase("action"))
                .findFirst().orElse(null);

        if (action != null) {
            String actionId = action.getId().toLowerCase();

            for (SkinData skin : skins.values()) {
                // must support this gun type
                if (!skin.getTypes().contains(type.name().toLowerCase())) continue;

                // must include the action id in its key
                if (!skin.getId().toLowerCase().contains(actionId)) continue;

                return skin;
            }
        }

        return null;
    }
}

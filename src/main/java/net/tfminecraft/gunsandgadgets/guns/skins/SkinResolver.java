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
        // 0. A gun built only from forged parts is drawn part by part when the pack has a forged
        //    model for its type (skins.yml forged-model).
        if (!parts.isEmpty() && parts.stream().allMatch(GunPart::isForged)) {
            for (SkinData skin : skins.values()) {
                if (skin.isForged() && skin.getTypes().contains(type.name().toLowerCase())) {
                    return skin;
                }
            }
        }

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
            // Forged actions match by their part (matchlock, not matchlock@steel); a new action can
            // borrow an existing design with skin-base.
            String actionId = action.getSkinBase().toLowerCase();

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

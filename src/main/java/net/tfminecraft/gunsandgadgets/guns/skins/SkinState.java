package net.tfminecraft.gunsandgadgets.guns.skins;

public enum SkinState {
    CARRY,
    AIM,
    RELOAD,
    /** Aimed: crouching with a loaded gun (the forged models' sights-up pose). */
    ADS,
    /**
     * Aimed with a scoped gun, when the skin has a {@code scope} pose: the gun is a spyglass, so the
     * pose also says how it looks held to the eye while looking through the scope.
     */
    SCOPE,
}

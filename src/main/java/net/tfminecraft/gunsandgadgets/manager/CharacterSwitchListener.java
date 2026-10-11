package net.tfminecraft.gunsandgadgets.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;

/** Registered only when RPCharacters is enabled. */
public class CharacterSwitchListener implements Listener {

    private final GunManager gunManager;

    public CharacterSwitchListener(GunManager gunManager) {
        this.gunManager = gunManager;
    }

    // A reload belongs to the character who started it, so a switch cancels it.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCharacterActivated(CharacterActivatedEvent event) {
        gunManager.cancelActiveReload(event.getOwner());
    }
}

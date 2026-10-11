package net.tfminecraft.gunsandgadgets.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;

/** Refunds while the outgoing character inventory is still loaded. */
public class CharacterSessionListener implements Listener {

    private final GunManager gunManager;

    public CharacterSessionListener(GunManager gunManager) {
        this.gunManager = gunManager;
    }

    // End precedes the outgoing inventory save and any incoming character.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSessionEnd(CharacterSessionEndEvent event) {
        gunManager.cancelActiveReload(event.getPlayer());
    }
}

package net.tfminecraft.gunsandgadgets.manager;

import net.tfminecraft.gunsandgadgets.util.LegacyModelData;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.event.item.UntargetedWeaponUseEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.attributes.AttributeReader;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.guns.stats.StatCalculator;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;
import net.tfminecraft.gunsandgadgets.shooter.ProjectileShooter;
import net.tfminecraft.gunsandgadgets.util.Caliber;
import net.tfminecraft.gunsandgadgets.util.SoundPlayer;
import net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.message.actionbar.ActionBarPriority;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class GunManager implements Listener {

    private final NamespacedKey skinKey = new NamespacedKey(GunsAndGadgets.getInstance(), "skin_id");
    private final NamespacedKey gunKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_id");
    private final NamespacedKey typeKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_type");

    private final NamespacedKey bulletsKey = new NamespacedKey(GunsAndGadgets.getInstance(), "bullets_loaded");
    private final NamespacedKey capacityKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_capacity");

    private final NamespacedKey lastFireKey = new NamespacedKey(GunsAndGadgets.getInstance(), "last_fire");
    private final NamespacedKey fireRateKey = new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_fire_rate");

    // New PDC keys
    private final NamespacedKey reloadAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "reload_ammo");
    private final NamespacedKey reloadAmountKey = new NamespacedKey(GunsAndGadgets.getInstance(), "reload_amount");
    private final NamespacedKey loadedAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "ammo_loaded");
    // Ammunition the player picked for the next reload; absent means the first carried caliber.
    private final NamespacedKey selectedAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "ammo_selected");

    private final Map<UUID, Boolean> reloading = new HashMap<>();
    // Preserve the exact inputs through config reloads, including their item metadata.
    private final Map<UUID, Collection<ItemStack>> reloadRefunds = new HashMap<>();
    // One click can arrive as several interact events (each hand, or an entity then the air)
    // spread over neighbouring ticks; clients allow a new click only every 4 ticks.
    private static final int AMMO_SWITCH_BURST_TICKS = 2;
    private final Map<UUID, Integer> lastAmmoSwitchTick = new HashMap<>();

    @EventHandler
    public void preventOldMuskets(UntargetedWeaponUseEvent e) {
        if(e.getWeapon().getNBTItem().getType().equalsIgnoreCase("MUSKETS")) e.setCancelled(true);
    }

    // Preserve the material interaction classification used to decide whether a gun click is consumed; the replacement BlockType query is also deprecated.
    @SuppressWarnings({"deprecation"})
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;

        // skip interactable blocks like chests, doors, etc.
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
            return;
        }

        handleGunUse(event.getPlayer(), event); // your gun logic
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            return; // allow placing guns in frames
        }

        handleGunUse(event.getPlayer(), event); // your gun logic
    }

    private boolean checkClass(Player p, ItemStack gun) {
        if(p.hasPermission("gg.bypass_class")) return true;
        if(!NBTItem.get(gun).hasType()) return true;
        LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(gun));
        StringListData data = (StringListData) mmo.getData(ItemStats.REQUIRED_CLASS);
        if(data == null) return true;
        PlayerData pd = PlayerData.get(p.getUniqueId());
        for(String s : data.getList()) {
            if(pd.getProfess().getId().equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public void handleGunUse(Player player, Cancellable event) {
        //quick offhand check just cancel
        ItemStack item = player.getInventory().getItemInOffHand();
        if(item != null) {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                String skinId = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
                if (skinId != null) {
                    event.setCancelled(true);;
                }
            }
        }
        
        
        item = player.getInventory().getItemInMainHand();
        if(item == null) return;
        if (!item.hasItemMeta()) return;

        UUID id = player.getUniqueId();

        ItemMeta meta = item.getItemMeta();
        String skinId = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        if (skinId == null) {
            return;
        }
        String gunId = meta.getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
        if (gunId == null) {
            return;
        }
        if (blockIfBroken(player, item)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if(!checkClass(player, item)) return;
        // ✅ Block duplicate reloads
        if (reloading.getOrDefault(id, false)) {
            return;
        }
        if (player.isSneaking()) {
            int tick = Bukkit.getCurrentTick();
            Integer last = lastAmmoSwitchTick.get(id);
            if (last == null || tick - last > AMMO_SWITCH_BURST_TICKS) {
                lastAmmoSwitchTick.put(id, tick);
                cycleAmmo(player, item);
            }
            return;
        }
        reloading.put(id, true);

        SkinData skin = SkinLoader.get().get(skinId);
        if (skin == null) {
            reloading.remove(id);
            return;
        }

        int bullets = meta.getPersistentDataContainer()
            .getOrDefault(bulletsKey, PersistentDataType.INTEGER, 0);
        if (bullets > 0) {
            String ammoId = meta.getPersistentDataContainer().get(loadedAmmoKey, PersistentDataType.STRING);
            Ammunition ammo = AmmunitionLoader.getByString(ammoId);
            if (ammo == null) {
                reloading.remove(id);
                return;
            }
            long now = System.currentTimeMillis();
            int fireRateStat = meta.getPersistentDataContainer()
                    .getOrDefault(fireRateKey, PersistentDataType.INTEGER, 0);

            // Convert fireRateStat to a cooldown in ms
            long cooldown = (long) (StatCalculator.calculateFireRate(fireRateStat) * 1000);

            long lastFired = meta.getPersistentDataContainer()
                    .getOrDefault(lastFireKey, PersistentDataType.LONG, 0L);

            if (now - lastFired < cooldown) {
                // Too soon to fire again
                reloading.remove(id);
                return;
            }

            // Update last fire time
            meta.getPersistentDataContainer().set(lastFireKey, PersistentDataType.LONG, now);
            item.setItemMeta(meta);

            // Consume bullet & shoot
            bullets--;
            meta.getPersistentDataContainer().set(bulletsKey, PersistentDataType.INTEGER, bullets);
            item.setItemMeta(meta);
            ProjectileShooter.shoot(player, item, ammo);
            unloadSame(player, item);

            if (bullets <= 0) {
                // Show carry model when empty
                meta.getPersistentDataContainer().remove(loadedAmmoKey);
                item.setItemMeta(meta);
                item = applyModel(item, skin, SkinState.CARRY);

                // Clear arrow if crossbow
                if (item.getType() == Material.CROSSBOW) {
                    CrossbowMeta cbMeta = (CrossbowMeta) item.getItemMeta();
                    cbMeta.setChargedProjectiles(new ArrayList<>());
                    item.setItemMeta(cbMeta);
                }
            } else {
                if (item.getType() == Material.CROSSBOW) {
                    chargeCrossbow(item, ammoId, bullets);
                }
            }
            player.getInventory().setItemInMainHand(item);
            reloading.remove(id);
            return;
        }

        int capacity = meta.getPersistentDataContainer()
        .getOrDefault(capacityKey, PersistentDataType.INTEGER, 1);

        // 🔫 Try to consume ammo before reload
        List<Ammunition> calibers = Caliber.get(item); // your helper from before
        Ammunition selected = getSelectedAmmo(meta, calibers);
        String taken = takeAmmo(player, capacity, selected == null ? calibers : List.of(selected));

        if (taken.equals("none")) {
            if (selected != null) {
                sendActionBar(player, "§cYou have no " + getAmmoName(selected)
                        + "§c left. Crouch and right-click to choose another.");
            }
            reloading.remove(id);
            return;
        }

        // Parse ammo info
        String[] split = taken.split("\\.");
        String ammoId = split[0];
        int takenAmount = Integer.parseInt(split[1]);
        if (calibers.size() > 1) {
            sendActionBar(player, "§7Loading " + getAmmoName(AmmunitionLoader.getByString(ammoId)));
        }

        // Save ammo info in PDC (for refund or finalize)
        meta.getPersistentDataContainer().set(reloadAmmoKey, PersistentDataType.STRING, ammoId);
        meta.getPersistentDataContainer().set(reloadAmountKey, PersistentDataType.INTEGER, takenAmount);
        item.setItemMeta(meta);

        // Switch to reload model
        player.getInventory().setItemInMainHand(applyModel(item, skin, SkinState.RELOAD));


        int reloadStat = meta.getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(GunsAndGadgets.getInstance(), "stat_value_reload"),
                        PersistentDataType.INTEGER, 0);

        int reloadTicks = (int) Math.round(StatCalculator.calculateReloadTicks(reloadStat)*AttributeReader.getReloadReductionMultFromAttributes(player));

        // Switch to reload model
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(applyModel(item, skin, SkinState.RELOAD));
        String reloadSounds = meta.getPersistentDataContainer().get(
            new NamespacedKey(GunsAndGadgets.getInstance(), "reload_sounds"),
            PersistentDataType.STRING
        );
        SoundPlayer.playSounds(player.getLocation(), reloadSounds, false, 1f);


        int reloadSlot = player.getInventory().getHeldItemSlot();
        Collection<ItemStack> reservation = reloadRefunds.get(id);
        new BukkitRunnable() {
            int tick = 0;
            Location lastLoc = player.getLocation().clone();

            // Keep the existing legacy text representation, formatting, and exact-string comparisons.
            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                ItemStack current = player.getInventory().getItemInMainHand();
                // A cancelled task must never act on a newer reload for this player.
                if (reloadRefunds.get(id) != reservation) {
                    cancel();
                    return;
                }
                if (current.getItemMeta() == null) {
                    cancelReload(player, reloadSlot);
                    cancel();
                    return;
                }

                String currentId = current.getItemMeta().getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
                if(!gunId.equals(currentId)) {
                    cancelReload(player, reloadSlot);
                    cancel();
                    return;
                }

                double progress = (double) tick / (reloadTicks - 1);
                String bar = makeProgressBar(progress, 10, "§7", "§f");
                player.sendTitle("", bar, 0, 5, 0);
                // 📍 Check movement since last tick
                Location now = player.getLocation();
                double moved = now.distanceSquared(lastLoc);
                lastLoc = now.clone();

                if (shouldDelayReload(moved, Math::random)) {
                    return;
                }
                tick++;

                if (tick >= reloadTicks) {
                    // ✅ Finish reload
                    current = applyModel(current, skin, SkinState.AIM);
                    ItemMeta reloadMeta = current.getItemMeta();

                    // Load exactly how many bullets were consumed
                    int loaded = reloadMeta.getPersistentDataContainer()
                            .getOrDefault(reloadAmountKey, PersistentDataType.INTEGER, 0);
                    String loadedAmmo = reloadMeta.getPersistentDataContainer()
                            .get(reloadAmmoKey, PersistentDataType.STRING);
                    // Save bullets + ammo type
                    reloadMeta.getPersistentDataContainer().set(bulletsKey, PersistentDataType.INTEGER, loaded);
                    if (loadedAmmo != null) {
                        reloadMeta.getPersistentDataContainer().set(loadedAmmoKey, PersistentDataType.STRING, loadedAmmo);
                    }

                    // Clear temporary reload info
                    reloadMeta.getPersistentDataContainer().remove(reloadAmmoKey);
                    reloadMeta.getPersistentDataContainer().remove(reloadAmountKey);

                    current.setItemMeta(reloadMeta);
                    player.getInventory().setItemInMainHand(current);

                    if (current.getType() == Material.CROSSBOW) {
                        chargeCrossbow(current, loadedAmmo, loaded);
                        player.getInventory().setItemInMainHand(current);
                    }

                    /*
                    double spread = StatCalculator.calculateAccuracy(accuracyStat);
                    player.sendMessage("§7Accuracy: §e" + String.format("%.2f° spread", spread));

                    int fireRateStat = meta.getPersistentDataContainer()
                        .getOrDefault(fireRateKey, PersistentDataType.INTEGER, 0);

                    // Convert fireRateStat to a cooldown in ms
                    long cooldown = (long) (StatCalculator.calculateFireRate(fireRateStat) * 1000);
                    player.sendMessage("§7Fire Rate: §e" + cooldown);

                    //player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
                    */
                    reloadRefunds.remove(id);
                    reloading.remove(id);
                    cancel();
                }
            }
        }.runTaskTimer(GunsAndGadgets.getInstance(), 0L, 1L);

    }

    private static boolean shouldDelayReload(double moved, java.util.function.DoubleSupplier random) {
        if (moved <= 0.0025) return false;
        double chance = Math.min(1.0, Math.sqrt(moved) * 2.0);
        return random.getAsDouble() < chance;
    }

    private void unloadSame(Player p, ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        String gunId = meta.getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
        String gunType = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        ItemStack[] contents = p.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack i = contents[slot];
            if(i == null) continue;
            ItemMeta m = i.getItemMeta();
            if(m == null) continue;
            String id = m.getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            if(id.equalsIgnoreCase(gunId)) continue;
            String type = m.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
            if(!type.equalsIgnoreCase(gunType)) continue;
            int bullets = m.getPersistentDataContainer()
                .getOrDefault(bulletsKey, PersistentDataType.INTEGER, 0);
            if(bullets <= 0) continue;
            String ammoId = m.getPersistentDataContainer().get(loadedAmmoKey, PersistentDataType.STRING);
            Ammunition ammo = AmmunitionLoader.getByString(ammoId);
            if (ammo == null) continue;
            ItemStack refund = TLibs.getItemAPI().getCreator().getItemFromPath(ammo.getInput());
            if (refund == null || refund.getType().isAir()) continue;
            refund.setAmount(bullets);

            // Only clear loaded state once its ammunition can actually be returned.
            m.getPersistentDataContainer().remove(loadedAmmoKey);
            m.getPersistentDataContainer().remove(bulletsKey);
            i.setItemMeta(m);
            String skinId = m.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
            SkinData skin = SkinLoader.get().get(skinId);
            if (skin != null) i = applyModel(i, skin, SkinState.CARRY);

            // A missing skin must not prevent either uncharging or returning ammunition.
            if (i.getType() == Material.CROSSBOW) {
                CrossbowMeta cbMeta = (CrossbowMeta) i.getItemMeta();
                cbMeta.setChargedProjectiles(new ArrayList<>());
                i.setItemMeta(cbMeta);
            }
            giveOrDrop(p, refund);
            p.getInventory().setItem(slot, i);
        }
        p.updateInventory();
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        // If this player is currently reloading, cancel it
        if (reloading.containsKey(id)) {
            cancelReload(player, event.getPreviousSlot());
        }

        Bukkit.getScheduler().runTask(GunsAndGadgets.getInstance(), () -> {
            ItemStack held = player.getInventory().getItem(event.getNewSlot());
            if (held == null || !held.hasItemMeta()) {
                return;
            }
            String heldGunId = held.getItemMeta().getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
            if (heldGunId == null) {
                return;
            }
            if (blockIfBroken(player, held)) {
                player.getInventory().setItem(event.getNewSlot(), held);
            }
        });
    }

    private void cancelReload(Player player, int slot) {
        UUID id = player.getUniqueId();
        reloading.remove(id);
        ItemStack item = player.getInventory().getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String skinId = pdc.get(skinKey, PersistentDataType.STRING);
            pdc.remove(reloadAmmoKey);
            pdc.remove(reloadAmountKey);
            item.setItemMeta(meta);
            SkinData skin = SkinLoader.get().get(skinId);
            if (skin != null) item = applyModel(item, skin, SkinState.CARRY);
            player.getInventory().setItem(slot, item);
        }
        Collection<ItemStack> refunds = reloadRefunds.remove(id);
        for (ItemStack refund : refunds) giveOrDrop(player, refund);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private boolean blockIfBroken(Player player, ItemStack item) {
        if (GunBrokenMarker.isBroken(item)) {
            return true;
        }
        GunCraftProvenance provenance = GunCraftProvenance.readFrom(item);
        if (provenance == null) {
            return false;
        }
        GunCraftProvenance.ResolvedParts resolved = provenance.resolveStampedParts();
        if (resolved.missingIds().isEmpty()) {
            return false;
        }
        ItemMeta brokenMeta = item.getItemMeta();
        String gunId = brokenMeta.getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
        GunBrokenMarker.markBroken(item, resolved.missingIds());
        GunBrokenMarker.notifyBroken(player, item, gunId, resolved.missingIds());
        return true;
    }

    

    // This path mutates the existing ItemStack; replacing it would change aliases held by callers.
    @SuppressWarnings("deprecation")
    public ItemStack applyModel(ItemStack i, SkinData data, SkinState state) {
        ItemStack skin = data.parseModel(state);
        if (skin == null || skin.getType().isAir()) {
            return i;
        }
        if (ItemSkinPreserver.hasSkinData(skin)) {
            return ItemSkinPreserver.applyAppearanceFromSkin(skin, i);
        }
        ItemMeta m = i.getItemMeta();
        if (m != null && LegacyModelData.has(skin.getItemMeta())) {
            LegacyModelData.set(m, LegacyModelData.get(skin.getItemMeta()));
            i.setItemMeta(m);
        }
        i.setType(skin.getType());
        return i;
    }


    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private void chargeCrossbow(ItemStack crossbow, String ammoId, int amount) {
        Ammunition ammo = AmmunitionLoader.getByString(ammoId);
        CrossbowMeta cbMeta = (CrossbowMeta) crossbow.getItemMeta();
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        if(cbMeta.hasChargedProjectiles()) cbMeta.setChargedProjectiles(new ArrayList<>());
        if(ammo != null) {
            ItemMeta arrowMeta = arrow.getItemMeta();
            arrowMeta.setDisplayName(StringFormatter.getName(TLibs.getItemAPI().getCreator().getItemFromPath(ammo.getInput()))+" §7x"+amount);
            arrow.setItemMeta(arrowMeta);
        }
        cbMeta.addChargedProjectile(arrow);
        crossbow.setItemMeta(cbMeta);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (reloading.containsKey(id)) {
            // prevent dropping the gun in hand while reloading
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (dropped != null && dropped.hasItemMeta()) {
                ItemMeta meta = dropped.getItemMeta();
                if (meta.getPersistentDataContainer().has(gunKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID id = player.getUniqueId();

        if (reloading.containsKey(id)) {
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();

            // Check clicked or cursor item for a gun
            if ((current != null && current.hasItemMeta() &&
                current.getItemMeta().getPersistentDataContainer().has(gunKey, PersistentDataType.STRING))
            || (cursor != null && cursor.hasItemMeta() &&
                cursor.getItemMeta().getPersistentDataContainer().has(gunKey, PersistentDataType.STRING))) {

                event.setCancelled(true);
            }
        }
    }

    /** Progress bar helper */
    private String makeProgressBar(double progress, int bars, String filled, String empty) {
        // Clamp progress between 0.0 and 1.0
        progress = Math.max(0.0, Math.min(1.0, progress));

        int filledBars = (int) Math.round(progress * bars);

        // Ensure that 100% progress always fills all bars
        if (progress >= 1.0) {
            filledBars = bars;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filledBars ? filled + "|" : empty + "|");
        }
        return sb.toString();
    }


    /**
     * Try to take ammo from the player's inventory.
     *
     * @param p        the player
     * @param amount   how many units required
     * @param calibers list of valid calibers for this gun
     * @return the ammo ID + how many removed (e.g. "ironshot.1"), or "none" if not enough
     */
    private String takeAmmo(Player p, int amount, Collection<Ammunition> calibers) {
        if (amount <= 0) return "none";
        for (Ammunition ammo : calibers) {
            java.util.List<ItemStack> matchingStacks = findAmmo(p, ammo);
            int totalFound = countStacks(matchingStacks);

            if (totalFound > 0) {
                // Take min(capacity, found)
                int toTake = Math.min(amount, totalFound);
                reloadRefunds.put(p.getUniqueId(), removeItems(p, matchingStacks, toTake));
                return ammo.getKey() + "." + toTake;
            }
        }

        // ❌ No ammo found in any caliber
        return "none";
    }

    /** Stacks of this ammunition in the player's inventory, in slot order. */
    private java.util.List<ItemStack> findAmmo(Player p, Ammunition ammo) {
        java.util.List<ItemStack> matchingStacks = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null) continue;
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, ammo.getInput())) {
                matchingStacks.add(item);
            }
        }
        return matchingStacks;
    }

    private int countStacks(java.util.List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack item : stacks) total += item.getAmount();
        return total;
    }

    /** The player's pick for the next reload, or null when unset or no longer a caliber of this gun. */
    private Ammunition getSelectedAmmo(ItemMeta meta, List<Ammunition> calibers) {
        String selectedId = meta.getPersistentDataContainer().get(selectedAmmoKey, PersistentDataType.STRING);
        for (Ammunition ammo : calibers) {
            if (ammo.getKey().equals(selectedId)) return ammo;
        }
        return null;
    }

    /**
     * Crouch + right-click: pick the next caliber the player carries for the next reload.
     * Bullets already loaded stay loaded.
     */
    private void cycleAmmo(Player player, ItemStack item) {
        List<Ammunition> calibers = Caliber.get(item);
        List<Ammunition> carried = new ArrayList<>();
        for (Ammunition ammo : calibers) {
            if (countStacks(findAmmo(player, ammo)) > 0) carried.add(ammo);
        }
        if (carried.isEmpty()) {
            sendActionBar(player, "§cYou carry no shot this weapon can fire.");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        Ammunition current = getSelectedAmmo(meta, calibers);
        // Without a pick, reloads use the first carried caliber, so step past that one.
        int index = calibers.indexOf(current != null ? current : carried.get(0));
        Ammunition next;
        do {
            index = (index + 1) % calibers.size();
            next = calibers.get(index);
        } while (!carried.contains(next));

        meta.getPersistentDataContainer().set(selectedAmmoKey, PersistentDataType.STRING, next.getKey());
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        sendActionBar(player, "§7Next load: " + getAmmoName(next)
                + " §8(" + countStacks(findAmmo(player, next)) + " carried)");
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1.5f);
    }

    // Keep the existing legacy text representation of ammunition item names.
    @SuppressWarnings("deprecation")
    private String getAmmoName(Ammunition ammo) {
        return StringFormatter.getName(TLibs.getItemAPI().getCreator().getItemFromPath(ammo.getInput())).trim();
    }

    private void sendActionBar(Player player, String legacyText) {
        // Hold MMOCore's stat bar back for two seconds so the message can be read; a null text only reserves it.
        if (!MMOPlayerData.get(player).getActionBar().show(ActionBarPriority.NORMAL, 40L, (String) null)) return;
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(legacyText));
    }


    /**
     * Remove a certain number of items of a specific type from a player's inventory.
     */
    private Collection<ItemStack> removeItems(Player p, java.util.List<ItemStack> matchingStacks, int amount) {
        Collection<ItemStack> reserved = new ArrayList<>();
        var stacks = matchingStacks.iterator();
        // takeAmmo caps amount at the total in these exact stacks.
        while (amount > 0) {
            ItemStack item = stacks.next();
            int remove = Math.min(item.getAmount(), amount);
            ItemStack refund = item.clone();
            refund.setAmount(remove);
            reserved.add(refund);
            item.setAmount(item.getAmount() - remove);
            amount -= remove;
        }
        p.updateInventory();
        return reserved;
    }

}


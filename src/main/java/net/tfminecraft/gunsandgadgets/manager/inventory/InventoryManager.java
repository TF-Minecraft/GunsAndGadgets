package net.tfminecraft.gunsandgadgets.manager.inventory;

import net.tfminecraft.gunsandgadgets.util.LegacyModelData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.BooleanData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.SoundType;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.PartData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinResolver;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.guns.stats.StatApplier;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.PartDataLoader;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;
import net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker;
import net.tfminecraft.gunsandgadgets.utils.MajorityTierResolver;
import net.tfminecraft.gunsandgadgets.utils.TierLore;

public class InventoryManager implements Listener {

    // ---------- OPEN ASSEMBLY (uses per-player selections if valid, else first) ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openCraftingInventory(Player player) {
        Inventory inv = Bukkit.createInventory(new AssemblyHolder(), 27, "§6Gun Assembly");

        // background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) { meta.setDisplayName("§o"); filler.setItemMeta(meta); }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // type button
        GunType chosen = TypeSelectionManager.getSelectedType(player);
        inv.setItem(0, createWeaponTypeButton(chosen));

        Collection<GunPart> parts = new ArrayList<>();

        // parts per slot
        for (PartData partData : PartDataLoader.get().values()) {
            int slot = partData.getSlot();
            if (slot <= 0 || slot >= inv.getSize()) continue;

            GunPart part = getSelectedOrFirstPart(partData.getId(), chosen, player);
            if (part != null) {
                parts.add(part);
                inv.setItem(slot, createPartItem(part));
            } else {
                // ❌ No valid part (permission or none exist) → barrier
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta bm = barrier.getItemMeta();
                if (bm != null) {
                    bm.setDisplayName("§cNo Part");
                    barrier.setItemMeta(bm);
                }
                inv.setItem(slot, barrier);
            }

        }

        inv.setItem(Cache.outputSlot, createOutputItem(chosen, parts, true));

        player.openInventory(inv);
    }

    // ---------- OPEN TYPE SELECTION (unchanged) ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openTypeSelection(Player player) {
        Inventory typeInv = Bukkit.createInventory(new TypeSelectionHolder(), 9, "§6Select Gun Type");
        int slot = 0;
        for (GunType type : GunType.values()) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + type.getDisplayName());
                meta.setLore(List.of("§7Click to choose this type"));
                item.setItemMeta(meta);
            }
            typeInv.setItem(slot++, item);
        }
        player.openInventory(typeInv);
    }

    // ---------- NEW: OPEN PART SELECTION FOR A CATEGORY ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openPartSelection(Player player, String partCategoryId) {
        GunType chosen = TypeSelectionManager.getSelectedType(player);

        // collect options matching this category + gun type
        List<GunPart> options = new ArrayList<>();
        for (GunPart part : PartLoader.getOrdered()) {
            if (!part.getPartType().getId().equalsIgnoreCase(partCategoryId)) continue;
            if (!part.getGunTypes().contains(chosen)) continue;
            if (part.isDisabled()) continue;
            if (!hasPermissionForPart(player, part)) continue; // 🚫 permission check
            options.add(part);
        }

        int size = Math.max(9, Math.min(54, ((options.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new PartSelectionHolder(partCategoryId), size, "§6Select " + partCategoryId);

        if (options.isEmpty()) {
            // ❌ No available parts → show barrier
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            if (bm != null) {
                bm.setDisplayName("§cNo Part");
                barrier.setItemMeta(bm);
            }
            inv.setItem(size / 2, barrier);
        } else {
            for (GunPart part : options) {
                ItemStack option = createPartItem(part);
                inv.addItem(option);
            }
        }

        // optional: back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) { bm.setDisplayName("§eBack"); back.setItemMeta(bm); }
        inv.setItem(size - 1, back);

        player.openInventory(inv);
    }


    // ---------- HELPERS ----------
    private GunPart getSelectedOrFirstPart(String partCategoryId, GunType chosen, Player player) {
        String selectedKey = SelectedPartsManager.get(player, partCategoryId);
        if (selectedKey != null) {
            GunPart gp = getPartByKey(selectedKey);
            if (gp != null && gp.getGunTypes().contains(chosen) && hasPermissionForPart(player, gp)
                    && gp.isEnabledForCrafting()) {
                return gp;
            }
        }
        return getFirstPartOfType(partCategoryId, chosen, player);
    }



    private GunPart getPartByKey(String key) {
        // if you have PartLoader.get() -> Map<String,GunPart>
        GunPart byMap = PartLoader.get().get(key);
        if (byMap != null) return byMap;

        for (GunPart gp : PartLoader.getOrdered()) {
            if (gp.getId().equalsIgnoreCase(key)) return gp;
        }
        return null;
    }

    private GunPart getFirstPartOfType(String id, GunType chosen, Player player) {
        for (GunPart part : PartLoader.getOrdered()) {
            if (!part.getGunTypes().contains(chosen)) continue;
            if (!part.getPartType().getId().equalsIgnoreCase(id)) continue;
            if (part.isDisabled()) continue;
            if (!hasPermissionForPart(player, part)) continue;
            return part;
        }
        return null; // ❌ none available
    }


    private String getPartIdForSlot(int slot) {
        for (PartData data : PartDataLoader.get().values()) {
            if (data.getSlot() == slot) return data.getId();
        }
        return null;
    }

    // ---------- ITEM BUILDERS (unchanged except already in your class) ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack createWeaponTypeButton(GunType type) {
        ItemStack item = new ItemStack(Material.NETHER_STAR); // placeholder icon
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Select Type: §e" + type.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to change weapon type");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String buildName(Collection<GunPart> parts) {
        // index → best fragment
        Map<Integer, GunPart.NameImpact> chosen = new HashMap<>();

        for (GunPart part : parts) {
            for (GunPart.NameImpact ni : part.getNameImpacts()) {
                int idx = ni.getIndex();

                if (idx == -1) {
                    // override everything
                    return "§7" + ni.getFragment();
                }

                GunPart.NameImpact existing = chosen.get(idx);
                if (existing == null || ni.getWeight() > existing.getWeight()) {
                    chosen.put(idx, ni);
                }
            }
        }

        // order by index
        List<Integer> order = new ArrayList<>(chosen.keySet());
        Collections.sort(order);

        StringBuilder sb = new StringBuilder("§7");
        for (int idx : order) {
            sb.append(chosen.get(idx).getFragment()).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * Computes the intersection of class requirements across all parts that have restrictions.
     * Returns an empty set if no parts have restrictions (no class lock).
     * Returns null if there is a conflict (intersection became empty but at least one part had requirements).
     */
    private Set<String> resolveClasses(Collection<GunPart> parts) {
        Set<String> intersection = null;
        for (GunPart part : parts) {
            List<String> reqs = part.getClassRequirements();
            if (reqs == null || reqs.isEmpty()) continue;
            if (intersection == null) {
                intersection = new HashSet<>(reqs);
            } else {
                intersection.retainAll(new HashSet<>(reqs));
                if (intersection.isEmpty()) return null; // conflict
            }
        }
        return intersection != null ? intersection : Collections.emptySet();
    }

    /**
     * Returns true if the given parts have incompatible class requirements,
     * and sends a descriptive message to the player.
     */
    public boolean hasClassConflict(Player player, Collection<GunPart> parts) {
        Set<String> resolved = resolveClasses(parts);
        if (resolved != null) return false; // no conflict

        player.sendMessage("§cClass conflict! These parts have incompatible class requirements:");
        for (GunPart part : parts) {
            List<String> reqs = part.getClassRequirements();
            if (reqs != null && !reqs.isEmpty()) {
                player.sendMessage("§7  " + part.getName() + "§c: §7" + String.join("§c, §7", reqs));
            }
        }
        return true;
    }

    private ItemStack makeTwoHanded(ItemStack item) {
        if (NBTItem.get(item).hasType()) {
            LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));

            // Convert back to list for MMOItems data
            BooleanData data = new BooleanData(true);
            StatHistory hist = mmo.computeStatHistory(ItemStats.TWO_HANDED);
            if (hist != null) {
                hist.setOriginalData(data);
                mmo.setStatHistory(ItemStats.TWO_HANDED, hist);
            }

            return mmo.newBuilder().build();
        }
        return item;
    }

    private ItemStack applyClass(ItemStack item, Collection<GunPart> parts) {
        Set<String> classes = resolveClasses(parts);
        // null = conflict (no valid class intersection) → skip applying class
        if (classes == null || classes.isEmpty()) return item;

        if (NBTItem.get(item).hasType() && !classes.isEmpty()) {
            LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));

            // Convert back to list for MMOItems data
            StringListData data = new StringListData(new ArrayList<>(classes));

            StatHistory hist = mmo.computeStatHistory(ItemStats.REQUIRED_CLASS);
            if (hist != null) {
                StringListData og = (StringListData) hist.getOriginalData();
                og.getList().clear();
                og.mergeWith(data);
                mmo.setStatHistory(ItemStats.REQUIRED_CLASS, hist);
            }

            return mmo.newBuilder().build();
        }
        return item;
    }


    public ItemStack createOutputItem(GunType type, Collection<GunPart> parts, boolean gui) {
        return createOutputItem(type, parts, gui, null);
    }

    public ItemStack rebuildFromParts(ItemStack existing, GunType type, Collection<GunPart> parts) {
        return createOutputItem(type, parts, false, existing);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons. This path mutates the existing ItemStack; replacing it would change aliases held by callers.
    @SuppressWarnings("deprecation")
    public ItemStack createOutputItem(GunType type, Collection<GunPart> parts, boolean gui, ItemStack preserveRuntime) {
        for (GunPart part : parts) {
            if (part.isDisabled()) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta bm = barrier.getItemMeta();
                if (bm != null) {
                    bm.setDisplayName("§cPart no longer available");
                    barrier.setItemMeta(bm);
                }
                return barrier;
            }
        }
        List<String> required = Cache.requiredParts.get(type);
        if (required != null) {
            for (String req : required) {
                boolean found = false;
                for (GunPart part : parts) {
                    if (part.getPartType().getId().equalsIgnoreCase(req)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // ❌ Missing at least one part → return barrier
                    ItemStack barrier = new ItemStack(Material.BARRIER);
                    ItemMeta bm = barrier.getItemMeta();
                    if (bm != null) {
                        bm.setDisplayName("§cNot enough parts");
                        barrier.setItemMeta(bm);
                    }
                    return barrier;
                }
            }
        }
        // Resolve skin
        SkinResolver resolver = new SkinResolver(SkinLoader.get());
        SkinData skin = resolver.resolve(type, parts);

        // Base item
        ItemStack base = TLibs.getItemAPI().getCreator().getItemFromPath(Cache.outputItems.getOrDefault(type, "v.stick"));
        
        base = applyClass(base, parts);

        ItemStack skinItem = skin.parseModel(SkinState.CARRY);
        if (ItemSkinPreserver.hasSkinData(skinItem)) {
            base = ItemSkinPreserver.applyAppearanceFromSkin(skinItem, base);
        } else {
            base.setType(skinItem.getType());
        }

        // Meta edits
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            if (!ItemSkinPreserver.hasSkinData(skinItem)
                    && skinItem.getItemMeta() != null
                    && LegacyModelData.has(skinItem.getItemMeta())) {
                LegacyModelData.set(meta, LegacyModelData.get(skinItem.getItemMeta()));
            }
            meta.setDisplayName(buildName(parts));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // ✅ Store skin ID
            NamespacedKey skinKey = new NamespacedKey(GunsAndGadgets.getInstance(), "skin_id");
            pdc.set(skinKey, PersistentDataType.STRING, skin.getId());

            NamespacedKey gunKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_id");
            NamespacedKey saltKey = new NamespacedKey(GunsAndGadgets.getInstance(), "accuracy_salt");
            if (preserveRuntime != null && preserveRuntime.hasItemMeta()) {
                PersistentDataContainer preservePdc = preserveRuntime.getItemMeta().getPersistentDataContainer();
                String existingGunId = preservePdc.get(gunKey, PersistentDataType.STRING);
                if (existingGunId != null) {
                    pdc.set(gunKey, PersistentDataType.STRING, existingGunId);
                } else {
                    pdc.set(gunKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                }
                Integer existingSalt = preservePdc.get(saltKey, PersistentDataType.INTEGER);
                if (existingSalt != null) {
                    pdc.set(saltKey, PersistentDataType.INTEGER, existingSalt);
                } else {
                    pdc.set(saltKey, PersistentDataType.INTEGER, ThreadLocalRandom.current().nextInt(0, 10000));
                }
            } else {
                pdc.set(gunKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                pdc.set(saltKey, PersistentDataType.INTEGER, ThreadLocalRandom.current().nextInt(0, 10000));
            }

            NamespacedKey typeKey = new NamespacedKey(GunsAndGadgets.getInstance(), "gun_type");
            pdc.set(typeKey, PersistentDataType.STRING, type.toString());

            // ✅ Store accuracy salt — set above with gun_id when preserving runtime

            // ---------- Caliber section ----------
            List<String> calibers = resolveCalibers(parts);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            if (!calibers.isEmpty()) {
                lore.add("");
                lore.add("§6§lCaliber:");
                for (String ammoKey : calibers) {
                    Ammunition ammo = AmmunitionLoader.getByString(ammoKey);
                    if (ammo != null) {
                        lore.add("§7• " + StringFormatter.getName(
                            TLibs.getItemAPI().getCreator().getItemFromPath(ammo.getInput())
                        ));
                    } else {
                        lore.add("§7• " + ammoKey);
                    }
                }

                // ✅ Save calibers in PDC
                NamespacedKey calibersKey = new NamespacedKey(GunsAndGadgets.getInstance(), "calibers");
                pdc.set(calibersKey, PersistentDataType.STRING, String.join(";", calibers));
            }

            // ---------- Sounds section ----------
            storeSounds(type, parts, SoundType.SHOOT, pdc,
                    new NamespacedKey(GunsAndGadgets.getInstance(), "shoot_sounds"));
            storeSounds(type, parts, SoundType.RELOAD, pdc,
                    new NamespacedKey(GunsAndGadgets.getInstance(), "reload_sounds"));

            meta.setLore(lore);
            base.setItemMeta(meta);

            // Apply stats afterwards (so lore & stat PDCs get written)
            base = StatApplier.apply(base, parts, gui);
            int majority = MajorityTierResolver.resolve(parts);
            if (majority > 0) {
                TierLore.applyTo(base, majority);
            }
        }

        boolean twoHanded = parts.stream().anyMatch(GunPart::isTwoHanded);
        if (twoHanded) base = makeTwoHanded(base);

        if (preserveRuntime != null) {
            copyRuntimeAmmoPdc(preserveRuntime, base);
        }

        if (!gui) {
            GunCraftProvenance.from(parts).applyTo(base);
            GunBrokenMarker.clearBroken(base);
        }

        return base;
    }

    private void copyRuntimeAmmoPdc(ItemStack from, ItemStack to) {
        if (from == null || to == null || !from.hasItemMeta() || !to.hasItemMeta()) {
            return;
        }
        ItemMeta fromMeta = from.getItemMeta();
        ItemMeta toMeta = to.getItemMeta();
        PersistentDataContainer fromPdc = fromMeta.getPersistentDataContainer();
        PersistentDataContainer toPdc = toMeta.getPersistentDataContainer();

        NamespacedKey bulletsKey = new NamespacedKey(GunsAndGadgets.getInstance(), "bullets_loaded");
        NamespacedKey loadedAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "ammo_loaded");
        NamespacedKey reloadAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "reload_ammo");
        NamespacedKey reloadAmountKey = new NamespacedKey(GunsAndGadgets.getInstance(), "reload_amount");
        NamespacedKey lastFireKey = new NamespacedKey(GunsAndGadgets.getInstance(), "last_fire");

        if (fromPdc.has(bulletsKey, PersistentDataType.INTEGER)) {
            toPdc.set(bulletsKey, PersistentDataType.INTEGER,
                    fromPdc.getOrDefault(bulletsKey, PersistentDataType.INTEGER, 0));
        } else {
            toPdc.remove(bulletsKey);
        }

        String loadedAmmo = fromPdc.get(loadedAmmoKey, PersistentDataType.STRING);
        if (loadedAmmo != null) {
            toPdc.set(loadedAmmoKey, PersistentDataType.STRING, loadedAmmo);
        } else {
            toPdc.remove(loadedAmmoKey);
        }

        String reloadAmmo = fromPdc.get(reloadAmmoKey, PersistentDataType.STRING);
        if (reloadAmmo != null) {
            toPdc.set(reloadAmmoKey, PersistentDataType.STRING, reloadAmmo);
        } else {
            toPdc.remove(reloadAmmoKey);
        }

        if (fromPdc.has(reloadAmountKey, PersistentDataType.INTEGER)) {
            toPdc.set(reloadAmountKey, PersistentDataType.INTEGER,
                    fromPdc.getOrDefault(reloadAmountKey, PersistentDataType.INTEGER, 0));
        } else {
            toPdc.remove(reloadAmountKey);
        }

        if (fromPdc.has(lastFireKey, PersistentDataType.LONG)) {
            toPdc.set(lastFireKey, PersistentDataType.LONG,
                    fromPdc.getOrDefault(lastFireKey, PersistentDataType.LONG, 0L));
        } else {
            toPdc.remove(lastFireKey);
        }

        to.setItemMeta(toMeta);
    }

    /**
     * Collect sounds for a given type and write them into the PDC.
     */
    private void storeSounds(GunType gunType, Collection<GunPart> parts,
                         SoundType soundType, PersistentDataContainer pdc, NamespacedKey key) {
        List<String> result = new ArrayList<>();
        
        // 🔄 1) Collect ALL overrides first (across all parts)
        for (GunPart part : parts) {
            List<GunPart.PartSound> overrides = part.getSoundOverrides().get(soundType);
            if (overrides != null) {
                for (GunPart.PartSound ps : overrides) {
                    if (ps.matches(gunType)) {
                        String group = String.join(",", ps.getSoundKeys());
                        if (!group.isBlank()) result.add(group);
                    }
                }
            }
        }

        // ✅ If we found overrides, they take full priority → skip base sounds
        if (!result.isEmpty()) {
            String finalStr = String.join(";", result);
            pdc.set(key, PersistentDataType.STRING, finalStr);
            return;
        }

        // 🎵 2) Otherwise, gather normal sounds
        for (GunPart part : parts) {
            List<GunPart.PartSound> base = part.getSounds().get(soundType);
            if (base != null) {
                for (GunPart.PartSound ps : base) {
                    if (ps.matches(gunType)) {
                        String group = String.join(",", ps.getSoundKeys());
                        if (!group.isBlank()) result.add(group);
                    }
                }
            }
        }

        if (!result.isEmpty()) {
            String finalStr = String.join(";", result);
            pdc.set(key, PersistentDataType.STRING, finalStr);
        }
    }

    /**
     * Finds the calibers for the selected parts.
     * If an override is present, uses the last override only.
     */
    private List<String> resolveCalibers(Collection<GunPart> parts) {
        List<String> overrides = new ArrayList<>();
        List<String> normal = new ArrayList<>();

        for (GunPart part : parts) {
            if (part.getCaliberOverrides() != null && !part.getCaliberOverrides().isEmpty()) {
                overrides.addAll(part.getCaliberOverrides());
            } else if (part.getCalibers() != null && !part.getCalibers().isEmpty()) {
                normal.addAll(part.getCalibers());
            }
        }

        if (!overrides.isEmpty()) {
            // ✅ Only the last override matters
            return List.of(overrides.get(overrides.size() - 1));
        }
        return normal;
    }



    public Collection<GunPart> collectPartsForPlayer(Player player, GunType chosen) {
        Collection<GunPart> parts = new ArrayList<>();
        for (PartData partData : PartDataLoader.get().values()) {
            GunPart part = getSelectedOrFirstPart(partData.getId(), chosen, player);
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }



    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack createPartItem(GunPart part) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemFromPath(part.getItemKey());

        ItemMeta m = i.getItemMeta();

        // Name
        m.setDisplayName(part.getName());

        NamespacedKey key = new NamespacedKey(GunsAndGadgets.getInstance(), "part_id");
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, part.getId());

        // Lore
        List<String> lore = new ArrayList<>();

        if (part.hasTier()) {
            lore.add(TierLore.formatComponentLine(part.getTier()));
            if (part.getLore() != null && !part.getLore().isEmpty()) {
                lore.add("");
            }
        }

        if (part.getLore() != null && !part.getLore().isEmpty()) {
            for (String line : part.getLore()) {
                lore.add(StringFormatter.formatHex(line));
            }
        }

        // Add stats
        if (part.getStats() != null && !part.getStats().isEmpty()) {
            lore.add(""); // spacer line
            lore.add(StringFormatter.formatHex("#b38e88§lStats:"));

            for (Map.Entry<Stats, Integer> entry : part.getStats().entrySet()) {
                Stats stat = entry.getKey();
                int value = entry.getValue();

                // Fixed color for stat names
                String statColor = "#c2b48a"; // cyan-ish for readability

                // Dynamic color for values
                String valueColor;
                if (value < 0) {
                    valueColor = "#FF5555"; // red
                } else if (value == 0) {
                    valueColor = "#AAAAAA"; // gray
                } else if (value < 4) {
                    valueColor = "#FFD700"; // gold
                } else if (value < 7) {
                    valueColor = "#FFFF55"; // yellow
                } else {
                    valueColor = "#55FF55"; // green
                }

                // Format line
                String line = "§7■ "+statColor + stat.getDisplayName()
                            + "§e: " + valueColor + value;
                lore.add(StringFormatter.formatHex(line));
            }
        }

        if(part.hasCost() && Cache.requireInput) {
            lore.add(""); // spacer line
            lore.add(StringFormatter.formatHex("#76de91§lInput:"));
            lore.addAll(CostFormatter.getCostsFormatted(part.getCost()));
        }

        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    // ---------- CLICK HANDLER ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ASSEMBLY GUI
        if (event.getInventory().getHolder() instanceof AssemblyHolder) {
            event.setCancelled(true); // fully managed GUI

            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            if(current == null || !current.hasItemMeta()) return;
            if(current.getType().equals(Material.BARRIER)) return;
            if(event.getSlot() == Cache.outputSlot) return;
            // slot 0 -> choose gun type
            if (event.getSlot() == 0) {
                openTypeSelection(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }

            // clicking a part slot -> open part selection for that category
            String partCategoryId = getPartIdForSlot(event.getSlot());
            if (partCategoryId != null) {
                openPartSelection(player, partCategoryId);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            }
            return;
        }

        // TYPE SELECTION GUI
        if (event.getInventory().getHolder() instanceof TypeSelectionHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String name = clicked.getItemMeta().getDisplayName();
            if (name != null && name.equals("§eBack")) {
                openCraftingInventory(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }

            String clean = name == null ? "" : name.replace("§e", "").trim();
            for (GunType type : GunType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(clean)) {
                    TypeSelectionManager.setSelectedType(player, type);
                    // reset selected parts to avoid invalid leftovers
                    SelectedPartsManager.clear(player);
                    openCraftingInventory(player);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                    return;
                }
            }
            return;
        }

        // PART SELECTION GUI
        if (event.getInventory().getHolder() instanceof PartSelectionHolder holder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            // back button
            String dn = clicked.getItemMeta().getDisplayName();
            if ("§eBack".equals(dn)) {
                openCraftingInventory(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            NamespacedKey key = new NamespacedKey(GunsAndGadgets.getInstance(), "part_id");
            String partKey = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (partKey == null) return;

            // save selection and reopen preview
            SelectedPartsManager.set(player, holder.getPartId(), partKey);
            openCraftingInventory(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
        }
    }

    private boolean hasPermissionForPart(Player player, GunPart part) {
        if(!Cache.requireInput) return true; //tutorial setup
        if (part.getPermissions() == null || part.getPermissions().isEmpty()) return true;
        for (String perm : part.getPermissions()) {
            if (player.hasPermission(perm)) return true;
        }
        return false;
    }
}

package net.tfminecraft.gunsandgadgets.manager.inventory;

import net.tfminecraft.gunsandgadgets.util.LegacyModelData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.bukkit.inventory.InventoryHolder;
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
import net.tfminecraft.gunsandgadgets.guns.forge.ForgeLore;
import net.tfminecraft.gunsandgadgets.guns.forge.Metal;
import net.tfminecraft.gunsandgadgets.guns.forge.PartForge;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.parts.PartData;
import net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinResolver;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.guns.stats.StatApplier;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader;
import net.tfminecraft.gunsandgadgets.loader.MetalLoader;
import net.tfminecraft.gunsandgadgets.shooter.ShotFx;
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
        meta.setDisplayName("§o"); filler.setItemMeta(meta);
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
                bm.setDisplayName("§cNo part available");
                barrier.setItemMeta(bm);
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
            ItemStack item = new ItemStack(ForgedLook.typeIconsOn() ? Material.STONE_HOE : Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            ForgedLook.applyTypeIcon(meta, type);
            meta.setDisplayName("§e" + type.getDisplayName());
            meta.setLore(List.of("§7Click to choose this type"));
            item.setItemMeta(meta);
            typeInv.setItem(slot++, item);
        }
        player.openInventory(typeInv);
    }

    // ---------- NEW: OPEN PART SELECTION FOR A CATEGORY ----------
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openPartSelection(Player player, String partCategoryId) {
        GunType chosen = TypeSelectionManager.getSelectedType(player);

        // collect options matching this category + gun type; a forged part is one option whose
        // materials are picked next
        List<GunPart> options = new ArrayList<>();
        Set<String> forgedSeen = new HashSet<>();
        for (GunPart part : selectable(player, partCategoryId, chosen, null)) {
            if (part.isForged() && !forgedSeen.add(part.getBaseId())) continue;
            options.add(part);
        }

        int size = Math.max(9, Math.min(54, ((options.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new PartSelectionHolder(partCategoryId), size, "§6Select " + categoryName(partCategoryId, chosen));

        if (options.isEmpty()) {
            // ❌ No available parts → show barrier
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            bm.setDisplayName("§cNo part available");
            barrier.setItemMeta(bm);
            inv.setItem(size / 2, barrier);
        } else {
            for (GunPart part : options) {
                ItemStack option = part.isForged() ? createForgedChoiceItem(part) : createPartItem(part);
                inv.addItem(option);
            }
        }

        // optional: back button
        inv.setItem(size - 1, backButton());

        player.openInventory(inv);
    }

    /** The materials a forged part can be made from, each with its cost. */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openMetalSelection(Player player, String partCategoryId, String baseId) {
        GunType chosen = TypeSelectionManager.getSelectedType(player);
        List<GunPart> variants = selectable(player, partCategoryId, chosen, baseId);
        // A part that takes a fitting: one button per material, and the fitting is picked next.
        Map<String, List<GunPart>> byMetal = new LinkedHashMap<>();
        for (GunPart variant : variants) {
            if (variant.getFittingId() != null) byMetal.computeIfAbsent(variant.getMetalId(), k -> new ArrayList<>()).add(variant);
        }
        int count = byMetal.isEmpty() ? variants.size() : byMetal.size();
        int size = Math.max(9, Math.min(54, ((count + 1 + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new MetalSelectionHolder(partCategoryId, baseId), size, "§6Select Material");
        if (byMetal.isEmpty()) {
            for (GunPart variant : variants) {
                inv.addItem(createPartItem(variant));
            }
        } else {
            for (List<GunPart> fitted : byMetal.values()) {
                inv.addItem(createMaterialChoiceItem(fitted));
            }
        }
        inv.setItem(size - 1, backButton());
        player.openInventory(inv);
    }

    /**
     * The animal fittings (leather, wool, feather of each quality) for one material of a part:
     * a row per kind, a new row whenever the quality starts again.
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openFittingSelection(Player player, String partCategoryId, String baseId, String metalId) {
        GunType chosen = TypeSelectionManager.getSelectedType(player);
        List<GunPart> fitted = new ArrayList<>();
        for (GunPart variant : selectable(player, partCategoryId, chosen, baseId)) {
            if (variant.getFittingId() != null && metalId.equals(variant.getMetalId())) fitted.add(variant);
        }
        List<Integer> slots = fittingSlots(fitted);
        // rows up to the last fitting, plus one more only when the back button has no room left in it
        int last = slots.isEmpty() ? -1 : slots.get(slots.size() - 1);
        int size = Math.max(9, Math.min(54, ((last + 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(new FittingSelectionHolder(partCategoryId, baseId, metalId), size, "§6Select Fitting");
        for (int i = 0; i < fitted.size(); i++) {
            if (slots.get(i) < size - 1) inv.setItem(slots.get(i), createPartItem(fitted.get(i)));
        }
        inv.setItem(size - 1, backButton());
        player.openInventory(inv);
    }

    /** "leather", "leather or wool", "leather, wool or feather": the fitting types these variants take. */
    static String fittingTypes(List<GunPart> fitted) {
        List<String> types = new ArrayList<>();
        for (GunPart part : fitted) {
            Metal fitting = MetalLoader.getByString(part.getFittingId());
            String type = fitting == null || fitting.getType().isEmpty() ? part.getFittingId() : fitting.getType();
            if (!types.contains(type)) types.add(type);
        }
        if (types.size() == 1) return types.getFirst();
        return String.join(", ", types.subList(0, types.size() - 1)) + " or " + types.getLast();
    }

    /** Slots for fittings: a new row whenever the quality starts again (leather, then wool, then feather). */
    static List<Integer> fittingSlots(List<GunPart> fitted) {
        List<Integer> slots = new ArrayList<>();
        int row = 0;
        int column = 0;
        int lastTier = Integer.MAX_VALUE;
        for (GunPart variant : fitted) {
            Metal fitting = MetalLoader.getByString(variant.getFittingId());
            int tier = fitting == null ? 1 : fitting.getTier();
            if (!slots.isEmpty() && (tier <= lastTier || column >= 9)) {
                row++;
                column = 0;
            }
            slots.add(row * 9 + column++);
            lastTier = tier;
        }
        // Rows that would not fit beside the back button (e.g. fittings listed by quality, not by
        // kind): lay them out one after another so every fitting stays reachable.
        if (!slots.isEmpty() && slots.get(slots.size() - 1) > 52) {
            slots.clear();
            for (int i = 0; i < fitted.size(); i++) slots.add(i);
        }
        return slots;
    }

    /** One material of a part that takes a fitting: the part in that material, before the fitting is chosen. */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack createMaterialChoiceItem(List<GunPart> fitted) {
        GunPart variant = fitted.getFirst();
        ItemStack i = createPartItem(variant);
        ItemMeta m = i.getItemMeta();
        GunPart plain = PartLoader.getByString(PartForge.variantId(variant.getBaseId(), variant.getMetalId()));
        m.setDisplayName(plain != null ? plain.getName() : variant.getBaseName());
        m.getPersistentDataContainer().remove(new NamespacedKey(GunsAndGadgets.getInstance(), "part_id"));
        m.getPersistentDataContainer().set(forgeMetalKey(), PersistentDataType.STRING, variant.getMetalId());
        List<String> lore = new ArrayList<>();
        Metal metal = MetalLoader.getByString(variant.getMetalId());
        if (metal != null && !metal.getDescription().isEmpty()) {
            lore.addAll(metal.getDescription());
            lore.add("");
        }
        // The plain variant has the part's written values: what good quality gives in this material.
        List<String> arrows = ForgeLore.lines(plain != null ? plain : variant);
        if (!arrows.isEmpty()) {
            lore.addAll(arrows);
            lore.add("");
        }
        String types = fittingTypes(fitted);
        lore.add("§7Fitted with " + types + ":");
        lore.add("§7better quality improves what this part is for.");
        if (Cache.requireInput) {
            lore.add("");
            lore.add(StringFormatter.formatHex("#76de91§lInput:"));
            // the fitting's amount: what the fitted variant costs beyond the plain one
            int amount = 0;
            if (plain != null) {
                lore.addAll(CostFormatter.getCostsFormatted(plain.getCost()));
                for (Map.Entry<String, Integer> cost : variant.getCost().entrySet()) {
                    if (!plain.getCost().containsKey(cost.getKey())) amount = Math.max(amount, cost.getValue());
                }
            }
            lore.add("§7- §f" + Character.toUpperCase(types.charAt(0)) + types.substring(1) + (amount > 0 ? " §e" + amount : ""));
        }
        lore.add("");
        lore.add("§eClick to choose the fitting");
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    /** Enabled parts of a category the player may craft for this gun type (one forged part's variants when baseId is set). */
    private List<GunPart> selectable(Player player, String partCategoryId, GunType chosen, String baseId) {
        List<GunPart> options = new ArrayList<>();
        for (GunPart part : PartLoader.getOrdered()) {
            if (!part.getPartType().getId().equalsIgnoreCase(partCategoryId)) continue;
            if (!part.getGunTypes().contains(chosen)) continue;
            if (!part.isEnabledForCrafting()) continue;
            if (baseId != null && !part.getBaseId().equals(baseId)) continue;
            if (!hasPermissionForPart(player, part)) continue; // 🚫 permission check
            options.add(part);
        }
        return options;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack backButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§eBack"); back.setItemMeta(bm);
        return back;
    }

    /** A forged part in the part list: its tendencies, and a click to choose the material. */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private ItemStack createForgedChoiceItem(GunPart part) {
        ItemStack i = TLibs.getItemAPI().getCreator().getItemFromPath(part.getItemKey());
        ItemMeta m = i.getItemMeta();
        ForgedLook.applyIcon(m, part, false);
        m.setDisplayName(part.getBaseName());
        m.getPersistentDataContainer().set(forgeBaseKey(), PersistentDataType.STRING, part.getBaseId());
        List<String> lore = new ArrayList<>();
        for (String line : part.getBaseLore()) {
            lore.add(StringFormatter.formatHex(line));
        }
        if (!lore.isEmpty()) lore.add("");
        lore.addAll(ForgeLore.lines(part, false)); // the reference (steel) arrows, whichever material comes first
        lore.add("");
        lore.add("§eClick to choose the material");
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    /** A part category as a screen title shows it: "Barrel", and "Grip" for a pistol's stock. */
    static String categoryName(String partCategoryId, GunType type) {
        if (partCategoryId.equalsIgnoreCase("stock") && type == GunType.PISTOL) {
            return "Grip";
        }
        String name = partCategoryId.replace('_', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static NamespacedKey forgeBaseKey() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "forge_base");
    }

    private static NamespacedKey forgeMetalKey() {
        return new NamespacedKey(GunsAndGadgets.getInstance(), "forge_metal");
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
            if (!part.isEnabledForCrafting()) continue;
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
        ItemStack item = new ItemStack(ForgedLook.typeIconsOn() ? Material.STONE_HOE : Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        ForgedLook.applyTypeIcon(meta, type);
        meta.setDisplayName("§6Gun Type: §e" + type.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to change gun type");
        meta.setLore(lore);
        item.setItemMeta(meta);
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
                    return StringFormatter.formatHex(ForgedLook.tierColor(MajorityTierResolver.resolve(parts))) + ni.getFragment();
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

        // the name is written in the colour of the gun's final tier
        StringBuilder sb = new StringBuilder(StringFormatter.formatHex(ForgedLook.tierColor(MajorityTierResolver.resolve(parts))));
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
            if (reqs.isEmpty()) continue;
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

        player.sendMessage("§cThese parts cannot go together. They are made for different callings:");
        for (GunPart part : parts) {
            List<String> reqs = part.getClassRequirements();
            if (!reqs.isEmpty()) {
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

        if (NBTItem.get(item).hasType()) {
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
                bm.setDisplayName("§cPart no longer available");
                barrier.setItemMeta(bm);
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
                    bm.setDisplayName("§cNot enough parts");
                    barrier.setItemMeta(bm);
                    return barrier;
                }
            }
        }
        // A gun whose parts declare shot (or limit it) but leave none to load could never fire:
        // e.g. a plain-shot-only lock with a powered-shot-only chamber.
        List<String> calibers = resolveCalibers(parts);
        if (calibers.isEmpty() && declaresShot(parts)) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            bm.setDisplayName("§cThese parts don't make a gun");
            bm.setLore(List.of("§7No shot fits all of them."));
            barrier.setItemMeta(bm);
            return barrier;
        }
        // Resolve skin
        SkinResolver resolver = new SkinResolver(SkinLoader.get());
        SkinData skin = resolver.resolve(type, parts);
        if (skin == null) {
            ItemStack unavailable = new ItemStack(Material.BARRIER);
            ItemMeta unavailableMeta = unavailable.getItemMeta();
            unavailableMeta.setDisplayName("§cNo known design fits these parts");
            unavailable.setItemMeta(unavailableMeta);
            return unavailable;
        }

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
                    && LegacyModelData.has(skinItem.getItemMeta())) {
                LegacyModelData.set(meta, LegacyModelData.get(skinItem.getItemMeta()));
            }
            ForgedLook.stamp(meta, skin, SkinState.CARRY, parts);
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
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            if (!calibers.isEmpty()) {
                lore.add("");
                lore.add("§6§lShot:");
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
                if (calibers.size() > 1) {
                    lore.add("§8Swap hands to change shot");
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
            ShotFx.stamp(parts, pdc, new NamespacedKey(GunsAndGadgets.getInstance(), "shoot_fx"));

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
        if (to == null || !from.hasItemMeta() || !to.hasItemMeta()) {
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
        NamespacedKey selectedAmmoKey = new NamespacedKey(GunsAndGadgets.getInstance(), "ammo_selected");

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

        String selectedAmmo = fromPdc.get(selectedAmmoKey, PersistentDataType.STRING);
        if (selectedAmmo != null) {
            toPdc.set(selectedAmmoKey, PersistentDataType.STRING, selectedAmmo);
        } else {
            toPdc.remove(selectedAmmoKey);
        }

        to.setItemMeta(toMeta);
    }

    /**
     * Collect sounds for a given type and write them into the PDC.
     */
    /** One sound group: "key,key" plus "|pitch|volume" when they aren't 1. */
    private static void addGroup(List<String> result, GunPart.PartSound ps, double pitch, double volume) {
        String group = String.join(",", ps.getSoundKeys());
        if (!group.isBlank()) {
            result.add(group + GunPart.PartSound.levelSuffix(pitch * ps.getPitch(), volume * ps.getVolume()));
        }
    }

    private void storeSounds(GunType gunType, Collection<GunPart> parts,
                         SoundType soundType, PersistentDataContainer pdc, NamespacedKey key) {
        List<String> result = new ArrayList<>();

        // The report takes every part's pitch and volume and the barrel material's.
        double pitch = 1.0;
        double volume = 1.0;
        Metal metal = null;
        if (soundType == SoundType.SHOOT) {
            for (GunPart part : parts) {
                pitch *= part.getSoundPitch();
                volume *= part.getSoundVolume();
            }
            metal = ShotFx.barrelMetal(parts);
            if (metal != null) {
                pitch *= metal.getSoundPitch();
                volume *= metal.getSoundVolume();
            }
        }

        // 🔄 1) Collect ALL overrides first (across all parts)
        for (GunPart part : parts) {
            List<GunPart.PartSound> overrides = part.getSoundOverrides().get(soundType);
            if (overrides != null) {
                for (GunPart.PartSound ps : overrides) {
                    if (ps.matches(gunType)) {
                        addGroup(result, ps, pitch, volume);
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

        // 🎵 2) Otherwise, gather normal sounds (and the barrel material's layers, e.g. mythril's ring)
        List<List<GunPart.PartSound>> sources = new ArrayList<>();
        for (GunPart part : parts) {
            sources.add(part.getSounds().get(soundType));
        }
        if (metal != null) {
            sources.add(metal.getSounds().get(soundType));
        }
        for (List<GunPart.PartSound> base : sources) {
            if (base != null) {
                for (GunPart.PartSound ps : base) {
                    if (ps.matches(gunType)) {
                        addGroup(result, ps, pitch, volume);
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
     * If an override is present, the last part with one supplies every caliber.
     */
    private static boolean declaresShot(Collection<GunPart> parts) {
        for (GunPart part : parts) {
            if (!part.getCalibers().isEmpty() || part.getAmmoFilter() != null || part.declaresAmmoFamily()
                    || (part.getCaliberOverrides() != null && !part.getCaliberOverrides().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveCalibers(Collection<GunPart> parts) {
        List<String> override = null;
        List<String> normal = new ArrayList<>();

        for (GunPart part : parts) {
            if (part.getCaliberOverrides() != null && !part.getCaliberOverrides().isEmpty()) {
                override = part.getCaliberOverrides();
            } else if (!part.getCalibers().isEmpty()) {
                normal.addAll(part.getCalibers());
            }
        }

        List<String> calibers = override != null
                ? new ArrayList<>(override) // ✅ Only the last override matters, with all of its calibers
                : normal;
        // A proofed chamber loads only powered shot and an airgun lock only plain shot.
        for (GunPart part : parts) {
            GunPart.AmmoFilter filter = part.getAmmoFilter();
            if (filter != null) {
                calibers.removeIf(key -> {
                    Ammunition ammo = AmmunitionLoader.getByString(key);
                    return ammo != null && !filter.allows(ammo.isPowered());
                });
            }
        }
        return calibers;
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
        ForgedLook.applyIcon(m, part, true);

        // Name
        m.setDisplayName(part.getName());

        NamespacedKey key = new NamespacedKey(GunsAndGadgets.getInstance(), "part_id");
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, part.getId());

        // Lore
        List<String> lore = new ArrayList<>();

        if (part.hasTier()) {
            lore.add(TierLore.formatComponentLine(part.getTier()));
            if (!part.getLore().isEmpty()) {
                lore.add("");
            }
        }

        if (!part.getLore().isEmpty()) {
            for (String line : part.getLore()) {
                lore.add(StringFormatter.formatHex(line));
            }
        }

        if (part.isForged()) {
            // What the part does, not by how much: the material sets the numbers on the gun.
            lore.add("");
            lore.addAll(ForgeLore.lines(part));
        } else if (!part.getStats().isEmpty()) {
            // Add stats
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

        InventoryHolder menu = event.getInventory().getHolder(false);

        // ASSEMBLY GUI
        if (menu instanceof AssemblyHolder) {
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
        if (menu instanceof TypeSelectionHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String name = clicked.getItemMeta().getDisplayName();
            if (name.equals("§eBack")) {
                openCraftingInventory(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }

            String clean = name.replace("§e", "").trim();
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
        if (menu instanceof PartSelectionHolder holder) {
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
            String baseId = clicked.getItemMeta().getPersistentDataContainer().get(forgeBaseKey(), PersistentDataType.STRING);
            if (baseId != null) {
                openMetalSelection(player, holder.getPartId(), baseId);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            selectPart(player, holder.getPartId(), clicked);
            return;
        }

        // MATERIAL SELECTION GUI (forged parts)
        if (menu instanceof MetalSelectionHolder holder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if ("§eBack".equals(clicked.getItemMeta().getDisplayName())) {
                openPartSelection(player, holder.getPartId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            String metalId = clicked.getItemMeta().getPersistentDataContainer().get(forgeMetalKey(), PersistentDataType.STRING);
            if (metalId != null) {
                openFittingSelection(player, holder.getPartId(), holder.getBaseId(), metalId);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            selectPart(player, holder.getPartId(), clicked);
            return;
        }

        // FITTING SELECTION GUI (the animal fittings of one material)
        if (menu instanceof FittingSelectionHolder holder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if ("§eBack".equals(clicked.getItemMeta().getDisplayName())) {
                openMetalSelection(player, holder.getPartId(), holder.getBaseId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                return;
            }
            selectPart(player, holder.getPartId(), clicked);
        }
    }

    /** Saves the clicked part for its slot and reopens the assembly preview. */
    private void selectPart(Player player, String partCategoryId, ItemStack clicked) {
        NamespacedKey key = new NamespacedKey(GunsAndGadgets.getInstance(), "part_id");
        String partKey = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (partKey == null) return;

        // save selection and reopen preview
        SelectedPartsManager.set(player, partCategoryId, partKey);
        openCraftingInventory(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }

    private boolean hasPermissionForPart(Player player, GunPart part) {
        if(!Cache.requireInput) return true; //tutorial setup
        String materialPermission = part.getMaterialPermission();
        if (materialPermission != null && !player.hasPermission(materialPermission)) return false;
        if (part.getPermissions().isEmpty()) return true;
        for (String perm : part.getPermissions()) {
            if (player.hasPermission(perm)) return true;
        }
        return false;
    }
}

package net.tfminecraft.gunsandgadgets.manager.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.*;
import net.tfminecraft.gunsandgadgets.guns.parts.*;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.guns.stats.Stats;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.util.LegacyModelData;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.*;

class InventoryManagerTest {
  ServerMock server;
  PlayerMock player;
  GunsAndGadgets plugin;
  InventoryManager manager;
  GunPart action;
  SkinData skin;
  MockedStatic<GunsAndGadgets> plugins;
  MockedStatic<TLibs> libs;
  MockedStatic<ItemSkinPreserver> appearance;
  MockedStatic<LegacyModelData> model;
  MockedStatic<NBTItem> nbt;
  NBTItem nbtItem;
  ItemAPI items;
  int oldSlot;
  boolean oldRequire;
  Map<GunType, List<String>> oldRequired;
  HashMap<GunType, String> oldOutputs;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    manager = new InventoryManager();
    plugin = mock(GunsAndGadgets.class);
    when(plugin.namespace()).thenReturn("gunsandgadgets");
    plugins = mockStatic(GunsAndGadgets.class);
    plugins.when(GunsAndGadgets::getInstance).thenReturn(plugin);
    libs = mockStatic(TLibs.class);
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    libs.when(TLibs::getItemAPI).thenReturn(items);
    when(items.getCreator().getItemFromPath(anyString()))
        .thenAnswer(i -> new ItemStack(Material.STICK));
    appearance = mockStatic(ItemSkinPreserver.class);
    appearance.when(() -> ItemSkinPreserver.hasSkinData(any())).thenReturn(true);
    appearance
        .when(() -> ItemSkinPreserver.applyAppearanceFromSkin(any(), any()))
        .thenAnswer(i -> i.getArgument(1));
    model = mockStatic(LegacyModelData.class);
    nbt = mockStatic(NBTItem.class);
    nbtItem = mock(NBTItem.class);
    nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbtItem);
    oldSlot = Cache.outputSlot;
    oldRequire = Cache.requireInput;
    oldRequired = Cache.requiredParts;
    oldOutputs = Cache.outputItems;
    Cache.outputSlot = 26;
    Cache.requireInput = true;
    Cache.requiredParts = new HashMap<>();
    Cache.outputItems = new HashMap<>();
    action = part("flintlock", "action", 1);
    skin = mock(SkinData.class);
    when(skin.getId()).thenReturn("rifle_flintlock");
    when(skin.getTypes()).thenReturn(List.of("rifle", "pistol", "shotgun", "launcher"));
    when(skin.parseModel(any())).thenReturn(new ItemStack(Material.STICK));
    SkinLoader.get().put("rifle_flintlock", skin);
  }

  @AfterEach
  void close() {
    SelectedPartsManager.clear(player);
    PartLoader.clear();
    PartDataLoader.clear();
    SkinLoader.clear();
    AmmunitionLoader.clear();
    Cache.outputSlot = oldSlot;
    Cache.requireInput = oldRequire;
    Cache.requiredParts = oldRequired;
    Cache.outputItems = oldOutputs;
    nbt.close();
    model.close();
    appearance.close();
    libs.close();
    plugins.close();
    MockBukkit.unmock();
  }

  GunPart part(String id, String category, int slot) {
    var config = new YamlConfiguration();
    config.set("slot", slot);
    PartData data = new PartData(category, config);
    PartDataLoader.get().put(category, data);
    GunPart p = mock(GunPart.class);
    when(p.getId()).thenReturn(id);
    when(p.getName()).thenReturn(id);
    when(p.getItemKey()).thenReturn("v.stick");
    when(p.getPartType()).thenReturn(data);
    when(p.getGunTypes()).thenReturn(Set.of(GunType.values()));
    when(p.isEnabledForCrafting()).thenAnswer(i -> !p.isDisabled());
    PartLoader.get().put(id, p);
    PartLoader.getOrdered().add(p);
    return p;
  }

  NamespacedKey key(String name) {
    return new NamespacedKey(plugin, name);
  }

  ItemStack output(GunPart... parts) {
    return manager.createOutputItem(GunType.RIFLE, List.of(parts), false);
  }

  String string(ItemStack item, String name) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .get(key(name), PersistentDataType.STRING);
  }

  ItemStack named(String name) {
    var item = new ItemStack(Material.PAPER);
    var m = item.getItemMeta();
    m.setDisplayName(name);
    item.setItemMeta(m);
    return item;
  }

  InventoryClickEvent click(InventoryHolder holder, int slot, ItemStack item) {
    var e = mock(InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(player);
    when(e.getInventory()).thenReturn(server.createInventory(holder, 27));
    when(e.getSlot()).thenReturn(slot);
    when(e.getCurrentItem()).thenReturn(item);
    return e;
  }

  @Test
  void outputContainsIdentityTypeSkinAndCraftProvenance() {
    var item = output(action);
    assertNotNull(UUID.fromString(string(item, "gun_id")));
    assertEquals("RIFLE", string(item, "gun_type"));
    assertEquals("rifle_flintlock", string(item, "skin_id"));
    assertNotNull(string(item, "gg_craft_parts"));
    assertTrue(item.getItemMeta().getPersistentDataContainer().has(key("accuracy_salt")));
  }

  @Test
  void previewDoesNotStampCraftProvenance() {
    var item = manager.createOutputItem(GunType.RIFLE, List.of(action), true);
    assertNull(string(item, "gg_craft_parts"));
  }

  @Test
  void disabledAndMissingRequiredPartsProduceBarrier() {
    when(action.isDisabled()).thenReturn(true);
    assertEquals(Material.BARRIER, output(action).getType());
    when(action.isDisabled()).thenReturn(false);
    Cache.requiredParts.put(GunType.RIFLE, List.of("action", "barrel"));
    assertEquals(Material.BARRIER, output(action).getType());
    var barrel = part("long", "barrel", 2);
    assertNotEquals(Material.BARRIER, output(barrel, action).getType());
  }

  @Test
  void highestWeightedNameFragmentsAreOrderedAndOverrideCanReplaceAll() {
    var barrel = part("long", "barrel", 2);
    when(action.getNameImpacts())
        .thenReturn(
            List.of(new GunPart.NameImpact("Rifle", 2, 1), new GunPart.NameImpact("Common", 0, 1)));
    when(barrel.getNameImpacts())
        .thenReturn(
            List.of(new GunPart.NameImpact("Fine", 0, 2), new GunPart.NameImpact("Ignored", 0, 1)));
    assertEquals("§7Fine Rifle", output(action, barrel).getItemMeta().getDisplayName());
    when(barrel.getNameImpacts()).thenReturn(List.of(new GunPart.NameImpact("Unique", -1, 1)));
    assertEquals("§7Unique", output(action, barrel).getItemMeta().getDisplayName());
  }

  @Test
  void incompatibleClassesAreReportedAndUnrestrictedPartsIgnored() {
    var barrel = part("long", "barrel", 2);
    var free = part("plain", "stock", 3);
    when(free.getClassRequirements()).thenReturn(List.of());
    assertFalse(manager.hasClassConflict(player, List.of(action, barrel, free)));
    when(action.getClassRequirements()).thenReturn(List.of("ranger", "mage"));
    when(barrel.getClassRequirements()).thenReturn(List.of("mage"));
    assertFalse(manager.hasClassConflict(player, List.of(action, barrel)));
    when(barrel.getClassRequirements()).thenReturn(List.of("warrior"));
    assertTrue(manager.hasClassConflict(player, List.of(action, barrel, free)));
    assertEquals(Material.STICK, output(action, barrel).getType());
  }

  @Test
  void compatibleClassRequirementOnUntypedItemPreservesItem() {
    when(action.getClassRequirements()).thenReturn(List.of("ranger"));
    assertEquals(Material.STICK, output(action).getType());
  }

  @Test
  void twoHandedUntypedItemIsPreserved() {
    when(action.isTwoHanded()).thenReturn(true);
    assertEquals(Material.STICK, output(action).getType());
  }

  @Test
  void rebuildingPreservesIdentitySaltAndAmmoRuntime() {
    var previous = output(action);
    var m = previous.getItemMeta();
    var p = m.getPersistentDataContainer();
    p.set(key("bullets_loaded"), PersistentDataType.INTEGER, 4);
    p.set(key("ammo_loaded"), PersistentDataType.STRING, "shot");
    p.set(key("reload_ammo"), PersistentDataType.STRING, "shot");
    p.set(key("reload_amount"), PersistentDataType.INTEGER, 2);
    p.set(key("last_fire"), PersistentDataType.LONG, 123L);
    previous.setItemMeta(m);
    var rebuilt = manager.rebuildFromParts(previous, GunType.RIFLE, List.of(action));
    assertEquals(string(previous, "gun_id"), string(rebuilt, "gun_id"));
    for (var key : List.of("accuracy_salt", "bullets_loaded", "reload_amount"))
      assertEquals(
          previous
              .getItemMeta()
              .getPersistentDataContainer()
              .get(key(key), PersistentDataType.INTEGER),
          rebuilt
              .getItemMeta()
              .getPersistentDataContainer()
              .get(key(key), PersistentDataType.INTEGER));
    assertEquals("shot", string(rebuilt, "ammo_loaded"));
    assertEquals("shot", string(rebuilt, "reload_ammo"));
    assertEquals(
        123L,
        rebuilt
            .getItemMeta()
            .getPersistentDataContainer()
            .get(key("last_fire"), PersistentDataType.LONG));
  }

  @Test
  void rebuildingWithoutRuntimeGeneratesIdentityAndLeavesAmmoAbsent() {
    var old = named("old");
    var rebuilt = manager.rebuildFromParts(old, GunType.RIFLE, List.of(action));
    assertNotNull(string(rebuilt, "gun_id"));
    assertNull(string(rebuilt, "ammo_loaded"));
    assertNull(string(rebuilt, "reload_ammo"));
    assertFalse(rebuilt.getItemMeta().getPersistentDataContainer().has(key("bullets_loaded")));
  }

  @Test
  void calibersCombineUnlessLastOverridePresent() {
    var barrel = part("long", "barrel", 2);
    when(action.getCalibers()).thenReturn(List.of("known", "unknown"));
    var config = new YamlConfiguration();
    AmmunitionLoader.get()
        .put(
            "known",
            new net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition("known", config));
    assertEquals("known;unknown", string(output(action), "calibers"));
    when(barrel.getCaliberOverrides()).thenReturn(List.of("override1", "override2"));
    assertEquals("override2", string(output(action, barrel), "calibers"));
    when(action.getCalibers()).thenReturn(List.of());
    when(action.getCaliberOverrides()).thenReturn(null);
    assertNull(string(output(action), "calibers"));
  }

  @Test
  void soundOverridesReplaceNormalSoundsAndFilterGunType() {
    var wrong = new GunPart.PartSound(List.of("wrong"), GunType.PISTOL);
    var blank = new GunPart.PartSound(List.of(), null);
    var normal = new GunPart.PartSound(List.of("bang", "bang2"), null);
    when(action.getSounds()).thenReturn(Map.of(SoundType.SHOOT, List.of(wrong, blank, normal)));
    assertEquals("bang,bang2", string(output(action), "shoot_sounds"));
    when(action.getSoundOverrides())
        .thenReturn(
            Map.of(
                SoundType.SHOOT,
                List.of(wrong, blank, new GunPart.PartSound(List.of("quiet"), GunType.RIFLE))));
    assertEquals("quiet", string(output(action), "shoot_sounds"));
  }

  @Test
  void majorityTierIsWrittenIntoOutput() {
    when(action.hasTier()).thenReturn(true);
    when(action.getTier()).thenReturn(3);
    var item = output(action);
    assertEquals(
        3,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(key("gg_majority_tier"), PersistentDataType.INTEGER));
  }

  @Test
  void typeMenuContainsEveryGunType() {
    manager.openTypeSelection(player);
    var top = player.getOpenInventory().getTopInventory();
    assertInstanceOf(TypeSelectionHolder.class, top.getHolder());
    for (int i = 0; i < GunType.values().length; i++)
      assertEquals(
          "§e" + GunType.values()[i].getDisplayName(),
          top.getItem(i).getItemMeta().getDisplayName());
  }

  @Test
  void assemblyShowsAvailablePartsMissingCategoriesAndSkipsInvalidSlots() {
    part("bad0", "zero", 0);
    part("bad27", "outside", 27);
    var restricted = part("locked", "barrel", 2);
    when(restricted.getPermissions()).thenReturn(List.of("special"));
    manager.openCraftingInventory(player);
    var top = player.getOpenInventory().getTopInventory();
    assertInstanceOf(AssemblyHolder.class, top.getHolder());
    assertEquals("flintlock", string(top.getItem(1), "part_id"));
    assertEquals(Material.BARRIER, top.getItem(2).getType());
    assertEquals(Material.STICK, top.getItem(26).getType());
  }

  @Test
  void partMenuFiltersCategoryTypeDisabledAndPermissions() {
    var wrong = part("wrong", "barrel", 2);
    var pistol = part("pistol", "action", 1);
    when(pistol.getGunTypes()).thenReturn(Set.of(GunType.PISTOL));
    var disabled = part("disabled", "action", 1);
    when(disabled.isDisabled()).thenReturn(true);
    var locked = part("locked", "action", 1);
    when(locked.getPermissions()).thenReturn(List.of("missing"));
    manager.openPartSelection(player, "action");
    var top = player.getOpenInventory().getTopInventory();
    assertEquals("flintlock", string(top.getItem(0), "part_id"));
    assertNull(top.getItem(1));
    assertEquals(Material.ARROW, top.getItem(8).getType());
    manager.openPartSelection(player, "absent");
    assertEquals(
        Material.BARRIER, player.getOpenInventory().getTopInventory().getItem(4).getType());
  }

  @Test
  void partLoreShowsTiersStatsAndCosts() {
    when(action.hasTier()).thenReturn(true);
    when(action.getTier()).thenReturn(2);
    when(action.getLore()).thenReturn(List.of("Detail"));
    when(action.getStats())
        .thenReturn(
            Map.of(
                Stats.values()[0],
                -1,
                Stats.values()[1],
                0,
                Stats.values()[2],
                2,
                Stats.values()[3],
                5,
                Stats.values()[4],
                8));
    when(action.hasCost()).thenReturn(true);
    when(action.getCost()).thenReturn(Map.of("v.iron_ingot", 2));
    manager.openPartSelection(player, "action");
    var item = player.getOpenInventory().getTopInventory().getItem(0);
    assertTrue(item.getItemMeta().getLore().stream().anyMatch(l -> l.contains("Detail")));
    assertTrue(item.getItemMeta().getLore().stream().anyMatch(l -> l.contains("Input:")));
    Cache.requireInput = false;
    manager.openPartSelection(player, "action");
    assertFalse(
        player.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getLore().stream()
            .anyMatch(l -> l.contains("Input:")));
  }

  @Test
  void selectedPartSurvivesOrFallsBackWhenInvalid() {
    var other = part("second", "action", 1);
    SelectedPartsManager.set(player, "action", "SECOND");
    assertEquals(
        List.of(other), new ArrayList<>(manager.collectPartsForPlayer(player, GunType.RIFLE)));
    when(other.isDisabled()).thenReturn(true);
    assertEquals(
        List.of(action), new ArrayList<>(manager.collectPartsForPlayer(player, GunType.RIFLE)));
    SelectedPartsManager.set(player, "action", "removed");
    assertEquals(
        List.of(action), new ArrayList<>(manager.collectPartsForPlayer(player, GunType.RIFLE)));
    when(action.getGunTypes()).thenReturn(Set.of(GunType.PISTOL));
    assertTrue(manager.collectPartsForPlayer(player, GunType.RIFLE).isEmpty());
  }

  @Test
  void partPermissionsAllowAnyMatchingPermissionAndTutorialBypass() {
    when(action.getPermissions()).thenReturn(List.of("absent", "allowed"));
    assertTrue(manager.collectPartsForPlayer(player, GunType.RIFLE).isEmpty());
    player.setOp(true);
    assertEquals(1, manager.collectPartsForPlayer(player, GunType.RIFLE).size());
    player.setOp(false);
    Cache.requireInput = false;
    assertEquals(1, manager.collectPartsForPlayer(player, GunType.RIFLE).size());
  }

  @Test
  void assemblyClicksOpenTypeAndPartMenusOnlyOnValidSlots() {
    var event = click(new AssemblyHolder(), 0, named("type"));
    manager.onInventoryClick(event);
    verify(event).setCancelled(true);
    assertInstanceOf(
        TypeSelectionHolder.class, player.getOpenInventory().getTopInventory().getHolder());
    event = click(new AssemblyHolder(), 1, named("part"));
    manager.onInventoryClick(event);
    assertInstanceOf(
        PartSelectionHolder.class, player.getOpenInventory().getTopInventory().getHolder());
    for (var item :
        Arrays.asList(
            null,
            new ItemStack(Material.GRAY_STAINED_GLASS_PANE),
            new ItemStack(Material.BARRIER))) {
      manager.onInventoryClick(click(new AssemblyHolder(), 2, item));
    }
    manager.onInventoryClick(click(new AssemblyHolder(), 26, named("output")));
    manager.onInventoryClick(click(new AssemblyHolder(), 9, named("unknown")));
  }

  @Test
  void selectingTypeClearsSelectedPartsAndReopensAssembly() {
    SelectedPartsManager.set(player, "action", "flintlock");
    manager.onInventoryClick(click(new TypeSelectionHolder(), 1, named("§ePistol")));
    assertEquals(GunType.PISTOL, TypeSelectionManager.getSelectedType(player));
    assertNull(SelectedPartsManager.get(player, "action"));
    assertInstanceOf(AssemblyHolder.class, player.getOpenInventory().getTopInventory().getHolder());
  }

  @Test
  void selectingPartStoresIdAndBackButtonsReturnToAssembly() {
    var item = named("part");
    var m = item.getItemMeta();
    m.getPersistentDataContainer().set(key("part_id"), PersistentDataType.STRING, "flintlock");
    item.setItemMeta(m);
    manager.onInventoryClick(click(new PartSelectionHolder("action"), 0, item));
    assertEquals("flintlock", SelectedPartsManager.get(player, "action"));
    manager.onInventoryClick(click(new PartSelectionHolder("action"), 8, named("§eBack")));
    manager.onInventoryClick(click(new TypeSelectionHolder(), 8, named("§eBack")));
    assertInstanceOf(AssemblyHolder.class, player.getOpenInventory().getTopInventory().getHolder());
  }

  @Test
  void irrelevantAndEmptyMenuClicksAreIgnored() {
    var e = mock(InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
    manager.onInventoryClick(e);
    verify(e, never()).setCancelled(anyBoolean());
    for (var holder : List.of(new TypeSelectionHolder(), new PartSelectionHolder("action"))) {
      manager.onInventoryClick(click(holder, 0, null));
      manager.onInventoryClick(click(holder, 0, named("unknown")));
    }
    manager.onInventoryClick(click(null, 0, named("unknown")));
  }

  @Test
  void typedOutputAppliesClassIntersectionAndTwoHandedHistory() {
    var oldMythic = io.lumine.mythic.lib.MythicLib.plugin;
    var oldItems = net.Indyuce.mmoitems.MMOItems.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    net.Indyuce.mmoitems.MMOItems.plugin =
        mock(net.Indyuce.mmoitems.MMOItems.class, RETURNS_DEEP_STUBS);
    when(net.Indyuce.mmoitems.MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    when(nbtItem.hasType()).thenReturn(true);
    when(action.getClassRequirements()).thenReturn(List.of("ranger"));
    when(action.isTwoHanded()).thenReturn(true);
    var classHistory = mock(net.Indyuce.mmoitems.stat.type.StatHistory.class);
    var handHistory = mock(net.Indyuce.mmoitems.stat.type.StatHistory.class);
    var original = new net.Indyuce.mmoitems.stat.data.StringListData(List.of("old"));
    when(classHistory.getOriginalData()).thenReturn(original);
    try (var live =
        mockConstruction(
            net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
            (mock, context) -> {
              when(mock.computeStatHistory(net.Indyuce.mmoitems.ItemStats.REQUIRED_CLASS))
                  .thenReturn(classHistory);
              when(mock.computeStatHistory(net.Indyuce.mmoitems.ItemStats.TWO_HANDED))
                  .thenReturn(handHistory);
              when(mock.newBuilder().build()).thenAnswer(i -> new ItemStack(Material.STICK));
            })) {
      output(action);
      assertEquals(List.of("ranger"), original.getList());
      verify(handHistory)
          .setOriginalData(
              argThat(
                  d -> d instanceof net.Indyuce.mmoitems.stat.data.BooleanData b && b.isEnabled()));
      assertEquals(2, live.constructed().size());
    } finally {
      io.lumine.mythic.lib.MythicLib.plugin = oldMythic;
      net.Indyuce.mmoitems.MMOItems.plugin = oldItems;
    }
  }

  @Test
  void typedOutputWithoutHistoryStillBuilds() {
    var oldMythic = io.lumine.mythic.lib.MythicLib.plugin;
    var oldItems = net.Indyuce.mmoitems.MMOItems.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    net.Indyuce.mmoitems.MMOItems.plugin =
        mock(net.Indyuce.mmoitems.MMOItems.class, RETURNS_DEEP_STUBS);
    when(net.Indyuce.mmoitems.MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    when(nbtItem.hasType()).thenReturn(true);
    when(action.getClassRequirements()).thenReturn(List.of("ranger"));
    when(action.isTwoHanded()).thenReturn(true);
    try (var live =
        mockConstruction(
            net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
            (mock, context) -> {
              when(mock.computeStatHistory(any())).thenReturn(null);
              when(mock.newBuilder().build()).thenAnswer(i -> new ItemStack(Material.STICK));
            })) {
      assertEquals(Material.STICK, output(action).getType());
      assertEquals(2, live.constructed().size());
    } finally {
      io.lumine.mythic.lib.MythicLib.plugin = oldMythic;
      net.Indyuce.mmoitems.MMOItems.plugin = oldItems;
    }
  }

  @Test
  void plainSkinCopiesModelDataAndRequestsMaterialChange() {
    appearance.when(() -> ItemSkinPreserver.hasSkinData(any())).thenReturn(false);
    var base = spy(new ItemStack(Material.STICK));
    doNothing().when(base).setType(any());
    when(items.getCreator().getItemFromPath(anyString())).thenReturn(base);
    model.when(() -> LegacyModelData.has(any())).thenReturn(true);
    model.when(() -> LegacyModelData.get(any())).thenReturn(123);
    var result = output(action);
    verify(base).setType(Material.STICK);
    model.verify(() -> LegacyModelData.set(any(), eq(123)));
    assertSame(base, result);
    model.when(() -> LegacyModelData.has(any())).thenReturn(false);
    output(action);
    model.verify(() -> LegacyModelData.set(any(), eq(123)), times(1));
  }

  @Test
  void selectedPermissionAndTypeChangesFallBackToFirstAvailablePart() {
    var second = part("second", "action", 1);
    SelectedPartsManager.set(player, "action", "second");
    assertTrue(manager.collectPartsForPlayer(player, GunType.RIFLE).contains(second));
    when(second.getPermissions()).thenReturn(List.of("denied"));
    assertTrue(manager.collectPartsForPlayer(player, GunType.RIFLE).contains(action));
    when(second.getPermissions()).thenReturn(List.of());
    when(second.getGunTypes()).thenReturn(Set.of(GunType.PISTOL));
    assertTrue(manager.collectPartsForPlayer(player, GunType.RIFLE).contains(action));
  }

  @Test
  void missingSkinProducesUnavailableOutputInsteadOfCrashing() {
    SkinLoader.clear();
    assertEquals(Material.BARRIER, output(action).getType());
  }

  @Test
  void tierWithoutDescriptionDoesNotAddExtraDescriptionSpacer() {
    when(action.hasTier()).thenReturn(true);
    when(action.getTier()).thenReturn(2);
    manager.openPartSelection(player, "action");
    assertEquals(
        1, player.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getLore().size());
  }

  @Test
  void rebuiltOutputWithoutPreviousMetadataReceivesFreshIdentity() {
    var prior = mock(ItemStack.class);
    var result = manager.rebuildFromParts(prior, GunType.RIFLE, List.of(action));
    assertNotNull(string(result, "gun_id"));
  }

  @Test
  void existingBaseLoreIsRetained() {
    when(items.getCreator().getItemFromPath(anyString()))
        .thenAnswer(
            i -> {
              var item = new ItemStack(Material.STICK);
              var meta = item.getItemMeta();
              meta.setLore(List.of("Existing"));
              item.setItemMeta(meta);
              return item;
            });
    assertEquals("Existing", output(action).getItemMeta().getLore().getFirst());
  }

  @Test
  void airModelHasNoMetadataAndDoesNotCopyRuntimeState() {
    when(skin.parseModel(any())).thenReturn(new ItemStack(Material.AIR));
    appearance.when(() -> ItemSkinPreserver.hasSkinData(any())).thenReturn(false);
    assertEquals(
        Material.AIR,
        manager.rebuildFromParts(named("old"), GunType.RIFLE, List.of(action)).getType());
  }

  @Test
  void metadataFreeMenuItemsAreIgnored() {
    for (var holder :
        List.of(
            new AssemblyHolder(), new TypeSelectionHolder(), new PartSelectionHolder("action"))) {
      var event = click(holder, 0, mock(ItemStack.class));
      manager.onInventoryClick(event);
      verify(event).setCancelled(true);
    }
  }

  @Test
  void cancelledTwoHandedItemBuildReturnsNoItemWithoutCopyingRuntime() {
    var oldMythic = io.lumine.mythic.lib.MythicLib.plugin;
    var oldItems = net.Indyuce.mmoitems.MMOItems.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    net.Indyuce.mmoitems.MMOItems.plugin =
        mock(net.Indyuce.mmoitems.MMOItems.class, RETURNS_DEEP_STUBS);
    when(net.Indyuce.mmoitems.MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    when(nbtItem.hasType()).thenReturn(true);
    when(action.isTwoHanded()).thenReturn(true);
    try (var live =
        mockConstruction(
            net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
            (mock, context) -> {
              when(mock.computeStatHistory(any())).thenReturn(null);
              when(mock.newBuilder().build()).thenReturn(null);
            })) {
      assertNull(manager.rebuildFromParts(named("old"), GunType.RIFLE, List.of(action)));
    } finally {
      io.lumine.mythic.lib.MythicLib.plugin = oldMythic;
      net.Indyuce.mmoitems.MMOItems.plugin = oldItems;
    }
  }
}

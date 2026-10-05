package net.tfminecraft.gunsandgadgets.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.attributes.AttributeReader;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.shooter.ProjectileShooter;
import net.tfminecraft.gunsandgadgets.util.SoundPlayer;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class GunManagerTest {
  ServerMock server;
  PlayerMock player;
  GunManager manager;
  GunsAndGadgets plugin;
  MockedStatic<GunsAndGadgets> pluginStatic;
  MockedStatic<TLibs> libs;
  MockedStatic<AttributeReader> attributes;
  MockedStatic<SoundPlayer> sounds;
  MockedStatic<io.lumine.mythic.lib.api.player.MMOPlayerData> mmoPlayers;
  io.lumine.mythic.lib.api.player.MMOPlayerData mmoPlayer;
  ItemStack gun;
  SkinData skin;
  io.lumine.mythic.lib.MythicLib oldMythic;
  net.Indyuce.mmoitems.MMOItems oldItems;

  @BeforeEach
  void setup() {
    oldItems = net.Indyuce.mmoitems.MMOItems.plugin;
    net.Indyuce.mmoitems.MMOItems.plugin =
        mock(net.Indyuce.mmoitems.MMOItems.class, RETURNS_DEEP_STUBS);
    oldMythic = io.lumine.mythic.lib.MythicLib.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    server = MockBukkit.mock();
    player = server.addPlayer();
    player.setOp(true);
    plugin = mock(GunsAndGadgets.class);
    when(plugin.getName()).thenReturn("GunsAndGadgets");
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.namespace()).thenReturn("gunsandgadgets");
    pluginStatic = mockStatic(GunsAndGadgets.class);
    pluginStatic.when(GunsAndGadgets::getInstance).thenReturn(plugin);
    manager = new GunManager();
    skin = mock(SkinData.class);
    when(skin.parseModel(any())).thenReturn(null);
    SkinLoader.get().put("test", skin);
    Ammunition ammo = new Ammunition("shot", new YamlConfiguration());
    AmmunitionLoader.get().put("shot", ammo);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getChecker().checkItemWithPath(any(), anyString()))
        .thenAnswer(i -> ((ItemStack) i.getArgument(0)).getType() == Material.IRON_NUGGET);
    when(api.getCreator().getItemFromPath(anyString()))
        .thenAnswer(i -> new ItemStack(Material.IRON_NUGGET));
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getItemAPI).thenReturn(api);
    attributes = mockStatic(AttributeReader.class);
    attributes
        .when(() -> AttributeReader.getReloadReductionMultFromAttributes(player))
        .thenReturn(1.0);
    sounds = mockStatic(SoundPlayer.class);
    mmoPlayer = mock(io.lumine.mythic.lib.api.player.MMOPlayerData.class, RETURNS_DEEP_STUBS);
    when(mmoPlayer.getActionBar().show(anyInt(), anyLong(), nullable(String.class))).thenReturn(true);
    mmoPlayers = mockStatic(io.lumine.mythic.lib.api.player.MMOPlayerData.class);
    mmoPlayers
        .when(() -> io.lumine.mythic.lib.api.player.MMOPlayerData.get(any(OfflinePlayer.class)))
        .thenReturn(mmoPlayer);
    gun = new ItemStack(Material.STICK);
    set("skin_id", "test");
    set("gun_id", new String("same-id"));
    set("gun_type", "rifle");
    set("calibers", "shot");
    setInt("stat_value_capacity", 3);
    setInt("stat_value_reload", 20);
    player.getInventory().setItemInMainHand(gun);
    player.getInventory().setItem(1, new ItemStack(Material.IRON_NUGGET, 5));
  }

  @AfterEach
  void cleanup() {
    if (server != null && plugin != null) server.getScheduler().cancelTasks(plugin);
    if (sounds != null) sounds.close();
    if (mmoPlayers != null) mmoPlayers.close();
    if (attributes != null) attributes.close();
    if (libs != null) libs.close();
    if (pluginStatic != null) pluginStatic.close();
    SkinLoader.clear();
    AmmunitionLoader.clear();
    MockBukkit.unmock();
    io.lumine.mythic.lib.MythicLib.plugin = oldMythic;
    net.Indyuce.mmoitems.MMOItems.plugin = oldItems;
  }

  NamespacedKey key(String name) {
    return new NamespacedKey(plugin, name);
  }

  void set(String name, String value) {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer().set(key(name), PersistentDataType.STRING, value);
    gun.setItemMeta(m);
  }

  void setInt(String name, int value) {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer().set(key(name), PersistentDataType.INTEGER, value);
    gun.setItemMeta(m);
  }

  int readInt(ItemStack item, String name) {
    return item.getItemMeta()
        .getPersistentDataContainer()
        .getOrDefault(key(name), PersistentDataType.INTEGER, 0);
  }

  Cancellable use() {
    Cancellable event = mock(Cancellable.class);
    manager.handleGunUse(player, event);
    return event;
  }

  @Test
  void reloadAcceptsDistinctStringsWithEqualGunIds() {
    use();
    ItemStack held = player.getInventory().getItemInMainHand();
    var m = held.getItemMeta();
    m.getPersistentDataContainer()
        .set(key("gun_id"), PersistentDataType.STRING, new String("same-id"));
    held.setItemMeta(m);
    player.getInventory().setItemInMainHand(held);
    server.getScheduler().performTicks(12);
    held = player.getInventory().getItemInMainHand();
    assertEquals(
        3, readInt(held, "bullets_loaded"), "Equal persisted gun IDs must complete reload");
    assertEquals(0, readInt(held, "reload_amount"));
    assertEquals(2, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void duplicateUseDoesNotTakeAmmoTwice() {
    use();
    use();
    assertEquals(2, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void noAmmoLeavesGunUnloaded() {
    player.getInventory().setItem(1, null);
    use();
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
  }

  @Test
  void unknownSkinDoesNotConsumeAmmo() {
    SkinLoader.clear();
    use();
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void loadedGunConsumesOneBulletAndSetsCooldown() {
    setInt("bullets_loaded", 2);
    set("ammo_loaded", "shot");
    player.getInventory().setItemInMainHand(gun);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
      assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
      shooter.verify(
          () ->
              ProjectileShooter.shoot(eq(player), any(), eq(AmmunitionLoader.getByString("shot"))));
      use();
      assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    }
  }

  @Test
  void finalBulletClearsLoadedAmmoAndUsesCarryModel() {
    setInt("bullets_loaded", 1);
    set("ammo_loaded", "shot");
    player.getInventory().setItemInMainHand(gun);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
      ItemStack held = player.getInventory().getItemInMainHand();
      assertEquals(0, readInt(held, "bullets_loaded"));
      assertFalse(held.getItemMeta().getPersistentDataContainer().has(key("ammo_loaded")));
      verify(skin).parseModel(SkinState.CARRY);
    }
  }

  @Test
  void brokenGunCancelsWithoutConsumingAmmo() {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer().set(key("gg_broken"), PersistentDataType.BOOLEAN, true);
    gun.setItemMeta(m);
    player.getInventory().setItemInMainHand(gun);
    verify(use()).setCancelled(true);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void ordinaryItemsRemainUsable() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
    verify(use(), never()).setCancelled(true);
  }

  @Test
  void switchingSlotsRefundsReservedAmmoAndClearsReloadState() {
    use();
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItem(0), "reload_amount"));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItem(0), "bullets_loaded"));
  }

  @Test
  void changingGunIdentityCancelsReload() {
    use();
    var held = player.getInventory().getItemInMainHand();
    var m = held.getItemMeta();
    m.getPersistentDataContainer().set(key("gun_id"), PersistentDataType.STRING, "other");
    held.setItemMeta(m);
    player.getInventory().setItemInMainHand(held);
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void activeReloadBlocksDroppingGunsButAllowsOrdinaryItems() {
    use();
    var event = mock(org.bukkit.event.player.PlayerDropItemEvent.class);
    when(event.getPlayer()).thenReturn(player);
    var dropped = mock(org.bukkit.entity.Item.class);
    when(event.getItemDrop()).thenReturn(dropped);
    when(dropped.getItemStack()).thenReturn(mock(ItemStack.class));
    manager.onDrop(event);
    verify(event, never()).setCancelled(true);
    when(dropped.getItemStack()).thenReturn(new ItemStack(Material.DIRT));
    manager.onDrop(event);
    verify(event, never()).setCancelled(true);
    when(dropped.getItemStack()).thenReturn(gun);
    manager.onDrop(event);
    verify(event).setCancelled(true);
  }

  @Test
  void activeReloadBlocksGunInventoryMovement() {
    use();
    var event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getCurrentItem()).thenReturn(new ItemStack(Material.DIRT));
    manager.onInventoryClick(event);
    verify(event, never()).setCancelled(true);
    when(event.getCursor()).thenReturn(gun);
    manager.onInventoryClick(event);
    verify(event).setCancelled(true);
  }

  @Test
  void offhandGunCancelsVanillaUse() {
    player.getInventory().setItemInOffHand(gun);
    player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
    verify(use()).setCancelled(true);
  }

  @Test
  void missingGunOrSkinIdDoesNotConsumeAmmo() {
    var m = gun.getItemMeta();
    m.getPersistentDataContainer().remove(key("gun_id"));
    gun.setItemMeta(m);
    player.getInventory().setItemInMainHand(gun);
    verify(use(), never()).setCancelled(true);
    m.getPersistentDataContainer().remove(key("skin_id"));
    gun.setItemMeta(m);
    player.getInventory().setItemInMainHand(gun);
    verify(use(), never()).setCancelled(true);
  }

  @Test
  void modelUpdatesPreserveItemIdentityAndCustomModelData() {
    gun = spy(gun);
    doNothing().when(gun).setType(any(Material.class));
    when(skin.parseModel(SkinState.CARRY)).thenReturn(new ItemStack(Material.AIR));
    assertSame(gun, manager.applyModel(gun, skin, SkinState.CARRY));
    ItemStack model = new ItemStack(Material.BLAZE_ROD);
    when(skin.parseModel(SkinState.CARRY)).thenReturn(model);
    try (var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var data = mockStatic(net.tfminecraft.gunsandgadgets.util.LegacyModelData.class)) {
      data.when(() -> net.tfminecraft.gunsandgadgets.util.LegacyModelData.has(any()))
          .thenReturn(true);
      data.when(() -> net.tfminecraft.gunsandgadgets.util.LegacyModelData.get(any()))
          .thenReturn(99);
      assertSame(gun, manager.applyModel(gun, skin, SkinState.CARRY));
      verify(gun).setType(Material.BLAZE_ROD);
      data.verify(() -> net.tfminecraft.gunsandgadgets.util.LegacyModelData.set(any(), eq(99)));
    }
  }

  @Test
  void crossbowReloadChargesArrowAndFinalShotClearsIt() {
    gun.setType(Material.CROSSBOW);
    player.getInventory().setItemInMainHand(gun);
    use();
    server.getScheduler().performTicks(12);
    var held = player.getInventory().getItemInMainHand();
    assertEquals(3, readInt(held, "bullets_loaded"));
    assertEquals(
        1,
        ((org.bukkit.inventory.meta.CrossbowMeta) held.getItemMeta())
            .getChargedProjectiles()
            .size());
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
      assertEquals(2, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
      server.getScheduler().performTicks(1);
      held = player.getInventory().getItemInMainHand();
      var m = held.getItemMeta();
      m.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, 1);
      m.getPersistentDataContainer().remove(key("last_fire"));
      held.setItemMeta(m);
      player.getInventory().setItemInMainHand(held);
      use();
      assertTrue(
          ((org.bukkit.inventory.meta.CrossbowMeta)
                  player.getInventory().getItemInMainHand().getItemMeta())
              .getChargedProjectiles()
              .isEmpty());
    }
  }

  @Test
  void firingUnloadsOtherGunsOfSameTypeAndRefundsTheirAmmo() {
    var other = gun.clone();
    other.setType(Material.CROSSBOW);
    var m = other.getItemMeta();
    m.getPersistentDataContainer().set(key("gun_id"), PersistentDataType.STRING, "other");
    m.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, 4);
    m.getPersistentDataContainer().set(key("ammo_loaded"), PersistentDataType.STRING, "shot");
    other.setItemMeta(m);
    player.getInventory().setItem(2, other);
    var different = other.clone();
    m = different.getItemMeta();
    m.getPersistentDataContainer().set(key("gun_type"), PersistentDataType.STRING, "pistol");
    different.setItemMeta(m);
    player.getInventory().setItem(3, different);
    var empty = other.clone();
    m = empty.getItemMeta();
    m.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, 0);
    empty.setItemMeta(m);
    player.getInventory().setItem(4, empty);
    setInt("bullets_loaded", 2);
    set("ammo_loaded", "shot");
    player.getInventory().setItemInMainHand(gun);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
    }
    assertEquals(0, readInt(player.getInventory().getItem(2), "bullets_loaded"));
    assertEquals(4, readInt(player.getInventory().getItem(3), "bullets_loaded"));
    assertEquals(9, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void unknownLoadedAmmoDoesNotInvokeProjectileIntegration() {
    setInt("bullets_loaded", 1);
    set("ammo_loaded", "removed");
    player.getInventory().setItemInMainHand(gun);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
      shooter.verifyNoInteractions();
    }
    assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
  }

  @Test
  void reloadConsumesAcrossMultipleAmmoStacks() {
    player.getInventory().setItem(1, new ItemStack(Material.IRON_NUGGET, 1));
    player.getInventory().setItem(2, new ItemStack(Material.IRON_NUGGET, 1));
    player.getInventory().setItem(3, new ItemStack(Material.IRON_NUGGET, 5));
    use();
    server.getScheduler().performTicks(12);
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(4, player.getInventory().getItem(3).getAmount());
  }

  @Test
  void movingDuringReloadDelaysProgressUntilStationary() {
    use();
    for (int i = 0; i < 15; i++) {
      player.teleport(player.getLocation().add(1, 0, 0));
      server.getScheduler().performTicks(1);
    }
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    server.getScheduler().performTicks(12);
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
  }

  @Test
  void rightClicksRespectInteractableBlocksAndOtherActions() {
    var e = mock(org.bukkit.event.player.PlayerInteractEvent.class);
    when(e.getPlayer()).thenReturn(player);
    when(e.getAction()).thenReturn(org.bukkit.event.block.Action.LEFT_CLICK_AIR);
    manager.onRightClick(e);
    var block = mock(org.bukkit.block.Block.class);
    when(block.getType()).thenReturn(Material.CHEST);
    when(e.getClickedBlock()).thenReturn(block);
    when(e.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
    manager.onRightClick(e);
    verify(e, never()).setCancelled(true);
    when(block.getType()).thenReturn(Material.STONE);
    manager.onRightClick(e);
    verify(e).setCancelled(true);
  }

  @Test
  void rightClickAirUsesGunAndItemFramesRemainAvailable() {
    var air = mock(org.bukkit.event.player.PlayerInteractEvent.class);
    when(air.getPlayer()).thenReturn(player);
    when(air.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
    manager.onRightClick(air);
    verify(air).setCancelled(true);
    var frame = mock(org.bukkit.event.player.PlayerInteractEntityEvent.class);
    when(frame.getRightClicked()).thenReturn(mock(org.bukkit.entity.ItemFrame.class));
    manager.onEntityInteract(frame);
    verify(frame, never()).setCancelled(true);
    when(frame.getRightClicked()).thenReturn(mock(org.bukkit.entity.Cow.class));
    when(frame.getPlayer()).thenReturn(player);
    manager.onEntityInteract(frame);
    verify(frame).setCancelled(true);
  }

  @Test
  void legacyMusketsAreCancelledButOtherWeaponsAreNot() {
    var e =
        mock(
            net.Indyuce.mmoitems.api.event.item.UntargetedWeaponUseEvent.class, RETURNS_DEEP_STUBS);
    when(e.getWeapon().getNBTItem().getType()).thenReturn("SWORD");
    manager.preventOldMuskets(e);
    verify(e, never()).setCancelled(true);
    when(e.getWeapon().getNBTItem().getType()).thenReturn("muskets");
    manager.preventOldMuskets(e);
    verify(e).setCancelled(true);
  }

  @Test
  void missingStampedPartsMarkGunBrokenAndBlockUse() {
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
            List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("missing", 1)), 1)
        .applyTo(gun);
    player.getInventory().setItemInMainHand(gun);
    verify(use()).setCancelled(true);
    assertTrue(
        net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
            player.getInventory().getItemInMainHand()));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void resolvedStampedPartsRemainUsableAndSlotSelectionChecksBrokenGuns() {
    var part = mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class);
    PartLoader.get().put("live", part);
    try {
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("live", 1)), 1)
          .applyTo(gun);
      player.getInventory().setItemInMainHand(gun);
      use();
      server.getScheduler().performTicks(12);
      assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
      var m = gun.getItemMeta();
      m.getPersistentDataContainer().set(key("gg_broken"), PersistentDataType.BOOLEAN, true);
      gun.setItemMeta(m);
      player.getInventory().setItem(2, gun);
      manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
      server.getScheduler().performTicks(1);
      assertTrue(
          net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
              player.getInventory().getItem(2)));
      player.getInventory().setItem(2, new ItemStack(Material.DIRT));
      manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
      server.getScheduler().performTicks(1);
      player.getInventory().setItem(2, gun);
      manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
      server.getScheduler().performTicks(1);
    } finally {
      PartLoader.get().remove("live");
    }
  }

  @Test
  void registeredItemAppearanceUsesSkinPreserverResult() {
    var model = new ItemStack(Material.BLAZE_ROD);
    var result = gun.clone();
    when(skin.parseModel(SkinState.AIM)).thenReturn(model);
    try (var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class)) {
      skins
          .when(() -> net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.hasSkinData(model))
          .thenReturn(true);
      skins
          .when(
              () ->
                  net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver
                      .applyAppearanceFromSkin(model, gun))
          .thenReturn(result);
      assertSame(result, manager.applyModel(gun, skin, SkinState.AIM));
    }
  }

  @Test
  void plainModelWithoutCustomDataChangesMaterialOnly() {
    gun = spy(gun);
    doNothing().when(gun).setType(any(Material.class));
    when(skin.parseModel(SkinState.CARRY)).thenReturn(new ItemStack(Material.BLAZE_ROD));
    try (var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var data = mockStatic(net.tfminecraft.gunsandgadgets.util.LegacyModelData.class)) {
      assertSame(gun, manager.applyModel(gun, skin, SkinState.CARRY));
      verify(gun).setType(Material.BLAZE_ROD);
    }
  }

  @Test
  void inventoryProtectionOnlyAppliesToReloadingPlayersAndGuns() {
    var e = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
    manager.onInventoryClick(e);
    when(e.getWhoClicked()).thenReturn(player);
    when(e.getCurrentItem()).thenReturn(gun);
    manager.onInventoryClick(e);
    verify(e, never()).setCancelled(true);
    use();
    manager.onInventoryClick(e);
    verify(e).setCancelled(true);
    reset(e);
    when(e.getWhoClicked()).thenReturn(player);
    when(e.getCurrentItem()).thenReturn(new ItemStack(Material.DIRT));
    when(e.getCursor()).thenReturn(new ItemStack(Material.DIRT));
    manager.onInventoryClick(e);
    verify(e, never()).setCancelled(true);
  }

  @Test
  void dropsOutsideReloadAreAllowedAndNullItemsIgnored() {
    var e = mock(org.bukkit.event.player.PlayerDropItemEvent.class);
    when(e.getPlayer()).thenReturn(player);
    manager.onDrop(e);
    verify(e, never()).setCancelled(true);
    use();
    var item = mock(org.bukkit.entity.Item.class);
    when(e.getItemDrop()).thenReturn(item);
    manager.onDrop(e);
    verify(e, never()).setCancelled(true);
    var plain = gun.clone();
    var m = plain.getItemMeta();
    m.getPersistentDataContainer().remove(key("gun_id"));
    plain.setItemMeta(m);
    when(item.getItemStack()).thenReturn(plain);
    manager.onDrop(e);
    verify(e, never()).setCancelled(true);
  }

  @Test
  void untypedItemsDoNotRequireAClass() {
    player.setOp(false);
    var nbt = mock(io.lumine.mythic.lib.api.item.NBTItem.class);
    try (var api = mockStatic(io.lumine.mythic.lib.api.item.NBTItem.class)) {
      api.when(() -> io.lumine.mythic.lib.api.item.NBTItem.get(any(ItemStack.class)))
          .thenReturn(nbt);
      use();
      assertEquals(2, player.getInventory().getItem(1).getAmount());
    }
  }

  @Test
  void classRestrictionAcceptsMatchingClassAndRejectsOthers() {
    player.setOp(false);
    var nbt = mock(io.lumine.mythic.lib.api.item.NBTItem.class);
    when(nbt.hasType()).thenReturn(true);
    var data = mock(net.Indyuce.mmocore.api.player.PlayerData.class, RETURNS_DEEP_STUBS);
    when(data.getProfess().getId()).thenReturn("RANGER");
    var required = new net.Indyuce.mmoitems.stat.data.StringListData(List.of("mage", "ranger"));
    try (var api = mockStatic(io.lumine.mythic.lib.api.item.NBTItem.class);
        var players = mockStatic(net.Indyuce.mmocore.api.player.PlayerData.class);
        var items =
            mockConstruction(
                net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class,
                (mock, context) -> when(mock.getData(any())).thenReturn(required))) {
      api.when(() -> io.lumine.mythic.lib.api.item.NBTItem.get(any(ItemStack.class)))
          .thenReturn(nbt);
      players
          .when(() -> net.Indyuce.mmocore.api.player.PlayerData.get(player.getUniqueId()))
          .thenReturn(data);
      when(data.getProfess().getId()).thenReturn("warrior");
      use();
      assertEquals(5, player.getInventory().getItem(1).getAmount());
      when(data.getProfess().getId()).thenReturn("RANGER");
      use();
      assertEquals(2, player.getInventory().getItem(1).getAmount());
    }
  }

  @Test
  void typedItemWithoutClassRestrictionIsAllowed() {
    player.setOp(false);
    var nbt = mock(io.lumine.mythic.lib.api.item.NBTItem.class);
    when(nbt.hasType()).thenReturn(true);
    try (var api = mockStatic(io.lumine.mythic.lib.api.item.NBTItem.class);
        var items = mockConstruction(net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class)) {
      api.when(() -> io.lumine.mythic.lib.api.item.NBTItem.get(any(ItemStack.class)))
          .thenReturn(nbt);
      use();
      assertEquals(2, player.getInventory().getItem(1).getAmount());
    }
  }

  @Test
  void noMainHandOrOffhandMetadataIsIgnored() {
    var p = mock(org.bukkit.entity.Player.class);
    var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
    when(p.getInventory()).thenReturn(inventory);
    var event = mock(Cancellable.class);
    manager.handleGunUse(p, event);
    verify(event, never()).setCancelled(true);
    when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.DIRT));
    manager.handleGunUse(p, event);
    when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIRT));
    manager.handleGunUse(p, event);
    var metaItem = new ItemStack(Material.STICK);
    var m = metaItem.getItemMeta();
    m.setDisplayName("Ordinary");
    metaItem.setItemMeta(m);
    when(inventory.getItemInOffHand()).thenReturn(metaItem);
    manager.handleGunUse(p, event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void reloadTaskStopsWhenHeldItemLosesMetadata() {
    use();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    server.getScheduler().performTicks(12);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void slotChangeWithDeletedReloadSkinRefundsReservedAmmo() {
    use();
    SkinLoader.clear();
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItem(0), "bullets_loaded"));
    assertEquals(0, readInt(player.getInventory().getItem(0), "reload_amount"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void slotChangeWithDeletedAmmoRefundsOriginalItemsAndClearsReservation() {
    use();
    AmmunitionLoader.clear();
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItem(0), "reload_amount"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void otherWeaponsWithMissingSkinOrAmmoDoNotCrashFiring() {
    for (int slot = 2; slot <= 5; slot++) {
      var other = gun.clone();
      var m = other.getItemMeta();
      m.getPersistentDataContainer().set(key("gun_id"), PersistentDataType.STRING, "other-" + slot);
      m.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, 2);
      m.getPersistentDataContainer().set(key("ammo_loaded"), PersistentDataType.STRING, "shot");
      if (slot == 2) m.getPersistentDataContainer().remove(key("skin_id"));
      else if (slot == 3)
        m.getPersistentDataContainer().set(key("skin_id"), PersistentDataType.STRING, "deleted");
      else if (slot == 4)
        m.getPersistentDataContainer()
            .set(key("ammo_loaded"), PersistentDataType.STRING, "deleted");
      else if (slot == 5) m.getPersistentDataContainer().remove(key("ammo_loaded"));
      other.setItemMeta(m);
      player.getInventory().setItem(slot, other);
    }
    setInt("bullets_loaded", 2);
    set("ammo_loaded", "shot");
    player.getInventory().setItemInMainHand(gun);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      use();
    }
    for (int slot = 2; slot <= 5; slot++)
      assertEquals(
          slot <= 3 ? 0 : 2, readInt(player.getInventory().getItem(slot), "bullets_loaded"));
    assertEquals(9, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void obsoleteTaskCannotCancelANewReload() {
    use();
    server.getScheduler().performTicks(5);
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    gun = player.getInventory().getItemInMainHand().clone();
    set("gun_id", "new-reload");
    player.getInventory().setItemInMainHand(gun);
    use();
    server.getScheduler().performTicks(12);
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(2, player.getInventory().getItem(1).getAmount());
    // Successful completion discards reservation snapshots; a later slot change cannot refund
    // again.
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    assertEquals(2, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void unavailableRefundItemLeavesOtherGunLoaded() {
    var other = gun.clone();
    var meta = other.getItemMeta();
    meta.getPersistentDataContainer().set(key("gun_id"), PersistentDataType.STRING, "other");
    meta.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, 2);
    meta.getPersistentDataContainer().set(key("ammo_loaded"), PersistentDataType.STRING, "shot");
    other.setItemMeta(meta);
    player.getInventory().setItem(2, other);
    ItemAPI api = TLibs.getItemAPI();
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      for (ItemStack invalid : Arrays.asList(null, new ItemStack(Material.AIR))) {
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(invalid);
        setInt("bullets_loaded", 2);
        set("ammo_loaded", "shot");
        player.getInventory().setItemInMainHand(gun);
        use();
        assertEquals(2, readInt(player.getInventory().getItem(2), "bullets_loaded"));
        assertEquals(5, player.getInventory().getItem(1).getAmount());
      }
    }
  }

  @Test
  void cancellationPreservesExactReservedItemsAndDropsOverflowOnlyOnce() {
    var ammo = new ItemStack(Material.IRON_NUGGET, 5);
    var meta = ammo.getItemMeta();
    meta.displayName(net.kyori.adventure.text.Component.text("Special ammunition"));
    ammo.setItemMeta(meta);
    player.getInventory().setItem(1, ammo);
    use();
    for (int slot = 1; slot < player.getInventory().getSize(); slot++)
      player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
    SkinLoader.clear();
    AmmunitionLoader.clear();
    var change = new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2);
    manager.onSlotChange(change);
    manager.onSlotChange(change);
    server.getScheduler().performTicks(12);
    var drops = player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class);
    assertEquals(1, drops.size());
    ItemStack refund = drops.iterator().next().getItemStack();
    assertEquals(3, refund.getAmount());
    assertTrue(ammo.isSimilar(refund));
    assertEquals(0, readInt(player.getInventory().getItem(0), "reload_amount"));
  }

  @Test
  void nonpositiveCapacityNeverCreatesOrConsumesAmmunition() {
    for (int capacity : new int[] {-2, 0}) {
      setInt("stat_value_capacity", capacity);
      player.getInventory().setItemInMainHand(gun);
      use();
      assertEquals(5, player.getInventory().getItem(1).getAmount());
      assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
      verify(skin, never()).parseModel(SkinState.RELOAD);
    }
  }

  @Test
  void smallMovementCanEitherDelayOrAdvanceReload() throws Exception {
    var method =
        GunManager.class.getDeclaredMethod(
            "shouldDelayReload", double.class, java.util.function.DoubleSupplier.class);
    method.setAccessible(true);
    assertEquals(
        false,
        method.invoke(
            null,
            .0025,
            (java.util.function.DoubleSupplier)
                () -> {
                  fail("Stationary reload must not sample randomness");
                  return 0;
                }));
    assertEquals(true, method.invoke(null, .01, (java.util.function.DoubleSupplier) () -> .1));
    assertEquals(false, method.invoke(null, .01, (java.util.function.DoubleSupplier) () -> .9));
  }

  @Test
  void reloadWithMissingPendingAmmoDoesNotWriteNullPdcValue() {
    use();
    var held = player.getInventory().getItemInMainHand();
    var meta = held.getItemMeta();
    meta.getPersistentDataContainer().remove(key("reload_ammo"));
    held.setItemMeta(meta);
    player.getInventory().setItemInMainHand(held);
    server.getScheduler().performTicks(12);
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertFalse(
        player
            .getInventory()
            .getItemInMainHand()
            .getItemMeta()
            .getPersistentDataContainer()
            .has(key("ammo_loaded")));
  }

  @Test
  void crossbowReloadKeepsConsumedAmmoWhenDefinitionDisappearsBeforeCompletion() {
    gun.setType(Material.CROSSBOW);
    player.getInventory().setItemInMainHand(gun);
    use();
    AmmunitionLoader.clear();
    server.getScheduler().performTicks(12);
    var loaded = player.getInventory().getItemInMainHand();
    assertEquals(3, readInt(loaded, "bullets_loaded"));
    assertEquals(
        "shot",
        loaded
            .getItemMeta()
            .getPersistentDataContainer()
            .get(key("ammo_loaded"), PersistentDataType.STRING));
    assertEquals(
        1,
        ((org.bukkit.inventory.meta.CrossbowMeta) loaded.getItemMeta())
            .getChargedProjectiles()
            .size());
    use();
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 0, 2));
    assertEquals(2, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void crossbowWithUnknownLoadedAmmoPreservesLoadedState() {
    gun.setType(Material.CROSSBOW);
    setInt("bullets_loaded", 2);
    set("ammo_loaded", "removed");
    player.getInventory().setItemInMainHand(gun);
    use();
    assertEquals(2, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(
        0,
        ((org.bukkit.inventory.meta.CrossbowMeta)
                player.getInventory().getItemInMainHand().getItemMeta())
            .getChargedProjectiles()
            .size());
  }

  @Test
  void missingMetadataOnMainHandIsIgnored() {
    var p = mock(org.bukkit.entity.Player.class);
    var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
    when(p.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
    var event = mock(Cancellable.class);
    manager.handleGunUse(p, event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void unloadingIgnoresAirAndInvalidNegativeMagazineCounts() throws Exception {
    var p = mock(org.bukkit.entity.Player.class);
    var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
    when(p.getInventory()).thenReturn(inventory);
    var other = gun.clone();
    var meta = other.getItemMeta();
    meta.getPersistentDataContainer().set(key("gun_id"), PersistentDataType.STRING, "other");
    meta.getPersistentDataContainer().set(key("bullets_loaded"), PersistentDataType.INTEGER, -1);
    meta.getPersistentDataContainer().set(key("ammo_loaded"), PersistentDataType.STRING, "shot");
    other.setItemMeta(meta);
    when(inventory.getContents()).thenReturn(new ItemStack[] {new ItemStack(Material.AIR), other});
    var method =
        GunManager.class.getDeclaredMethod(
            "unloadSame", org.bukkit.entity.Player.class, ItemStack.class);
    method.setAccessible(true);
    method.invoke(manager, p, gun);
    verify(inventory, never()).addItem(any(ItemStack.class));
    assertEquals(-1, readInt(other, "bullets_loaded"));
  }

  @Test
  void plainModelCanChangeAirTargetWithoutMetadata() {
    var air = new ItemStack(Material.AIR);
    when(skin.parseModel(SkinState.CARRY)).thenReturn(new ItemStack(Material.STICK));
    try (var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class)) {
      assertSame(air, manager.applyModel(air, skin, SkinState.CARRY));
      assertEquals(Material.STICK, air.getType());
    }
  }

  @Test
  void cancelledReloadHandlesMissingOldSlotMetadataSkinAndReservation() {
    for (int scenario = 0; scenario < 5; scenario++) {
      manager = new GunManager();
      player.getInventory().setItemInMainHand(gun);
      player.getInventory().setItem(1, new ItemStack(Material.IRON_NUGGET, 5));
      use();
      var p = mock(org.bukkit.entity.Player.class);
      when(p.getUniqueId()).thenReturn(player.getUniqueId());
      var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
      when(p.getInventory()).thenReturn(inventory);
      ItemStack previous =
          scenario == 0 ? null : scenario == 1 ? mock(ItemStack.class) : gun.clone();
      if (scenario >= 2) {
        var meta = previous.getItemMeta();
        if (scenario == 2) meta.getPersistentDataContainer().remove(key("skin_id"));
        if (scenario == 3)
          meta.getPersistentDataContainer()
              .set(key("reload_ammo"), PersistentDataType.STRING, "shot");
        if (scenario == 4)
          meta.getPersistentDataContainer()
              .set(key("reload_amount"), PersistentDataType.INTEGER, 2);
        previous.setItemMeta(meta);
      }
      when(inventory.getItem(0)).thenReturn(previous);
      when(inventory.getItem(2)).thenReturn(mock(ItemStack.class));
      manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(p, 0, 2));
      server.getScheduler().performTicks(1);
      verify(inventory).addItem(new ItemStack(Material.IRON_NUGGET, 3));
      server.getScheduler().cancelTasks(plugin);
    }
  }

  @Test
  void slotSelectionKeepsHealthyGunAndInventoryProtectionIgnoresMetadataFreeItems() {
    manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(player, 2, 0));
    server.getScheduler().performTicks(1);
    assertFalse(
        net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
            player.getInventory().getItemInMainHand()));
    use();
    var e = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(player);
    when(e.getCurrentItem()).thenReturn(mock(ItemStack.class));
    when(e.getCursor()).thenReturn(mock(ItemStack.class));
    manager.onInventoryClick(e);
    verify(e, never()).setCancelled(true);
    when(e.getCurrentItem()).thenReturn(null);
    manager.onInventoryClick(e);
    verify(e, never()).setCancelled(true);
  }

  // Three calibers told apart by item type: iron nuggets, gold nuggets (steel), copper (bronze).
  void threeCalibers() {
    for (String[] entry :
        new String[][] {{"iron", "iron_nugget"}, {"steel", "gold_nugget"}, {"bronze", "copper_ingot"}}) {
      var config = new YamlConfiguration();
      config.set("input", "v." + entry[1]);
      AmmunitionLoader.get().put(entry[0], new Ammunition(entry[0], config));
    }
    var checker = TLibs.getItemAPI().getChecker();
    var creator = TLibs.getItemAPI().getCreator();
    doAnswer(
            i ->
                ((ItemStack) i.getArgument(0))
                    .getType()
                    .getKey()
                    .getKey()
                    .equals(((String) i.getArgument(1)).substring(2)))
        .when(checker)
        .checkItemWithPath(any(), anyString());
    doAnswer(i -> new ItemStack(Material.matchMaterial(((String) i.getArgument(0)).substring(2))))
        .when(creator)
        .getItemFromPath(anyString());
    set("calibers", "iron;steel;bronze");
    player.getInventory().setItemInMainHand(gun);
  }

  String actionBar() {
    var bar = player.nextActionBar();
    return bar == null
        ? null
        : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(bar);
  }

  String selected() {
    return player
        .getInventory()
        .getItemInMainHand()
        .getItemMeta()
        .getPersistentDataContainer()
        .get(key("ammo_selected"), PersistentDataType.STRING);
  }

  @Test
  void crouchClickCyclesCarriedCalibersWithoutFiringOrReloading() {
    threeCalibers();
    setInt("bullets_loaded", 1);
    set("ammo_loaded", "iron");
    player.getInventory().setItemInMainHand(gun);
    player.getInventory().setItem(2, new ItemStack(Material.COPPER_INGOT, 4));
    player.setSneaking(true);
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      verify(use()).setCancelled(true);
      // Iron loads by default, steel is not carried, so the first pick is bronze.
      assertEquals("bronze", selected());
      assertTrue(actionBar().matches("Next load: .*Copper.* \\(4 carried\\)"));
      // Further events of the same click, even a tick or two later, must not switch again.
      use();
      server.getScheduler().performTicks(2);
      use();
      assertEquals("bronze", selected());
      assertNull(player.nextActionBar());
      server.getScheduler().performTicks(1);
      use();
      assertEquals("iron", selected());
      assertTrue(actionBar().contains("(5 carried)"));
      shooter.verifyNoInteractions();
    }
    assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(4, player.getInventory().getItem(2).getAmount());
  }

  @Test
  void crouchClickWithoutUsableAmmoSaysSo() {
    threeCalibers();
    player.getInventory().setItem(1, null);
    player.setSneaking(true);
    use();
    assertEquals("You carry no shot this weapon can fire.", actionBar());
    assertNull(selected());
  }

  @Test
  void reloadUsesSelectedCaliber() {
    threeCalibers();
    set("ammo_selected", "steel");
    player.getInventory().setItemInMainHand(gun);
    player.getInventory().setItem(2, new ItemStack(Material.GOLD_NUGGET, 2));
    use();
    assertTrue(actionBar().matches("Loading .*Gold.*"));
    server.getScheduler().performTicks(12);
    ItemStack held = player.getInventory().getItemInMainHand();
    assertEquals(2, readInt(held, "bullets_loaded"));
    assertEquals(
        "steel",
        held.getItemMeta().getPersistentDataContainer().get(key("ammo_loaded"), PersistentDataType.STRING));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals("steel", selected());
  }

  @Test
  void reloadRefusesOtherAmmoWhenSelectedCaliberRunsOut() {
    threeCalibers();
    set("ammo_selected", "steel");
    player.getInventory().setItemInMainHand(gun);
    use();
    assertTrue(
        actionBar().matches("You have no .*Gold.* left\\. Crouch and right-click to choose another\\."));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    // The refusal must not leave the player stuck in a reload.
    player.getInventory().setItem(2, new ItemStack(Material.GOLD_NUGGET, 1));
    use();
    assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
  }

  @Test
  void selectionThatIsNoLongerACaliberFallsBackToFirstCarried() {
    threeCalibers();
    set("ammo_selected", "mythril");
    player.getInventory().setItemInMainHand(gun);
    use();
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    assertEquals(
        "iron",
        player
            .getInventory()
            .getItemInMainHand()
            .getItemMeta()
            .getPersistentDataContainer()
            .get(key("reload_ammo"), PersistentDataType.STRING));
  }

  @Test
  void ammoMessagesHoldMmoCoreBarAndYieldToBusierOnes() {
    threeCalibers();
    player.setSneaking(true);
    use();
    verify(mmoPlayer.getActionBar()).show(30, 40L, (String) null);
    assertNotNull(player.nextActionBar());
    when(mmoPlayer.getActionBar().show(anyInt(), anyLong(), nullable(String.class))).thenReturn(false);
    server.getScheduler().performTicks(3);
    use();
    assertEquals("iron", selected());
    assertNull(player.nextActionBar());
  }

  @Test
  void singleCaliberReloadShowsNoAmmoName() {
    use();
    assertNull(player.nextActionBar());
  }
}

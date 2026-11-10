package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.data.*;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.manager.GunRefreshListener;
import net.tfminecraft.gunsandgadgets.util.CostFormatter;
import net.tfminecraft.gunsandgadgets.utils.*;
import net.tfminecraft.tlibs.TLibs;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Old guns taken apart for their materials. */
class GunRefundTest extends GunsTestSupport {

  @AfterEach
  void resetRefunds() {
    Cache.refundsEnabled = false;
    Cache.refundsAutomatic = false;
  }

  ItemStack gun(String name, Map<String, Integer> inputs) {
    return gun(name, inputs, "barrel");
  }

  ItemStack gun(String name, Map<String, Integer> inputs, String partId) {
    ItemStack gun = new ItemStack(Material.STICK, 2);
    var meta = gun.getItemMeta();
    if (name != null) meta.setDisplayName(name);
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "gun_id"), PersistentDataType.STRING, "old-gun");
    gun.setItemMeta(meta);
    new GunCraftProvenance(List.of(new GGCraftPart(partId, 1)), 1).applyTo(gun);
    if (inputs != null) GunCraftInputs.applyTo(gun, inputs);
    return gun;
  }

  GunPart part(boolean retired, Map<String, Integer> cost) {
    GunPart part = mock(GunPart.class);
    when(part.getId()).thenReturn("barrel");
    when(part.isDisabled()).thenReturn(retired);
    when(part.isRetired()).thenReturn(retired);
    when(part.getCost()).thenReturn(cost);
    PartLoader.get().put("barrel", part);
    return part;
  }

  void healthyParts() throws Exception {
    new PartLoader().load(file("parts.yml", "other: {stats: ['damage 1']}").toFile());
  }

  @Test
  void oldGunsArePricedAndThoseWithoutAPriceGiveNothing() throws Exception {
    ItemStack old = gun("Old", Map.of("v.iron_ingot", 3, "v.flint", 0));
    part(true, Map.of("v.iron_ingot", 4));
    assertNull(GunRefunder.refundFor(old)); // refunds off
    Cache.refundsEnabled = true;
    assertNull(GunRefunder.refundFor(new ItemStack(Material.STICK)));
    assertFalse(GunRefunder.isOld(null));
    assertEquals(Map.of("v.iron_ingot", 6), GunRefunder.refundFor(old)); // recorded inputs, two guns
    ItemStack odd = gun("Odd", null);
    var meta = odd.getItemMeta();
    meta.getPersistentDataContainer()
        .set(GGCraftKeys.craftInputs(), PersistentDataType.STRING, "{\"v.iron_ingot\": null, \"v.flint\": 2}");
    odd.setItemMeta(meta);
    assertEquals(Map.of("v.flint", 4), GunRefunder.refundFor(odd));
    part(false, Map.of("v.iron_ingot", 4));
    assertFalse(GunRefunder.isOld(old));
    assertNull(GunRefunder.refundFor(old));
    // a part only disabled in parts.yml (no new crafts) leaves its guns alone
    when(PartLoader.getByString("barrel").isDisabled()).thenReturn(true);
    assertFalse(GunRefunder.isOld(old));
    // a removed part still refunds what the crafting took, once the part files loaded cleanly
    PartLoader.clear();
    assertFalse(GunRefunder.isOld(old)); // no parts loaded at all
    healthyParts();
    assertTrue(GunRefunder.isOld(old));
    assertEquals(Map.of("v.iron_ingot", 6), GunRefunder.refundFor(old));
    // a parts file that failed to read pauses it
    new PartLoader().load(file("broken.yml", "x: [").toFile());
    assertFalse(GunRefunder.isOld(old));
    healthyParts();
    // a forged part never counts as removed (a material taken out of metals.yml by mistake)
    assertFalse(GunRefunder.isOld(gun("Forged", Map.of(), "barrel@steel")));
    // crafted before inputs were recorded: the parts' costs, nothing when a part is gone
    ItemStack older = gun("Older", null);
    assertEquals(Map.of(), GunRefunder.refundFor(older));
    part(true, Map.of("v.iron_ingot", 4));
    assertEquals(Map.of("v.iron_ingot", 8), GunRefunder.refundFor(older));
    // crafted for free: nothing back
    assertEquals(Map.of(), GunRefunder.refundFor(gun("Free", Map.of())));
    // from before guns recorded their parts: old, nothing back
    ItemStack unstamped = new ItemStack(Material.STICK);
    var meta2 = unstamped.getItemMeta();
    meta2.getPersistentDataContainer().set(new NamespacedKey(plugin, "gun_id"), PersistentDataType.STRING, "ancient");
    unstamped.setItemMeta(meta2);
    assertTrue(GunRefunder.isOld(unstamped));
    assertEquals(Map.of(), GunRefunder.refundFor(unstamped));
  }

  @Test
  void takingApartRemovesTheGunAndGivesTheMaterials() {
    Cache.refundsEnabled = true;
    part(true, new HashMap<>());
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    World world = mock(World.class);
    when(player.getInventory()).thenReturn(inventory);
    when(player.getWorld()).thenReturn(world);
    when(player.getName()).thenReturn("Hunter");
    ItemStack over = new ItemStack(Material.IRON_INGOT, 6);
    when(inventory.addItem(any(ItemStack[].class)))
        .thenReturn(new HashMap<>(), new HashMap<>(Map.of(0, over)));
    Runnable remove = mock(Runnable.class);
    var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
    try (var libs = mockStatic(TLibs.class);
        var costs = mockStatic(CostFormatter.class)) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      costs.when(() -> CostFormatter.getCostsFormatted(any())).thenReturn(List.of("- Iron 70"));
      // not old: nothing happens
      assertFalse(GunRefunder.takeApart(player, new ItemStack(Material.STICK), remove));
      // a material that can't be made keeps the gun
      ItemStack old = gun("§7Old Musket", Map.of("v.iron_ingot", 35));
      when(api.getCreator().getItemFromPath("v.iron_ingot")).thenReturn(null);
      assertFalse(GunRefunder.takeApart(player, old, remove));
      when(api.getCreator().getItemFromPath("v.iron_ingot")).thenReturn(new ItemStack(Material.AIR));
      assertFalse(GunRefunder.takeApart(player, old, remove));
      verifyNoInteractions(remove);
      // 70 ingots: 64 + 6, the 6 don't fit and drop at the player's feet
      when(api.getCreator().getItemFromPath("v.iron_ingot")).thenReturn(new ItemStack(Material.IRON_INGOT));
      assertTrue(GunRefunder.takeApart(player, old, remove));
      verify(remove).run();
      verify(inventory, times(2)).addItem(any(ItemStack[].class));
      verify(world).dropItemNaturally(any(), eq(over));
      verify(player).sendMessage("§eYour old §7Old Musket§e was taken apart for its materials:");
      verify(player).sendMessage("- Iron 70");
      // a free gun is just removed
      assertTrue(GunRefunder.takeApart(player, gun(null, Map.of()), remove));
      verify(player).sendMessage("§eYour old gun§e was retired and removed.");
      verify(player, never()).sendMessage("§7Its loaded shots were returned too.");
      // loaded shots come back with it: three in each of the two guns
      ItemStack loaded = gun("Loaded", Map.of());
      assertNull(GunRefunder.loadedShots(loaded)); // empty
      var meta = loaded.getItemMeta();
      meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bullets_loaded"), PersistentDataType.INTEGER, 3);
      meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ammo_loaded"), PersistentDataType.STRING, "ball");
      loaded.setItemMeta(meta);
      assertNull(GunRefunder.loadedShots(loaded)); // unknown shot
      var ball = mock(net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition.class);
      when(ball.getInput()).thenReturn("m.utils.ball");
      net.tfminecraft.gunsandgadgets.loader.AmmunitionLoader.get().put("ball", ball);
      when(api.getCreator().getItemFromPath("m.utils.ball")).thenReturn(null);
      assertNull(GunRefunder.loadedShots(loaded));
      when(api.getCreator().getItemFromPath("m.utils.ball")).thenReturn(new ItemStack(Material.AIR));
      assertNull(GunRefunder.loadedShots(loaded));
      when(api.getCreator().getItemFromPath("m.utils.ball")).thenReturn(new ItemStack(Material.IRON_NUGGET));
      assertEquals(6, GunRefunder.loadedShots(loaded).getAmount());
      assertTrue(GunRefunder.takeApart(player, loaded, remove));
      verify(player).sendMessage("§7Its loaded shots were returned too.");
    }
  }

  @Test
  void automaticRefundsTakeOldGunsInsteadOfRefreshingThem() {
    var player = server.addPlayer();
    ItemStack old = new ItemStack(Material.STICK);
    player.getInventory().setItem(2, old);
    try (var refresh = mockStatic(GunStatRefresher.class);
        var refunder = mockStatic(GunRefunder.class)) {
      refresh.when(() -> GunStatRefresher.isGun(old)).thenReturn(true);
      refresh.when(() -> GunStatRefresher.isManaged(old)).thenReturn(true);
      refresh
          .when(() -> GunStatRefresher.refreshIfOutdated(old))
          .thenReturn(GunStatRefresher.RefreshResult.unchanged());
      refunder
          .when(() -> GunRefunder.takeApart(eq(player), eq(old), any()))
          .thenAnswer(
              call -> {
                call.<Runnable>getArgument(2).run();
                return true;
              });
      // automatic off: only the refresh runs
      new GunRefreshListener().onHotbarSelect(new PlayerItemHeldEvent(player, 0, 2));
      server.getScheduler().performOneTick();
      assertEquals(old, player.getInventory().getItem(2));
      Cache.refundsAutomatic = true;
      new GunRefreshListener().onHotbarSelect(new PlayerItemHeldEvent(player, 0, 2));
      server.getScheduler().performOneTick();
      assertNull(player.getInventory().getItem(2));
      refresh.verify(() -> GunStatRefresher.refreshIfOutdated(old), times(1));
      // a gun that isn't old is refreshed as before
      ItemStack current = new ItemStack(Material.BLAZE_ROD);
      player.getInventory().setItem(3, current);
      refresh.when(() -> GunStatRefresher.isGun(current)).thenReturn(true);
      refresh.when(() -> GunStatRefresher.isManaged(current)).thenReturn(true);
      refresh
          .when(() -> GunStatRefresher.refreshIfOutdated(current))
          .thenReturn(GunStatRefresher.RefreshResult.unchanged());
      new GunRefreshListener().onHotbarSelect(new PlayerItemHeldEvent(player, 2, 3));
      server.getScheduler().performOneTick();
      refresh.verify(() -> GunStatRefresher.refreshIfOutdated(current));
      // storage opened by something that isn't a player is only refreshed
      Inventory chest = mock(Inventory.class);
      when(chest.getHolder(false)).thenReturn(mock(BlockInventoryHolder.class));
      when(chest.getContents()).thenReturn(new ItemStack[] {old});
      InventoryOpenEvent open = mock(InventoryOpenEvent.class);
      when(open.getInventory()).thenReturn(chest);
      when(open.getPlayer()).thenReturn(mock(HumanEntity.class));
      new GunRefreshListener().onInventoryOpen(open);
      server.getScheduler().performOneTick();
      verify(chest, never()).setItem(anyInt(), any());
      // opened by a player: still only refreshed, it may not be theirs
      when(open.getPlayer()).thenReturn(player);
      new GunRefreshListener().onInventoryOpen(open);
      server.getScheduler().performOneTick();
      verify(chest, never()).setItem(anyInt(), any());
      // clicking it in the chest neither; in the player's own inventory it is taken apart
      var click = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
      when(click.getWhoClicked()).thenReturn(player);
      when(click.getClickedInventory()).thenReturn(chest);
      when(click.getSlot()).thenReturn(0);
      when(chest.getItem(0)).thenReturn(old);
      new GunRefreshListener().onInventoryClick(click);
      server.getScheduler().performOneTick();
      verify(chest, never()).setItem(anyInt(), any());
      player.getInventory().setItem(5, old);
      when(click.getClickedInventory()).thenReturn(player.getInventory());
      when(click.getSlot()).thenReturn(5);
      new GunRefreshListener().onInventoryClick(click);
      server.getScheduler().performOneTick();
      assertNull(player.getInventory().getItem(5));
    }
  }
}

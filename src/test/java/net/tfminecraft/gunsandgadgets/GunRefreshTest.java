package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.data.*;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.manager.GunRefreshListener;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.utils.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class GunRefreshTest extends GunsTestSupport {
  ItemStack gun(String type, String id, boolean provenance) {
    ItemStack gun = new ItemStack(Material.STICK, 2);
    var meta = gun.getItemMeta();
    meta.setDisplayName("Crafted gun");
    if (type != null)
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "gun_type"), PersistentDataType.STRING, type);
    if (id != null)
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "gun_id"), PersistentDataType.STRING, id);
    gun.setItemMeta(meta);
    if (provenance) new GunCraftProvenance(List.of(new GGCraftPart("barrel", 1)), 1).applyTo(gun);
    return gun;
  }

  GunPart livePart(int revision) {
    GunPart part = mock(GunPart.class);
    when(part.getId()).thenReturn("barrel");
    when(part.getRevision()).thenReturn(revision);
    PartLoader.get().put("barrel", part);
    return part;
  }

  @Test
  void refreshAndManagedChecksRejectUnmanagedAirMissingMetadataAndBrokenGuns() {
    assertFalse(GunStatRefresher.refresh(null).isChanged());
    assertFalse(GunStatRefresher.refresh(new ItemStack(Material.AIR)).isChanged());
    assertFalse(GunStatRefresher.refresh(gun("RIFLE", "id", false)).isChanged());
    assertFalse(GunStatRefresher.isManaged(null));
    assertFalse(GunStatRefresher.isManaged(new ItemStack(Material.AIR)));
    assertFalse(GunStatRefresher.isManaged(new ItemStack(Material.STICK)));
    ItemStack metadataFree = mock(ItemStack.class);
    when(metadataFree.getType()).thenReturn(Material.STICK);
    when(metadataFree.hasItemMeta()).thenReturn(false);
    assertFalse(GunStatRefresher.isManaged(metadataFree));
    assertFalse(GunBrokenMarker.isBroken(metadataFree));
    assertFalse(GunStatRefresher.isManaged(gun("RIFLE", null, true)));
    assertFalse(GunStatRefresher.isManaged(gun("RIFLE", "id", false)));
    ItemStack managed = gun("RIFLE", "id", true);
    assertTrue(GunStatRefresher.isManaged(managed));
    GunBrokenMarker.markBroken(managed, List.of("barrel"));
    assertFalse(GunStatRefresher.refresh(managed, true).isChanged());
    assertFalse(GunStatRefresher.refreshIfOutdated(null).isChanged());
    assertFalse(GunStatRefresher.refreshIfOutdated(new ItemStack(Material.AIR)).isChanged());
    assertFalse(GunStatRefresher.refreshIfOutdated(new ItemStack(Material.STICK)).isChanged());
  }

  @Test
  void currentRevisionsDoNotRebuildUnlessForcedAndMissingPartsReportError() {
    ItemStack managed = gun("RIFLE", "id", true);
    livePart(1);
    assertFalse(GunStatRefresher.refresh(managed).isChanged());
    assertFalse(GunStatRefresher.refreshIfOutdated(managed).isChanged());
    PartLoader.clear();
    var result = GunStatRefresher.refresh(managed, true);
    assertFalse(result.isChanged());
    assertEquals("missing parts: barrel", result.getError());
    assertNull(result.getItem());
    assertTrue(result.getOutdatedParts().isEmpty());
  }

  @Test
  void forcedRefreshRejectsMissingBlankAndInvalidGunTypes() {
    livePart(2);
    for (String type : new String[] {null, " ", "not-a-type"}) {
      assertEquals(
          "invalid or missing gun_type",
          GunStatRefresher.refresh(gun(type, "id", true), true).getError());
    }
  }

  @Test
  void outdatedRefreshRebuildsSynchronizesProvenanceAndPreservesStackCount() {
    GunPart live = livePart(2);
    ItemStack managed = gun("RIFLE", "id", true);
    ItemStack rebuilt = gun("RIFLE", "id", false);
    Cache.statRefreshDebug = true;
    try (var inventories =
        mockConstruction(
            InventoryManager.class,
            (manager, context) ->
                when(manager.rebuildFromParts(any(), any(), any()))
                    .thenAnswer(invocation -> rebuilt.clone()))) {
      var result = GunStatRefresher.refreshIfOutdated(managed);
      assertTrue(result.isChanged());
      assertNull(result.getError());
      assertEquals(2, result.getItem().getAmount());
      assertEquals(1, result.getOutdatedParts().size());
      assertEquals("barrel", result.getOutdatedParts().getFirst().getId());
      assertEquals(2, GunCraftProvenance.readFrom(result.getItem()).getPartsRevision());
      verify(inventories.constructed().getFirst())
          .rebuildFromParts(managed, GunType.RIFLE, List.of(live));
      assertTrue(GunStatRefresher.refresh(gun("RIFLE", null, true), true).isChanged());
      Cache.statRefreshDebug = false;
      assertTrue(GunStatRefresher.refresh(managed, true).isChanged());
    }
    assertTrue(GunStatRefresher.RefreshResult.updated(rebuilt, null).getOutdatedParts().isEmpty());
  }

  @Test
  void brokenMarkersReplacePriorMissingPartsLoreAndClearPersistentFlag() {
    assertFalse(GunBrokenMarker.isBroken(null));
    assertFalse(GunBrokenMarker.isBroken(new ItemStack(Material.STICK)));
    GunBrokenMarker.markBroken(null, List.of("x"));
    ItemStack gun = gun("RIFLE", "id", true);
    GunBrokenMarker.markBroken(gun, null);
    GunBrokenMarker.markBroken(gun, List.of());
    GunBrokenMarker.markBroken(new ItemStack(Material.AIR), List.of("x"));
    assertFalse(GunBrokenMarker.isBroken(gun));
    GunBrokenMarker.markBroken(gun, List.of("barrel"));
    assertTrue(GunBrokenMarker.isBroken(gun));
    assertEquals("§c§lBROKEN", gun.getItemMeta().getDisplayName());
    var meta = gun.getItemMeta();
    meta.setLore(List.of("Custom lore", "§7Missing parts: previous"));
    gun.setItemMeta(meta);
    GunBrokenMarker.markBroken(gun, List.of("barrel", "stock"));
    assertEquals(
        List.of("Custom lore", "§7Missing parts: barrel, stock"), gun.getItemMeta().getLore());
    GunBrokenMarker.clearBroken(null);
    GunBrokenMarker.clearBroken(new ItemStack(Material.STICK));
    GunBrokenMarker.clearBroken(gun);
    assertFalse(GunBrokenMarker.isBroken(gun));
    meta = gun.getItemMeta();
    meta.getPersistentDataContainer().set(GGCraftKeys.broken(), GGCraftKeys.BOOLEAN, false);
    gun.setItemMeta(meta);
    assertFalse(GunBrokenMarker.isBroken(gun));
  }

  @Test
  void brokenWarningsAreLimitedPerGunUntilSessionCleared() {
    Player player = mock(Player.class);
    when(player.getName()).thenReturn("Hunter");
    String id = UUID.randomUUID().toString();
    GunBrokenMarker.notifyBroken(player, null, id, null);
    GunBrokenMarker.notifyBroken(player, null, id, List.of());
    GunBrokenMarker.notifyBroken(null, null, null, List.of("barrel"));
    GunBrokenMarker.notifyBroken(player, null, null, List.of("barrel"));
    GunBrokenMarker.notifyBroken(player, null, id, List.of("barrel"));
    GunBrokenMarker.notifyBroken(player, null, id, List.of("barrel"));
    verify(player).sendMessage(contains("weapon is broken"));
    GunBrokenMarker.clearWarningSession(null);
    GunBrokenMarker.clearWarningSession(id);
    GunBrokenMarker.notifyBroken(player, null, id, List.of("barrel"));
    verify(player, times(2)).sendMessage(contains("weapon is broken"));
    GunBrokenMarker.clearWarningSession(id);
  }

  @Test
  void droppedItemRefreshRunsAfterEventAndOnlyWritesChangedManagedItems() {
    GunRefreshListener listener = new GunRefreshListener();
    Item dropped = mock(Item.class);
    PlayerDropItemEvent event = new PlayerDropItemEvent(server.addPlayer(), dropped);
    ItemStack original = gun("RIFLE", "id", true), rebuilt = new ItemStack(Material.BLAZE_ROD);
    when(dropped.getItemStack()).thenReturn(original);
    try (var refresh = mockStatic(GunStatRefresher.class)) {
      refresh.when(() -> GunStatRefresher.isManaged(original)).thenReturn(false, true, true);
      refresh
          .when(() -> GunStatRefresher.refreshIfOutdated(original))
          .thenReturn(
              GunStatRefresher.RefreshResult.unchanged(),
              GunStatRefresher.RefreshResult.updated(rebuilt, List.of()));
      for (int index = 0; index < 3; index++) {
        listener.onDrop(event);
        server.getScheduler().performOneTick();
      }
      verify(dropped).setItemStack(rebuilt);
    }
  }

  @Test
  void inventoryRefreshUsesPostEventSlotAndCursorAndHandlesOutsideClicks() {
    GunRefreshListener listener = new GunRefreshListener();
    InventoryClickEvent nonplayer = mock(InventoryClickEvent.class);
    when(nonplayer.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    listener.onInventoryClick(nonplayer);
    var player = server.addPlayer();
    Inventory inventory = mock(Inventory.class);
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getClickedInventory()).thenReturn(inventory);
    when(event.getSlot()).thenReturn(3);
    ItemStack original = gun("RIFLE", "id", true), rebuilt = new ItemStack(Material.BLAZE_ROD);
    when(inventory.getItem(3))
        .thenReturn(
            null, new ItemStack(Material.AIR), new ItemStack(Material.STICK), original, original);
    try (var refresh = mockStatic(GunStatRefresher.class)) {
      refresh.when(() -> GunStatRefresher.isManaged(original)).thenReturn(true);
      refresh
          .when(() -> GunStatRefresher.refreshIfOutdated(original))
          .thenReturn(
              GunStatRefresher.RefreshResult.unchanged(),
              GunStatRefresher.RefreshResult.updated(rebuilt, List.of()));
      for (int index = 0; index < 5; index++) {
        listener.onInventoryClick(event);
        server.getScheduler().performOneTick();
      }
      verify(inventory).setItem(3, rebuilt);
      when(event.getClickedInventory()).thenReturn(null);
      player.setItemOnCursor(original);
      listener.onInventoryClick(event);
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, player.getItemOnCursor());
    }
  }

  @Test
  void hotbarRefreshWritesTheNewSlotAfterScheduledTask() {
    var player = server.addPlayer();
    ItemStack original = gun("RIFLE", "id", true), rebuilt = new ItemStack(Material.BLAZE_ROD);
    player.getInventory().setItem(2, original);
    try (var refresh = mockStatic(GunStatRefresher.class)) {
      refresh.when(() -> GunStatRefresher.isManaged(original)).thenReturn(true);
      refresh
          .when(() -> GunStatRefresher.refreshIfOutdated(original))
          .thenReturn(GunStatRefresher.RefreshResult.updated(rebuilt, List.of()));
      new GunRefreshListener().onHotbarSelect(new PlayerItemHeldEvent(player, 0, 2));
      assertEquals(original, player.getInventory().getItem(2));
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, player.getInventory().getItem(2));
    }
  }

  @Test
  void failedRebuildKeepsOriginalItemAndStampedRevisionsAndDoesNotReplaceDroppedGun() {
    livePart(2);
    ItemStack original = gun("RIFLE", "preserved-id", true);
    ItemStack baseline = original.clone();
    Item dropped = mock(Item.class);
    when(dropped.getItemStack()).thenReturn(original);
    PlayerDropItemEvent event = new PlayerDropItemEvent(server.addPlayer(), dropped);
    for (ItemStack invalid :
        java.util.Arrays.asList(
            null, new ItemStack(Material.AIR), new ItemStack(Material.BARRIER))) {
      try (var inventories =
          mockConstruction(
              InventoryManager.class,
              (manager, context) ->
                  when(manager.rebuildFromParts(any(), any(), any())).thenReturn(invalid))) {
        var result = GunStatRefresher.refresh(original, true);
        assertFalse(result.isChanged());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("rebuild"));
        assertNull(result.getItem());
        assertEquals(baseline, original);
        assertEquals(1, GunCraftProvenance.readFrom(original).getPartsRevision());
        assertEquals(1, GunCraftProvenance.readFrom(original).getParts().getFirst().getRevision());
        new GunRefreshListener().onDrop(event);
        server.getScheduler().performOneTick();
        verify(dropped, never()).setItemStack(any());
        assertEquals(baseline, original);
      }
    }
  }
}

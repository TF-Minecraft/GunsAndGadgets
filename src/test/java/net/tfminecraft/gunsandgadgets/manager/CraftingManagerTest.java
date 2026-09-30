package net.tfminecraft.gunsandgadgets.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.manager.inventory.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class CraftingManagerTest {
  CraftingManager manager;
  InventoryManager assembly;
  Player player;
  PlayerInventory inventory;
  InventoryClickEvent click;
  GunPart part;
  ItemStack material, crafted;
  MockedStatic<TLibs> libs;
  MockedStatic<GunsAndGadgets> plugins;
  boolean oldRequire;
  int oldSlot;

  @BeforeEach
  void setup() throws Exception {
    MockBukkit.mock();
    oldRequire = Cache.requireInput;
    oldSlot = Cache.outputSlot;
    Cache.requireInput = true;
    Cache.outputSlot = 8;
    manager = new CraftingManager();
    assembly = mock(InventoryManager.class);
    var field = CraftingManager.class.getDeclaredField("inv");
    field.setAccessible(true);
    field.set(manager, assembly);
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    inventory = mock(PlayerInventory.class);
    when(player.getInventory()).thenReturn(inventory);
    click = mock(InventoryClickEvent.class);
    when(click.getWhoClicked()).thenReturn(player);
    Inventory top = mock(Inventory.class);
    when(top.getHolder()).thenReturn(new AssemblyHolder());
    when(click.getInventory()).thenReturn(top);
    when(click.getSlot()).thenReturn(8);
    when(click.getCurrentItem()).thenReturn(new ItemStack(Material.CROSSBOW));
    part = mock(GunPart.class);
    when(part.hasCost()).thenReturn(true);
    when(part.getCost()).thenReturn(Map.of("iron", 3));
    when(assembly.collectPartsForPlayer(player, GunType.RIFLE)).thenReturn(List.of(part));
    crafted = new ItemStack(Material.CROSSBOW);
    when(assembly.createOutputItem(GunType.RIFLE, List.of(part), false)).thenReturn(crafted);
    material = new ItemStack(Material.IRON_INGOT, 5);
    when(inventory.getContents())
        .thenReturn(new ItemStack[] {null, new ItemStack(Material.DIRT), material});
    when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getChecker().checkItemWithPath(any(), eq("iron")))
        .thenAnswer(i -> ((ItemStack) i.getArgument(0)).getType() == Material.IRON_INGOT);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getItemAPI).thenReturn(api);
    GunsAndGadgets plugin = mock(GunsAndGadgets.class);
    when(plugin.namespace()).thenReturn("gunsandgadgets");
    plugins = mockStatic(GunsAndGadgets.class);
    plugins.when(GunsAndGadgets::getInstance).thenReturn(plugin);
  }

  @AfterEach
  void cleanup() {
    if (libs != null) libs.close();
    if (plugins != null) plugins.close();
    Cache.requireInput = oldRequire;
    Cache.outputSlot = oldSlot;
    MockBukkit.unmock();
  }

  @Test
  void disabledInputRequirementDoesNotConsumeMaterials() {
    Cache.requireInput = false;
    manager.onCraft(click);
    assertEquals(5, material.getAmount(), "Free crafting must preserve available ingredients");
    assertEquals(Map.of(), GunCraftInputs.readFrom(crafted));
    verify(inventory).addItem(crafted);
  }

  @Test
  void paidCraftConsumesExactCostsAndDeliversWeapon() {
    manager.onCraft(click);
    assertEquals(2, material.getAmount());
    assertEquals(Map.of("iron", 3), GunCraftInputs.readFrom(crafted));
    verify(inventory).addItem(crafted);
    verify(player).closeInventory();
  }

  @Test
  void canceledAndUnavailableRebuildsPreserveIngredients() {
    for (ItemStack unavailable :
        Arrays.asList(null, new ItemStack(Material.AIR), new ItemStack(Material.BARRIER))) {
      when(assembly.createOutputItem(GunType.RIFLE, List.of(part), false)).thenReturn(unavailable);
      manager.onCraft(click);
      assertEquals(5, material.getAmount(), "Failed output must preserve crafting materials");
    }
    verify(inventory, never()).addItem(any(ItemStack.class));
    verify(player, never()).closeInventory();
    verify(player, times(3)).sendMessage(contains("could not be created"));
  }

  @Test
  void fullInventoryDropsUndeliveredWeaponAtPlayer() {
    var world = mock(org.bukkit.World.class);
    var location = new org.bukkit.Location(world, 12, 64, 8);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(location);
    when(inventory.addItem(crafted)).thenReturn(new HashMap<>(Map.of(0, crafted)));
    manager.onCraft(click);
    assertEquals(2, material.getAmount());
    verify(world).dropItemNaturally(location, crafted);
    verify(player).closeInventory();
  }

  @Test
  void staffBypassesMaterials() {
    when(player.hasPermission("gg.bypass_crafting_cost")).thenReturn(true);
    manager.onCraft(click);
    assertEquals(5, material.getAmount());
    assertEquals(Map.of(), GunCraftInputs.readFrom(crafted));
    verify(inventory).addItem(crafted);
  }

  @Test
  void insufficientMaterialsRejectWithoutConsumption() {
    material.setAmount(2);
    manager.onCraft(click);
    assertEquals(2, material.getAmount());
    verify(inventory, never()).addItem(any(ItemStack.class));
    verify(player).sendMessage("§cLacking inputs");
  }

  @Test
  void disabledPartsRejectCraft() {
    when(part.isDisabled()).thenReturn(true);
    manager.onCraft(click);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }

  @Test
  void classConflictRejectsCraft() {
    when(assembly.hasClassConflict(player, List.of(part))).thenReturn(true);
    manager.onCraft(click);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }

  @Test
  void decorationBarrierEmptyAndOtherSlotsNeverCraft() {
    when(click.getCurrentItem()).thenReturn(null);
    manager.onCraft(click);
    when(click.getCurrentItem()).thenReturn(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    manager.onCraft(click);
    when(click.getCurrentItem()).thenReturn(new ItemStack(Material.BARRIER));
    manager.onCraft(click);
    when(click.getCurrentItem()).thenReturn(crafted);
    when(click.getSlot()).thenReturn(2);
    manager.onCraft(click);
    verifyNoInteractions(assembly);
  }

  @Test
  void costsAggregateAcrossPartsAndStacks() {
    GunPart second = mock(GunPart.class), free = mock(GunPart.class);
    when(second.hasCost()).thenReturn(true);
    when(second.getCost()).thenReturn(Map.of("iron", 4));
    var parts = List.of(part, second, free);
    when(assembly.collectPartsForPlayer(player, GunType.RIFLE)).thenReturn(parts);
    when(assembly.createOutputItem(GunType.RIFLE, parts, false)).thenReturn(crafted);
    ItemStack other = new ItemStack(Material.IRON_INGOT, 4);
    when(inventory.getContents())
        .thenReturn(new ItemStack[] {null, new ItemStack(Material.DIRT), material, other});
    manager.onCraft(click);
    assertEquals(0, material.getAmount());
    assertEquals(2, other.getAmount());
    assertEquals(Map.of("iron", 7), GunCraftInputs.readFrom(crafted));
    verify(inventory).addItem(crafted);
  }

  @Test
  void stationOpensOnlyForMainHandNonSneakingMatchingBlock() {
    var event = mock(org.bukkit.event.player.PlayerInteractEvent.class);
    var block = mock(org.bukkit.block.Block.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getAction()).thenReturn(org.bukkit.event.block.Action.LEFT_CLICK_BLOCK);
    manager.onStationOpen(event);
    when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
    when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
    manager.onStationOpen(event);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    manager.onStationOpen(event);
    when(event.getClickedBlock()).thenReturn(block);
    var api = mock(net.tfminecraft.tlibs.objects.api.BlockAPI.class, RETURNS_DEEP_STUBS);
    libs.when(TLibs::getBlockAPI).thenReturn(api);
    manager.onStationOpen(event);
    when(api.getChecker().checkBlock(block, Cache.station)).thenReturn(true);
    when(player.isSneaking()).thenReturn(true);
    manager.onStationOpen(event);
    verifyNoInteractions(assembly);
    when(player.isSneaking()).thenReturn(false);
    manager.onStationOpen(event);
    verify(event).setCancelled(true);
    verify(assembly).openCraftingInventory(player);
  }

  @Test
  void nonPlayerAndUnrelatedInventoryIgnored() {
    when(click.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
    manager.onCraft(click);
    when(click.getWhoClicked()).thenReturn(player);
    when(click.getInventory()).thenReturn(mock(Inventory.class));
    manager.onCraft(click);
    verify(click, never()).setCancelled(true);
    verifyNoInteractions(assembly);
  }

  @Test
  void zeroCostIngredientDoesNotRequireAMatchingStack() {
    when(part.getCost()).thenReturn(Map.of("absent", 0));
    manager.onCraft(click);
    assertEquals(5, material.getAmount());
    assertEquals(Map.of(), GunCraftInputs.readFrom(crafted));
    verify(inventory).addItem(crafted);
  }
}

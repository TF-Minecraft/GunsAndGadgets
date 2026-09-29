package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.tfminecraft.gunsandgadgets.attributes.*;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.*;
import net.tfminecraft.gunsandgadgets.guns.ammunition.*;
import net.tfminecraft.gunsandgadgets.guns.parts.*;
import net.tfminecraft.gunsandgadgets.guns.stats.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.manager.inventory.*;
import net.tfminecraft.gunsandgadgets.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HelpersTest extends GunsTestSupport {
  @Test
  void publicUtilityInstancesRemainConstructible() {
    assertNotNull(new AttributeReader());
    assertNotNull(new Caliber());
    assertNotNull(new CostFormatter());
    assertNotNull(new SoundPlayer());
    assertNotNull(new Cache());
    assertNotNull(new StatCalculator());
    assertNotNull(new StatApplier());
    assertNotNull(new SelectedPartsManager());
    assertNotNull(new TypeSelectionManager());
  }

  @Test
  void legacyModelsReplaceEntireComponentAndFailClearlyWhenAbsent() {
    ItemMeta meta = mock(ItemMeta.class);
    CustomModelDataComponent data = mock(CustomModelDataComponent.class);
    when(meta.getCustomModelDataComponent()).thenReturn(data);
    when(data.getFloats()).thenReturn(List.of());
    assertFalse(LegacyModelData.has(meta));
    assertThrows(IllegalStateException.class, () -> LegacyModelData.get(meta));
    when(data.getFloats()).thenReturn(List.of(23f));
    assertTrue(LegacyModelData.has(meta));
    assertEquals(23, LegacyModelData.get(meta));
    LegacyModelData.set(meta, 12);
    verify(data).setFloats(List.of(12f));
    verify(data).setFlags(List.of());
    verify(data).setStrings(List.of());
    verify(data).setColors(List.of());
    verify(meta).setCustomModelDataComponent(data);
    LegacyModelData.set(meta, null);
    verify(meta).setCustomModelDataComponent(null);
  }

  @Test
  void caliberFiltersMissingDefinitionsAndSelectionStateIsPlayerScoped() throws Exception {
    assertTrue(Caliber.get(null).isEmpty());
    assertTrue(Caliber.get(mock(ItemStack.class)).isEmpty());
    assertTrue(Caliber.get(new ItemStack(Material.STONE)).isEmpty());
    ItemStack item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    meta.setDisplayName("Gun");
    item.setItemMeta(meta);
    assertTrue(Caliber.get(item).isEmpty());
    NamespacedKey key = new NamespacedKey(plugin, "calibers");
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    assertTrue(Caliber.get(item).isEmpty());
    Ammunition ball = new Ammunition("ball", yaml("{}"));
    AmmunitionLoader.get().put("ball", ball);
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "missing;ball");
    item.setItemMeta(meta);
    assertEquals(List.of(ball), Caliber.get(item));
    var one = server.addPlayer();
    var two = server.addPlayer();
    assertNull(SelectedPartsManager.get(one, "barrel"));
    SelectedPartsManager.set(one, "barrel", "long");
    assertEquals("long", SelectedPartsManager.get(one, "barrel"));
    assertNull(SelectedPartsManager.get(two, "barrel"));
    SelectedPartsManager.clear(one);
    assertNull(SelectedPartsManager.get(one, "barrel"));
    assertEquals(GunType.RIFLE, TypeSelectionManager.getSelectedType(one));
    TypeSelectionManager.setSelectedType(one, GunType.PISTOL);
    assertEquals(GunType.PISTOL, TypeSelectionManager.getSelectedType(one));
    assertNull(new AssemblyHolder().getInventory());
    assertNull(new TypeSelectionHolder().getInventory());
    var holder = new PartSelectionHolder("barrel");
    assertEquals("barrel", holder.getPartId());
    assertNull(holder.getInventory());
  }

  @Test
  void attributesWeightKnownStatsAndIgnoreUnknownAttributes() throws Exception {
    Player player = server.addPlayer();
    try (MockedStatic<PlayerData> players = mockStatic(PlayerData.class)) {
      PlayerData data = mock(PlayerData.class, RETURNS_DEEP_STUBS);
      players.when(() -> PlayerData.get(player.getUniqueId())).thenReturn(data);
      assertEquals(1, AttributeReader.getAccuracyMultFromAttributes(player));
      assertEquals(1, AttributeReader.getReloadReductionMultFromAttributes(player));
      Cache.attributes.add(
          new AttributeData(
              "dexterity", yaml("accuracy-per-level: 2\nreload-reduction-per-level: 3")));
      Cache.attributes.add(new AttributeData("absent", yaml("{}")));
      when(data.getAttributes().getInstance("absent")).thenReturn(null);
      when(data.getAttributes().getInstance("dexterity").getTotal()).thenReturn(5);
      assertEquals(.9, AttributeReader.getAccuracyMultFromAttributes(player));
      assertEquals(.85, AttributeReader.getReloadReductionMultFromAttributes(player));
    }
  }

  @Test
  void soundGroupsUseEachGroupAndFallbackOnlyWhenNothingPlayed() {
    World world = mock(World.class);
    Location loc = new Location(world, 0, 1, 0);
    SoundPlayer.playSounds(null, "a", true, 1);
    SoundPlayer.playSounds(loc, null, false, 1);
    verifyNoInteractions(world);
    SoundPlayer.playSounds(loc, "", true, 2);
    verify(world).playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2f, 1.2f);
    clearInvocations(world);
    SoundPlayer.playSounds(loc, "a;,; ;b", true, 3);
    verify(world).playSound(loc, "a", SoundCategory.PLAYERS, 3f, 1f);
    verify(world).playSound(loc, "b", SoundCategory.PLAYERS, 3f, 1f);
    verifyNoMoreInteractions(world);
  }

  @Test
  void impactsUseHitFaceOrIncomingDirectionAndCullDistantPlayers() throws Exception {
    World world = mock(World.class);
    Location loc = new Location(world, 1, 2, 3);
    Vector dir = new Vector(1, 0, 0);
    assertNull(ImpactVfx.onBlockSurface(null, dir, null));
    Location noWorld = new Location(null, 1, 2, 3);
    assertSame(noWorld, ImpactVfx.onBlockSurface(noWorld, dir, null));
    assertSame(loc, ImpactVfx.onBlockSurface(loc, null, null));
    assertEquals(loc, ImpactVfx.onBlockSurface(loc, new Vector(), null));
    assertEquals(.94, ImpactVfx.onBlockSurface(loc, dir, null).getX(), 1e-9);
    when(world.rayTraceBlocks(any(), any(), eq(3d), eq(FluidCollisionMode.NEVER), eq(true)))
        .thenReturn(new RayTraceResult(new Vector(1, 2, 3), BlockFace.WEST));
    assertEquals(.94, ImpactVfx.onBlockSurface(loc, dir, null).getX(), 1e-9);
    when(world.rayTraceBlocks(any(), any(), eq(3d), eq(FluidCollisionMode.NEVER), eq(true)))
        .thenReturn(new RayTraceResult(new Vector(1, 2, 3)));
    assertEquals(.94, ImpactVfx.onBlockSurface(loc, dir, null).getX(), 1e-9);
    var method = ImpactVfx.class.getDeclaredMethod("surfaceOffset", Vector.class, Vector.class);
    method.setAccessible(true);
    assertEquals(new Vector(-.06, 0, 0), method.invoke(null, dir, new Vector()));
    Player near = mock(Player.class), far = mock(Player.class);
    when(near.getLocation()).thenReturn(loc);
    when(far.getLocation()).thenReturn(new Location(world, 1000, 0, 0));
    when(world.getPlayers()).thenReturn(List.of(near, far));
    ImpactVfx.spawn(null, Particle.FLAME, 1, 0, 0, 0, 0, null);
    ImpactVfx.spawn(noWorld, Particle.FLAME, 1, 0, 0, 0, 0, null);
    ImpactVfx.spawn(loc, null, 1, 0, 0, 0, 0, null);
    ImpactVfx.spawn(loc, Particle.FLAME, 1, 0, 0, 0, 0, null);
    verify(near).spawnParticle(Particle.FLAME, loc, 1, 0d, 0d, 0d, 0d);
    Object data = new Object();
    ImpactVfx.spawn(loc, Particle.BLOCK, 1, 0, 0, 0, 0, data);
    verify(near).spawnParticle(Particle.BLOCK, loc, 1, 0d, 0d, 0d, 0d, data);
    verify(far, never())
        .spawnParticle(
            any(),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble());
  }

  @Test
  void guiStatsAggregateCostsAndSortDisplayLines() throws Exception {
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      tlibs.when(TLibs::getItemAPI).thenReturn(api);
      ItemStack iron = new ItemStack(Material.IRON_INGOT), stick = new ItemStack(Material.STICK);
      when(api.getCreator().getItemFromPath("v.iron_ingot")).thenReturn(iron);
      when(api.getCreator().getItemFromPath("v.stick")).thenReturn(stick);
      var formatted = CostFormatter.getCostsFormatted(Map.of("v.stick", 1, "v.iron_ingot", 2));
      assertTrue(formatted.getFirst().contains("Iron Ingot"));
      assertTrue(formatted.get(1).endsWith("1"));
      GunPart cost = new GunPart("cost", yaml("cost: ['v.iron_ingot(2)']")),
          empty = new GunPart("empty", yaml("{}"));
      ItemStack item = new ItemStack(Material.STICK);
      StatApplier.apply(item, List.of(cost, cost, empty), true);
      assertTrue(item.getItemMeta().getLore().stream().anyMatch(s -> s.endsWith("4")));
      assertEquals("§e§lClick to Craft", item.getItemMeta().getLore().getLast());
      StatApplier.apply(item, List.of(empty), true);
    }
  }
}

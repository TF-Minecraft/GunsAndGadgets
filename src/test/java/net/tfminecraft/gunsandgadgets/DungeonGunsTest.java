package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.tfminecraft.gunsandgadgets.api.DungeonGuns;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.data.*;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.guns.stats.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.utils.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

/** Dungeon guns: the API, the power they keep through refreshes, and their look; normal guns stay as they were. */
class DungeonGunsTest extends GunsTestSupport {

  static final String METALS =
      """
steel: {name: Steel, tier: 2}
maplewood: {name: Maple, kind: wood}
common_leather: {name: Common, kind: animal, tier: 1, type: leather, better: 0.5}
good_leather: {name: Good, kind: animal, tier: 2, type: leather}
""";

  static final String PARTS =
      """
long: {part-type: barrel, type: [rifle, launcher], tendencies: [accuracy 2, damage 3], class: [Musketeer], name-impact: [Long 0 1]}
short: {part-type: barrel, type: [rifle], tendencies: [accuracy 1], class: [Musketeer]}
grenadier: {part-type: barrel, type: [rifle], tendencies: [accuracy 1], class: [Grenadier]}
off: {part-type: barrel, type: [rifle], tendencies: [accuracy 1], disabled: true}
old: {part-type: barrel, type: [rifle]}
loader: {part-type: loader, type: [rifle], tendencies: [reload 1, damage_percent -10]}
proof: {part-type: chamber, type: [rifle], tendencies: [accuracy 1], ammo-filter: powered}
single: {part-type: chamber, type: [rifle], tendencies: [accuracy 1]}
flint: {part-type: action, type: [rifle], tendencies: [fire_rate 2]}
steam: {part-type: action, type: [rifle], tendencies: [accuracy 1], ammo-filter: plain}
stock:
  part-type: stock
  type: [rifle]
  material: wood
  tendencies: [moving 10]
  fitting: {types: [leather], amount: 1, stats: [moving]}
""";

  static final Map<String, String> IDS =
      Map.of("barrel", "long@steel", "loader", "loader@steel", "chamber", "single@steel", "action", "flint@steel",
          "stock", "stock@maplewood+good_leather");
  static final Map<String, Integer> COLORS =
      Map.of("barrel", 0x112233, "loader", 0x223344, "chamber", 0x334455, "action", 0x445566, "stock", 0x556677);

  MockedStatic<TLibs> libs;
  MockedStatic<ItemSkinPreserver> appearance;
  MockedStatic<NBTItem> nbt;

  @BeforeEach
  void load() throws Exception {
    libs = mockStatic(TLibs.class);
    var items = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
    libs.when(TLibs::getItemAPI).thenReturn(items);
    when(items.getCreator().getItemFromPath(anyString())).thenAnswer(i -> new ItemStack(Material.STICK));
    appearance = mockStatic(ItemSkinPreserver.class);
    appearance.when(() -> ItemSkinPreserver.hasSkinData(any())).thenReturn(true);
    appearance.when(() -> ItemSkinPreserver.applyAppearanceFromSkin(any(), any())).thenAnswer(i -> i.getArgument(1));
    nbt = mockStatic(NBTItem.class);
    nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(mock(NBTItem.class));
    SkinData skin = mock(SkinData.class);
    when(skin.getId()).thenReturn("forged");
    when(skin.isForged()).thenReturn(true);
    when(skin.getTypes()).thenReturn(List.of("rifle", "launcher"));
    when(skin.parseModel(any())).thenAnswer(i -> new ItemStack(Material.STONE_HOE));
    SkinLoader.get().put("forged", skin);
    Cache.requiredParts.put(GunType.RIFLE, List.of("Barrel", "loader", "chamber", "action", "stock"));
    Cache.requiredParts.put(GunType.LAUNCHER, List.of("barrel"));
    new PartDataLoader().load(file("types.yml", ForgedLooksTest.TYPES).toFile());
    new MetalLoader().load(file("metals.yml", METALS).toFile());
    new PartLoader().load(file("parts.yml", PARTS).toFile());
  }

  @AfterEach
  void close() {
    DungeonGuns.onRefresh(null);
    nbt.close();
    appearance.close();
    libs.close();
  }

  ItemStack crafted(Map<String, String> ids) {
    List<GunPart> parts = new ArrayList<>();
    for (String slot : ForgedLook.SLOTS) parts.add(PartLoader.getByString(ids.get(slot)));
    return new InventoryManager().createOutputItem(GunType.RIFLE, parts, false);
  }

  ItemStack dungeon(double power) {
    return DungeonGuns.build("rifle", IDS, COLORS, power, "court");
  }

  static Map<String, String> with(String slot, String id) {
    Map<String, String> ids = new HashMap<>(IDS);
    ids.put(slot, id);
    return ids;
  }

  PersistentDataContainer pdc(ItemStack item) {
    return item.getItemMeta().getPersistentDataContainer();
  }

  double percent(ItemStack item) {
    return GunStats.get(pdc(item), "damage_percent", 0);
  }

  /** Every stored value but the ones that are random per gun. */
  Map<String, Object> data(ItemStack item) {
    Map<String, Object> out = new TreeMap<>();
    for (NamespacedKey key : pdc(item).getKeys()) {
      if (key.getKey().equals("gun_id") || key.getKey().equals("accuracy_salt")) continue;
      for (var type : List.of(PersistentDataType.STRING, PersistentDataType.INTEGER, PersistentDataType.DOUBLE)) {
        if (pdc(item).has(key, type)) out.put(key.getKey(), pdc(item).get(key, type));
      }
    }
    return out;
  }

  @Test
  void slotsListOneCraftablePartEachThatAgreeOnAClass() {
    Map<String, List<String>> slots = DungeonGuns.slots("Rifle", "steel", "maplewood");
    assertEquals(List.of("barrel", "loader", "chamber", "action", "stock"), List.copyOf(slots.keySet()));
    assertEquals(List.of("long@steel", "short@steel"), slots.get("barrel"));
    assertEquals(List.of("proof@steel", "single@steel"), slots.get("chamber"));
    assertEquals(List.of("stock@maplewood+good_leather"), slots.get("stock"));
    assertEquals(Map.of("barrel", List.of("long@steel")), DungeonGuns.slots("launcher", "steel", "maplewood"));
    assertEquals(List.of(), DungeonGuns.slots("rifle", "iron", "oak").get("barrel"));
    assertTrue(DungeonGuns.slots("cannon", "steel", "maplewood").isEmpty());
    assertTrue(DungeonGuns.slots("pistol", "steel", "maplewood").isEmpty());
  }

  @Test
  void powerScalesOnlyTheDamagePercentAndLeavesTheRestAsCrafted() {
    ItemStack crafted = crafted(IDS);
    ItemStack gun = dungeon(1.5);
    Map<String, Object> was = data(crafted);
    Map<String, Object> now = data(gun);
    assertEquals(-10.0, percent(crafted), 1e-9);
    assertEquals(35.0, percent(gun), 1e-9); // 0.9 of the hit, times 1.5
    assertEquals(1.5, now.remove("gg_dungeon_power"));
    assertEquals("court", now.remove("gg_dungeon_marker"));
    assertEquals("{}", now.remove("gg_craft_inputs"));
    for (String key : List.of("stat_value_damage_percent", "stat_exact_damage_percent")) {
      assertNotEquals(was.remove(key), now.remove(key));
    }
    assertEquals(was, now);
    List<Component> changed = new ArrayList<>(gun.lore());
    changed.removeAll(crafted.lore());
    assertEquals(1, changed.size()); // the Damage line
    assertTrue(was.containsKey("gg_craft_parts") && was.containsKey("stat_exact_reload"));
    assertTrue(gun.getItemMeta().isUnbreakable());
    assertFalse(crafted.getItemMeta().isUnbreakable());
    assertEquals(crafted.getItemMeta().displayName(), gun.getItemMeta().displayName());
    // a gun whose parts change no damage gets the power as its percent; power 1 is the crafted gun
    assertEquals(data(crafted).get("stat_exact_damage_percent"), percent(dungeon(1)));
    GunPart plain = PartLoader.getByString("short@steel");
    assertEquals(100.0, percent(StatApplier.apply(new ItemStack(Material.STICK), List.of(plain), false, null, null, null, 2)), 1e-9);
    assertEquals(0.0, percent(StatApplier.apply(new ItemStack(Material.STICK), List.of(plain), false)), 1e-9);
  }

  @Test
  void aNormalGunIsStampedAsBefore() {
    Map<String, Object> data = data(crafted(IDS));
    assertEquals(
        "{accuracy=3.0, damage=3.0, damage_percent=-10.0, fire_rate=2.0, moving=10.0, reload=1.0}",
        stats(data, "stat_exact_").toString());
    assertEquals(
        "{accuracy=3, damage=3, damage_percent=-10, fire_rate=2, moving=10, reload=1}",
        stats(data, "stat_value_").toString());
    assertEquals(
        Set.of("gg_craft_parts", "gg_parts_revision", "gg_majority_tier", "gg_tier_lore_start", "gun_type", "skin_id"),
        data.keySet());
  }

  /** The stats under this prefix, taken out of {@code data}. */
  static Map<String, Object> stats(Map<String, Object> data, String prefix) {
    Map<String, Object> out = new TreeMap<>();
    data.forEach((key, value) -> {
      if (key.startsWith(prefix)) out.put(key.substring(prefix.length()), value);
    });
    data.keySet().removeIf(key -> key.startsWith(prefix));
    return out;
  }

  @Test
  void whatCannotBeBuiltIsNull() {
    assertNotNull(dungeon(1));
    assertNull(DungeonGuns.build("cannon", IDS, COLORS, 1, null));
    assertNull(DungeonGuns.build("rifle", Map.of("barrel", "long@steel"), COLORS, 1, null));
    assertNull(dungeon(0));
    assertNull(dungeon(Double.NaN));
    for (String barrel : List.of("gone@steel", "old", "loader@steel", "off@steel")) {
      assertNull(DungeonGuns.build("rifle", with("barrel", barrel), COLORS, 1, null), barrel);
    }
    Map<String, String> misnamed = new HashMap<>(IDS);
    misnamed.put("grip", misnamed.remove("stock"));
    assertNull(DungeonGuns.build("rifle", misnamed, COLORS, 1, null));
    assertNull(DungeonGuns.build("launcher", Map.of("barrel", "short@steel"), COLORS, 1, null));
    Cache.requiredParts.put(GunType.LAUNCHER, List.of("barrel", "loader"));
    GunPart loader = PartLoader.getByString("loader@steel");
    loader.getGunTypes().add(GunType.LAUNCHER);
    loader.getClassRequirements().add("Grenadier");
    Map<String, String> clash = Map.of("barrel", "long@steel", "loader", "loader@steel");
    assertNull(DungeonGuns.build("launcher", clash, COLORS, 1, null));
    loader.getClassRequirements().clear();
    assertNotNull(DungeonGuns.build("launcher", clash, COLORS, 1, null));
    // no shot fits a plain-only lock and a powered-only chamber
    Map<String, String> shotless = with("action", "steam@steel");
    shotless.put("chamber", "proof@steel");
    assertNull(DungeonGuns.build("rifle", shotless, COLORS, 1, null));
    for (ItemStack broken : new ItemStack[] {null, new ItemStack(Material.AIR)}) {
      try (var made = mockConstruction(InventoryManager.class,
          (m, c) -> when(m.createOutputItem(any(), any(), anyBoolean(), any(), any())).thenReturn(broken))) {
        assertNull(dungeon(1));
      }
    }
  }

  @Test
  void aGunReadsBackAsItWasBuiltAndAPartCanBeSwapped() {
    ItemStack gun = dungeon(1.5);
    DungeonGuns.Parts parts = DungeonGuns.read(gun).orElseThrow();
    assertEquals(new DungeonGuns.Parts("rifle", IDS, COLORS, 1.5, "court"), parts);
    Map<String, String> ids = new HashMap<>(parts.partIds());
    ids.put("barrel", "short@steel");
    ItemStack swapped = DungeonGuns.build(parts.gunType(), ids, Map.of("barrel", 0xFF123456), parts.power(), null);
    DungeonGuns.Parts after = DungeonGuns.read(swapped).orElseThrow();
    assertEquals("short@steel", after.partIds().get("barrel"));
    assertEquals(0x123456, after.colors().get("barrel"));
    assertEquals(0xFFFFFF, after.colors().get("stock"));
    assertNull(after.marker());
    // a launcher has no stock
    ItemStack launcher = DungeonGuns.build("launcher", Map.of("barrel", "long@steel"), COLORS, 1, null);
    assertEquals(Map.of("barrel", 0x112233), DungeonGuns.read(launcher).orElseThrow().colors());

    assertTrue(DungeonGuns.isDungeonGun(gun));
    for (ItemStack other : new ItemStack[] {null, new ItemStack(Material.STICK), crafted(IDS)}) {
      assertFalse(DungeonGuns.isDungeonGun(other));
      assertTrue(DungeonGuns.read(other).isEmpty());
    }
    PartLoader.get().remove("flint@steel");
    assertTrue(DungeonGuns.read(gun).isEmpty());
  }

  @Test
  void refreshingKeepsThePowerColoursNameAndOtherPluginsData() {
    ItemStack gun = dungeon(1.5);
    ItemMeta meta = gun.getItemMeta();
    meta.displayName(Component.text("The Court's Rifle"));
    NamespacedKey set = new NamespacedKey("trialrooms", "dungeon_set");
    meta.getPersistentDataContainer().set(set, PersistentDataType.STRING, "court");
    GunStats.set(meta.getPersistentDataContainer(), "speed", 99); // no longer on these parts
    gun.setItemMeta(meta);
    ItemStack fresh = GunStatRefresher.refresh(gun, true).getItem();
    assertEquals(DungeonGuns.read(gun), DungeonGuns.read(fresh));
    assertEquals(35.0, percent(fresh), 1e-9);
    assertTrue(fresh.getItemMeta().isUnbreakable());
    assertEquals(Component.text("The Court's Rifle"), fresh.getItemMeta().displayName());
    assertEquals("court", pdc(fresh).get(set, PersistentDataType.STRING));
    assertEquals(0, GunStats.get(pdc(fresh), "speed", 0));
    assertEquals(Map.of(), GunCraftInputs.readFrom(fresh));

    // the maker's own finish (its lore) gets the old and the new gun
    ItemStack finished = new ItemStack(Material.CROSSBOW);
    DungeonGuns.onRefresh((old, rebuilt) -> old.isSimilar(gun) && DungeonGuns.isDungeonGun(rebuilt) ? finished : null);
    assertSame(finished, GunStatRefresher.refresh(gun, true).getItem());
    // a finish that fails keeps the plain gun
    DungeonGuns.onRefresh((old, rebuilt) -> {
      throw new IllegalStateException("broken");
    });
    assertEquals(DungeonGuns.read(gun), DungeonGuns.read(GunStatRefresher.refresh(gun, true).getItem()));
    // nobody crafted it: never taken apart, whatever became of its parts
    assertFalse(GunRefunder.isOld(gun));
    // a normal gun is refreshed as before
    ItemStack crafted = crafted(IDS);
    assertEquals(data(crafted), data(GunStatRefresher.refresh(crafted, true).getItem()));
    assertFalse(DungeonGuns.isDungeonGun(GunStatRefresher.refresh(crafted, true).getItem()));
  }

  @Test
  void everyPoseOfADungeonGunShowsTheNeutralMaterialAndItsColours() {
    SkinData skin = mock(SkinData.class);
    when(skin.getForgedModel(any())).thenReturn(NamespacedKey.fromString("tfmc_guns:rifle_carry"));
    for (boolean dungeon : new boolean[] {true, false}) {
      ItemStack gun = dungeon ? dungeon(1.5) : crafted(IDS);
      for (SkinState state : SkinState.values()) {
        // MockBukkit has no item models: check the calls on a mocked meta with the gun's data
        ItemMeta meta = mock(ItemMeta.class);
        CustomModelDataComponent component = mock(CustomModelDataComponent.class);
        when(meta.getCustomModelDataComponent()).thenReturn(component);
        var data = pdc(gun);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        ForgedLook.stamp(meta, skin, state, ForgedLook.stampedParts(gun));
        verify(component).setStrings(dungeon
            ? List.of("long", "dungeon", "loader", "dungeon", "single", "dungeon", "flint", "dungeon", "stock", "dungeon")
            : List.of("long", "steel", "loader", "steel", "single", "steel", "flint", "steel", "stock", "maplewood"));
        verify(component).setColors(dungeon
            ? List.of(Color.fromRGB(0x112233), Color.fromRGB(0x223344), Color.fromRGB(0x334455), Color.fromRGB(0x445566), Color.fromRGB(0x556677))
            : List.of());
      }
    }
  }
}

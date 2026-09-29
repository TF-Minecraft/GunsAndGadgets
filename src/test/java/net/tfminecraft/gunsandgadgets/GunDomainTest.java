package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.guns.*;
import net.tfminecraft.gunsandgadgets.guns.ammunition.*;
import net.tfminecraft.gunsandgadgets.guns.data.*;
import net.tfminecraft.gunsandgadgets.guns.parts.*;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.guns.stats.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.util.LegacyModelData;
import net.tfminecraft.gunsandgadgets.utils.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GunDomainTest extends GunsTestSupport {
  GunPart part(String id, String config) throws Exception {
    return new GunPart(id, yaml(config));
  }

  ItemStack item() {
    ItemStack item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    meta.setDisplayName("Gun");
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void partDefinitionsParseOptionsCostsSoundsAndStableRevisionContent() throws Exception {
    PartData type = new PartData("barrel", yaml("slot: 4"));
    PartDataLoader.get().put("barrel", type);
    assertEquals("barrel", type.getId());
    assertEquals(4, type.getSlot());
    GunPart part =
        part(
            "test",
            """
name: Test Barrel
tier: 2
disabled: true
two-handed: true
item: v.iron_ingot
type: [rifle, pistol, unknown]
stats: ['damage 3', 'accuracy -1', 'unknown 8', 'bad']
cost: ['v.iron_ingot(4)', 'v.stick', '(bad)', 'v.stone(abc)', 'v.stone(']
permissions: [gg.test]
class: [warrior]
caliber: [medium, small]
caliber-override: [large]
options: [smokeless, no_light, invalid]
lore: [First, Second]
name-impact: ['Barrel 1 2', 'broken x 3', 'short']
skin-impact: ['STEEL 3', 'broken x', 'short']
sounds: ['shoot a,b(rifle)', 'reload c', 'bad c', 'shoot d(unknown)', 'shoot e(unclosed', 'short', 'shoot , ,']
sound-overrides: ['shoot override(pistol)']
""");
    assertEquals("test", part.getId());
    assertEquals("Test Barrel", part.getName());
    assertSame(type, part.getPartType());
    assertEquals("v.iron_ingot", part.getItemKey());
    assertEquals(Map.of(Stats.DAMAGE, 3, Stats.ACCURACY, -1), part.getStats());
    assertEquals(Set.of(GunType.RIFLE, GunType.PISTOL), part.getGunTypes());
    assertEquals(4, part.getCost().get("v.iron_ingot"));
    assertEquals(1, part.getCost().get("v.stick"));
    assertTrue(part.hasCost());
    assertEquals(List.of("gg.test"), part.getPermissions());
    assertEquals(List.of("warrior"), part.getClassRequirements());
    assertEquals(List.of("medium", "small"), part.getCalibers());
    assertTrue(part.hasCaliberOverrides());
    assertEquals(List.of("large"), part.getCaliberOverrides());
    assertTrue(part.hasOption(GunPart.PartOption.SMOKELESS));
    assertTrue(part.isTwoHanded());
    assertTrue(part.isDisabled());
    assertFalse(part.isEnabledForCrafting());
    assertEquals(2, part.getTier());
    assertTrue(part.hasTier());
    assertEquals(List.of("First", "Second"), part.getLore());
    assertEquals(0, part.getRevision());
    part.setRevision(3);
    assertEquals(3, part.getRevision());
    var name = part.getNameImpacts().getFirst();
    assertEquals("Barrel", name.getFragment());
    assertEquals(1, name.getIndex());
    assertEquals(2, name.getWeight());
    var skin = part.getSkinImpacts().getFirst();
    assertEquals("steel", skin.getSkinId());
    assertEquals(3, skin.getWeight());
    var sound = part.getSounds().get(SoundType.SHOOT).getFirst();
    assertEquals(GunType.RIFLE, sound.getRequiredType());
    assertTrue(sound.matches(GunType.RIFLE));
    assertFalse(sound.matches(GunType.SHOTGUN));
    assertEquals(List.of("a", "b"), sound.getSoundKeys());
    Random rng = mock(Random.class);
    when(rng.nextInt(2)).thenReturn(1);
    assertEquals("b", sound.pickRandomKey(rng));
    assertNull(new GunPart.PartSound(List.of(), null).pickRandomKey(rng));
    assertTrue(new GunPart.PartSound(List.of("a"), null).matches(GunType.RIFLE));
    assertEquals(1, part.getSoundOverrides().size());
    String hash = part.buildRevisionContent();
    assertTrue(hash.contains("cost="));
    assertTrue(hash.contains("skin-impact=steel:3"));
    assertEquals(hash, part.buildRevisionContent());
    GunPart empty = part("empty", "part-type: absent");
    assertNull(empty.getPartType());
    assertFalse(empty.hasCost());
    assertFalse(empty.hasCaliberOverrides());
    assertTrue(empty.isEnabledForCrafting());
    assertFalse(empty.hasTier());
    assertNotNull(empty.buildRevisionContent());
  }

  @Test
  void malformedStatDoesNotDiscardOtherValidPartStats() throws Exception {
    GunPart part = assertDoesNotThrow(() -> part("bad", "stats: ['damage invalid', 'reload 2']"));
    assertEquals(Map.of(Stats.RELOAD, 2), part.getStats());
  }

  @Test
  void ammunitionAndStatsRespectBoundaryFormulas() throws Exception {
    Ammunition ammo =
        new Ammunition(
            "ball",
            yaml(
                "input: v.stone\n"
                    + "amount: 2\n"
                    + "stats: ['damage 3','bad','reload no']\n"
                    + "options: [rocket, bad]"));
    assertEquals("ball", ammo.getKey());
    assertEquals("v.stone", ammo.getInput());
    assertEquals(2, ammo.getAmount());
    assertEquals(Map.of("damage", 3), ammo.getStats());
    assertTrue(ammo.hasOption(Ammunition.AmmoOption.ROCKET));
    assertFalse(ammo.hasOption(Ammunition.AmmoOption.SMOKELESS));
    for (Stats stat : Stats.values()) {
      assertSame(stat, Stats.fromKey(stat.getKey().toUpperCase(Locale.ROOT)));
      assertFalse(stat.getDisplayName().isBlank());
    }
    assertEquals("Fire Rate", Stats.FIRE_RATE.getDisplayName());
    assertNull(Stats.fromKey("invalid"));
    assertEquals("Rifle", GunType.RIFLE.getDisplayName());
    assertEquals(120, StatCalculator.calculateReloadTicks(-100));
    assertEquals(60, StatCalculator.calculateReloadTicks(0));
    assertEquals(10, StatCalculator.calculateReloadTicks(100));
    assertEquals(40, StatCalculator.calculateAccuracy(-100));
    assertEquals(25, StatCalculator.calculateAccuracy(0));
    assertEquals(4.5, StatCalculator.calculateAccuracy(1));
    assertEquals(2.5, StatCalculator.calculateAccuracy(5));
    assertEquals(1.2, StatCalculator.calculateAccuracy(20));
    assertEquals(.07, StatCalculator.calculateAccuracy(30), 1e-9);
    assertEquals(4, StatCalculator.calculateFireRate(-30));
    assertEquals(1, StatCalculator.calculateFireRate(0));
    assertEquals(.5, StatCalculator.calculateFireRate(10));
    assertEquals(.1, StatCalculator.calculateFireRate(20), 1e-9);
    assertEquals(.1, StatCalculator.calculateFireRate(30));
  }

  @Test
  void modelsHandleStatesOverridesItemsAdderAndLegacyNumbers() throws Exception {
    SkinData blank = new SkinData("blank", yaml("{}"));
    assertEquals("blank", blank.getId());
    assertEquals("", blank.getCarry());
    assertEquals("", blank.getReload());
    assertEquals("", blank.getAim());
    assertTrue(blank.getTypes().isEmpty());
    assertTrue(blank.getOptions().isEmpty());
    assertFalse(blank.hasOption("crossbow"));
    for (SkinState state : SkinState.values())
      assertEquals(Material.BARRIER, blank.parseModel(state).getType());
    try (MockedStatic<LegacyModelData> legacy = mockStatic(LegacyModelData.class)) {
      SkinData models =
          new SkinData(
              "steel",
              yaml(
                  "carry: STICK.12\n"
                      + "reload: invalid\n"
                      + "aim: BOW.bad\n"
                      + "types: [rifle]\n"
                      + "options: [crossbow]"));
      assertEquals(Material.STICK, models.parseModel(SkinState.CARRY).getType());
      legacy.verify(() -> LegacyModelData.set(any(), eq(12)));
      assertEquals(Material.BARRIER, models.parseModel(SkinState.RELOAD).getType());
      assertEquals(Material.BOW, models.parseModel(SkinState.AIM).getType());
      assertTrue(models.hasOption("CROSSBOW"));
      assertEquals(List.of("rifle"), models.getTypes());
    }
    assertEquals(
        Material.AIR,
        new SkinData("air", yaml("carry: AIR.2")).parseModel(SkinState.CARRY).getType());
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      tlibs.when(TLibs::getItemAPI).thenReturn(api);
      SkinData ia = new SkinData("ia", yaml("carry: ' ia.test '\nreload: ia.empty\naim: ia.air"));
      when(api.getCreator().getItemFromPath("ia.test")).thenReturn(new ItemStack(Material.DIAMOND));
      when(api.getCreator().getItemFromPath("ia.empty")).thenReturn(null);
      when(api.getCreator().getItemFromPath("ia.air")).thenReturn(new ItemStack(Material.AIR));
      assertEquals(Material.DIAMOND, ia.parseModel(SkinState.CARRY).getType());
      assertEquals(Material.BARRIER, ia.parseModel(SkinState.RELOAD).getType());
      assertEquals(Material.BARRIER, ia.parseModel(SkinState.AIM).getType());
    }
  }

  @Test
  void skinVotesAndActionFallbackChooseMatchingType() throws Exception {
    PartDataLoader.get().put("action", new PartData("action", yaml("{}")));
    PartDataLoader.get().put("barrel", new PartData("barrel", yaml("{}")));
    SkinData steel = new SkinData("steel", yaml("types: [rifle]")),
        fallback = new SkinData("flint-rifle", yaml("types: [rifle]")),
        wrongType = new SkinData("flint-pistol", yaml("types: [pistol]"));
    Map<String, SkinData> skins = new LinkedHashMap<>();
    skins.put("steel", steel);
    skins.put("wrong", wrongType);
    skins.put("flint-rifle", fallback);
    SkinResolver resolver = new SkinResolver(skins);
    GunPart action = part("flint", "part-type: action"),
        voter = part("barrel", "skin-impact: ['STEEL 3']");
    assertSame(steel, resolver.resolve(GunType.RIFLE, List.of(voter, action)));
    assertSame(fallback, resolver.resolve(GunType.RIFLE, List.of(action)));
    assertNull(resolver.resolve(GunType.RIFLE, List.of(voter = part("barrel", "{}"))));
    assertNull(resolver.resolve(GunType.SHOTGUN, List.of(action)));
    assertSame(
        fallback,
        resolver.resolve(
            GunType.RIFLE, List.of(action, part("missing-vote", "skin-impact: ['missing 2']"))));
    GunPart nullImpacts = mock(GunPart.class);
    when(nullImpacts.getPartType()).thenReturn(PartDataLoader.getByString("barrel"));
    when(nullImpacts.getSkinImpacts()).thenReturn(null);
    assertNull(resolver.resolve(GunType.RIFLE, List.of(nullImpacts)));
  }

  @Test
  void stampedPartsTrackLiveRevisionsMissingEntriesAndPersistentData() throws Exception {
    GGCraftPart entry = new GGCraftPart();
    entry.setId("one");
    entry.setRevision(2);
    assertEquals("one", entry.getId());
    assertEquals(2, entry.getRevision());
    assertTrue(new GunCraftProvenance().getParts().isEmpty());
    assertTrue(new GunCraftProvenance(null, 0).getParts().isEmpty());
    GunPart one = part("one", "{}"), two = part("two", "{}");
    one.setRevision(2);
    two.setRevision(3);
    PartLoader.get().put("one", one);
    PartLoader.get().put("two", two);
    GunCraftProvenance provenance = GunCraftProvenance.from(List.of(one, two));
    assertEquals(3, provenance.getPartsRevision());
    assertFalse(provenance.isOutdated());
    assertTrue(provenance.getOutdatedParts().isEmpty());
    ItemStack item = item();
    provenance.applyTo(null);
    provenance.applyTo(new ItemStack(Material.STONE));
    provenance.applyTo(item);
    var restored = GunCraftProvenance.readFrom(item);
    assertEquals(2, restored.getParts().size());
    assertEquals(3, restored.getPartsRevision());
    assertNull(GunCraftProvenance.readFrom(null));
    assertNull(GunCraftProvenance.readFrom(mock(ItemStack.class)));
    assertNull(GunCraftProvenance.readFrom(new ItemStack(Material.STONE)));
    assertNull(GunCraftProvenance.readFrom(item()));
    one.setRevision(4);
    PartLoader.get().remove("two");
    assertTrue(restored.isOutdated());
    assertEquals("one", restored.getOutdatedParts().getFirst().getId());
    var resolved = restored.resolveStampedParts();
    assertEquals(List.of(one), resolved.live());
    assertEquals(List.of("two"), resolved.missingIds());
    restored.syncRevisions();
    assertEquals(4, restored.getPartsRevision());
    assertFalse(restored.isOutdated());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(GGCraftKeys.craftParts(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    assertNull(GunCraftProvenance.readFrom(item));
    meta.getPersistentDataContainer()
        .set(GGCraftKeys.craftParts(), PersistentDataType.STRING, "null");
    meta.getPersistentDataContainer().remove(GGCraftKeys.partsRevision());
    item.setItemMeta(meta);
    assertTrue(GunCraftProvenance.readFrom(item).getParts().isEmpty());
    assertEquals(0, GunCraftProvenance.readFrom(item).getPartsRevision());
  }

  @Test
  void tierVotingAndLorePreserveDescriptionsAndRecordIndices() throws Exception {
    GunPart one = part("one", "tier: 1"), two = part("two", "tier: 2"), none = part("none", "{}");
    assertEquals(0, MajorityTierResolver.resolve(null));
    assertEquals(0, MajorityTierResolver.resolve(List.of()));
    assertEquals(0, MajorityTierResolver.resolve(Arrays.asList(null, none)));
    assertEquals(1, MajorityTierResolver.resolve(List.of(one, one, two)));
    assertEquals(2, MajorityTierResolver.resolve(List.of(one, two)));
    assertEquals(16, MajorityTierResolver.resolve(List.of(one, part("sixteen", "tier: 16"))));
    assertEquals(
        List.of("I", "II", "III", "IV", "5"),
        java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(TierLore::toRoman).toList());
    assertTrue(TierLore.formatComponentLine(2).contains("II Component"));
    TierLore.applyTo(null, 1);
    TierLore.applyTo(mock(ItemStack.class), 1);
    TierLore.applyTo(new ItemStack(Material.STONE), 1);
    TierLore.applyTo(item(), 0);
    TierLore.applyTo(item(), -1);
    for (List<String> lore :
        List.of(
            List.<String>of(),
            List.of(" "),
            List.of("Description", "§a "),
            List.of("Description"))) {
      ItemStack item = item();
      var meta = item.getItemMeta();
      meta.setLore(lore);
      item.setItemMeta(meta);
      TierLore.applyTo(item, 3);
      var data = item.getItemMeta().getPersistentDataContainer();
      assertEquals(3, data.get(GGCraftKeys.majorityTier(), PersistentDataType.INTEGER));
      assertTrue(
          item.getItemMeta()
              .getLore()
              .get(data.get(GGCraftKeys.tierLoreStart(), PersistentDataType.INTEGER))
              .contains("III"));
    }
  }

  @Test
  void statsHeaderIsAddedOnceAndValuesArePersisted() throws Exception {
    GunPart part =
        part("stats", "stats: ['damage 8','accuracy -1','reload 0','speed 2','range 5']");
    ItemStack item = item();
    StatApplier.apply(item, List.of(part), false);
    assertEquals(
        1, item.getItemMeta().getLore().stream().filter(line -> line.contains("Stats:")).count());
    assertEquals(
        8,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "stat_value_damage"), PersistentDataType.INTEGER));
    StatApplier.apply(item, List.of(part), false);
    assertEquals(
        1, item.getItemMeta().getLore().stream().filter(line -> line.contains("Stats:")).count());
    ItemStack noMeta = mock(ItemStack.class);
    assertSame(noMeta, StatApplier.apply(noMeta, List.of(), false));
  }
}

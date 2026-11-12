package net.tfminecraft.gunsandgadgets.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.attributes.AttributeReader;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.ammunition.Ammunition;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.shooter.ProjectileShooter;
import net.tfminecraft.gunsandgadgets.util.SoundPlayer;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.*;
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

  org.bukkit.entity.Player drawer() {
    var p = spy(player);
    doNothing().when(p).setCooldown(any(Material.class), anyInt());
    doReturn(true).when(p).isOnGround();
    doReturn(null).when(p).getCurrentInput();
    return p;
  }

  @Test
  void aGunTakenIntoTheHandWaitsItsDrawTime() {
    var old = new java.util.HashMap<>(Cache.drawBaseTicks);
    try {
      Cache.drawBaseTicks = new java.util.HashMap<>(java.util.Map.of(net.tfminecraft.gunsandgadgets.guns.GunType.RIFLE, 10));
      setInt("stat_value_draw", 50); // half the base: 5 ticks
      player.getInventory().setItemInMainHand(gun);
      var p = drawer();
      var timer = manager.drawTimer();
      timer.tick(p);
      verify(p).setCooldown(eq(Material.STICK), eq(5));
      assertFalse(manager.isDrawn(player, "same-id"));
      use();
      assertEquals("Still drawing your gun.", actionBar());
      assertEquals(5, player.getInventory().getItem(1).getAmount());
      for (int i = 0; i < 4; i++) timer.tick(p);
      verify(p).setCooldown(eq(Material.STICK), eq(1)); // the sweep refreshes every few ticks
      assertFalse(manager.isDrawn(player, "same-id"));
      timer.tick(p);
      verify(p).setCooldown(eq(Material.STICK), eq(0));
      assertTrue(manager.isDrawn(player, "same-id"));
      assertFalse(manager.isDrawn(player, "other"));
      timer.tick(p); // stays drawn
      assertTrue(manager.isDrawn(player, "same-id"));
      // another gun in the same slot draws from the start
      set("gun_id", "other");
      player.getInventory().setItemInMainHand(gun);
      assertEquals("other", DrawTimer.gunId(player.getInventory().getItemInMainHand()));
      assertEquals("other", DrawTimer.gunId(p.getInventory().getItemInMainHand()));
      timer.tick(p);
      assertFalse(manager.isDrawn(player, "other"));
      assertFalse(manager.isDrawn(player, "same-id"));
      set("gun_id", "same-id");
      player.getInventory().setItemInMainHand(gun);
      timer.tick(p);
      // a different slot draws again; a gun without a known type is ready at once
      player.getInventory().setHeldItemSlot(4);
      set("gun_type", "cannon");
      player.getInventory().setItemInMainHand(gun);
      timer.tick(p);
      assertTrue(manager.isDrawn(player, "same-id"));
      // a new draw in another slot whose look changes item type mid-draw, then put away:
      // the sweep follows the item type and is cleared, so other items of that type aren't blocked
      player.getInventory().setHeldItemSlot(5);
      set("gun_type", "rifle");
      player.getInventory().setItemInMainHand(gun);
      timer.tick(p);
      gun.setType(Material.CROSSBOW);
      player.getInventory().setItemInMainHand(gun);
      for (int i = 0; i < 4; i++) timer.tick(p);
      verify(p, atLeastOnce()).setCooldown(eq(Material.CROSSBOW), intThat(t -> t > 0));
      assertFalse(manager.isDrawn(player, "same-id"));
      player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
      timer.tick(p);
      verify(p).setCooldown(Material.CROSSBOW, 0);
      gun.setType(Material.STICK);
      // something else in the hand forgets the gun
      player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
      timer.tick(p);
      assertFalse(manager.isDrawn(player, "same-id"));
      player.getInventory().setItemInMainHand(null);
      timer.tick(p);
      timer.forget(player);
      // without draw times every gun is ready and nothing is tracked
      Cache.drawBaseTicks = new java.util.HashMap<>();
      timer.tick(p);
      assertTrue(manager.isDrawn(player, "anything"));
    } finally {
      Cache.drawBaseTicks = old;
    }
  }

  @Test
  void everyPlayerIsTickedForDrawAndAim() {
    manager.tickPlayers();
    assertTrue(manager.isDrawn(player, "same-id"));
    assertFalse(manager.isReloading(player));
    assertNull(manager.skinOf(null));
    assertNull(manager.skinOf(new ItemStack(Material.STICK)));
    assertSame(skin, manager.skinOf(gun));
    assertEquals(0, manager.loadedBullets(null));
    var bare = mock(ItemStack.class);
    when(bare.getType()).thenReturn(Material.STICK);
    assertEquals(0, manager.loadedBullets(bare));
    assertEquals(0, manager.loadedBullets(new ItemStack(Material.AIR)));
    assertNull(DrawTimer.gunId(new ItemStack(Material.AIR)));
    assertNull(manager.skinOf(bare));
    assertNull(DrawTimer.gunId(bare));
    assertNull(DrawTimer.gunId(null));
    assertEquals(0, manager.loadedBullets(new ItemStack(Material.STICK)));
    // the scope release's stand-in event has nothing to cancel
    try {
      var field = GunManager.class.getDeclaredField("RELEASE");
      field.setAccessible(true);
      var release = (org.bukkit.event.Cancellable) field.get(null);
      release.setCancelled(true);
      assertFalse(release.isCancelled());
    } catch (ReflectiveOperationException e) {
      fail(e);
    }
  }

  void loaded() {
    setInt("bullets_loaded", 2);
    set("ammo_loaded", "shot");
    player.getInventory().setItemInMainHand(gun);
  }

  @Test
  void crouchingAimsALoadedDrawnGunAndSlowsTheWalk() {
    try (var preserver = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var modelData = mockStatic(net.tfminecraft.gunsandgadgets.util.LegacyModelData.class)) {
    boolean wasOn = Cache.aimingEnabled;
    double oldSlow = Cache.aimSlow;
    try {
      var aim = manager.getAiming();
      // aiming switched off
      Cache.aimingEnabled = false;
      loaded();
      aim.onSneak(player, true);
      assertFalse(aim.isAiming(player));
      Cache.aimingEnabled = true;
      Cache.aimSlow = 0.5;
      // nothing to aim: no gun, an unknown design, an empty gun, a reload, an undrawn gun
      player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
      aim.start(player);
      set("skin_id", "missing");
      player.getInventory().setItemInMainHand(gun);
      aim.start(player);
      set("skin_id", "test");
      setInt("bullets_loaded", 0);
      player.getInventory().setItemInMainHand(gun);
      aim.start(player);
      loaded();
      try {
        var reloading = GunManager.class.getDeclaredField("reloading");
        reloading.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<java.util.UUID, Boolean>) reloading.get(manager);
        map.put(player.getUniqueId(), true);
        aim.start(player);
        map.clear();
      } catch (ReflectiveOperationException e) {
        fail(e);
      }
      var old = new java.util.HashMap<>(Cache.drawBaseTicks);
      Cache.drawBaseTicks = new java.util.HashMap<>(java.util.Map.of(net.tfminecraft.gunsandgadgets.guns.GunType.RIFLE, 10));
      aim.start(player);
      Cache.drawBaseTicks = old;
      assertFalse(aim.isAiming(player));

      // aim: slower walk (the zoom), sights-up model; a second crouch changes nothing
      player.setSneaking(true);
      when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.AIM)).thenReturn(new ItemStack(Material.CROSSBOW));
      aim.onSneak(player, true);
      assertTrue(aim.isAiming(player));
      aim.start(player);
      var speed = player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
      assertNotNull(speed.getModifier(Aiming.ZOOM));
      assertFalse(aim.isScoped(player, player.getInventory().getItemInMainHand()));
      aim.tick(player); // still crouched, same loaded gun
      assertTrue(aim.isAiming(player));
      // letting go lowers it back to the loaded (charged crossbow) pose and ends the zoom
      aim.onSneak(player, false);
      assertFalse(aim.isAiming(player));
      assertNull(speed.getModifier(Aiming.ZOOM));
      assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
      aim.stop(player); // nothing to stop
      // a design that vanished while aimed: the aim ends, the gun stays as it is
      aim.start(player);
      set("skin_id", "missing");
      gun.setType(Material.CROSSBOW);
      player.getInventory().setItemInMainHand(gun);
      aim.stop(player);
      assertFalse(aim.isAiming(player));
      set("skin_id", "test");
      player.getInventory().setItemInMainHand(gun);

      // the aim ends by itself: crouch released, another item, an empty gun
      gun = player.getInventory().getItemInMainHand();
      aim.start(player);
      player.setSneaking(false);
      aim.tick(player);
      assertFalse(aim.isAiming(player));
      player.setSneaking(true);
      aim.start(player);
      player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
      aim.tick(player);
      assertFalse(aim.isAiming(player));
      player.getInventory().setItemInMainHand(gun);
      aim.start(player);
      setInt("bullets_loaded", 0);
      player.getInventory().setItemInMainHand(gun);
      aim.tick(player);
      assertFalse(aim.isAiming(player));
      aim.tick(player); // not aiming
      // no slowdown configured: aiming adds no modifier
      Cache.aimSlow = 0;
      loaded();
      aim.start(player);
      assertNull(speed.getModifier(Aiming.ZOOM));
      aim.stop(player);
      // a player without a speed attribute just aims
      var noSpeed = spy(player);
      doReturn(null).when(noSpeed).getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
      Aiming.zoom(noSpeed, true);
    } finally {
      Cache.aimingEnabled = wasOn;
      Cache.aimSlow = oldSlow;
    }
    }
  }

  @Test
  void scopedGunsLookThroughTheScopeAndFireOnRelease() {
    try (var preserver = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var modelData = mockStatic(net.tfminecraft.gunsandgadgets.util.LegacyModelData.class)) {
    boolean wasOn = Cache.aimingEnabled;
    try {
      Cache.aimingEnabled = true;
      loaded();
      var scope = mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class);
      when(scope.hasOption(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.PartOption.SCOPED)).thenReturn(true);
      try (var looks = mockStatic(net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.class);
          var shooter = mockStatic(ProjectileShooter.class)) {
        looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(null);
        assertFalse(Aiming.scoped(gun));
        looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(java.util.List.of(mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class)));
        assertFalse(Aiming.scoped(gun));
        looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(java.util.List.of(scope));
        when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.ADS)).thenReturn(new ItemStack(Material.CROSSBOW));
        var aim = manager.getAiming();
        player.setSneaking(true);
        // aimed: still the two-handed (crossbow) gun, its scope not raised
        aim.start(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        assertEquals(Material.CROSSBOW, held.getType());
        assertFalse(aim.isScoped(player, held));
        assertTrue(aim.canRaiseScope(player, held));
        assertFalse(aim.canRaiseScope(player, new ItemStack(Material.STICK)));
        // right-click raises the scope: cancelled, no shot, the gun becomes a spyglass
        Cancellable raise = use();
        verify(raise).setCancelled(true);
        shooter.verifyNoInteractions();
        held = player.getInventory().getItemInMainHand();
        assertEquals(Material.SPYGLASS, held.getType());
        assertTrue(aim.isScoped(player, held));
        assertFalse(aim.isScoped(player, null));
        assertFalse(aim.canRaiseScope(player, held));
        // the held key's repeat looks through it: not fired, not cancelled
        Cancellable click = use();
        verify(click, never()).setCancelled(true);
        shooter.verifyNoInteractions();
        int oldSettle = Cache.scopeSettleTicks;
        try {
          Cache.scopeSettleTicks = 12;
          // a quick tap lowers the scope back to the aimed gun without a shot
          manager.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(player, held, 5));
          shooter.verifyNoInteractions();
          assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
          assertTrue(aim.isAiming(player));
          use();
          held = player.getInventory().getItemInMainHand();
          assertEquals(Material.SPYGLASS, held.getType());
          // the steady cue comes once per hold, when the scope has been up long enough
          var looker = mock(org.bukkit.entity.Player.class);
          when(looker.getUniqueId()).thenReturn(player.getUniqueId());
          when(looker.getLocation()).thenReturn(player.getLocation());
          aim.settle(looker, held);
          when(looker.isHandRaised()).thenReturn(true);
          when(looker.getActiveItemUsedTime()).thenReturn(4);
          aim.settle(looker, held);
          when(looker.getActiveItemUsedTime()).thenReturn(12);
          aim.settle(looker, held);
          aim.settle(looker, held);
          verify(looker, times(1)).playSound(any(org.bukkit.Location.class), eq(org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON), anyFloat(), anyFloat());
          aim.settle(looker, gun);
          aim.settle(looker, held);
          verify(looker, times(2)).playSound(any(org.bukkit.Location.class), eq(org.bukkit.Sound.BLOCK_TRIPWIRE_CLICK_ON), anyFloat(), anyFloat());
        } finally {
          Cache.scopeSettleTicks = oldSettle;
        }
        // releasing it once steady fires, and the scope comes down to the aimed gun (a shot is left)
        manager.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(player, held, 20));
        shooter.verify(() -> ProjectileShooter.shoot(eq(player), any(), any()));
        assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
        assertTrue(aim.isAiming(player));

        // a tap nobody looks through comes down by itself after the grace ticks, not while looked through
        use();
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        var idle = spy(player);
        doReturn(false).when(idle).isHandRaised();
        aim.tick(idle); // within the grace
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        server.getScheduler().performTicks(Aiming.SCOPE_GRACE_TICKS + 1);
        var looking = spy(player);
        doReturn(true).when(looking).isHandRaised();
        doReturn(0).when(looking).getActiveItemUsedTime();
        aim.tick(looking); // looked through: stays raised
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        aim.tick(idle);
        assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
        aim.tick(idle); // nothing raised
        // lowering with nothing raised, an emptied gun, or a vanished design changes nothing
        aim.lowerScope(player);
        assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
        use();
        gun = player.getInventory().getItemInMainHand();
        setInt("bullets_loaded", 0);
        player.getInventory().setItemInMainHand(gun);
        aim.lowerScope(player);
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        gun = player.getInventory().getItemInMainHand();
        setInt("bullets_loaded", 1);
        set("skin_id", "missing");
        player.getInventory().setItemInMainHand(gun);
        aim.lowerScope(player);
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        // a vanished design can't be raised either
        player.getInventory().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        set("skin_id", "missing");
        aim.raiseScope(player);
        set("skin_id", "test");
        loaded();
        // releasing anything else does nothing
        manager.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(player, new ItemStack(Material.BOW), 5));
        // leaving the game ends the aim
        manager.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null, org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(aim.isAiming(player));
        verify(skin, never()).parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.SCOPE);
        // a skin with a scope pose shows it while scoped (still a spyglass, whatever its material)
        when(skin.hasScopePose()).thenReturn(true);
        when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.SCOPE)).thenReturn(new ItemStack(Material.STICK));
        loaded();
        aim.start(player);
        verify(skin, never()).parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.SCOPE);
        use();
        verify(skin).parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.SCOPE);
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        assertTrue(aim.isScoped(player, player.getInventory().getItemInMainHand()));
        aim.stop(player);
        server.getScheduler().performTicks(Aiming.SCOPE_GRACE_TICKS + 1); // past the held-key window
        // an unscoped gun has no scope to raise, even when the skin has a scope pose
        looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(null);
        loaded();
        aim.start(player);
        assertFalse(aim.canRaiseScope(player, player.getInventory().getItemInMainHand()));
        verify(skin, times(1)).parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.SCOPE);
        verify(skin, atLeastOnce()).parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.ADS);
        aim.stop(player);
        // with a gun in the off hand the scope isn't raised (its clicks are cancelled, so it could never be
        // looked through), and nothing fires
        looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(java.util.List.of(scope));
        when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.ADS)).thenReturn(new ItemStack(Material.CROSSBOW));
        loaded();
        aim.start(player);
        player.getInventory().setItemInOffHand(gun.clone());
        verify(use(), atLeastOnce()).setCancelled(true);
        assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
        player.getInventory().setItemInOffHand(null);
        // the aim ending with the scope raised (an un-crouch while looking through): right-click still held
        // is swallowed, not fired from the hip; each swallowed repeat extends that until the key is let go
        use();
        assertEquals(Material.SPYGLASS, player.getInventory().getItemInMainHand().getType());
        aim.stop(player);
        assertFalse(aim.isAiming(player));
        server.getScheduler().performTicks(6);
        use(); // a repeat: swallowed, and the window extended
        server.getScheduler().performTicks(6);
        assertTrue(aim.holdsAfterScope(player)); // past the first window: only the swallowed repeat kept it
        server.getScheduler().performTicks(Aiming.SCOPE_GRACE_TICKS + 1);
        assertFalse(aim.holdsAfterScope(player)); // let go: over
        assertFalse(aim.holdsAfterScope(player));
        // a stopped aim leaves nothing raised: aiming again, the grace doesn't touch the crossbow
        loaded();
        aim.start(player);
        server.getScheduler().performTicks(Aiming.SCOPE_GRACE_TICKS + 1);
        aim.tick(idle);
        assertEquals(Material.CROSSBOW, player.getInventory().getItemInMainHand().getType());
        aim.stop(player);
        // an ads model that isn't a crossbow is shown as it is: nothing to charge
        when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.ADS)).thenReturn(new ItemStack(Material.BOW));
        assertEquals(Material.BOW, manager.showAimed(new ItemStack(Material.CROSSBOW), skin).getType());
      }
    } finally {
      Cache.aimingEnabled = wasOn;
    }
    }
  }

  @Test
  void anAimedGunMovedDroppedOrLostOnDeathIsLowered() {
    try (var preserver = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var modelData = mockStatic(net.tfminecraft.gunsandgadgets.util.LegacyModelData.class);
        var looks = mockStatic(net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.class)) {
    boolean wasOn = Cache.aimingEnabled;
    try {
      Cache.aimingEnabled = true;
      var scope = mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class);
      when(scope.hasOption(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.PartOption.SCOPED)).thenReturn(true);
      looks.when(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stampedParts(any())).thenReturn(java.util.List.of(scope));
      when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.AIM)).thenReturn(new ItemStack(Material.CROSSBOW));
      when(skin.parseModel(net.tfminecraft.gunsandgadgets.guns.skins.SkinState.CARRY)).thenReturn(new ItemStack(Material.STONE_HOE));
      var aim = manager.getAiming();
      player.setSneaking(true);
      assertFalse(aim.isAimedGun(player, gun)); // not aiming

      // moved to another slot while scoped: it is lowered there, not left a spyglass
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      ItemStack held = player.getInventory().getItemInMainHand();
      assertEquals(Material.SPYGLASS, held.getType());
      assertTrue(aim.isAimedGun(player, held));
      assertFalse(aim.isAimedGun(player, new ItemStack(Material.STICK)));
      player.getInventory().setItemInMainHand(null);
      player.getInventory().setItem(7, held);
      aim.tick(player);
      assertFalse(aim.isAiming(player));
      assertEquals(Material.CROSSBOW, player.getInventory().getItem(7).getType());
      player.getInventory().setItem(7, null);

      // on the cursor, emptied: back to the carry look
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      gun = player.getInventory().getItemInMainHand();
      player.getInventory().setItemInMainHand(null);
      setInt("bullets_loaded", 0);
      player.setItemOnCursor(gun);
      aim.tick(player);
      assertEquals(Material.STONE_HOE, player.getItemOnCursor().getType());

      // a design that vanished stays as it is, in a slot or on the cursor
      set("skin_id", "test");
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      gun = player.getInventory().getItemInMainHand();
      player.getInventory().setItemInMainHand(null);
      set("skin_id", "missing");
      player.getInventory().setItem(7, gun);
      aim.tick(player);
      assertEquals(Material.SPYGLASS, player.getInventory().getItem(7).getType());
      player.getInventory().setItem(7, null);
      set("skin_id", "test");
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      gun = player.getInventory().getItemInMainHand();
      player.getInventory().setItemInMainHand(null);
      set("skin_id", "missing");
      player.setItemOnCursor(gun);
      aim.tick(player);
      assertEquals(Material.SPYGLASS, player.getItemOnCursor().getType());
      // gone from the inventory entirely: nothing to lower
      player.setItemOnCursor(null);
      set("skin_id", "test");
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      player.getInventory().setItemInMainHand(null);
      aim.tick(player);
      assertFalse(aim.isAiming(player));

      // dropped while scoped: it lands lowered
      loaded();
      aim.start(player);
      aim.raiseScope(player);
      held = player.getInventory().getItemInMainHand();
      var drop = mock(org.bukkit.event.player.PlayerDropItemEvent.class);
      when(drop.getPlayer()).thenReturn(player);
      var entity = mock(org.bukkit.entity.Item.class);
      when(drop.getItemDrop()).thenReturn(entity);
      when(entity.getItemStack()).thenReturn(held);
      ItemStack spare = held.clone(); // still a spyglass, for the death below
      manager.onDropLower(drop);
      verify(entity).setItemStack(argThat(i -> i.getType() == Material.CROSSBOW));
      when(entity.getItemStack()).thenReturn(new ItemStack(Material.DIRT));
      manager.onDropLower(drop);
      gun = held.clone();
      set("skin_id", "missing");
      when(entity.getItemStack()).thenReturn(gun);
      manager.onDropLower(drop);
      when(drop.getItemDrop()).thenReturn(null);
      manager.onDropLower(drop);
      verify(entity, times(1)).setItemStack(any());

      // dying while scoped: the gun drops lowered and the aim ends
      var death = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
      when(death.getEntity()).thenReturn(player);
      assertEquals(Material.SPYGLASS, spare.getType());
      var drops = new java.util.ArrayList<>(java.util.List.of(spare, new ItemStack(Material.DIRT), gun));
      when(death.getDrops()).thenReturn(drops);
      manager.onDeath(death);
      assertEquals(Material.CROSSBOW, drops.get(0).getType());
      assertEquals(Material.DIRT, drops.get(1).getType());
      assertSame(gun, drops.get(2)); // its design vanished: left as it is
      assertFalse(aim.isAiming(player));
    } finally {
      Cache.aimingEnabled = wasOn;
    }
    }
  }

  @Test
  void crouchEventsAndTheSwapKeyOnlyActOnGuns() {
    manager.onSneak(new org.bukkit.event.player.PlayerToggleSneakEvent(player, true));
    manager.onSneak(new org.bukkit.event.player.PlayerToggleSneakEvent(player, false));
    player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    var event = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player, null, null);
    manager.onSwapHands(event);
    assertFalse(event.isCancelled());
    // mid-reload the key is swallowed without switching shot
    player.getInventory().setItemInMainHand(gun);
    use();
    var during = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player, null, null);
    manager.onSwapHands(during);
    assertTrue(during.isCancelled());
    assertNull(
        player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer()
            .get(key("ammo_selected"), PersistentDataType.STRING));
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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(CharacterSessionEndEvent.Reason.class)
  void everySessionEndRefundsOutgoingAmmoExactlyOnce(CharacterSessionEndEvent.Reason reason) {
    use();
    assertEquals(2, player.getInventory().getItem(1).getAmount());
    var listener = new CharacterSessionListener(manager);
    var session = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 1);
    var event = new CharacterSessionEndEvent(player, session, reason);
    listener.onSessionEnd(event);
    listener.onSessionEnd(event);
    manager.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, (String) null));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void reloadWaitsForReadyIdentityAndCannotFinishUnderANewerSessionOfTheSameCharacter() {
    var identity = mock(CharacterIdentity.class);
    var provider = MockBukkit.createMockPlugin("IdentityProvider");
    server.getServicesManager().register(CharacterIdentity.class, identity, provider, org.bukkit.plugin.ServicePriority.Normal);
    var ref = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
    var current = new java.util.concurrent.atomic.AtomicReference<CharacterSession>();
    when(identity.currentSession(player)).thenAnswer(call -> Optional.ofNullable(current.get()));
    when(identity.isCurrent(any())).thenAnswer(call -> call.getArgument(0).equals(current.get()));
    use();
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    current.set(new CharacterSession(ref, 1));
    use();
    server.getScheduler().performTicks(1);
    assertEquals(2, player.getInventory().getItem(1).getAmount());
    current.set(new CharacterSession(ref, 3));
    server.getScheduler().performTicks(12);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    use();
    server.getScheduler().performTicks(12);
    assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(2, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void providerAppearanceCancelsAnAccountFallbackReloadAndReturnsItsReservationOnce() {
    use();
    var identity = mock(CharacterIdentity.class);
    var provider = MockBukkit.createMockPlugin("IdentityProvider");
    server.getServicesManager().register(CharacterIdentity.class, identity, provider, org.bukkit.plugin.ServicePriority.Normal);
    server.getScheduler().performTicks(12);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    manager.cancelActiveReload(player);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void disconnectedReloadOwnerCannotCompleteTheOldTimer() {
    use();
    player.disconnect();
    server.getScheduler().performTicks(12);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
  }

  @Test
  void quittingCancelsReloadBeforeTheInventoryIsSaved() {
    use();
    manager.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, (String) null));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
  }

  @Test
  void characterSwitchWithoutReloadLeavesInventoryAlone() {
    manager.cancelActiveReload(player);
    manager.cancelActiveReload(null);
    assertEquals(5, player.getInventory().getItem(1).getAmount());
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
    PartLoader.clear();
    PartLoader.getOrdered().add(mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class)); // parts loaded cleanly
    try {
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("missing", 1)), 1)
          .applyTo(gun);
      player.getInventory().setItemInMainHand(gun);
      verify(use()).setCancelled(true);
      assertTrue(
          net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
              player.getInventory().getItemInMainHand()));
      assertEquals(5, player.getInventory().getItem(1).getAmount());
      // a forged part gone missing is a config mistake: the shot is refused but nothing is marked,
      // so the gun works again once the config is fixed
      gun = player.getInventory().getItemInMainHand();
      net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.clearBroken(gun);
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("barrel@steel", 1)), 1)
          .applyTo(gun);
      player.getInventory().setItemInMainHand(gun);
      verify(use()).setCancelled(true);
      assertFalse(
          net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
              player.getInventory().getItemInMainHand()));
      // so is any part while the part files failed to load
      PartLoader.clear();
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("missing", 1)), 1)
          .applyTo(gun);
      player.getInventory().setItemInMainHand(gun);
      verify(use()).setCancelled(true);
      assertFalse(
          net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
              player.getInventory().getItemInMainHand()));
      assertEquals(5, player.getInventory().getItem(1).getAmount());
    } finally {
      PartLoader.clear();
    }
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
  void forgedSkinsSetThePoseModelAndKeepThePartStrings() {
    when(skin.isForged()).thenReturn(true);
    when(skin.getForgedModel(SkinState.AIM)).thenReturn(NamespacedKey.fromString("tfmc_guns:rifle_aim"));
    when(skin.parseModel(SkinState.AIM)).thenReturn(new ItemStack(Material.CROSSBOW));
    var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
    var item = spy(new ItemStack(Material.STONE_HOE));
    doNothing().when(item).setType(any(Material.class));
    doReturn(meta).when(item).getItemMeta();
    doReturn(true).when(item).setItemMeta(any());
    try (var looks = mockStatic(net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.class)) {
      assertSame(item, manager.applyModel(item, skin, SkinState.AIM));
      verify(item).setType(Material.CROSSBOW);
      looks.verify(() -> net.tfminecraft.gunsandgadgets.guns.skins.ForgedLook.stamp(eq(meta), eq(skin), eq(SkinState.AIM), any()));
      verify(item).setItemMeta(meta);
    }
    // an item without meta just changes material
    var bare = spy(new ItemStack(Material.STONE_HOE));
    doNothing().when(bare).setType(any(Material.class));
    doReturn(null).when(bare).getItemMeta();
    assertSame(bare, manager.applyModel(bare, skin, SkinState.AIM));
    verify(bare, never()).setItemMeta(any());
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
  void deferredSlotCheckRequiresTheSameReadySessionIncludingReturningToTheSameCharacter() {
    var identity = mock(CharacterIdentity.class);
    var provider = MockBukkit.createMockPlugin("IdentityProvider");
    server.getServicesManager().register(CharacterIdentity.class, identity, provider, org.bukkit.plugin.ServicePriority.Normal);
    var ref = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
    var current = new java.util.concurrent.atomic.AtomicReference<>(new CharacterSession(ref, 1));
    when(identity.currentSession(player)).thenAnswer(call -> Optional.ofNullable(current.get()));
    when(identity.isCurrent(any())).thenAnswer(call -> call.getArgument(0).equals(current.get()));
    var change = new org.bukkit.event.player.PlayerItemHeldEvent(player, 2, 0);
    try (var marker = mockStatic(net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.class)) {
      marker.when(() -> net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(any())).thenReturn(true);
      manager.onSlotChange(change);
      current.set(new CharacterSession(ref, 3));
      server.getScheduler().performTicks(1);
      marker.verifyNoInteractions();
      manager.onSlotChange(change);
      server.getScheduler().performTicks(1);
      marker.verify(() -> net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(any()));
      marker.clearInvocations();
      current.set(null);
      manager.onSlotChange(change);
      server.getScheduler().performTicks(1);
      marker.verifyNoInteractions();
      current.set(new CharacterSession(ref, 4));
      manager.onSlotChange(change);
      server.getServicesManager().unregister(identity);
      server.getScheduler().performTicks(1);
      marker.verifyNoInteractions();
    }
  }

  @Test
  void deferredSlotCheckStopsIfAProviderAppearsOrThePlayerDisconnects() {
    var change = new org.bukkit.event.player.PlayerItemHeldEvent(player, 2, 0);
    try (var marker = mockStatic(net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.class)) {
      manager.onSlotChange(change);
      var identity = mock(CharacterIdentity.class);
      var provider = MockBukkit.createMockPlugin("IdentityProvider");
      server.getServicesManager().register(CharacterIdentity.class, identity, provider, org.bukkit.plugin.ServicePriority.Normal);
      server.getScheduler().performTicks(1);
      marker.verifyNoInteractions();
      server.getServicesManager().unregister(identity);
      manager.onSlotChange(change);
      player.disconnect();
      server.getScheduler().performTicks(1);
      marker.verifyNoInteractions();
    }
  }

  @Test
  void disablingBeforeProviderShutdownReturnsReservedAmmoWithoutWaitingForATimer() throws Exception {
    use();
    var field = GunsAndGadgets.class.getDeclaredField("gunManager");
    field.setAccessible(true);
    field.set(plugin, manager);
    when(plugin.getServer()).thenReturn(server);
    doCallRealMethod().when(plugin).onDisable();
    plugin.onDisable();
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "reload_amount"));
    server.getScheduler().performTicks(12);
    assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
  }

  @Test
  void deferredSlotCheckLeavesAnEmptiedSlotAlone() {
    var online = mock(org.bukkit.entity.Player.class);
    var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
    when(online.isOnline()).thenReturn(true);
    when(online.getInventory()).thenReturn(inventory);
    for (ItemStack emptyOrPlain : Arrays.asList(null, mock(ItemStack.class))) {
      when(inventory.getItem(2)).thenReturn(emptyOrPlain);
      manager.onSlotChange(new org.bukkit.event.player.PlayerItemHeldEvent(online, 0, 2));
      server.getScheduler().performTicks(1);
    }
    verify(inventory, times(2)).getItem(2);
    verify(inventory, never()).setItem(anyInt(), any());
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

  org.bukkit.event.player.PlayerSwapHandItemsEvent swap() {
    var event =
        new org.bukkit.event.player.PlayerSwapHandItemsEvent(
            player, player.getInventory().getItemInOffHand(), player.getInventory().getItemInMainHand());
    manager.onSwapHands(event);
    return event;
  }

  @Test
  void swapHandsKeyCyclesCarriedCalibersWithoutFiringOrReloading() {
    threeCalibers();
    setInt("bullets_loaded", 1);
    set("ammo_loaded", "iron");
    player.getInventory().setItemInMainHand(gun);
    player.getInventory().setItem(2, new ItemStack(Material.COPPER_INGOT, 4));
    try (var shooter = mockStatic(ProjectileShooter.class)) {
      assertTrue(swap().isCancelled());
      // Iron loads by default, steel is not carried, so the first pick is bronze.
      assertEquals("bronze", selected());
      assertTrue(actionBar().matches("Next load: .*Copper.* \\(4 carried\\)"));
      // Further events of the same press, even a tick or two later, must not switch again.
      swap();
      server.getScheduler().performTicks(2);
      swap();
      assertEquals("bronze", selected());
      assertNull(player.nextActionBar());
      server.getScheduler().performTicks(1);
      swap();
      assertEquals("iron", selected());
      assertTrue(actionBar().contains("(5 carried)"));
      shooter.verifyNoInteractions();
    }
    assertEquals(1, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    assertEquals(5, player.getInventory().getItem(1).getAmount());
    assertEquals(4, player.getInventory().getItem(2).getAmount());
  }

  @Test
  void swapHandsWithoutUsableAmmoSaysSo() {
    threeCalibers();
    player.getInventory().setItem(1, null);
    swap();
    assertEquals("You carry no shot this gun can fire.", actionBar());
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
        actionBar().matches("You have no .*Gold.* left\\. Press .+ to choose another\\."));
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
    swap();
    verify(mmoPlayer.getActionBar()).show(30, 40L, (String) null);
    assertNotNull(player.nextActionBar());
    when(mmoPlayer.getActionBar().show(anyInt(), anyLong(), nullable(String.class))).thenReturn(false);
    server.getScheduler().performTicks(3);
    swap();
    assertEquals("iron", selected());
    assertNull(player.nextActionBar());
  }

  @Test
  void singleCaliberReloadShowsNoAmmoName() {
    use();
    assertNull(player.nextActionBar());
  }

  @Test
  void retiredPartsBlockUseOnlyWhileTheSwitchIsOn() {
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    var retired = mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class);
    when(retired.isDisabled()).thenReturn(true);
    when(retired.getId()).thenReturn("old");
    var live = mock(net.tfminecraft.gunsandgadgets.guns.parts.GunPart.class);
    PartLoader.get().put("old", retired);
    PartLoader.get().put("live", live);
    boolean previous = net.tfminecraft.gunsandgadgets.cache.Cache.retireDisabledPartGuns;
    try {
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(
                  new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("live", 1),
                  new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("old", 1)),
              1)
          .applyTo(gun);
      player.getInventory().setItemInMainHand(gun);
      net.tfminecraft.gunsandgadgets.cache.Cache.retireDisabledPartGuns = true;
      use();
      use();
      server.getScheduler().performTicks(12);
      assertEquals(0, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
      assertEquals("§cThis gun's design has been retired. It can no longer be used.", player.nextMessage());
      assertNull(player.nextMessage());
      assertFalse(
          net.tfminecraft.gunsandgadgets.utils.GunBrokenMarker.isBroken(
              player.getInventory().getItemInMainHand()));
      // Turning the switch off restores the gun: nothing was written to it.
      net.tfminecraft.gunsandgadgets.cache.Cache.retireDisabledPartGuns = false;
      use();
      server.getScheduler().performTicks(12);
      assertEquals(3, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
      // A gun made only from live parts is never retired.
      net.tfminecraft.gunsandgadgets.cache.Cache.retireDisabledPartGuns = true;
      var fresh = new ItemStack(gun);
      new net.tfminecraft.gunsandgadgets.guns.data.GunCraftProvenance(
              List.of(new net.tfminecraft.gunsandgadgets.guns.data.GGCraftPart("live", 1)), 1)
          .applyTo(fresh);
      var m = fresh.getItemMeta();
      m.getPersistentDataContainer().remove(key("bullets_loaded"));
      fresh.setItemMeta(m);
      player.getInventory().setItemInMainHand(fresh);
      use();
      server.getScheduler().performTicks(12);
      // Two shots were left in the pouch after the first reload.
      assertEquals(2, readInt(player.getInventory().getItemInMainHand(), "bullets_loaded"));
    } finally {
      net.tfminecraft.gunsandgadgets.cache.Cache.retireDisabledPartGuns = previous;
      PartLoader.get().remove("old");
      PartLoader.get().remove("live");
    }
  }
}

package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.loader.*;
import net.tfminecraft.gunsandgadgets.manager.*;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import net.tfminecraft.gunsandgadgets.utils.GunStatRefresher;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GunsLifecycleCommandTest extends GunsTestSupport {
  @Test
  void lifecycleCreatesPreservesReloadsAndRegistersAllPluginCollaborators() throws Exception {
    for (String dependency : List.of("TLibs", "MythicLib", "MMOItems", "MMOCore")) {
      MockBukkit.createMockPlugin(dependency);
    }
    plugins.when(GunsAndGadgets::getInstance).thenCallRealMethod();
    try (var config = mockConstruction(ConfigLoader.class);
        var ammunition = mockConstruction(AmmunitionLoader.class);
        var partData = mockConstruction(PartDataLoader.class);
        var parts = mockConstruction(PartLoader.class);
        var skins = mockConstruction(SkinLoader.class);
        var crafting = mockConstruction(CraftingManager.class);
        var guns = mockConstruction(GunManager.class);
        var inventories = mockConstruction(InventoryManager.class)) {
      GunsAndGadgets actual = MockBukkit.load(GunsAndGadgets.class);
      assertSame(actual, GunsAndGadgets.getInstance());
      assertSame(guns.constructed().getFirst(), actual.getGunManager());
      assertNotNull(actual.getCommand("gg").getExecutor());
      assertNotNull(actual.getCommand("gg").getTabCompleter());
      for (String resource :
          List.of("config.yml", "part-types.yml", "parts.yml", "skins.yml", "ammunition.yml")) {
        assertTrue(Files.exists(actual.getDataFolder().toPath().resolve(resource)), resource);
      }
      var custom = actual.getDataFolder().toPath().resolve("config.yml");
      Files.writeString(custom, "custom: preserved\n");
      actual.createConfigs();
      actual.reload();
      assertEquals("custom: preserved\n", Files.readString(custom));
      verify(config.constructed().getFirst(), times(2)).loadConfig(any());
      verify(ammunition.constructed().getFirst(), times(2)).load(any());
      verify(partData.constructed().getFirst(), times(2)).load(any());
      verify(parts.constructed().getFirst(), times(2)).load(any());
      verify(skins.constructed().getFirst(), times(2)).load(any());
      SkinData skin = mock(SkinData.class);
      SkinLoader.get().put("skin", skin);
      assertSame(skin, actual.getSkin("skin"));
      actual.onDisable();
      try (var paths = Files.walk(actual.getDataFolder().toPath())) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
      actual.createConfigs();
      assertTrue(Files.isDirectory(actual.getDataFolder().toPath().resolve("data")));
    }
  }

  @Test
  void missingCommandRegistrationLogsFailureWithoutThrowing() {
    doCallRealMethod().when(plugin).registerCommands();
    assertDoesNotThrow(plugin::registerCommands);
  }

  void execute(CommandSender sender, String... args) {
    assertTrue(new GgCommand().onCommand(sender, mock(Command.class), "gg", args));
  }

  @Test
  void reloadEnforcesPermissionAndUnknownInputShowsUsage() {
    CommandSender sender = mock(CommandSender.class);
    execute(sender);
    execute(sender, "unknown");
    verify(sender, times(2)).sendMessage("§eUsage: /gg reload | /gg refresh");
    execute(sender, "reload");
    verify(plugin, never()).reload();
    verify(sender).sendMessage(contains("do not have permission to reload"));
    when(sender.hasPermission("gunsandgadgets.reload")).thenReturn(true);
    execute(sender, "RELOAD");
    verify(plugin).reload();
    verify(sender).sendMessage(contains("Reloaded configs"));
  }

  @Test
  void refreshEnforcesPermissionPlayerAndManagedItemRequirements() {
    CommandSender console = mock(CommandSender.class);
    execute(console, "refresh");
    verify(console).sendMessage(contains("do not have permission to refresh"));
    when(console.hasPermission("gunsandgadgets.reload")).thenReturn(true);
    execute(console, "refresh");
    verify(console).sendMessage(contains("only be used by a player"));
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(player.hasPermission("gunsandgadgets.reload")).thenReturn(true);
    when(player.getInventory()).thenReturn(inventory);
    execute(player, "refresh");
    verify(player).sendMessage("§cThat item is not a managed crafted gun.");
  }

  @Test
  void refreshReportsFailureUnchangedOrWritesRebuiltGun() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack original = new ItemStack(Material.STICK), rebuilt = new ItemStack(Material.BLAZE_ROD);
    when(player.hasPermission("gunsandgadgets.reload")).thenReturn(true);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(original);
    try (var refresh = mockStatic(GunStatRefresher.class, CALLS_REAL_METHODS)) {
      refresh.when(() -> GunStatRefresher.isManaged(original)).thenReturn(true);
      refresh
          .when(() -> GunStatRefresher.refresh(original, true))
          .thenReturn(
              GunStatRefresher.RefreshResult.failed("missing receiver"),
              GunStatRefresher.RefreshResult.unchanged(),
              GunStatRefresher.RefreshResult.updated(rebuilt, List.of()));
      execute(player, "refresh");
      verify(player).sendMessage("§cRefresh failed: missing receiver");
      execute(player, "refresh");
      verify(player).sendMessage("§eNothing to update on that gun.");
      execute(player, "refresh");
      verify(inventory).setItemInMainHand(rebuilt);
      verify(player).sendMessage("§aGun stats refreshed.");
    }
  }

  @Test
  void completionRespectsPermissionArgumentCountAndPrefix() {
    GgCommand command = new GgCommand();
    CommandSender sender = mock(CommandSender.class);
    assertTrue(command.onTabComplete(sender, null, "gg", new String[] {""}).isEmpty());
    when(sender.hasPermission("gunsandgadgets.reload")).thenReturn(true);
    assertEquals(
        List.of("reload", "refresh"),
        command.onTabComplete(sender, null, "gg", new String[] {"RE"}));
    assertEquals(
        List.of("reload"), command.onTabComplete(sender, null, "gg", new String[] {"rel"}));
    assertEquals(
        List.of("refresh"), command.onTabComplete(sender, null, "gg", new String[] {"ref"}));
    assertTrue(command.onTabComplete(sender, null, "gg", new String[] {"z"}).isEmpty());
    assertTrue(command.onTabComplete(sender, null, "gg", new String[] {"reload", ""}).isEmpty());
  }
}

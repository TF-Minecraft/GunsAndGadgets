package net.tfminecraft.gunsandgadgets;

import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.logging.Logger;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.loader.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

abstract class GunsTestSupport {
  @TempDir Path temp;
  ServerMock server;
  GunsAndGadgets plugin;
  MockedStatic<GunsAndGadgets> plugins;

  @BeforeEach
  void startServer() {
    server = MockBukkit.mock();
    plugin = mock(GunsAndGadgets.class);
    when(plugin.getName()).thenReturn("GunsAndGadgets");
    when(plugin.namespace()).thenReturn("gunsandgadgets");
    when(plugin.getLogger()).thenReturn(Logger.getLogger("GunsTest"));
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    when(plugin.isEnabled()).thenReturn(true);
    plugins = mockStatic(GunsAndGadgets.class, CALLS_REAL_METHODS);
    plugins.when(GunsAndGadgets::getInstance).thenReturn(plugin);
    GunsAndGadgets.getRevisionTracker().load(temp.toFile());
    clear();
  }

  @AfterEach
  void stopServer() {
    try {
      clear();
      MockBukkit.unmock();
    } finally {
      plugins.close();
    }
  }

  void clear() {
    Cache.clear();
    PartDataLoader.clear();
    PartLoader.clear();
    AmmunitionLoader.clear();
    SkinLoader.clear();
  }

  YamlConfiguration yaml(String text) throws Exception {
    YamlConfiguration cfg = new YamlConfiguration();
    cfg.loadFromString(text);
    return cfg;
  }

  Path file(String name, String text) throws Exception {
    return Files.writeString(temp.resolve(name), text);
  }
}

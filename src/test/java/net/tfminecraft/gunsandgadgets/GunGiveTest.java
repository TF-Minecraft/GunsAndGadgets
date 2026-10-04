package net.tfminecraft.gunsandgadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.parts.*;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.manager.*;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GunGiveTest extends GunsTestSupport {
    CommandSender sender;
    PlayerMock target;
    GgCommand command=new GgCommand();
    String[] valid={"give","Gunner","rifle","barrel"};
    @BeforeEach void setupGive() {
        sender=mock(CommandSender.class); target=server.addPlayer("Gunner");
        Cache.givePermission="custom.staff"; when(sender.hasPermission("custom.staff")).thenReturn(true);
        Cache.requiredParts.put(GunType.RIFLE,List.of("barrel"));
        PartLoader.get().put("barrel",part("barrel",false,GunType.RIFLE));
    }
    @AfterEach void resetPermission() { Cache.givePermission="gunsandgadgets.give"; }
    GunPart part(String category,boolean disabled,GunType type) {
        GunPart p=mock(GunPart.class); PartData data=mock(PartData.class);
        when(data.getId()).thenReturn(category); when(p.getPartType()).thenReturn(data);
        when(p.getGunTypes()).thenReturn(Set.of(type)); when(p.isDisabled()).thenReturn(disabled); return p;
    }
    void run(String... args) { assertTrue(command.onCommand(sender,null,"gg",args)); }
    void reject(int index,String value,String message) {
        clearInvocations(sender); String[] args=valid.clone();args[index]=value;run(args);
        verify(sender).sendMessage(contains(message)); assertEquals(-1,target.getInventory().first(Material.STICK));
    }
    @Test void validatesAndGivesWithoutReloadPermission() throws Exception {
        var ctor=GunGiveCommand.class.getDeclaredConstructor();ctor.setAccessible(true);ctor.newInstance();
        Cache.givePermission="";run(valid);Cache.givePermission="custom.staff";
        when(sender.hasPermission("custom.staff")).thenReturn(false);run(valid);
        when(sender.hasPermission("custom.staff")).thenReturn(true);run("give");
        reject(1,"Offline","online");reject(2,"unknown","type");reject(3,"unknown","part");
        PartLoader.get().put("disabled",part("barrel",true,GunType.RIFLE));reject(3,"disabled","part");
        PartLoader.get().put("pistol",part("barrel",false,GunType.PISTOL));reject(3,"pistol","part");
        run("give","Gunner","rifle","barrel","barrel");
        PartLoader.get().put("stock",part("stock",false,GunType.RIFLE));reject(3,"stock","categories");
        try(var builders=mockConstruction(InventoryManager.class,(b,c)->when(b.hasClassConflict(any(),any())).thenReturn(true))) {
            run(valid);verify(sender).sendMessage(contains("conflicting"));
        }
        for(int i=0;i<36;i++)target.getInventory().setItem(i,new ItemStack(Material.STONE));
        clearInvocations(sender);run(valid);verify(sender).sendMessage(contains("empty inventory"));target.getInventory().clear(); target.getInventory().setItem(0,new ItemStack(Material.AIR));
        for(Material material:List.of(Material.AIR,Material.BARRIER,Material.STICK)) {
            try(var builders=mockConstruction(InventoryManager.class,(b,c)->when(b.createOutputItem(any(),any(),eq(false))).thenReturn(new ItemStack(material)))) { run(valid); }
        }
        try(var builders=mockConstruction(InventoryManager.class)) {run(valid);}
        ItemStack airSlot=mock(ItemStack.class); when(airSlot.getType()).thenReturn(Material.AIR);
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class); var builders=mockConstruction(InventoryManager.class)) {
            var airTarget=mock(org.bukkit.entity.Player.class); var inventory=mock(org.bukkit.inventory.PlayerInventory.class);
            when(airTarget.getInventory()).thenReturn(inventory);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{airSlot});
            bukkit.when(()->org.bukkit.Bukkit.getPlayerExact("Gunner")).thenReturn(airTarget);
            run(valid);
        }
        assertEquals(Map.of(),GunCraftInputs.readFrom(target.getInventory().getItem(target.getInventory().first(Material.STICK))));
        verify(sender).sendMessage(contains("Gave completed Rifle"));
    }
    List<String> tab(String... args) {return command.onTabComplete(sender,null,"gg",args);}
    @Test void completionUsesSeparatePermission() {
        assertTrue(tab().isEmpty()); assertTrue(tab("z").isEmpty());
        assertEquals(List.of("give"),tab("g"));assertEquals(List.of("Gunner"),tab("give","G"));
        assertEquals(List.of("rifle"),tab("give","Gunner","r"));
        assertEquals(List.of("barrel"),tab("give","Gunner","rifle","b"));
        assertTrue(tab("give").isEmpty());
        Cache.givePermission="";assertTrue(tab("give","").isEmpty());Cache.givePermission="custom.staff";
        when(sender.hasPermission("custom.staff")).thenReturn(false);assertTrue(tab("give","").isEmpty());assertTrue(tab("g").isEmpty());
    }
}

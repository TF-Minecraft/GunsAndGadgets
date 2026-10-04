package net.tfminecraft.gunsandgadgets.manager;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.gunsandgadgets.cache.Cache;
import net.tfminecraft.gunsandgadgets.guns.GunType;
import net.tfminecraft.gunsandgadgets.guns.data.GunCraftInputs;
import net.tfminecraft.gunsandgadgets.guns.parts.GunPart;
import net.tfminecraft.gunsandgadgets.loader.PartLoader;
import net.tfminecraft.gunsandgadgets.manager.inventory.InventoryManager;

/** Staff spawning retains normal assembly validation, skins and provenance. */
public final class GunGiveCommand {
    private GunGiveCommand() {}

    static boolean allowed(CommandSender sender) {
        return !Cache.givePermission.isBlank() && sender.hasPermission(Cache.givePermission);
    }

    static boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage("§cYou do not have permission to give guns.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /gg give <player> <rifle|pistol|shotgun|launcher> <part> [part...]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }
        GunType type;
        try {
            type = GunType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cUnknown gun type.");
            return true;
        }
        Map<String, GunPart> parts = new LinkedHashMap<>();
        for (int i = 3; i < args.length; i++) {
            GunPart part = PartLoader.getByString(args[i]);
            if (part == null || part.isDisabled() || !part.getGunTypes().contains(type)) {
                sender.sendMessage("§cUnknown, disabled or incompatible part: " + args[i]);
                return true;
            }
            if (parts.putIfAbsent(part.getPartType().getId().toLowerCase(Locale.ROOT), part) != null) {
                sender.sendMessage("§cOnly one part per category is allowed.");
                return true;
            }
        }
        List<String> required = Cache.requiredParts.getOrDefault(type, List.of());
        Set<String> categories = new HashSet<>();
        required.forEach(s -> categories.add(s.toLowerCase(Locale.ROOT)));
        if (!parts.keySet().equals(categories)) {
            sender.sendMessage("§cSupply exactly these part categories: " + String.join(", ", required));
            return true;
        }
        InventoryManager builder = new InventoryManager();
        if (builder.hasClassConflict(target, parts.values())) {
            sender.sendMessage("§cThese parts have conflicting class requirements.");
            return true;
        }
        if (Arrays.stream(target.getInventory().getStorageContents()).noneMatch(slot -> slot == null || slot.getType().isAir())) {
            sender.sendMessage("§cRecipient needs an empty inventory slot.");
            return true;
        }
        ItemStack item = builder.createOutputItem(type, parts.values(), false);
        if (item == null || item.getType().isAir() || item.getType() == Material.BARRIER) {
            sender.sendMessage("§cGun could not be built; check its template, parts and skin.");
            return true;
        }
        GunCraftInputs.applyTo(item, Map.of());
        target.getInventory().addItem(item);
        sender.sendMessage("§aGave completed " + type.getDisplayName() + " to " + target.getName() + ".");
        return true;
    }

    static List<String> complete(CommandSender sender, String[] args) {
        if (!allowed(sender)) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        else if (args.length == 3) Arrays.stream(GunType.values()).forEach(t -> options.add(t.name().toLowerCase(Locale.ROOT)));
        else if (args.length >= 4) options.addAll(PartLoader.get().keySet());
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}

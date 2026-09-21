package net.tfminecraft.gunsandgadgets.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.gunsandgadgets.attributes.AttributeData;
import net.tfminecraft.gunsandgadgets.guns.GunType;

public class Cache {
    public static int outputSlot;
    public static HashMap<GunType, String> outputItems = new HashMap<>();
    public static String station;
    public static boolean requireInput;

    public static boolean blockDamage;
    public static String rocketSound;
    public static boolean statRefreshDebug;

    public static Map<GunType, List<String>> requiredParts = new HashMap<>();

    public static List<AttributeData> attributes = new ArrayList<>();

    public static List<String> creators = Arrays.asList("drefvelin");

    /** Drop YAML-backed registries so a reload does not append. */
    public static void clear() {
        outputItems.clear();
        requiredParts.clear();
        attributes.clear();
    }
}

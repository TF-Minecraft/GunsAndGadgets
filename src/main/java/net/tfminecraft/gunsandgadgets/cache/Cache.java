package net.tfminecraft.gunsandgadgets.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.gunsandgadgets.attributes.AttributeData;
import net.tfminecraft.gunsandgadgets.guns.GunType;

public class Cache {
    public static String givePermission = "gunsandgadgets.give";

    public static int outputSlot;
    public static HashMap<GunType, String> outputItems = new HashMap<>();
    public static String station;
    public static boolean requireInput;

    public static boolean blockDamage;
    public static String rocketSound;
    public static boolean statRefreshDebug;

    // Shot handling (config.yml "handling"); these defaults are the behaviour before it existed.
    public static double headshotMultiplier = 1.5;
    public static double spreadSneaking = 1.0;
    public static double spreadMoving = 1.0;
    public static double spreadSprinting = 1.0;
    public static double spreadAirborne = 1.0;
    public static double spreadMounted = 1.0;
    public static double maxSpreadDegrees = Double.POSITIVE_INFINITY; // no cap unless configured
    public static double falloffStartDistance = 0.0;
    public static double falloffMinMultiplier = 1.0;
    // Accuracy above 5 tightens spread by 5% a point instead of the old line that reaches 0.07° at 30.
    public static boolean smoothAccuracy = false;
    // Pellet cone (a shotgun barrel's spread stat): each accuracy point tightens it, to a floor.
    public static double coneTightenPerAccuracy = 0.03;
    public static double coneMinFactor = 0.4;

    // Guns stamped with a disabled or missing part refuse to fire or reload.
    public static boolean retireDisabledPartGuns = false;

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

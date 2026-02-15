package me.vekster.lightanticheat.util.hook.plugin.simplehook;

import me.vekster.lightanticheat.util.hook.plugin.HookUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class McMMOHook extends HookUtil {

    private static final String PLUGIN_NAME = "mcMMO";
    private static final String ABILITY_API_CLASS = "com.gmail.nossr50.api.AbilityAPI";
    private static final String[] BLOCK_BREAK_ABILITY_METHODS = {
            "treeFellerEnabled",
            "superBreakerEnabled",
            "gigaDrillBreakerEnabled",
            "greenTerraEnabled"
    };

    public static boolean isPrevented(Player player, Block block) {
        if (!isPlugin(PLUGIN_NAME))
            return false;

        Boolean activeAbility = isBlockBreakAbilityActive(player);
        if (Boolean.TRUE.equals(activeAbility))
            return true;

        return isPrevented(block.getType());
    }

    public static boolean isPrevented(Material material) {
        if (!isPlugin(PLUGIN_NAME))
            return false;
        String name = material.name();
        if (name.endsWith("_LOG") || name.endsWith("_LEAVES"))
            return true;
        return false;
    }

    private static Boolean isBlockBreakAbilityActive(Player player) {
        Class<?> abilityApiClass;
        try {
            abilityApiClass = Class.forName(ABILITY_API_CLASS);
        } catch (ClassNotFoundException exception) {
            return null;
        }

        for (String methodName : BLOCK_BREAK_ABILITY_METHODS) {
            Boolean active = invokeAbilityMethod(abilityApiClass, methodName, player);
            if (Boolean.TRUE.equals(active))
                return true;
        }
        return false;
    }

    private static Boolean invokeAbilityMethod(Class<?> abilityApiClass, String methodName, Player player) {
        try {
            Method method = abilityApiClass.getMethod(methodName, Player.class);
            Object result = method.invoke(null, player);
            if (result instanceof Boolean)
                return (Boolean) result;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
        return false;
    }

}

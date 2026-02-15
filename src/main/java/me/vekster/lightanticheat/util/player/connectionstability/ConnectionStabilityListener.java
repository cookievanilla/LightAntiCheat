package me.vekster.lightanticheat.util.player.connectionstability;

import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionStabilityListener implements Listener {

    private static final Map<UUID, List<Integer>> PLAYERS = new ConcurrentHashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PLAYERS.put(event.getPlayer().getUniqueId(), Collections.synchronizedList(new ArrayList<>(Arrays.asList(0, 0, 0, 0))));
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        List<Integer> list = PLAYERS.get(uuid);
        if (list == null)
            return;
        synchronized (list) {
            int lastIndex = list.size() - 1;
            list.set(lastIndex, list.get(lastIndex) + 1);
        }
    }

    public static void loadConnectionCalculator() {
        Scheduler.runTaskTimer(() -> {
            Set<UUID> onlinePlayers = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers())
                onlinePlayers.add(player.getUniqueId());

            PLAYERS.entrySet().removeIf(entry -> {
                if (!onlinePlayers.contains(entry.getKey()))
                    return true;
                List<Integer> list = entry.getValue();
                synchronized (list) {
                    list.add(0);
                    list.remove(0);
                }
                return false;
            });
        }, 40, 40);
    }

    public static void loadConnectionCalculatorOnReload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PLAYERS.put(player.getUniqueId(), Collections.synchronizedList(new ArrayList<>(Arrays.asList(0, 0, 0, 0))));
        }
    }

    public static ConnectionStability getConnectionStability(Player player) {
        return getConnectionStability(player.getUniqueId());
    }

    public static ConnectionStability getConnectionStability(UUID uuid) {
        List<Integer> list = PLAYERS.get(uuid);
        if (list == null)
            return ConnectionStability.HIGH;

        int maxVal = 0;
        synchronized (list) {
            for (int value : list)
                if (value > maxVal)
                    maxVal = value;
        }

        int max = (int) Math.floor(maxVal / 2.0);
        if (max <= 25)
            return ConnectionStability.HIGH;
        else if (max <= 50)
            return ConnectionStability.MEDIUM;
        else
            return ConnectionStability.LOW;
    }

}

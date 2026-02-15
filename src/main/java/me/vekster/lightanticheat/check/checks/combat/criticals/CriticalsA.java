package me.vekster.lightanticheat.check.checks.combat.criticals;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.combat.CombatCheck;
import me.vekster.lightanticheat.event.playerattack.LACAsyncPlayerAttackEvent;
import me.vekster.lightanticheat.event.playerattack.LACPlayerAttackEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.player.cache.history.HistoryElement;
import me.vekster.lightanticheat.player.cache.history.PlayerCacheHistory;
import me.vekster.lightanticheat.util.hook.plugin.FloodgateHook;
import me.vekster.lightanticheat.util.hook.plugin.simplehook.ValhallaMMOHook;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffectType;

/**
 * Packet/Bypass mode
 */
public class CriticalsA extends CombatCheck implements Listener {
    private static final long REPEAT_WINDOW_MS = 1600L;

    public CriticalsA() {
        super(CheckName.CRITICALS_A);
    }

    @EventHandler
    public void onHit(LACPlayerAttackEvent event) {
        if (!event.isEntityAttackCause())
            return;
        if (ValhallaMMOHook.isPluginInstalled())
            return;
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        refreshContext(buffer, player, lacPlayer, cache);

        if (!isCheckAllowed(player, lacPlayer))
            return;

        if (player.isFlying() || player.isInsideVehicle() || lacPlayer.isGliding() || lacPlayer.isRiptiding() ||
                lacPlayer.isClimbing() || lacPlayer.isInWater())
            return;
        if (cache.flyingTicks >= -3 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return;
        long time = System.currentTimeMillis();
        if (hasRecentMobilityContext(cache, lacPlayer, time, 800, 1300, 1000, 1700, 1400, 2400))
            return;
        if (time - cache.lastInsideVehicle <= 150 || time - cache.lastInWater <= 150 ||
                time - cache.lastWasFished <= 4000 || time - cache.lastTeleport <= 700 ||
                time - cache.lastRespawn <= 500 || time - cache.lastEntityVeryNearby <= 500 ||
                time - cache.lastSlimeBlock <= 500 || time - cache.lastHoneyBlock <= 500 ||
                time - cache.lastWasHit <= 350 || time - cache.lastWasDamaged <= 150 ||
                time - cache.lastKbVelocity <= 500)
            return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return;

        if (getEffectAmplifier(cache, PotionEffectType.BLINDNESS) != 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) != 0)
            return;

        for (Block block : getWithinBlocks(player)) {
            if (!isActuallyPassable(block))
                return;
        }

        boolean ground = false;
        for (Block block : getDownBlocks(player, 0.1)) {
            if (!isActuallyPassable(block)) {
                ground = true;
                break;
            }
        }
        if (!ground) return;

        if (((Entity) player).isOnGround() || player.getFallDistance() == 0)
            return;
        if (!isBlockHeight((float) getBlockY(player.getLocation().getY())))
            return;

        if (getItemStackAttributes(player, "PLAYER_SWEEPING_DAMAGE_RATIO") != 0 ||
                getPlayerAttributes(player).getOrDefault("PLAYER_SWEEPING_DAMAGE_RATIO", 0.0) > 0.01)
            buffer.put("attribute", System.currentTimeMillis());
        if (System.currentTimeMillis() - buffer.getLong("attribute") < 2500)
            return;

        if (!hasMicroJumpPattern(cache))
            return;

        callViolationEventIfRepeat(player, lacPlayer, event.getEvent(), buffer, REPEAT_WINDOW_MS);
    }

    @EventHandler
    public void onAsyncHit(LACAsyncPlayerAttackEvent event) {
        if (event.hasDamageCause() && !event.isEntityAttackCause())
            return;
        if (event.getEntityId() == 0)
            return;
        if (ValhallaMMOHook.isPluginInstalled())
            return;

        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        refreshContext(buffer, player, lacPlayer, cache);

        if (!isCheckAllowed(player, lacPlayer))
            return;

        if (FloodgateHook.isBedrockPlayer(player, true))
            return;

        if (player.getFallDistance() == 0 || ((Entity) player).isOnGround())
            return;

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return;

        if (getEffectAmplifier(cache, PotionEffectType.BLINDNESS) != 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) != 0)
            return;

        if (player.isFlying() || player.isInsideVehicle() || lacPlayer.isGliding() || lacPlayer.isRiptiding() ||
                lacPlayer.isClimbing() || lacPlayer.isInWater())
            return;
        if (cache.flyingTicks >= -3 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return;
        long time = System.currentTimeMillis();
        if (hasRecentMobilityContext(cache, lacPlayer, time, 800, 1300, 1000, 1700, 1400, 2400))
            return;
        if (time - cache.lastInsideVehicle <= 150 || time - cache.lastInWater <= 150 ||
                time - cache.lastWasFished <= 4000 || time - cache.lastTeleport <= 700 ||
                time - cache.lastRespawn <= 500 || time - cache.lastEntityVeryNearby <= 500 ||
                time - cache.lastSlimeBlock <= 500 || time - cache.lastHoneyBlock <= 500 ||
                time - cache.lastWasHit <= 350 || time - cache.lastWasDamaged <= 150 ||
                time - cache.lastKbVelocity <= 500)
            return;

        for (Block block : getWithinBlocks(player)) {
            if (!isActuallyPassable(block) || !isActuallyPassable(block.getRelative(BlockFace.UP)))
                return;
        }

        boolean ground = false;
        for (Block block : getDownBlocks(player, 0.1)) {
            if (!isActuallyPassable(block) || !isActuallyPassable(block.getRelative(BlockFace.DOWN))) {
                ground = true;
                break;
            }
        }
        if (!ground) return;

        if (!hasMicroJumpPattern(cache))
            return;

        if (getItemStackAttributes(player, "PLAYER_SWEEPING_DAMAGE_RATIO") != 0 ||
                getPlayerAttributes(player).getOrDefault("PLAYER_SWEEPING_DAMAGE_RATIO", 0.0) > 0.01)
            buffer.put("attribute", System.currentTimeMillis());
        if (System.currentTimeMillis() - buffer.getLong("attribute") < 2500)
            return;

        Scheduler.runTask(true, () -> {
            callViolationEventIfRepeat(player, lacPlayer, null, buffer, REPEAT_WINDOW_MS);
        });
    }

    private void refreshContext(Buffer buffer, Player player, LACPlayer lacPlayer, PlayerCache cache) {
        long currentTeleport = cache.lastTeleport;
        if (buffer.getLong("contextLastTeleport") != currentTeleport) {
            resetRepeatBuffer(buffer);
            buffer.put("contextLastTeleport", currentTeleport);
        }

        String context = (((Entity) player).isOnGround() ? "G" : "A") +
                (lacPlayer.isInWater() ? "W" : "D") +
                (player.isInsideVehicle() ? "V" : "N");
        String previousContext = buffer.getString("movementContext");
        if (previousContext != null && !previousContext.equals(context))
            resetRepeatBuffer(buffer);
        buffer.put("movementContext", context);
    }

    private void resetRepeatBuffer(Buffer buffer) {
        buffer.put("lastMethodFlag", 0L);
        buffer.put("missedMethodFlag", false);
    }

    private boolean hasMicroJumpPattern(PlayerCache cache) {
        return isMicroJumpHistory(cache.history.onEvent.location) && isMicroJumpHistory(cache.history.onPacket.location);
    }

    private boolean isMicroJumpHistory(PlayerCacheHistory<Location> history) {
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double previousY = Double.NaN;
        int tinyDeltaCount = 0;
        int directionChanges = 0;
        int steadyLargeRise = 0;
        int steadyLargeDrop = 0;
        int currentDirection = 0;

        for (int i = 0; i < HistoryElement.values().length; i++) {
            double y = history.get(HistoryElement.values()[i]).getY();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            if (Double.isNaN(previousY)) {
                previousY = y;
                continue;
            }

            double delta = y - previousY;
            if (Math.abs(delta) <= LOWEST_BLOCK_HEIGHT)
                tinyDeltaCount++;

            if (Math.abs(delta) >= 0.06) {
                if (delta > 0)
                    steadyLargeRise++;
                else
                    steadyLargeDrop++;
            }

            int direction = delta > LOWEST_BLOCK_HEIGHT ? 1 : delta < -LOWEST_BLOCK_HEIGHT ? -1 : 0;
            if (direction != 0 && currentDirection != 0 && direction != currentDirection)
                directionChanges++;
            if (direction != 0)
                currentDirection = direction;

            previousY = y;
        }

        double span = maxY - minY;
        if (span >= 0.3)
            return false;
        if (tinyDeltaCount < 2)
            return false;
        if (directionChanges == 0)
            return false;
        return steadyLargeRise < 2 && steadyLargeDrop < 2;
    }

}

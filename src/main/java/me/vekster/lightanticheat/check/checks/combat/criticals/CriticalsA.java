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

        if (!isCheckAllowed(player, lacPlayer))
            return;

        if (!passesCommonChecks(player, lacPlayer, cache, true))
            return;

        Buffer buffer = getBuffer(player, true);
        refreshContext(buffer, player, lacPlayer, cache);

        if (!passesGroundShapeChecks(player))
            return;

        if (!hasMicroJumpPattern(cache))
            return;

        if (isSweepingAttributeRecentlyUsed(player, buffer))
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

        if (!isCheckAllowed(player, lacPlayer))
            return;

        if (!passesCommonChecks(player, lacPlayer, cache, false))
            return;

        Buffer buffer = getBuffer(player, true);
        refreshContext(buffer, player, lacPlayer, cache);

        if (!passesGroundShapeChecks(player))
            return;

        if (!hasMicroJumpPattern(cache))
            return;

        if (isSweepingAttributeRecentlyUsed(player, buffer))
            return;

        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, null, buffer, REPEAT_WINDOW_MS));
    }

    private boolean passesCommonChecks(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean allowBedrock) {
        if (!allowBedrock && FloodgateHook.isBedrockPlayer(player, true))
            return false;

        if (player.getFallDistance() == 0 || ((Entity) player).isOnGround())
            return false;

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return false;

        if (getEffectAmplifier(cache, PotionEffectType.BLINDNESS) != 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) != 0)
            return false;

        if (player.isFlying() || player.isInsideVehicle() || lacPlayer.isGliding() || lacPlayer.isRiptiding() ||
                lacPlayer.isClimbing() || lacPlayer.isInWater())
            return false;
        if (cache.flyingTicks >= -3 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return false;

        long time = System.currentTimeMillis();
        if (hasRecentMobilityContext(cache, lacPlayer, time, 800, 1300, 1000, 1700, 1400, 2400))
            return false;
        if (time - cache.lastInsideVehicle <= 150 || time - cache.lastInWater <= 150 ||
                time - cache.lastWasFished <= 4000 || time - cache.lastTeleport <= 700 ||
                time - cache.lastRespawn <= 500 || time - cache.lastEntityVeryNearby <= 500 ||
                time - cache.lastSlimeBlock <= 500 || time - cache.lastHoneyBlock <= 500 ||
                time - cache.lastWasHit <= 350 || time - cache.lastWasDamaged <= 150 ||
                time - cache.lastKbVelocity <= 500)
            return false;

        return true;
    }

    private boolean passesGroundShapeChecks(Player player) {
        for (Block block : getWithinBlocks(player)) {
            if (!isActuallyPassable(block) || !isActuallyPassable(block.getRelative(BlockFace.UP)))
                return false;
        }

        boolean ground = false;
        for (Block block : getDownBlocks(player, 0.1)) {
            if (!isActuallyPassable(block) || !isActuallyPassable(block.getRelative(BlockFace.DOWN))) {
                ground = true;
                break;
            }
        }
        return ground;
    }

    private boolean isSweepingAttributeRecentlyUsed(Player player, Buffer buffer) {
        if (getItemStackAttributes(player, "PLAYER_SWEEPING_DAMAGE_RATIO") != 0 ||
                getPlayerAttributes(player).getOrDefault("PLAYER_SWEEPING_DAMAGE_RATIO", 0.0) > 0.01)
            buffer.put("attribute", System.currentTimeMillis());
        return System.currentTimeMillis() - buffer.getLong("attribute") < 2500;
    }

    private void refreshContext(Buffer buffer, Player player, LACPlayer lacPlayer, PlayerCache cache) {
        long teleportState = cache.lastTeleport;
        long waterState = cache.lastInWater;
        long vehicleState = cache.lastInsideVehicle;

        if (buffer.getLong("contextLastTeleport") != teleportState ||
                buffer.getLong("contextLastWater") != waterState ||
                buffer.getLong("contextLastVehicle") != vehicleState) {
            resetRepeatBuffer(buffer);
            buffer.put("contextLastTeleport", teleportState);
            buffer.put("contextLastWater", waterState);
            buffer.put("contextLastVehicle", vehicleState);
        }

        String context = (((Entity) player).isOnGround() ? "G" : "A") +
                (lacPlayer.isInWater() ? "W" : "D") +
                (player.isInsideVehicle() ? "V" : "N") +
                (lacPlayer.isGliding() ? "E" : "-") +
                (lacPlayer.isRiptiding() ? "R" : "-");
        String previousContext = buffer.getString("movementContext");
        if (previousContext != null && !previousContext.equals(context))
            resetRepeatBuffer(buffer);

        buffer.put("movementContext", context);
    }

    private void resetRepeatBuffer(Buffer buffer) {
        // Keys below must match Check#callViolationEventIfRepeat implementation.
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

        int samples = 0;
        int tinyDeltaCount = 0;
        int directionChanges = 0;
        int steadyLargeRise = 0;
        int steadyLargeDrop = 0;
        int currentDirection = 0;

        for (HistoryElement element : HistoryElement.values()) {
            Location location = history.get(element);
            if (location == null)
                return false;

            samples++;
            double y = location.getY();
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

        if (samples < 4)
            return false;

        double span = maxY - minY;
        if (span >= 0.3)
            return false;

        int requiredTinyDeltas = Math.max(2, samples / 5);
        if (tinyDeltaCount < requiredTinyDeltas)
            return false;

        if (directionChanges == 0)
            return false;

        int maxLargeMoves = Math.max(1, samples / 4);
        return steadyLargeRise < maxLargeMoves && steadyLargeDrop < maxLargeMoves;
    }

}

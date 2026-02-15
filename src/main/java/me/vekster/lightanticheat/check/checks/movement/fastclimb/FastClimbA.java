package me.vekster.lightanticheat.check.checks.movement.fastclimb;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.player.cache.history.HistoryElement;
import me.vekster.lightanticheat.util.async.AsyncUtil;
import me.vekster.lightanticheat.util.hook.plugin.FloodgateHook;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

/**
 * Vertical speed while climbing
 */
public class FastClimbA extends MovementCheck implements Listener {
    private static final int ANNOYING_BUG_IDLE_RESET_TICKS = 12;

    public FastClimbA() {
        super(CheckName.FASTCLIMB_A);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || !isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -5 || cache.climbingTicks <= 2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return false;
        long time = System.currentTimeMillis();
        long instabilityGrace = getDynamicGraceWindow(lacPlayer, 350);
        return time - cache.lastInsideVehicle > 150 && time - cache.lastInWater > 150 &&
                time - cache.lastKnockback > 500 && time - cache.lastKnockbackNotVanilla > 2000 &&
                time - cache.lastWasFished > 4000 && time - cache.lastTeleport > 500 &&
                time - cache.lastRespawn > 500 && time - cache.lastEntityVeryNearby > 200 &&
                time - cache.lastBlockExplosion > 6500 && time - cache.lastEntityExplosion > 2500 &&
                time - cache.lastSlimeBlockVertical > 1000 && time - cache.lastSlimeBlockHorizontal > 1000 &&
                time - cache.lastHoneyBlockVertical > 700 && time - cache.lastHoneyBlockHorizontal > 700 &&
                time - cache.lastWasHit > 250 && time - cache.lastWasDamaged > 100 &&
                time - cache.lastFlight > 750 &&
                !hasRecent121MobilityBoost(cache, time, false) &&
                !hasInstabilityCooldown(cache, time, instabilityGrace);
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);
        if (hasTeleportOrRespawnTransition(cache, buffer)) {
            resetTracking(buffer);
        }

        if (!isCheckAllowed(player, lacPlayer, true)) {
            resetTracking(buffer);
            return;
        }

        if (!isConditionAllowed(player, lacPlayer, event)) {
            resetTracking(buffer);
            return;
        }

        if (FloodgateHook.isBedrockPlayer(player, true)) {
            resetTracking(buffer);
            return;
        }

        if (cache.sneakingTicks > -15)
            return;
        Block block = AsyncUtil.getBlock(player.getLocation());
        if (block == null) return;
        Block downBlock = AsyncUtil.getBlock(player.getLocation().subtract(0, 1, 0));
        if (downBlock == null) return;

        Set<Material> withinMaterials = new HashSet<>();
        withinMaterials.addAll(event.getToWithinMaterials());
        withinMaterials.addAll(event.getFromWithinMaterials());

        if (!isClimbableContext(block, downBlock, withinMaterials, player.getLocation())) {
            resetTracking(buffer);
            return;
        }

        boolean scaffoldingContext = withinMaterials.contains(VerUtil.material.get("SCAFFOLDING"));

        buffer.put("climbingEvents", buffer.getInt("climbingEvents") + 1);
        if (buffer.getInt("climbingEvents") <= 2)
            return;

        double verticalSpeed = distanceVertical(event.getFrom(), event.getTo());

        double verticalSpeed1 = distanceVertical(cache.history.onEvent.location.get(HistoryElement.FIRST), event.getTo()) / 2.0;
        if (Math.abs(verticalSpeed1) < Math.abs(verticalSpeed)) verticalSpeed = verticalSpeed1;

        double verticalSpeed2 = distanceVertical(cache.history.onEvent.location.get(HistoryElement.SECOND), event.getTo()) / 3.0;
        if (Math.abs(verticalSpeed2) < Math.abs(verticalSpeed)) verticalSpeed = verticalSpeed2;

        double verticalSpeed3 = distanceVertical(cache.history.onPacket.location.get(HistoryElement.FIRST), event.getTo());
        if (Math.abs(verticalSpeed3) < Math.abs(verticalSpeed)) verticalSpeed = verticalSpeed3;

        double distanceVertical = distanceVertical(event.getFrom(), event.getTo());
        if (Math.abs(verticalSpeed - 0.5 - 0.1176001) < 0.0175 || Math.abs(verticalSpeed - 0.5 + 0.15001) < 0.0175 ||
                Math.abs(distanceVertical - 0.5 - 0.1176001) < 0.0175 || Math.abs(distanceVertical - 0.5 + 0.15001) < 0.0175) {
            buffer.put("annoyingBug", true);
            buffer.put("annoyingBugIdleTicks", 0);
        } else if (buffer.getBoolean("annoyingBug")) {
            int annoyingBugIdleTicks = buffer.getInt("annoyingBugIdleTicks") + 1;
            if (annoyingBugIdleTicks >= ANNOYING_BUG_IDLE_RESET_TICKS) {
                buffer.put("annoyingBug", false);
                buffer.put("annoyingBugIdleTicks", 0);
            } else {
                buffer.put("annoyingBugIdleTicks", annoyingBugIdleTicks);
            }
        }

        double maxUpSpeed = 0.1176001 * 1.5;
        double maxDownSpeed = -0.15001 * 1.65;
        boolean vineContext = isVine(block.getType()) || isVine(downBlock.getType()) || withinMaterials.stream().anyMatch(this::isVine);
        boolean ladderContext = isLadder(block.getType()) || isLadder(downBlock.getType()) || withinMaterials.stream().anyMatch(this::isLadder);
        boolean edgeTransition = isClimbingEdgeTransition(event.getFrom(), event.getTo(), withinMaterials);

        if (VerIdentifier.getVersion().isOlderThan(LACVersion.V1_13)) {
            maxUpSpeed *= 2;
            maxDownSpeed *= 2;
        }
        if (ladderContext) {
            maxUpSpeed *= VerIdentifier.getVersion().isOlderThan(LACVersion.V1_13) ? 1.15 : 1.05;
        }
        if (vineContext) {
            maxUpSpeed *= 1.12;
            maxDownSpeed *= 1.08;
        }
        if (scaffoldingContext) {
            maxUpSpeed *= 1.18;
            maxDownSpeed *= 1.12;
        }
        if (edgeTransition) {
            maxUpSpeed *= 1.2;
            maxDownSpeed *= 1.15;
        }

        if (!(verticalSpeed > maxUpSpeed || verticalSpeed < maxDownSpeed)) {
            buffer.put("climbFlags", Math.max(0, buffer.getInt("climbFlags") - 1));
            return;
        }

        if (buffer.getBoolean("annoyingBug")) {
            if (Math.abs(verticalSpeed - 0.5 - 0.1176001) < 0.025 || Math.abs(verticalSpeed - 0.5 + 0.15001) < 0.025 ||
                    Math.abs(distanceVertical - 0.5 - 0.1176001) < 0.025 || Math.abs(distanceVertical - 0.5 + 0.15001) < 0.025) {
                return;
            }
        }

        boolean vertical = distanceVertical(event.getFrom(), event.getTo()) >= 0;
        Location eventPrevious = null;
        Location packetPrevious = null;
        for (int i = 0; i < 5 && i < HistoryElement.values().length; i++) {
            Location eventLocation = cache.history.onEvent.location.get(HistoryElement.values()[i]);
            Location packetLocation = cache.history.onPacket.location.get(HistoryElement.values()[i]);
            if (eventPrevious == null) {
                eventPrevious = eventLocation;
                packetPrevious = packetLocation;
                continue;
            }
            double eventVerticalSpeed = distanceVertical(eventLocation, eventPrevious);
            double packetVerticalSpeed = distanceVertical(packetLocation, packetPrevious);
            if (vertical && eventVerticalSpeed < -0.007 || !vertical && eventVerticalSpeed > 0.007 ||
                    vertical && packetVerticalSpeed < -0.007 || !vertical && packetVerticalSpeed > 0.007)
                return;
            eventPrevious = eventLocation;
            packetPrevious = packetLocation;
        }

        Scheduler.runTask(true, () -> {
            buffer.put("climbFlags", buffer.getInt("climbFlags") + 1);
            int requiredClimbFlags = getConnectionBufferRequirement(lacPlayer, 1);
            if (buffer.getInt("climbFlags") <= requiredClimbFlags)
                return;
            callViolationEvent(player, lacPlayer, event);
        });
    }

    private boolean hasTeleportOrRespawnTransition(PlayerCache cache, Buffer buffer) {
        if (buffer.getLong("lastProcessedTeleport") != cache.lastTeleport ||
                buffer.getLong("lastProcessedRespawn") != cache.lastRespawn ||
                buffer.getLong("lastProcessedWorldChange") != cache.lastWorldChange) {
            buffer.put("lastProcessedTeleport", cache.lastTeleport);
            buffer.put("lastProcessedRespawn", cache.lastRespawn);
            buffer.put("lastProcessedWorldChange", cache.lastWorldChange);
            return true;
        }
        return false;
    }

    private void resetTracking(Buffer buffer) {
        buffer.put("climbingEvents", 0);
        buffer.put("climbFlags", 0);
        buffer.put("annoyingBug", false);
        buffer.put("annoyingBugIdleTicks", 0);
    }

    private boolean isClimbableContext(Block block, Block downBlock, Set<Material> withinMaterials, Location location) {
        if (!isClimbable(block.getType()) && !isClimbable(downBlock.getType()) && withinMaterials.stream().noneMatch(this::isClimbable)) {
            return false;
        }

        Block headBlock = AsyncUtil.getBlock(location.clone().add(0, 1, 0));
        Block frontBlock = AsyncUtil.getBlock(location.clone().add(location.getDirection().setY(0).normalize().multiply(0.4)));
        return headBlock != null && (isClimbable(headBlock.getType()) || isClimbable(block.getType()) ||
                isClimbable(downBlock.getType()) || (frontBlock != null && isClimbable(frontBlock.getType())) ||
                withinMaterials.stream().anyMatch(this::isClimbable));
    }

    private boolean isClimbingEdgeTransition(Location from, Location to, Set<Material> withinMaterials) {
        double verticalDelta = to.getY() - from.getY();
        if (Math.abs(verticalDelta) < 0.01) {
            return false;
        }
        Block aboveTo = AsyncUtil.getBlock(to.clone().add(0, 1, 0));
        boolean leavingClimbable = aboveTo == null || !isClimbable(aboveTo.getType());
        boolean enteringPlatform = Math.abs(to.getY() - Math.floor(to.getY())) < 0.03;
        return leavingClimbable && enteringPlatform && withinMaterials.stream().anyMatch(this::isClimbable);
    }

    private boolean isClimbable(Material material) {
        return isLadder(material) || isVine(material) || material == VerUtil.material.get("SCAFFOLDING");
    }

    private boolean isLadder(Material material) {
        return material != null && material.name().contains("LADDER");
    }

    private boolean isVine(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.equals("VINE") || name.contains("_VINES") || name.contains("_VINE");
    }

}

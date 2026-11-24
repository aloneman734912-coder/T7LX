package com.t7lx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Client-side printer for active Litematica placements.
 * Reads the placement manager and the transformed schematic world, rather
 * than assuming the schematic is centred on the player.
 */
public final class LitematicaBuilder {
    public enum Mode { SURVIVAL, CREATIVE }

    private static boolean running;
    private static boolean paused;
    private static Mode mode = Mode.SURVIVAL;
    private static int placed;
    private static int tickDelay;
    private static BlockPos currentTarget;
    private static BlockPos currentSupport;
    private static BlockHitResult currentSupportHit;
    private static boolean placementPending;
    private static int placementWaitTicks;
    private static String status = "Stopped";

    private LitematicaBuilder() {}

    public static void start() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            mode = client.player.getAbilities().instabuild ? Mode.CREATIVE : Mode.SURVIVAL;
        }
        running = true;
        paused = false;
        placed = 0;
        tickDelay = 0;
        status = "Looking for loaded Litematica placements...";
    }

    public static void pauseOrResume() {
        if (!running) { start(); return; }
        paused = !paused;
        status = paused ? "Paused" : "Building...";
    }

    public static void stop() {
        running = false;
        paused = false;
        currentTarget = null;
        placementPending = false;
        placementWaitTicks = 0;
        clearMovement(Minecraft.getInstance());
        status = "Stopped";
    }

    public static boolean isRunning() { return running; }
    public static boolean isPaused() { return paused; }
    public static Mode getMode() { return mode; }
    public static void toggleMode() { mode = mode == Mode.SURVIVAL ? Mode.CREATIVE : Mode.SURVIVAL; }
    public static int getPlacedCount() { return placed; }
    public static String getStatus() { return status; }

    public static void tick(Minecraft client) {
        if (!running || paused || client.player == null || client.level == null
                || client.gameMode == null || client.screen != null) {
            if (!running || paused || client.screen != null) clearMovement(client);
            return;
        }
        // One decision every client tick: no artificial printer delay.
        if (mode == Mode.CREATIVE && !client.player.getAbilities().instabuild) {
            status = "Creative selected: switch to Creative";
            clearMovement(client);
            return;
        }

        Object schematicWorld = getSchematicWorld();
        if (schematicWorld == null) {
            status = "Litematica schematic world unavailable";
            clearMovement(client);
            return;
        }
        List<Object> placements = getEnabledPlacements();
        if (placements.isEmpty()) {
            status = "No loaded Litematica placement found";
            clearMovement(client);
            return;
        }

        // Keep the current target until it is completed, preventing the bot
        // from constantly changing direction while walking to a block.
        if (currentTarget != null) {
            BlockState now = client.level.getBlockState(currentTarget);
            BlockState wanted = getSchematicState(schematicWorld, currentTarget);
            if (wanted == null || wanted.isAir()) {
                currentTarget = null;
                placementPending = false;
            } else if (now.equals(wanted)) {
                // Count only confirmed placements; never abandon a block just
                // because the interaction packet was sent.
                placed++;
                currentTarget = null;
                placementPending = false;
                placementWaitTicks = 0;
            } else if (!now.isAir()) {
                // Something other than the requested block occupies this spot.
                currentTarget = null;
                placementPending = false;
            } else if (placementPending && ++placementWaitTicks < 8) {
                status = "Waiting for block placement confirmation...";
                clearMovement(client);
                return;
            } else if (placementPending) {
                // Server rejected the attempt: reacquire support and retry.
                placementPending = false;
                placementWaitTicks = 0;
                currentSupport = null;
                currentSupportHit = null;
            }
        }

        if (currentTarget == null) {
            currentTarget = findNearestMissing(client, schematicWorld, placements);
            currentSupport = null;
            currentSupportHit = null;
            placementPending = false;
            placementWaitTicks = 0;
        }
        if (currentTarget == null) {
            clearMovement(client);
            status = "No reachable missing blocks found in loaded placement";
            return;
        }

        BlockState required = getSchematicState(schematicWorld, currentTarget);
        if (required == null || required.isAir()) {
            currentTarget = null;
            return;
        }
        int slot = findBlock(client, required);
        if (mode == Mode.SURVIVAL && slot < 0) {
            clearMovement(client);
            status = "Missing " + required.getBlock().getName().getString() + " in inventory";
            currentTarget = null;
            return;
        }

        currentSupportHit = findSupport(client, currentTarget);
        if (currentSupportHit == null) {
            clearMovement(client);
            status = "Waiting for a supporting block";
            return;
        }
        currentSupport = currentSupportHit.getBlockPos();

        Vec3 eye = client.player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(currentTarget);
        double distance = eye.distanceTo(targetCenter);

        if (distance > 4.35) {
            // Walk the real player toward the next required position. Camera
            // yaw follows the route, and jump is pulsed when the player hits
            // an obstacle. This is normal client movement, not teleporting.
            aimAt(client, targetCenter);
            pressMovement(client, "keyUp", true);
            pressMovement(client, "keyJump", client.player.horizontalCollision);
            pressMovement(client, "keyShift", false);
            status = "Walking to block " + currentTarget.toShortString();
            return;
        }

        clearMovement(client);
        // Aim the actual player camera at the face which receives the block.
        aimAt(client, currentSupportHit.getLocation());

        // Only interact once the player's real crosshair is on the intended
        // support face. This keeps placement aligned with what the cursor sees.
        if (!(client.hitResult instanceof BlockHitResult crosshair)
                || !crosshair.getBlockPos().equals(currentSupport)
                || crosshair.getDirection() != currentSupportHit.getDirection()) {
            status = "Aiming at " + currentTarget.toShortString();
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        if (mode == Mode.CREATIVE) {
            // Creative mode can supply any schematic block: put the required
            // item into the selected hotbar slot and sync that creative slot.
            ItemStack creativeStack = new ItemStack(required.getBlock().asItem());
            client.player.getInventory().setItem(selected, creativeStack);
            client.gameMode.handleCreativeModeItemAdd(creativeStack, selected + 36);
        } else if (slot >= 9) {
            client.gameMode.handleContainerInput(
                    client.player.inventoryMenu.containerId, slot, selected,
                    net.minecraft.world.inventory.ContainerInput.SWAP, client.player);
        } else if (slot >= 0) {
            client.player.getInventory().setSelectedSlot(slot);
        }

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, crosshair);
        client.player.swing(InteractionHand.MAIN_HAND);
        placementPending = true;
        placementWaitTicks = 0;
        status = "Placing " + currentTarget.toShortString() + " (confirming...)";
    }

    private static BlockPos findNearestMissing(Minecraft client, Object schematicWorld, List<Object> placements) {
        BlockPos origin = client.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        // Scan a generous local cube around the player; the player is moved
        // toward the closest supported missing block and the scan repeats.
        for (int y = -8; y <= 8; y++) {
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!insideAnyPlacement(placements, pos)) continue;
                    BlockState required = getSchematicState(schematicWorld, pos);
                    if (required == null || required.isAir() || !client.level.getBlockState(pos).isAir()) continue;
                    if (mode == Mode.SURVIVAL && findBlock(client, required) < 0) continue;
                    if (findSupport(client, pos) == null) continue;
                    double distance = client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static void aimAt(Minecraft client, Vec3 point) {
        Vec3 delta = point.subtract(client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        client.player.setYRot(yaw);
        client.player.setYHeadRot(yaw);
        client.player.setXRot(Math.max(-90.0F, Math.min(90.0F, pitch)));
    }

    private static void pressMovement(Minecraft client, String fieldName, boolean down) {
        // Use Minecraft's actual KeyMapping instances. Reflection against
        // field names silently failed on some mappings, so W never activated.
        switch (fieldName) {
            case "keyUp" -> client.options.keyUp.setDown(down);
            case "keyJump" -> client.options.keyJump.setDown(down);
            case "keyShift" -> client.options.keyShift.setDown(down);
            default -> { }
        }
    }

    private static void clearMovement(Minecraft client) {
        if (client == null) return;
        client.options.keyUp.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
    }

    private static List<Object> getEnabledPlacements() {
        List<Object> result = new ArrayList<>();
        try {
            Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object manager = dataManager.getMethod("getSchematicPlacementManager").invoke(null);
            Object value = manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager);
            if (!(value instanceof Collection<?> placements)) return result;

            for (Object placement : placements) {
                Object enabled = placement.getClass().getMethod("isEnabled").invoke(placement);
                if (Boolean.TRUE.equals(enabled)) result.add(placement);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            status = "Could not read Litematica placement manager";
        }
        return result;
    }

    private static boolean insideAnyPlacement(List<Object> placements, BlockPos pos) {
        for (Object placement : placements) {
            try {
                Method boxesMethod = null;
                for (Method method : placement.getClass().getMethods()) {
                    if (method.getName().equals("getSubRegionBoxes") && method.getParameterCount() == 1) {
                        boxesMethod = method;
                        break;
                    }
                }
                if (boxesMethod == null) continue;

                Class<?> enumClass = boxesMethod.getParameterTypes()[0];
                Object enabledValue = null;
                if (enumClass.isEnum()) {
                    for (Object constant : enumClass.getEnumConstants()) {
                        if ("PLACEMENT_ENABLED".equals(((Enum<?>) constant).name())) {
                            enabledValue = constant;
                            break;
                        }
                    }
                }
                if (enabledValue == null) continue;

                Object boxes = boxesMethod.invoke(placement, enabledValue);
                if (!(boxes instanceof java.util.Map<?, ?> map)) continue;
                for (Object box : map.values()) {
                    BlockPos p1 = (BlockPos) box.getClass().getMethod("getPos1").invoke(box);
                    BlockPos p2 = (BlockPos) box.getClass().getMethod("getPos2").invoke(box);
                    if (p1 == null || p2 == null) continue;
                    int minX = Math.min(p1.getX(), p2.getX());
                    int maxX = Math.max(p1.getX(), p2.getX());
                    int minY = Math.min(p1.getY(), p2.getY());
                    int maxY = Math.max(p1.getY(), p2.getY());
                    int minZ = Math.min(p1.getZ(), p2.getZ());
                    int maxZ = Math.max(p1.getZ(), p2.getZ());
                    if (pos.getX() >= minX && pos.getX() <= maxX
                            && pos.getY() >= minY && pos.getY() <= maxY
                            && pos.getZ() >= minZ && pos.getZ() <= maxZ) return true;
                }
            } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
                // Try the next active placement.
            }
        }
        return false;
    }

    private static int findBlock(Minecraft client, BlockState required) {
        var inventory = client.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == required.getBlock().asItem()) return i;
        }
        return -1;
    }

    private static BlockHitResult findSupport(Minecraft client, BlockPos target) {
        for (Direction face : Direction.values()) {
            BlockPos support = target.relative(face.getOpposite());
            if (client.level.getBlockState(support).isAir()) continue;
            Vec3 hitPos = Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.499, face.getStepY() * 0.499, face.getStepZ() * 0.499);
            return new BlockHitResult(hitPos, face, support, false);
        }
        return null;
    }

    private static Object getSchematicWorld() {
        try {
            Class<?> handler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            return handler.getMethod("getSchematicWorld").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static BlockState getSchematicState(Object schematicWorld, BlockPos pos) {
        try {
            Object state = schematicWorld.getClass().getMethod("getBlockState", BlockPos.class)
                    .invoke(schematicWorld, pos);
            return state instanceof BlockState blockState ? blockState : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            status = "Could not read schematic block data";
            return null;
        }
    }
}

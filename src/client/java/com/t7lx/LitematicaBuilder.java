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
    private static String status = "Stopped";

    private LitematicaBuilder() {}

    public static void start() {
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
                || client.gameMode == null || client.screen != null) return;
        if (++tickDelay < 3) return;
        tickDelay = 0;

        if (mode == Mode.CREATIVE && !client.player.getAbilities().instabuild) {
            status = "Creative selected: switch to Creative";
            return;
        }

        Object schematicWorld = getSchematicWorld();
        if (schematicWorld == null) {
            status = "Litematica schematic world unavailable";
            return;
        }

        List<Object> placements = getEnabledPlacements();
        if (placements.isEmpty()) {
            status = "No loaded Litematica placement found";
            return;
        }

        BlockPos origin = client.player.blockPosition();
        boolean hasTargetInRange = false;
        boolean missingInventoryBlock = false;

        // The hologram is in world coordinates after Litematica applies its
        // placement origin, rotation and mirror. Only inspect positions inside
        // the actual active placement boxes.
        for (int y = -2; y <= 5; y++) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!insideAnyPlacement(placements, pos)) continue;

                    BlockState required = getSchematicState(schematicWorld, pos);
                    if (required == null || required.isAir()) continue;

                    BlockState current = client.level.getBlockState(pos);
                    if (!current.isAir()) continue;
                    hasTargetInRange = true;

                    int hotbarSlot = findBlock(client, required);
                    if (hotbarSlot < 0 && mode == Mode.SURVIVAL) {
                        missingInventoryBlock = true;
                        continue;
                    }

                    BlockHitResult hit = findSupport(client, pos);
                    if (hit == null) continue;

                    int selected = client.player.getInventory().getSelectedSlot();
                    if (mode == Mode.CREATIVE && hotbarSlot < 0) {
                        ItemStack creativeStack = new ItemStack(required.getBlock().asItem());
                        client.player.getInventory().setItem(selected, creativeStack);
                        client.gameMode.handleCreativeModeItemAdd(creativeStack, selected + 36);
                    } else if (hotbarSlot >= 9) {
                        client.gameMode.handleContainerInput(
                                client.player.inventoryMenu.containerId, hotbarSlot, selected,
                                net.minecraft.world.inventory.ContainerInput.SWAP, client.player);
                    } else if (hotbarSlot >= 0) {
                        client.player.getInventory().setSelectedSlot(hotbarSlot);
                    }

                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                    client.player.swing(InteractionHand.MAIN_HAND);
                    placed++;
                    status = "Building... placed " + placed;
                    return;
                }
            }
        }

        if (missingInventoryBlock) status = "Missing required blocks in inventory";
        else if (hasTargetInRange) status = "Waiting for a placeable block/support";
        else status = "Hologram found - move closer to its outline";
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

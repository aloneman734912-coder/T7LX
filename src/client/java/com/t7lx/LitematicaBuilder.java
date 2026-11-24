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

/**
 * Small client-side printer for the currently rendered Litematica schematic.
 * Litematica is accessed reflectively so T7LX still loads when it is not installed.
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
        status = "Searching hologram...";
    }

    public static void pauseOrResume() {
        if (!running) {
            start();
            return;
        }
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
    public static void toggleMode() {
        mode = mode == Mode.SURVIVAL ? Mode.CREATIVE : Mode.SURVIVAL;
    }
    public static int getPlacedCount() { return placed; }
    public static String getStatus() { return status; }

    public static void tick(Minecraft client) {
        if (!running || paused || client.player == null || client.level == null
                || client.gameMode == null || client.screen != null) return;

        if (++tickDelay < 3) return;
        tickDelay = 0;

        if (mode == Mode.CREATIVE && !client.player.getAbilities().instabuild) {
            status = "Creative mode selected: switch to Creative";
            return;
        }

        Object schematicWorld = getSchematicWorld();
        if (schematicWorld == null) {
            status = "No Litematica hologram loaded";
            return;
        }

        BlockPos origin = client.player.blockPosition();
        // Work locally around the player, bottom-up. This keeps scanning bounded
        // and makes the printer naturally follow large schematic builds.
        for (int y = -2; y <= 5; y++) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState required = getSchematicState(schematicWorld, pos);
                    if (required == null || required.isAir()) continue;

                    BlockState current = client.level.getBlockState(pos);
                    if (!current.isAir()) continue;

                    int hotbarSlot = findBlock(client, required);
                    if (hotbarSlot < 0 && mode == Mode.SURVIVAL) {
                        status = "Missing block in inventory";
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
                                client.player.inventoryMenu.containerId,
                                hotbarSlot, selected,
                                net.minecraft.world.inventory.ContainerInput.SWAP,
                                client.player);
                    } else if (hotbarSlot >= 0) {
                        client.player.getInventory().setSelectedSlot(hotbarSlot);
                    }

                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                    client.player.swing(InteractionHand.MAIN_HAND);
                    placed++;
                    status = "Building... placed " + placed;
                    return; // one placement per scan; avoid packet bursts
                }
            }
        }
        status = "No reachable missing blocks nearby";
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
            BlockState supportState = client.level.getBlockState(support);
            if (supportState.isAir()) continue;
            Vec3 hitPos = Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.499,
                    face.getStepY() * 0.499,
                    face.getStepZ() * 0.499);
            return new BlockHitResult(hitPos, face, support, false);
        }
        return null;
    }

    private static Object getSchematicWorld() {
        try {
            Class<?> handler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            Method getter = handler.getMethod("getSchematicWorld");
            return getter.invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static BlockState getSchematicState(Object schematicWorld, BlockPos pos) {
        try {
            Method method = schematicWorld.getClass().getMethod("getBlockState", BlockPos.class);
            Object state = method.invoke(schematicWorld, pos);
            return state instanceof BlockState blockState ? blockState : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            status = "Could not read Litematica schematic";
            return null;
        }
    }
}

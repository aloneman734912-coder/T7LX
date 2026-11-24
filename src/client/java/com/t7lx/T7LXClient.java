package com.t7lx;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import java.util.UUID;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class T7LXClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("t7lx", "main"));
    private static KeyMapping menuKey;
    private static KeyMapping forwardKey, backKey, leftKey, rightKey, upKey, downKey;

    private static boolean freecam;
    private static ArmorStand camera;
    private static float savedYaw, savedPitch;
    private static MobEffectInstance previousNightVision;
    private static boolean crystalMacro;
    private static int crystalCps = 10;
    private static long nextCrystalClickNanos;
    private static BlockPos crystalBase;
    private static UUID lastAttackedCrystal;
    private static boolean autoTotem;
    private static int lastTotemRefillTick = -1;
    private static boolean speedMine;

    @Override
    public void onInitializeClient() {
        menuKey = register("key.t7lx.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        forwardKey = register("key.t7lx.forward", GLFW.GLFW_KEY_W);
        backKey = register("key.t7lx.back", GLFW.GLFW_KEY_S);
        leftKey = register("key.t7lx.left", GLFW.GLFW_KEY_A);
        rightKey = register("key.t7lx.right", GLFW.GLFW_KEY_D);
        upKey = register("key.t7lx.up", GLFW.GLFW_KEY_SPACE);
        downKey = register("key.t7lx.down", GLFW.GLFW_KEY_LEFT_SHIFT);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.screen instanceof T7LXScreen) client.setScreen(null);
                else client.setScreen(new T7LXScreen());
            }
            if (freecam) tickFreecam(client);
            tickCrystalMacro(client);
            tickAutoTotem(client);
        });
    }

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    public static boolean isSpeedMineEnabled() { return speedMine; }
    public static void toggleSpeedMine() { speedMine = !speedMine; }

    public static boolean isFreecamEnabled() {
        return freecam;
    }

    public static ArmorStand getFreecamCamera() {
        return freecam ? camera : null;
    }

    public static void applyFreecamMouseLook(double mouseX, double mouseY) {
        if (!freecam || camera == null) return;

        float yawDelta = (float) mouseX * 0.15F;
        float pitchDelta = (float) mouseY * 0.15F;
        camera.setYRot(camera.getYRot() + yawDelta);
        camera.setXRot(Math.max(-90.0F, Math.min(90.0F,
                camera.getXRot() + pitchDelta)));

        // This camera is client-only and is not ticked by the world.
        // Keep previous and current rotations identical to prevent interpolation jitter.
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
    }

    static void toggleFreecam() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        if (!freecam) {
            savedYaw = client.player.getYRot();
            savedPitch = client.player.getXRot();
            camera = new ArmorStand(EntityType.ARMOR_STAND, client.level);
            camera.setPos(client.player.getX(), client.player.getEyeY(), client.player.getZ());
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.xOld = camera.getX();
            camera.yOld = camera.getY();
            camera.zOld = camera.getZ();
            camera.setYRot(savedYaw);
            camera.setXRot(savedPitch);
            camera.setNoGravity(true);
            camera.setInvisible(true);
            previousNightVision = client.player.getEffect(MobEffects.NIGHT_VISION);
            if (previousNightVision != null) previousNightVision = new MobEffectInstance(previousNightVision);
            client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 999999, 0, false, false, false));
            client.setCameraEntity(camera);
            freecam = true;
        } else {
            freecam = false;
            client.setCameraEntity(client.player);
            client.player.setYRot(savedYaw);
            client.player.setXRot(savedPitch);
            client.player.removeEffect(MobEffects.NIGHT_VISION);
            if (previousNightVision != null) client.player.addEffect(previousNightVision);
            previousNightVision = null;
            camera = null;
        }
    }


    public static boolean isCrystalMacroEnabled() {
        return crystalMacro;
    }

    public static void toggleCrystalMacro() {
        crystalMacro = !crystalMacro;
        nextCrystalClickNanos = 0L;
    }

    public static int getCrystalCps() {
        return crystalCps;
    }

    public static void setCrystalCps(int cps) {
        crystalCps = Math.max(1, Math.min(40, cps));
    }

    private static void tickCrystalMacro(Minecraft client) {
        boolean holdingUse = client.options.keyUse.isDown();
        if (!crystalMacro || client.player == null || client.level == null || client.gameMode == null
                || client.screen != null || !holdingUse
                || !client.player.getMainHandItem().is(Items.END_CRYSTAL)) {
            nextCrystalClickNanos = 0L;
            crystalBase = null;
            lastAttackedCrystal = null;
            return;
        }

        // Remember the base even after the crosshair moves onto the spawned crystal.
        if (client.hitResult instanceof BlockHitResult blockHit) {
            crystalBase = blockHit.getBlockPos();
            long now = System.nanoTime();
            long interval = 1_000_000_000L / crystalCps;
            if (nextCrystalClickNanos == 0L) nextCrystalClickNanos = now;

            int clicks = 0;
            while (now >= nextCrystalClickNanos && clicks < 3) {
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, blockHit);
                nextCrystalClickNanos += interval;
                clicks++;
            }
        }

        if (crystalBase == null) return;

        // Attack each spawned crystal once, as soon as it becomes visible.
        var nearbyCrystals = client.level.getEntitiesOfClass(
                EndCrystal.class, new AABB(crystalBase).inflate(2.0));
        if (nearbyCrystals.isEmpty()) {
            lastAttackedCrystal = null;
            return;
        }

        EndCrystal crystal = nearbyCrystals.get(0);
        if (!crystal.getUUID().equals(lastAttackedCrystal)) {
            client.gameMode.attack(client.player, crystal);
            client.player.swing(InteractionHand.MAIN_HAND);
            lastAttackedCrystal = crystal.getUUID();
        }
    }

    public static boolean isAutoTotemEnabled() {
        return autoTotem;
    }

    public static void toggleAutoTotem() {
        autoTotem = !autoTotem;
        lastTotemRefillTick = -1;
    }

    private static void tickAutoTotem(Minecraft client) {
        if (!autoTotem || client.player == null || client.gameMode == null || client.screen != null) return;

        // Keep the offhand supplied. When a totem pops, the next client tick
        // swaps the next available totem from the inventory into the offhand.
        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (lastTotemRefillTick == client.player.tickCount) return;

        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            if (!client.player.getInventory().getItem(inventorySlot).is(Items.TOTEM_OF_UNDYING)) continue;

            int menuSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
            client.gameMode.handleContainerInput(
                    client.player.inventoryMenu.containerId,
                    menuSlot,
                    40,
                    ContainerInput.SWAP,
                    client.player);
            lastTotemRefillTick = client.player.tickCount;
            return;
        }
    }

    private static void tickFreecam(Minecraft client) {
        if (client.player == null || client.level == null || camera == null) {
            freecam = false;
            if (client.player != null) client.setCameraEntity(client.player);
            camera = null;
            return;
        }

        // Mouse input rotates the freecam directly; do not copy player rotations.
        double speed = 0.55;
        float yaw = camera.getYRot();
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double sideX = Math.cos(radians);
        double sideZ = Math.sin(radians);
        double dx = 0, dy = 0, dz = 0;

        // Match the expected freecam controls: W forward, S backward,
        // A left, and D right relative to the camera's facing direction.
        if (forwardKey.isDown()) { dx += forwardX; dz += forwardZ; }
        if (backKey.isDown()) { dx -= forwardX; dz -= forwardZ; }
        if (rightKey.isDown()) { dx += sideX; dz += sideZ; }
        if (leftKey.isDown()) { dx -= sideX; dz -= sideZ; }
        if (upKey.isDown()) dy += 1;
        if (downKey.isDown()) dy -= 1;

        Vec3 movement = new Vec3(dx, dy, dz);
        if (movement.lengthSqr() > 0) {
            movement = movement.normalize().scale(speed);
            camera.setPos(camera.position().add(movement));
        }

        // This camera is not ticked by the world. Synchronize interpolation
        // history AFTER movement so rendering never lerps between stale positions.
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.xOld = camera.getX();
        camera.yOld = camera.getY();
        camera.zOld = camera.getZ();
        camera.xRotO = camera.getXRot();
        camera.yRotO = camera.getYRot();
    }
}

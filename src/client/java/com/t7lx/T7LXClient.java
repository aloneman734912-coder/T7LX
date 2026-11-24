package com.t7lx;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
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
        });
    }

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

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

package com.t7lx.mixin;

import com.t7lx.T7LXClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;turn(DD)V"
            )
    )
    private void t7lx$turnFreecamCamera(Entity entity, double yaw, double pitch) {
        Entity camera = T7LXClient.getFreecamCamera();
        if (camera != null) {
            camera.turn(yaw, pitch);
        } else {
            entity.turn(yaw, pitch);
        }
    }
}

package com.t7lx.mixin;

import com.t7lx.T7LXClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void t7lx$turnFreecamCamera(LocalPlayer player, double mouseX, double mouseY) {
        if (T7LXClient.isFreecamEnabled()) {
            T7LXClient.applyFreecamMouseLook(mouseX, mouseY);
        } else {
            player.turn(mouseX, mouseY);
        }
    }
}

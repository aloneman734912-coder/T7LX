package com.t7lx.mixin;

import com.t7lx.T7LXClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockState.class)
public abstract class SpeedMineMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void t7lx$multiplyMiningProgress(Player player, BlockGetter level, BlockPos pos,
                                              CallbackInfoReturnable<Float> cir) {
        if (T7LXClient.isSpeedMineEnabled()) {
            cir.setReturnValue(cir.getReturnValue() * 8.0F);
        }
    }
}

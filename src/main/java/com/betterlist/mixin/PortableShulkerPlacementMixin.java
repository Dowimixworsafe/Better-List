package com.betterlist.mixin;

import com.betterlist.server.PortableShulkerService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class PortableShulkerPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("RETURN"))
    private void trackPlacement(Level level, BlockPos pos, BlockState state, LivingEntity player, ItemStack stack, CallbackInfo ci) {
        PortableShulkerService.placed(level, pos);
    }
}

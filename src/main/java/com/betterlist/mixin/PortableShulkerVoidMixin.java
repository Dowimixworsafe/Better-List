package com.betterlist.mixin;

import com.betterlist.server.PortableShulkerService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class PortableShulkerVoidMixin {
    @Inject(method = "onBelowWorld", at = @At("RETURN"))
    private void bml_void(CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity item && item.isRemoved()) PortableShulkerService.lost(item, "void");
    }
}

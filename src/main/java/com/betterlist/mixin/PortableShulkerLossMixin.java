package com.betterlist.mixin;

import com.betterlist.server.PortableShulkerService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public class PortableShulkerLossMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void bml_despawn(CallbackInfo ci) {
        PortableShulkerService.lost((ItemEntity) (Object) this, "despawn");
    }

    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private void bml_destroyed(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PortableShulkerService.lost((ItemEntity) (Object) this,
                source.is(DamageTypes.FELL_OUT_OF_WORLD) ? "void" : source.is(DamageTypeTags.IS_FIRE) ? "fire" : "destroyed");
    }
}

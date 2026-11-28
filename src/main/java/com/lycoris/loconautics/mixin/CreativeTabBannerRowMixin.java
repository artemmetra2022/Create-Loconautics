package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.common.creative.CreativeSections;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;


// another mod rebuilds displayItems as a set, which merges every padding row into one empty slot, so this runs last
@Mixin(value = CreativeModeTab.class, remap = false, priority = 2000)
public class CreativeTabBannerRowMixin {

    @Shadow
    private Collection<ItemStack> displayItems;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void loconautics$reserveBannerRow(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if (!LoconauticsRegistries.LOCONAUTICS_TAB.isBound() || (Object) this != LoconauticsRegistries.LOCONAUTICS_TAB.get()) {
            return;
        }
        displayItems = CreativeSections.layout(new ArrayList<>(displayItems));
    }
}

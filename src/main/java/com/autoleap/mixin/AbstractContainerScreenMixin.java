package com.autoleap.mixin;

import com.autoleap.features.PetsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Shadow protected AbstractContainerMenu menu;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!PetsMenu.INSTANCE.isActive()) return;
        String title = ((AbstractContainerScreen<?>) (Object) this).getTitle().getString()
                .replaceAll("§.", "").trim();
        if (title.startsWith("Pets")) {
            ci.cancel();
            PetsMenu.INSTANCE.drawOverlay(guiGraphics, mouseX, mouseY, this.menu);
        }
    }
}

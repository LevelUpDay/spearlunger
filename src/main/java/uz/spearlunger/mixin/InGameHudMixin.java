package uz.spearlunger.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.norender.NoRenderConfig;

/** NoRender: portal/nausea, qovoq/powder snow qatlami, vignette. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$portal(DrawContext context, float strength, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().portalNausea)) ci.cancel();
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$nausea(DrawContext context, float strength, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().portalNausea)) ci.cancel();
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$pumpkin(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().pumpkin)) ci.cancel();
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$vignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().vignette)) ci.cancel();
    }
}

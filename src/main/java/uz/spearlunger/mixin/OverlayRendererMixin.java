package uz.spearlunger.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.norender.NoRenderConfig;

/** NoRender: past olov, suv ostidagi va blok ichidagi qatlam, totem animatsiyasi. */
@Mixin(InGameOverlayRenderer.class)
public class OverlayRendererMixin {
    /** Olov chizilishidan oldin pastga suramiz (yoki umuman chizmaymiz)... */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void spearlunger$fireHead(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite,
                                             CallbackInfo ci) {
        NoRenderConfig cfg = NoRenderConfig.get();
        int mode = cfg.enabled ? cfg.fire : 0;
        if (mode == 2) {
            ci.cancel();
        } else if (mode == 1) {
            matrices.push();
            matrices.translate(0.0f, -cfg.fireLower / 100.0f, 0.0f);
        }
    }

    /** ...va chizib bo'lgach qaytaramiz. */
    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void spearlunger$fireReturn(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite,
                                               CallbackInfo ci) {
        NoRenderConfig cfg = NoRenderConfig.get();
        if (cfg.enabled && cfg.fire == 1) matrices.pop();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void spearlunger$underwater(MinecraftClient client, MatrixStack matrices,
                                               VertexConsumerProvider consumers, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().underwaterOverlay)) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void spearlunger$inWall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider consumers,
                                           CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().inWall)) ci.cancel();
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$totem(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue,
                                   CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().totemAnimation)) ci.cancel();
    }
}

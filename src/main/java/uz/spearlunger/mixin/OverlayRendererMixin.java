package uz.spearlunger.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
    @WrapOperation(method = "renderOverlays", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V"))
    private void spearlunger$fire(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite,
                                  Operation<Void> original) {
        NoRenderConfig cfg = NoRenderConfig.get();
        int mode = cfg.enabled ? cfg.fire : 0;
        if (mode == 2) return;
        if (mode == 1) {
            matrices.push();
            matrices.translate(0.0f, -cfg.fireLower / 100.0f, 0.0f);
            original.call(matrices, consumers, sprite);
            matrices.pop();
            return;
        }
        original.call(matrices, consumers, sprite);
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

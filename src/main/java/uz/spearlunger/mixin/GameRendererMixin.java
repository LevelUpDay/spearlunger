package uz.spearlunger.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.norender.NoRenderConfig;

/** NoRender: zarba yeganda kamera silkinmaydi. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$hurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().hurtCam)) ci.cancel();
    }
}

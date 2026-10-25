package uz.spearlunger.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.norender.NoRenderConfig;

/** NoRender: yomg'ir va qor chizilmaydi. */
@Mixin(WeatherRendering.class)
public class WeatherMixin {
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$weather(VertexConsumerProvider consumers, Vec3d pos, WeatherRenderState state,
                                     CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().weather)) ci.cancel();
    }
}

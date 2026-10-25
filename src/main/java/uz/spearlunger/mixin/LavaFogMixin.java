package uz.spearlunger.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.norender.NoRenderConfig;

/** NoRender: lava ichidagi tuman - tuman ko'rish masofasigacha uzoqlashtiriladi. */
@Mixin(LavaFogModifier.class)
public class LavaFogMixin {
    @Inject(method = "applyStartEndModifier", at = @At("TAIL"), require = 0)
    private void spearlunger$clear(FogData data, Camera camera, ClientWorld world, float viewDistance,
                                   RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRenderConfig.on(NoRenderConfig.get().lavaFog)) {
            data.environmentalStart = data.renderDistanceStart;
            data.environmentalEnd = data.renderDistanceEnd;
        }
    }
}

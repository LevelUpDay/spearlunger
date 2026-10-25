package uz.spearlunger.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uz.norender.NoRenderConfig;

/** NoRender: portlash zarrachalari (crystal, anchor, TNT) chizilmaydi - ekran toza, FPS tushmaydi. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void spearlunger$explosion(ParticleEffect effect, double x, double y, double z, double vx, double vy,
                                       double vz, CallbackInfoReturnable<Particle> cir) {
        if (!NoRenderConfig.on(NoRenderConfig.get().explosions)) return;
        if (effect.getType() == ParticleTypes.EXPLOSION || effect.getType() == ParticleTypes.EXPLOSION_EMITTER) {
            cir.setReturnValue(null);
        }
    }
}

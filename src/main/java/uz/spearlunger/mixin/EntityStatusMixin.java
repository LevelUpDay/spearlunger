package uz.spearlunger.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uz.nametags.TotemPops;

/** Totem pop'larni sanash. TAIL: metod avval tarmoq thread'ida chaqirilib asosiy thread'ga o'tkaziladi. */
@Mixin(ClientPlayNetworkHandler.class)
public class EntityStatusMixin {
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void spearlunger$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        TotemPops.onStatus(packet);
    }
}

package uz.spearlunger.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** O'ng tugma bosib turilganda ishlatish orasidagi kechikish (vanilla 4 tick). */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int fastplace$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void fastplace$setItemUseCooldown(int value);
}

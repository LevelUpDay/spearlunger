package uz.autototem.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Inventar oynasining ekrandagi chap-yuqori burchagi (slotga sichqonchani olib borish uchun). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int autototem$getX();

    @Accessor("y")
    int autototem$getY();
}

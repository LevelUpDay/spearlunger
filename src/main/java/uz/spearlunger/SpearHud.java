package uz.spearlunger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public class SpearHud {
    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden || !Config.get().hud) return;
        String line = SpearLungerClient.CONTROLLER.hudLine(c);
        if (line == null) return;
        int x = ctx.getScaledWindowWidth() - c.textRenderer.getWidth(line) - 6;
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(line), x, 6, 0xFFFFFFFF);
    }
}

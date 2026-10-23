package uz.autotnt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class TntHud {
    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        AutoTntConfig cfg = AutoTntConfig.get();
        if (p == null || c.options.hudHidden || !cfg.hud) return;
        AutoTntConfig.Profile cf = cfg.p();

        String[] ign = {"Auto", "Flint->cart", "Fire charge", "Activator"};
        int x = 6, y = 6;
        line(ctx, c, "§6Auto TNT §7| Bow: " + (cfg.bowMode ? "§aON" : "§cOFF")
                + " §7| Profil " + (cfg.active + 1) + " §7| " + ign[Math.floorMod(cf.ignition, 4)], x, y);
        y += 10;
        line(ctx, c, "§7Hotbar: " + mark(p, cf.ignition == 3 ? Items.ACTIVATOR_RAIL : Items.RAIL, "Rail")
                + " " + mark(p, Items.TNT_MINECART, "Cart")
                + " " + mark(p, Items.FLINT_AND_STEEL, "Flint")
                + " " + mark(p, Items.CROSSBOW, "Xbow"), x, y);
        String pv = AutoTntClient.CONTROLLER.preview(c);
        if (pv != null) {
            y += 10;
            line(ctx, c, pv, x, y);
        }
    }

    private static void line(DrawContext ctx, MinecraftClient c, String s, int x, int y) {
        ctx.drawTextWithShadow(c.textRenderer, Text.literal(s), x, y, 0xFFFFFFFF);
    }

    private static String mark(ClientPlayerEntity p, Item item, String name) {
        boolean has = false;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(item)) { has = true; break; }
        }
        return (has ? "§a+" : "§c-") + name;
    }
}

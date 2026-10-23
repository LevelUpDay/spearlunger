package uz.autotnt;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class TntSettingsScreen extends Screen {
    private static final String[] IGN = {"Olov: Auto", "Olov: Flint (cart)", "Olov: Fire charge", "Olov: Activator"};
    private final Screen parent;

    public TntSettingsScreen(Screen parent) {
        super(Text.literal("Auto TNT Cart"));
        this.parent = parent;
    }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    private ButtonWidget btn(int x, int y, int w, Supplier<String> label, Runnable action) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
    }

    @Override
    protected void init() {
        AutoTntConfig cfg = AutoTntConfig.get();
        AutoTntConfig.Profile cf = cfg.p();
        int w = 150, s = 24;
        int x1 = width / 2 - 155, x2 = width / 2 + 5;
        int y = height / 2 - 100;

        // chap ustun: profil sozlamalari
        addDrawableChild(btn(x1, y, w, () -> "Profil: " + (cfg.active + 1) + "/" + cfg.profiles.length, () -> {
            cfg.active = (cfg.active + 1) % cfg.profiles.length;
            clearAndInit();
        }));
        addDrawableChild(new IntSlider(x1, y + s, w, 20, 0, 20, cf.delayTicks,
                v -> "Delay: " + v + " tick (" + (v * 50) + " ms)", v -> cf.delayTicks = v));
        addDrawableChild(new IntSlider(x1, y + 2 * s, w, 20, 30, 60, cf.reachTenths,
                v -> "Reach: " + (v / 10.0) + " blok", v -> cf.reachTenths = v));
        addDrawableChild(new IntSlider(x1, y + 3 * s, w, 20, 0, 100, cf.minSelfDistTenths,
                v -> "O'zimdan min: " + (v / 10.0) + " blok", v -> cf.minSelfDistTenths = v));
        addDrawableChild(new IntSlider(x1, y + 4 * s, w, 20, 1, 5, cf.cartCount,
                v -> "Minecart soni: " + v, v -> cf.cartCount = v));
        addDrawableChild(new IntSlider(x1, y + 5 * s, w, 20, 0, 20, cf.friendRadius,
                v -> "Do'st radiusi: " + v, v -> cf.friendRadius = v));
        addDrawableChild(btn(x1, y + 6 * s, w, () -> "Inventarga qaytarish: " + onoff(cfg.restoreInventory),
                () -> cfg.restoreInventory = !cfg.restoreInventory));

        // o'ng ustun
        addDrawableChild(btn(x2, y, w, () -> "Bow auto-TNT: " + onoff(cfg.bowMode), () -> cfg.bowMode = !cfg.bowMode));
        addDrawableChild(btn(x2, y + s, w, () -> IGN[Math.floorMod(cf.ignition, 4)], () -> cf.ignition = (cf.ignition + 1) % 4));
        addDrawableChild(btn(x2, y + 2 * s, w, () -> "Nishonni oldindan: " + onoff(cf.predictTarget),
                () -> cf.predictTarget = !cf.predictTarget));
        addDrawableChild(new IntSlider(x2, y + 3 * s, w, 20, 0, 40, cf.lookaheadTicks,
                v -> "Oldinga: " + v + " tick", v -> cf.lookaheadTicks = v));
        addDrawableChild(btn(x2, y + 4 * s, w, () -> "Inventardan olish: " + onoff(cfg.autoRefill),
                () -> cfg.autoRefill = !cfg.autoRefill));
        addDrawableChild(btn(x2, y + 5 * s, w, () -> "Tovush: " + onoff(cfg.sounds), () -> cfg.sounds = !cfg.sounds));
        addDrawableChild(btn(x2, y + 6 * s, w, () -> "Xabarlar: " + onoff(cfg.messages), () -> cfg.messages = !cfg.messages));
        addDrawableChild(btn(x2, y + 7 * s, w, () -> "HUD: " + onoff(cfg.hud), () -> cfg.hud = !cfg.hud));

        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(width / 2 - 100, y + 8 * s + 4, 200, 20).build());
    }

    @Override
    public void close() {
        AutoTntConfig.save();
        client.setScreen(parent);
    }

    private static class IntSlider extends SliderWidget {
        private final int min, max;
        private final IntFunction<String> label;
        private final IntConsumer setter;

        IntSlider(int x, int y, int w, int h, int min, int max, int cur,
                  IntFunction<String> label, IntConsumer setter) {
            super(x, y, w, h, Text.empty(), (cur - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}

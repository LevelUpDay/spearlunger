package uz.spearlunger;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Spear Lunger"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int x = width / 2 - 100;
        int y = height / 2 - 90;
        addDrawableChild(new IntSlider(x, y, 200, 20, 0, 40, c.extraDelayTicks,
                v -> "Qo'shimcha kechikish: " + v + " tick (" + (v * 50) + " ms)", v -> c.extraDelayTicks = v));
        addDrawableChild(new IntSlider(x, y + 24, 200, 20, 0, 20, c.minFood,
                v -> v == 0 ? "Min ochlik: o'chiq" : "Min ochlik: " + v, v -> c.minFood = v));
        addDrawableChild(new IntSlider(x, y + 48, 200, 20, 5, 200, c.rangeHorizontal,
                v -> "X gorizontal masofa: " + v, v -> c.rangeHorizontal = v));
        addDrawableChild(new IntSlider(x, y + 72, 200, 20, 5, 400, c.rangeVertical,
                v -> "X vertikal masofa: " + v, v -> c.rangeVertical = v));
        addDrawableChild(new IntSlider(x, y + 96, 200, 20, 20, 60, (int) Math.round(c.maceReach * 10),
                v -> "X mace masofasi: " + (v / 10.0) + " blok", v -> c.maceReach = v / 10.0));
        addDrawableChild(toggle(x, y + 120, () -> "X: uzoqda gorizontal qarash: " + (c.flattenPitch ? "ON" : "OFF"),
                () -> c.flattenPitch = !c.flattenPitch));
        addDrawableChild(toggle(x, y + 144, () -> "X: faqat havoda: " + (c.targetRequireAir ? "ON" : "OFF"),
                () -> c.targetRequireAir = !c.targetRequireAir));
        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(x, y + 172, 200, 20).build());
    }

    private static ButtonWidget toggle(int x, int y, Supplier<String> label, Runnable action) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, 200, 20).build();
    }

    @Override
    public void close() {
        Config.save();
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

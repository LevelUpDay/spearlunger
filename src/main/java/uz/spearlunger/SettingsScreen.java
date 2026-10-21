package uz.spearlunger;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

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
        int y = height / 2 - 50;
        addDrawableChild(new IntSlider(x, y, 200, 20, 0, 40, c.lungeDelayTicks,
                v -> "Lunge delay: " + v + " tick (" + (v * 50) + " ms)", v -> c.lungeDelayTicks = v));
        addDrawableChild(new IntSlider(x, y + 24, 200, 20, 5, 200, c.rangeHorizontal,
                v -> "X gorizontal masofa: " + v, v -> c.rangeHorizontal = v));
        addDrawableChild(new IntSlider(x, y + 48, 200, 20, 5, 400, c.rangeVertical,
                v -> "X vertikal masofa: " + v, v -> c.rangeVertical = v));
        addDrawableChild(ButtonWidget.builder(Text.literal("Tayyor"), b -> close())
                .dimensions(x, y + 80, 200, 20).build());
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

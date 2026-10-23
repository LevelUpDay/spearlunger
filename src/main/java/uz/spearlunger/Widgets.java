package uz.spearlunger;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Sozlama oynalari uchun umumiy tugma va slider. */
public final class Widgets {
    private Widgets() {}

    public static String onoff(boolean b) { return b ? "§aON" : "§cOFF"; }

    public static ButtonWidget button(int x, int y, int w, Supplier<String> label, Runnable action) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
    }

    public static class IntSlider extends SliderWidget {
        private final int min, max;
        private final IntFunction<String> label;
        private final IntConsumer setter;

        public IntSlider(int x, int y, int w, int min, int max, int cur,
                         IntFunction<String> label, IntConsumer setter) {
            super(x, y, w, 20, Text.empty(), (Math.max(min, Math.min(max, cur)) - min) / (double) (max - min));
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

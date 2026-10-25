package uz.spearlunger.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** ClickGUI'dagi bitta modul: chap tugma yoqadi/o'chiradi, o'ng tugma sozlamalarini ochadi. */
public final class Module {
    public enum Category {
        COMBAT("Combat"), MOVEMENT("Movement"), WORLD("World"), CLIENT("Client");

        public final String title;

        Category(String title) { this.title = title; }
    }

    public final String name;
    public final String desc;
    public final Category category;
    private final BooleanSupplier enabled;
    private final Consumer<Boolean> setEnabled;
    private final Supplier<List<Setting>> settings;

    Module(String name, String desc, Category category, BooleanSupplier enabled, Consumer<Boolean> setEnabled,
           Supplier<List<Setting>> settings) {
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.enabled = enabled;
        this.setEnabled = setEnabled;
        this.settings = settings;
    }

    /** false = faqat sozlamalar (masalan Anti-cheat), chap tugma ham sozlamalarni ochadi. */
    public boolean toggleable() { return enabled != null; }

    public boolean isEnabled() { return enabled != null && enabled.getAsBoolean(); }

    public void toggle() {
        if (setEnabled == null) return;
        setEnabled.accept(!isEnabled());
        Modules.saveAll();
    }

    /** Har chizishda qayta yig'iladi (masalan TNT profili almashsa qiymatlar yangilanadi). */
    public List<Setting> settings() { return settings.get(); }
}

package uz.spearlunger.gui;

import net.minecraft.client.option.KeyBinding;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Modul sozlamasi (ModuleScreen chizadi). */
public abstract class Setting {
    public final String name;
    public final String desc;

    private Setting(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /** Guruh sarlavhasi. */
    public static final class Group extends Setting {
        Group(String name) { super(name, ""); }
    }

    public static final class Bool extends Setting {
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        Bool(String name, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(name, desc);
            this.get = get;
            this.set = set;
        }
    }

    public static final class Int extends Setting {
        final int min, max;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> fmt;

        Int(String name, String desc, int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
            super(name, desc);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
        }
    }

    public static final class Choice extends Setting {
        final String[] values;
        final IntSupplier get;
        final IntConsumer set;

        Choice(String name, String desc, String[] values, IntSupplier get, IntConsumer set) {
            super(name, desc);
            this.values = values;
            this.get = get;
            this.set = set;
        }
    }

    public static final class Key extends Setting {
        final KeyBinding kb;

        Key(String name, String desc, KeyBinding kb) {
            super(name, desc);
            this.kb = kb;
        }
    }

    public static final class Action extends Setting {
        final Runnable run;

        Action(String name, String desc, Runnable run) {
            super(name, desc);
            this.run = run;
        }
    }

    /** Itemlar ro'yxati (ItemPickerScreen'da tanlanadi), id'lar: "minecraft:totem_of_undying". */
    public static final class Items extends Setting {
        final java.util.List<String> ids;

        Items(String name, String desc, java.util.List<String> ids) {
            super(name, desc);
            this.ids = ids;
        }
    }

    public static Items items(String name, String desc, java.util.List<String> ids) {
        return new Items(name, desc, ids);
    }

    public static Group group(String name) { return new Group(name); }

    public static Bool bool(String name, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        return new Bool(name, desc, get, set);
    }

    public static Int num(String name, String desc, int min, int max, IntSupplier get, IntConsumer set,
                          IntFunction<String> fmt) {
        return new Int(name, desc, min, max, get, set, fmt);
    }

    public static Int num(String name, String desc, int min, int max, IntSupplier get, IntConsumer set) {
        return new Int(name, desc, min, max, get, set, String::valueOf);
    }

    public static Choice choice(String name, String desc, String[] values, IntSupplier get, IntConsumer set) {
        return new Choice(name, desc, values, get, set);
    }

    public static Key key(String name, String desc, KeyBinding kb) { return new Key(name, desc, kb); }

    public static Action action(String name, String desc, Runnable run) { return new Action(name, desc, run); }
}

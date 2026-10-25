package uz.cheststealer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StealerConfig {
    public boolean enabled = false;
    /** 0 = faqat tanlanganlar, 1 = hammasi, 2 = tanlanganlardan tashqari hammasi. */
    public int mode = 0;
    /** Tanlangan itemlar (masalan "minecraft:totem_of_undying"). */
    public List<String> items = new ArrayList<>(List.of(
            "minecraft:totem_of_undying", "minecraft:enchanted_golden_apple", "minecraft:golden_apple",
            "minecraft:end_crystal", "minecraft:obsidian", "minecraft:respawn_anchor", "minecraft:glowstone",
            "minecraft:experience_bottle", "minecraft:ender_pearl"));
    /** Itemlarni olish orasidagi kechikish (tick, 0 = hammasi birdaniga). */
    public int delay = 1;
    /** Sandiq ochilgach birinchi itemdan oldin kutish (tick). */
    public int openDelay = 2;
    /** Tugagach sandiqni yopadi. */
    public boolean autoClose = false;
    /**
     * Faqat haqiqiy sandiqlar: oyna siz sandiq/barrel/shulker blokini bosganingizdan keyin ochilgan bo'lsa.
     * Serverlarning menyulari ham sandiq oynasi ko'rinishida ochiladi - ulardan olmaydi.
     */
    public boolean realOnly = true;
    /** Shulker box'dan ham. */
    public boolean shulkers = true;
    /** Shift bosib ochsangiz ishlamaydi (o'zingiz olmoqchi bo'lsangiz). */
    public boolean shiftPause = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static StealerConfig instance = new StealerConfig();

    public static StealerConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cheststealer.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                StealerConfig c = GSON.fromJson(Files.readString(p), StealerConfig.class);
                if (c != null) instance = c;
            }
            if (instance.items == null) instance.items = new ArrayList<>();
            save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

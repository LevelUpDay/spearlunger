package uz.fastplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class FastPlaceConfig {
    public boolean enabled = false;
    /** Qo'yishlar orasidagi kechikish (tick). Vanilla = 4, 1 = har tickda. */
    public int delay = 1;
    /** Bir tickda nechta blok (1 dan ko'p bo'lsa har biri uchun qaytadan nishon olinadi). */
    public int perTick = 1;
    /** Faqat blok ushlaganda (ovqat, kamon va boshqalarga ta'sir qilmaydi). */
    public boolean onlyBlocks = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FastPlaceConfig instance = new FastPlaceConfig();

    public static FastPlaceConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fastplace.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                FastPlaceConfig c = GSON.fromJson(Files.readString(p), FastPlaceConfig.class);
                if (c != null) instance = c;
            }
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

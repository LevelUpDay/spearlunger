package uz.crystalhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class CrystalHudConfig {
    public boolean enabled = true;
    /** Faqat qo'lda end crystal bo'lsa ko'rsatadi. */
    public boolean onlyHolding = true;
    /** Dushman shu masofada bo'lsa (blok). */
    public int range = 12;
    /** Crystal qo'yish masofasi (0.1 blok). */
    public int placeRangeTenths = 50;
    /** Nechta eng yaxshi joy ko'rsatiladi. */
    public int count = 3;
    /** Dushmanga kamida shuncha zarar (yurak yarimi). */
    public int minDamage = 6;
    /** O'zingizga eng ko'pi shuncha zarar. */
    public int maxSelf = 8;
    public boolean ignoreFriends = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CrystalHudConfig instance = new CrystalHudConfig();

    public static CrystalHudConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystalhud.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                CrystalHudConfig c = GSON.fromJson(Files.readString(p), CrystalHudConfig.class);
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

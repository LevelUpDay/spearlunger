package uz.macecombo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class MaceComboConfig {
    /** Tugma (standart J) bosilganda bitta kombinatsiya: pearl -> wind charge -> mace. */
    public boolean enabled = true;
    /** Nishon shundan uzoq bo'lsa avval ender pearl bilan yaqinlashadi (blok). */
    public int pearlDist = 12;
    /** Pearl nishondan shuncha oldinga tushadi (0.1 blok). */
    public int pearlShortTenths = 20;
    /** Shu masofada wind charge bilan tepaga sakraydi (blok). */
    public int windDist = 6;
    /** Mace bilan urish masofasi (0.1 blok). */
    public int reachTenths = 33;
    /** Nishonni qidirish masofasi (blok). */
    public int range = 48;
    public boolean pearl = true;
    public boolean ignoreFriends = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MaceComboConfig instance = new MaceComboConfig();

    public static MaceComboConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("macecombo.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                MaceComboConfig c = GSON.fromJson(Files.readString(p), MaceComboConfig.class);
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

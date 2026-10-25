package uz.pearlclutch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PearlConfig {
    public boolean enabled = true;
    /** O'zingiz otganda: harakatingizni hisoblab, pearl qaragan joyingizga tushadigan qilib otadi. */
    public boolean aimAssist = true;
    /** Baland joydan yiqilayotganda o'zi xavfsiz blokka pearl otadi. */
    public boolean autoClutch = true;
    /** Void'ga (ostida blok yo'q) yiqilayotganda ham. */
    public boolean voidClutch = true;
    /** Jami yiqilish shundan ko'p bo'lsa clutch qiladi (blok). */
    public int minFall = 10;
    /** Qo'nadigan blokni qidirish radiusi (blok). */
    public int radius = 10;
    /** Hotbar'da bo'lmasa inventardan oladi (keyin joyiga qaytaradi). */
    public boolean fromInventory = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PearlConfig instance = new PearlConfig();

    public static PearlConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pearlclutch.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PearlConfig c = GSON.fromJson(Files.readString(p), PearlConfig.class);
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

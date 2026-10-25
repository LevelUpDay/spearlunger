package uz.autototem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class TotemConfig {
    public boolean enabled = true;
    /** 0 = inventar (E + sichqoncha + F), 1 = tezkor (inventar ochilmaydi), 2 = hotbar (F). */
    public int mode = 0;
    /** Faqat jon (HP + absorption) shundan kam bo'lsa (0 = har doim). */
    public int health = 0;
    /** Totem kerak bo'lgach tasodifiy kutish: min..max tick. */
    public int delayMin = 0;
    public int delayMax = 2;
    /** Sichqonchani totemgacha olib borish vaqti (tick, 0 = bir zumda). */
    public int moveTicks = 2;
    /** F bosilgach inventarni yopishdan oldin kutish (tick). */
    public int closeDelay = 1;
    /** Totemni hotbar'dan ham olish. */
    public boolean fromHotbar = true;
    /** Totem ishlatilganda xabar (nechta qolgani bilan). */
    public boolean popMessage = true;
    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TotemConfig instance = new TotemConfig();

    public static TotemConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autototem.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                TotemConfig c = GSON.fromJson(Files.readString(p), TotemConfig.class);
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

package uz.playerchase;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PlayerChaseConfig {
    /** Modul yoqilganmi (ClickGUI'da chap tugma) - tugma faqat shu yoqilganda ishlaydi. */
    public boolean enabled = true;
    /** Nishon qidirish masofasi (blok). */
    public int range = 60;
    /** 0 = eng yaqin, 1 = qarash (pritsel) bo'yicha eng yaqin. */
    public int targetSelect = 0;
    public boolean ignoreFriends = true;

    /** Nishondan shuncha baland uchib boradi, keyin sho'ng'iydi. */
    public int attackHeight = 8;
    /** Yetib borishiga shuncha tick qolganda elytra'ni yechib mace'ga o'tadi. */
    public int unequipLead = 8;
    /** Firework'lar orasida eng kami. */
    public int fireworkDelay = 10;
    /** Elytra shundan kam durability bo'lsa ishlatilmaydi. */
    public int minDurability = 1;
    /** Firework bo'lmasa oyoq ostiga wind charge bilan otilib uchadi. */
    public boolean windTakeoff = true;
    /** Elytra/firework bo'lmasa, qarab turgan tomonga (nishon ustiga hisoblab) pearl otadi. */
    public boolean pearlFallback = true;
    /** Nishonga yetib, mace bilan urgandan keyin o'zi o'chadi. */
    public boolean autoOffAfterHit = true;
    /** Eng ko'pi shuncha tick ishlaydi (keyin o'zi bekor qiladi). */
    public int timeoutTicks = 400;

    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PlayerChaseConfig instance = new PlayerChaseConfig();

    public static PlayerChaseConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("playerchase.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PlayerChaseConfig c = GSON.fromJson(Files.readString(p), PlayerChaseConfig.class);
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

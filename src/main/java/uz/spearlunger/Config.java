package uz.spearlunger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    /**
     * Cooldown to'lgandan keyin qo'shimcha kutish (tick, 20 tick = 1 soniya).
     * Nayza cooldown'i baribir majburiy (netherite ~23 tick, diamond ~21 tick), bu undan tashqari.
     */
    public int extraDelayTicks = 0;
    /** Ochlik shundan past bo'lsa lunge qilmaydi (0 = o'chiq). Lunge III har safar ~3 ochlik yeydi. */
    public int minFood = 6;
    /** X: uzoqda pitch = 0 qilib qarash (Lunge impulsi faqat gorizontal, pastga qarasa kuchsiz). */
    public boolean flattenPitch = true;
    /** X: faqat havoda ishga tushadi (mace uchun tushish masofasi kerak). */
    public boolean targetRequireAir = true;
    /** X: nishongacha shu masofada mace bilan uradi (blok). */
    public double maceReach = 3.0;
    /** X: eng ko'pi bilan shuncha tick ishlaydi. */
    public int targetTimeoutTicks = 200;
    /** X uchun qidiruv: gorizontal masofa (blok). */
    public int rangeHorizontal = 50;
    /** X uchun qidiruv: vertikal masofa (blok). */
    public int rangeVertical = 100;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spearlunger.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            } else {
                save();
            }
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

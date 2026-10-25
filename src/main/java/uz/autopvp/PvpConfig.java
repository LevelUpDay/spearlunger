package uz.autopvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PvpConfig {
    /** Tugma (standart K) bilan yoqiladi, o'chirguningizcha o'zi jang qiladi. */
    public boolean enabled = false;
    /** Nishon qidirish masofasi (blok). */
    public int range = 32;
    /** Qilich/bolta bilan urish masofasi (0.1 blok). */
    public int meleeTenths = 30;

    /** Uzoqda: Lunge nayza bilan uchib borish (cooldown to'lishi bilan). */
    public boolean lunge = true;
    /** Uzoqda pitch = 0 (Lunge impulsi to'liq gorizontal). */
    public boolean flatten = true;
    /** Yerda bo'lsangiz wind charge bilan sakrash. */
    public boolean windJump = true;
    /** Shundan uzoqda wind charge (blok). */
    public int windMinDist = 5;
    /** Juda uzoqda va yerda bo'lsangiz nishon oldiga ender pearl. */
    public boolean pearl = true;
    public int pearlDist = 18;

    /** Yaqinda urish. */
    public boolean melee = true;
    /** Yaqinda ham nayza bilan (jab) uradi; OFF bo'lsa qilich (bo'lmasa bolta). */
    public boolean spearMelee = true;
    /** Nishon qalqon ushlasa bolta bilan o'chiradi. */
    public boolean axeShield = true;
    /** Tushayotganda mace (zirhli nishonga Breach, aks holda Density). */
    public boolean mace = true;
    /** Mace uchun kamida shuncha tushish (blok). */
    public int maceMinFall = 2;
    /** Ochlik shundan kam bo'lsa lunge qilmaydi (Lunge ochlik yeydi). */
    public int minFood = 6;

    public boolean players = true;
    public boolean mobs = false;
    public boolean ignoreFriends = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PvpConfig instance = new PvpConfig();

    public static PvpConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autopvp.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PvpConfig c = GSON.fromJson(Files.readString(p), PvpConfig.class);
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

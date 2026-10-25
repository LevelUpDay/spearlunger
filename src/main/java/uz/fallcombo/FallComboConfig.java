package uz.fallcombo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class FallComboConfig {
    public boolean enabled = false;
    /** 1-zarba: spear bilan zaryad (o'ng tugma) - tushish tezligi zararni oshiradi. */
    public boolean useSpear = true;
    /** 2-zarba: qilich (bo'lmasa bolta) bilan darhol. */
    public boolean followHit = true;
    /** Nishon qalqon ushlab tursa avval bolta bilan qalqonini o'chiradi. */
    public boolean breakShield = true;
    /** Oxirida mace'ga o'tib turadi (mace zarbasini boshqa mod/o'zingiz berasiz). */
    public boolean endOnMace = true;
    /** Almashtirish va urish bir tickda (zarba kuchsizlanmaydi). */
    public boolean sameTickSwap = true;
    /** Tushayotganda nishonga qarab turadi. */
    public boolean aim = true;

    /** Shundan baland tushayotganda ishga tushadi (blok). */
    public int minFall = 3;
    /** Nishon gorizontal masofasi (0.1 blok). */
    public int rangeTenths = 40;
    /** Qilich bilan urish masofasi (0.1 blok). */
    public int hitReachTenths = 30;
    /** Spear tegmasa ham shu masofada qilichga o'tadi (0.1 blok). */
    public int fallbackTenths = 22;

    public boolean players = true;
    public boolean mobs = false;
    public boolean ignoreFriends = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FallComboConfig instance = new FallComboConfig();

    public static FallComboConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fallcombo.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                FallComboConfig c = GSON.fromJson(Files.readString(p), FallComboConfig.class);
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

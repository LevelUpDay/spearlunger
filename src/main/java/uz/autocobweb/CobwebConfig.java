package uz.autocobweb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class CobwebConfig {
    public boolean enabled = true;

    /** Joyida turgan / devorga tiqilib qolgan dushman oyog'iga. */
    public boolean trapStill = true;
    /** Shuncha tick deyarli qimirlamasa "turibdi" deb hisoblanadi. */
    public int stillTicks = 4;
    /** Oldingizga yugurib/sakrab kelayotgan dushman yo'liga (tushadigan joyiga). */
    public boolean trapApproach = true;
    /** Shu masofadan yaqin kelsa (blok). */
    public int approachDist = 5;
    /** Dushman qayerda bo'lishini necha tick oldinga hisoblash. */
    public int lookahead = 3;
    /** Oyoq bilan birga bosh balandligiga ham (ikki qavat). */
    public boolean doubleWeb = false;

    /** Ustingizdan (mace bilan) tushayotgan dushman bo'lsa boshingiz ustiga. */
    public boolean antiMace = true;
    /** Baland joydan yiqilayotganda tagingizga (fall damage bo'lmaydi). */
    public boolean selfMlg = true;
    /** Shundan baland yiqilganda MLG (blok). */
    public int minFall = 5;

    /** Qo'yish masofasi (0.1 blok). */
    public int reachTenths = 45;
    /** Qo'yishlar orasidagi kechikish (tick). */
    public int placeDelay = 2;
    /** Qo'yishdan oldin o'sha joyga qarash (anti-cheat burilish sozlamalari bilan). */
    public boolean rotate = true;
    /** Hotbar'da bo'lmasa inventardan olib keladi. */
    public boolean fromInventory = true;
    /** O'yinchilardan tashqari dushman moblarga ham. */
    public boolean targetMobs = false;
    /** /tntfriend ro'yxatidagilarga qo'ymaydi. */
    public boolean ignoreFriends = true;
    public boolean messages = false;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CobwebConfig instance = new CobwebConfig();

    public static CobwebConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autocobweb.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                CobwebConfig c = GSON.fromJson(Files.readString(p), CobwebConfig.class);
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

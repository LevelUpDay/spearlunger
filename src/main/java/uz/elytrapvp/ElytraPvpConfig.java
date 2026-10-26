package uz.elytrapvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ElytraPvpConfig {
    public boolean enabled = false;
    /** Nishon qidirish masofasi (blok). */
    public int range = 48;

    // elytra
    /** Elytra bilan uchib borib, ustidan mace bilan tushish. */
    public boolean elytra = true;
    /** Nishon shundan uzoq bo'lsa uchadi (blok). */
    public int flyDist = 12;
    /** Nishondan shuncha baland uchib boradi, keyin sho'ng'iydi. */
    public int attackHeight = 18;
    /** Yetib borishga shuncha tick qolganda elytra yechiladi (tez uchsa avtomatik oldinroq). */
    public int unequipLead = 3;
    public int minDurability = 10;
    public int fireworkDelay = 20;
    /** Firework bo'lmasa: oyoq ostiga wind charge - tepaga otilib, elytra bilan sirpanib boradi. */
    public boolean windTakeoff = true;
    /** Yerda elytra kiyib qolgan bo'lsa nagrudnikni kiyadi. */
    public boolean armorOnGround = true;

    // yaqin jang
    /** Yaqinda: oyoq ostiga wind charge - sakrab, tushishda mace. */
    public boolean windJump = true;
    public int windJumpDist = 4;
    /** O'rtacha masofada: pearl + wind charge (Pearl Boost) bilan tepaga teleport, keyin mace. */
    public boolean pearlUp = true;
    public int pearlUpDist = 14;
    /** Stun slam: nishon qalqon ushlasa bolta + mace bir tickda. */
    public boolean stunSlam = true;
    /** Siz yurmasangiz nishonga o'zi yuradi (siz yursangiz sizniki ustun). */
    public boolean walk = true;
    /** Urish masofasi (0.1 blok). */
    public int reachTenths = 32;

    // omon qolish
    /** Joni shundan kam bo'lsa ovqat yeydi (1 = yarim yurak). */
    public int eatHealth = 8;
    /** Ovqatdan oldin nishondan uzoqqa pearl otib qochadi. */
    public boolean retreatPearl = true;
    public int retreatDist = 14;

    public boolean ignoreFriends = true;
    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ElytraPvpConfig instance = new ElytraPvpConfig();

    public static ElytraPvpConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("elytrapvp.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ElytraPvpConfig c = GSON.fromJson(Files.readString(p), ElytraPvpConfig.class);
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

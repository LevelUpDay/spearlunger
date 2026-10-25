package uz.autoshield;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ShieldConfig {
    public boolean enabled = true;

    /** O'yinchilar: yaqinda sizga qarab turgan / yugurib kelayotgan bo'lsa (zarbadan oldin). */
    public boolean players = true;
    /** O'yinchi xavf masofasi (0.1 blok). */
    public int playerRange = 38;
    /** Sizga shuncha gradus ichida qarab turgan bo'lsa "urmoqchi" deb hisoblanadi. */
    public int faceAngle = 40;
    /** Kamon/arbalet/trident bilan sizni nishonga olgan o'yinchilar. */
    public boolean playerRanged = true;
    /** Ustingizdan (mace bilan) tushayotgan o'yinchi. */
    public boolean maceFall = true;

    /** Moblar: yaqindagi dushman moblar urmoqchi bo'lsa. */
    public boolean mobs = true;
    /** Mob xavf masofasi (0.1 blok). */
    public int mobRangeTenths = 35;
    /** Skelet, pillager va boshqalar sizga qarab o'q tortsa. */
    public boolean mobRanged = true;
    /** Portlay deb turgan creeper. */
    public boolean creepers = true;

    /** Sizga qarab uchayotgan o'q, trident, fireball. */
    public boolean projectiles = true;
    /** Zarba yesangiz darhol ko'taradi. */
    public boolean onHurt = true;

    /** Xavf o'tgach yana shuncha tick ushlab turadi. */
    public int holdAfterTicks = 2;
    /** Qalqon eng ko'pi bilan shuncha tick ko'tarilib turadi, keyin tushiriladi (doim ko'tarilib qolmasin). */
    public int maxUpTicks = 12;
    /** Tushirilgach shuncha tick qayta ko'tarilmaydi (zarba/o'q bundan mustasno). */
    public int rearmTicks = 8;
    /** Hujumchi orqangizda bo'lsa unga buriladi (qalqon faqat old tomondan to'sadi). */
    public boolean autoFace = true;
    /** Chap tugmani bossangiz urishingiz uchun qalqonni tushiradi. */
    public boolean lowerToAttack = true;
    /** /tntfriend ro'yxatidagilarni xavf deb hisoblamaydi. */
    public boolean ignoreFriends = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ShieldConfig instance = new ShieldConfig();

    public static ShieldConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autoshield.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ShieldConfig c = GSON.fromJson(Files.readString(p), ShieldConfig.class);
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

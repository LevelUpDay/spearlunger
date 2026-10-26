package uz.automace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class MaceConfig {
    public boolean enabled = true;
    /** Mace'ga o'zi o'tsin (OFF - faqat qo'lda mace bo'lsa uradi). */
    public boolean autoSwitch = true;
    /** 0 = avto (zirhliga Breach, qolganiga Density), 1 = Density, 2 = Breach, 3 = Wind Burst. */
    public int prefer = 0;
    /** Urgandan keyin: 0 = mace'da qoladi, 1 = eski slotga qaytadi. */
    public int after = 1;
    /** Eski slotga qaytishdan oldin kutish (tick, 0 = shu tickda). */
    public int returnDelay = 0;
    /** Shu tushishdan (blok) keyin uradi. */
    public int minFall = 2;
    /** Urish masofasi (0.1 blok). */
    public int reachTenths = 33;
    /** 0 = eng kuchli (yerga tegishdan oldingi oxirgi tickda - tushish masofasi maksimal), 1 = darhol. */
    public int timing = 0;
    /** Faqat pritsel nishonda bo'lsa (OFF - eng yaqin nishonga o'zi qaraydi). */
    public boolean requireCrosshair = false;
    /** Zarbalar orasidagi eng kam kechikish (tick). */
    public int delay = 4;
    public boolean players = true;
    public boolean mobs = false;
    public boolean ignoreFriends = true;
    /** Nishon qalqon ushlasa avval bolta bilan o'chiradi (keyingi tickda mace). */
    public boolean axeShield = true;
    public boolean messages = false;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MaceConfig instance = new MaceConfig();

    public static MaceConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("automace.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                MaceConfig c = GSON.fromJson(Files.readString(p), MaceConfig.class);
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

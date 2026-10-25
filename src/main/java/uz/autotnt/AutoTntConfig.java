package uz.autotnt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AutoTntConfig {

    /** Har bir profilning o'z sozlamalari bor (3 ta profil). */
    public static class Profile {
        public int delayTicks = 0;        // amallar orasidagi kechikish (tick), 0 = bir tickda
        public int reachTenths = 45;      // blok qo'yish masofasi (0.1 blok)
        public int minSelfDistTenths = 30;// o'zingizdan eng kami shuncha uzoqda (0.1 blok)
        public int cartCount = 1;         // nechta TNT minecart (zanjir)
        public int ignition = 0;          // 0 Auto, 1 Flint->cart, 2 Fire charge, 3 Activator rail
        public boolean predictTarget = false; // harakatlanayotgan o'yinchini oldindan hisoblash
        public int lookaheadTicks = 10;   // necha tick oldinga hisoblash
        public int friendRadius = 6;      // do'st shu radiusda bo'lsa ishlamaydi (0 = o'chiq)
    }

    public boolean bowMode = false;
    public boolean sounds = true;
    public boolean messages = true;
    public boolean hud = true;
    public boolean autoRefill = true;     // hotbar'da yo'q narsani inventardan olib keladi
    public boolean restoreInventory = true; // ishlatib bo'lgach olib kelinganlarini joyiga qaytaradi
    /** Qo'lda rail qo'ysangiz: o'zi TNT minecart, kerak bo'lsa olov qo'yadi va kamon bilan otadi. */
    public boolean railTrigger = false;
    /** Rail rejimida oxirida kamon bilan cartga otish. */
    public boolean railBow = true;
    /** Rail rejimida kamonni necha tick tortish (4 = eng kami, shunda ham o'q uchadi). */
    public int bowPullTicks = 6;
    public int active = 0;
    public Profile[] profiles = { new Profile(), new Profile(), new Profile() };
    public List<String> friends = new ArrayList<>();

    public Profile p() {
        return profiles[Math.floorMod(active, profiles.length)];
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoTntConfig instance = new AutoTntConfig();

    public static AutoTntConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autotntcart.json");
    }

    public static void load() {
        try {
            Path pth = path();
            if (Files.exists(pth)) {
                AutoTntConfig c = GSON.fromJson(Files.readString(pth), AutoTntConfig.class);
                if (c != null) instance = c;
            }
            if (instance.profiles == null || instance.profiles.length < 3) {
                instance.profiles = new Profile[] { new Profile(), new Profile(), new Profile() };
            }
            for (int i = 0; i < instance.profiles.length; i++) {
                if (instance.profiles[i] == null) instance.profiles[i] = new Profile();
            }
            if (instance.friends == null) instance.friends = new ArrayList<>();
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

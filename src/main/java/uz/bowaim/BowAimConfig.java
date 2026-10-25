package uz.bowaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class BowAimConfig {
    public boolean enabled = false;
    /** Pritseldan shuncha gradus ichidagi nishonlar (eng yaqini tanlanadi). */
    public int fov = 60;
    /** Eng uzoq masofa (blok). */
    public int range = 80;
    /** Nishon harakatini oldindan hisoblash (qayerga yurayotgani, sakrayotgani). */
    public boolean predict = true;
    /** O'zingizning tezligingiz ham hisobga olinadi (o'q o'yinchi tezligini oladi). */
    public boolean selfVelocity = true;
    /** Qayerga: 0 = gavda, 1 = bosh, 2 = oyoq. */
    public int aimPoint = 0;
    /** Devor orqasidagilarni nishonga olmaslik. */
    public boolean visibleOnly = true;
    /** Tortishni boshlaganda tanlangan nishonda qolish. */
    public boolean stickyTarget = true;
    /** Silliq burilish (Anti-cheat sozlamalari) yoki bir zumda. */
    public boolean smooth = true;

    /** O'zi otadi: kamon yetarli tortilib nishonga qaraganda. */
    public boolean autoShoot = false;
    /** Kamonni necha % tortganda otadi. */
    public int shootCharge = 100;
    /** O'qlangan arbaletni ham o'zi otadi. */
    public boolean crossbowShoot = false;
    /** Trident ham. */
    public boolean trident = true;

    public boolean players = true;
    public boolean mobs = false;
    public boolean ignoreFriends = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BowAimConfig instance = new BowAimConfig();

    public static BowAimConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("bowaim.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                BowAimConfig c = GSON.fromJson(Files.readString(p), BowAimConfig.class);
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

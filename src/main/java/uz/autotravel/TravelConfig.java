package uz.autotravel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class TravelConfig {
    public boolean enabled = true;

    // elytra
    /** Elytra (kiyilgan yoki inventarda) va firework bo'lsa uchib boradi. */
    public boolean elytra = true;
    /** Shundan uzoq bo'lsa elytra bilan (blok). */
    public int elytraMinDist = 120;
    /** Yer (va oldindagi tog'/devor)dan shuncha baland uchadi. */
    public int cruiseHeight = 24;
    /** Elytra durability shundan kam qolsa qo'nadi va piyoda davom etadi. */
    public int minDurability = 20;
    /** Gorizontal tezlik shundan past bo'lsa firework (0.1 blok/tick). */
    public int fireworkSpeedTenths = 13;
    /** Firework'lar orasida eng kam (tick). */
    public int fireworkDelay = 25;
    /** Qo'ngach elytra o'rniga nagrudnikni kiyadi. */
    public boolean restoreChest = true;

    // kema
    /** Katta suvda kema (inventarda bo'lsa) bilan suzadi. */
    public boolean boat = true;
    /** Oldinda shuncha blok suv bo'lsa kemaga o'tiradi. */
    public int boatMinWater = 12;
    /** Qirg'oqqa yetgach kemani sindirib oladi. */
    public boolean pickupBoat = true;

    // piyoda
    public boolean breakBlocks = true;
    public boolean placeBlocks = true;
    /** Eng ko'p tushish balandligi (blok). */
    public int maxFall = 3;
    public boolean sprint = true;
    /** Bitta yo'l qidirishda ko'rib chiqiladigan eng ko'p nuqta. */
    public int searchNodes = 12000;

    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TravelConfig instance = new TravelConfig();

    public static TravelConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autotravel.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                TravelConfig c = GSON.fromJson(Files.readString(p), TravelConfig.class);
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

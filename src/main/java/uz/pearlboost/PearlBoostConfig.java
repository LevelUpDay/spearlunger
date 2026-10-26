package uz.pearlboost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PearlBoostConfig {
    public boolean enabled = true;
    /** 0 = pearl eng balandga chiqqanda urish (eng baland teleport), 1 = iloji boricha tez. */
    public int timing = 0;
    /** Hotbar'da bo'lmasa inventardan oladi (keyin joyiga qaytaradi). */
    public boolean fromInventory = true;

    /** Pearl Chase: dushman pearl otsa o'zingiz ham o'sha joy yoniga pearl otasiz. */
    public boolean chase = false;
    /** Dushman pearl'i shu masofadan uzoqqa tushsa quvmaydi (blok). */
    public int chaseRange = 40;
    /** Dushman tushadigan joydan shuncha oldinroqqa (0.1 blok). */
    public int chaseShortTenths = 20;
    public boolean ignoreFriends = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PearlBoostConfig instance = new PearlBoostConfig();

    public static PearlBoostConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pearlboost.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PearlBoostConfig c = GSON.fromJson(Files.readString(p), PearlBoostConfig.class);
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

package uz.pearltracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PearlTrackerConfig {
    public boolean enabled = true;
    /** Pearl uchadigan yo'l (nuqtalar). */
    public boolean trajectory = true;
    /** Tushadigan joy atrofini qizil qilib ajratish radiusi (0.1 blok). */
    public int radiusTenths = 15;
    /** O'zingizning pearl'ingiz ham. */
    public boolean own = false;
    /** Do'stlarnikini ko'rsatmaslik. */
    public boolean ignoreFriends = true;
    /** Chatga "X pearl otdi" xabari. */
    public boolean chat = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PearlTrackerConfig instance = new PearlTrackerConfig();

    public static PearlTrackerConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pearltracker.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PearlTrackerConfig c = GSON.fromJson(Files.readString(p), PearlTrackerConfig.class);
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

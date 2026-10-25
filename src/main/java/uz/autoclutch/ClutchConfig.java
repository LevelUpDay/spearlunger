package uz.autoclutch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ClutchConfig {
    public boolean enabled = true;
    /** Jami yiqilish shundan ko'p bo'lsa (3 blokdan ko'pi zarar beradi). */
    public int minFall = 5;
    /** Qaysi narsalar ishlatiladi (ustuvorlik tartibida). */
    public boolean water = true;
    public boolean powderSnow = true;
    public boolean slime = true;
    public boolean cobweb = true;
    public boolean hay = true;
    public boolean scaffolding = true;
    /** Qo'ngach suvni paqirga qaytarib oladi. */
    public boolean pickupWater = true;
    /** Hotbar'da bo'lmasa inventardan oladi (keyin joyiga qaytaradi). */
    public boolean fromInventory = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClutchConfig instance = new ClutchConfig();

    public static ClutchConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autoclutch.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ClutchConfig c = GSON.fromJson(Files.readString(p), ClutchConfig.class);
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

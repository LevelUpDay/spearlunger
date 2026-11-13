package uz.autobridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class BridgeConfig {
    public boolean enabled = false;
    /**
     * 0 = Tez (silent): kamera qimirlamaydi, qayerga qarasangiz ham ko'prik quradi (eng tezi);
     * 1 = Oddiy: o'zingiz orqaga-pastga qarab turishingiz kerak, mod shunchaki bosadi (kamerani o'zi burmaydi);
     * 2 = Klassik: kamera ko'rinib pastga-orqaga buriladi, qo'yadi (haqiqiy "god bridge" burilishi).
     */
    public int mode = 0;
    /** Hotbar'da qurilish bloki bo'lmasa inventardan oladi (keyin joyiga qaytaradi). */
    public boolean fromInventory = true;
    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BridgeConfig instance = new BridgeConfig();

    public static BridgeConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autobridge.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                BridgeConfig c = GSON.fromJson(Files.readString(p), BridgeConfig.class);
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

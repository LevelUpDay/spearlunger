package uz.autobridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class BridgeConfig {
    /** Yoqilsa: modul o'zi yuradi (W/shift kerak emas), qo'ygan yo'nalishida devorga yetguncha boradi. */
    public boolean enabled = false;
    /** BridgeStyle tartib raqami - qaysi uslub bilan ko'prik qurilishi (ClickGUI'da "Turi"). */
    public int style = BridgeStyle.CLASSIC.ordinal();
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

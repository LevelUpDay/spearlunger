package uz.anchorguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class AnchorGuardConfig {
    public boolean enabled = true;
    /** Siz anchor'ni portlatganda: avval blok qo'yadi, keyin portlatadi. */
    public boolean onDetonate = true;
    /** Yaqinda zaryadlangan anchor paydo bo'lsa (dushmanniki ham) oldindan blok qo'yadi. */
    public boolean proactive = true;
    /** Proaktiv qidiruv radiusi (blok). */
    public int radius = 6;
    /** Ikki qavat (oyoq + bosh balandligi) - zarar ancha kam. */
    public boolean twoHigh = true;
    /** Faqat obsidian / crying obsidian (OFF - istalgan to'liq blok). */
    public boolean obsidianOnly = false;
    /** Hotbar'da bo'lmasa inventardan olib keladi. */
    public boolean fromInventory = true;
    public boolean messages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AnchorGuardConfig instance = new AnchorGuardConfig();

    public static AnchorGuardConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("anchorguard.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                AnchorGuardConfig c = GSON.fromJson(Files.readString(p), AnchorGuardConfig.class);
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

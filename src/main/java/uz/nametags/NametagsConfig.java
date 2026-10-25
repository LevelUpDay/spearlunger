package uz.nametags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class NametagsConfig {
    public boolean enabled = true;
    public boolean name = true;
    /** Jon (health indicator modi bo'lsa o'chiq qoldiring). */
    public boolean health = false;
    public boolean distance = true;
    /** Nechta totem ishlatgani (pop). */
    public boolean totemPops = true;
    /** Chatga "X totem ishlatdi" xabari. */
    public boolean popChat = true;
    public boolean armor = true;
    /** Qo'lidagi narsalar (asosiy va chap qo'l). */
    public boolean hands = true;
    /** Zirhdagi protection darajasi (P4, B4...). */
    public boolean protection = true;
    /** Zirh/qurol durability %. */
    public boolean durability = true;
    /** Bosh ustidan qancha baland (0.1 blok) - health indicator va vanilla ismga xalaqit bermasligi uchun. */
    public int heightTenths = 8;
    /** Hajmi (%). */
    public int scale = 100;
    public int range = 64;
    /** Ko'rinmas o'yinchilar ham. */
    public boolean invisible = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NametagsConfig instance = new NametagsConfig();

    public static NametagsConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("nametags.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                NametagsConfig c = GSON.fromJson(Files.readString(p), NametagsConfig.class);
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

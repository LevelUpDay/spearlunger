package uz.crystaldefense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class CrystalDefenseConfig {
    public boolean enabled = true;
    /** Dushman shu radiusda bo'lsa xavf deb hisoblanadi (blok). */
    public int range = 8;

    /** Qochish: dushman pastda (chuqurda) turib crystal qilsa yoki jon kam bo'lsa pearl otadi. */
    public boolean escapePearl = true;
    /** Shu jon (yarim yurak) yoki kamida qochadi, dushman pastda bo'lmasa ham. */
    public int escapeHealth = 8;
    /** 0 = dushman ko'rmaydigan xavfsiz joyga (bo'lmasa oldinga), 1 = doim qaragan tomonga. */
    public int escapeMode = 0;

    /** Surround: oyoq atrofiga 4 tomondan obsidian. */
    public boolean surround = true;
    /** Atrofdagi crystal qo'yiladigan obsidian/bedrock ustiga oddiy blok qo'yib yopadi. */
    public boolean sealSpots = true;
    /** Bir tickda eng ko'pi bilan nechta blok. */
    public int blocksPerTick = 3;

    public boolean fromInventory = true;
    public boolean ignoreFriends = true;
    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CrystalDefenseConfig instance = new CrystalDefenseConfig();

    public static CrystalDefenseConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystaldefense.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                CrystalDefenseConfig c = GSON.fromJson(Files.readString(p), CrystalDefenseConfig.class);
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

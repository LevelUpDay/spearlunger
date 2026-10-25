package uz.autopotion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class PotionConfig {
    public boolean enabled = true;
    /** Jon shundan kam yoki teng bo'lsa (yarim yurak, 6 = 3 yurak). */
    public int health = 6;
    /** Absorption (sariq yuraklar) ham hisoblansin. */
    public boolean countAbsorption = false;
    /** Healing bo'lmasa Regeneration damlamasini ham otadi. */
    public boolean useRegen = true;
    /** Lingering damlamalar ham ishlatilsin. */
    public boolean useLingering = true;
    /** Otishlar orasidagi kechikish (tick). */
    public int cooldownTicks = 6;
    /** Kamera qimirlamaydi: pastga qarash faqat serverga yuboriladi (eng tez). */
    public boolean silent = true;
    /** Ovqat yeyayotgan / qalqon ushlab turgan bo'lsangiz ham to'xtatib otadi. */
    public boolean interrupt = true;
    /** Otilgan slotga inventardagi keyingi damlamani olib keladi. */
    public boolean refill = true;

    /** Buff damlamalar: effekt yo'q bo'lsa otadi. */
    public boolean buffStrength = false;
    public boolean buffSpeed = false;
    /** Yonayotgan bo'lsangiz Fire Resistance. */
    public boolean buffFireRes = true;

    public boolean messages = true;
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PotionConfig instance = new PotionConfig();

    public static PotionConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autopotion.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PotionConfig c = GSON.fromJson(Files.readString(p), PotionConfig.class);
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

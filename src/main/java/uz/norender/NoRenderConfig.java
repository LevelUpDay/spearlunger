package uz.norender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class NoRenderConfig {
    public boolean enabled = true;
    /** Ekrandagi olov: 0 = vanilla, 1 = past (kichik), 2 = umuman yo'q. */
    public int fire = 1;
    /** Past olov qancha pastga tushiriladi (%). */
    public int fireLower = 35;
    /** Suv ichida tuman yo'q - hamma narsa tiniq ko'rinadi. */
    public boolean waterFog = true;
    /** Suv ostidagi ko'k ekran qatlami. */
    public boolean underwaterOverlay = true;
    /** Lava ichidagi tuman. */
    public boolean lavaFog = true;
    /** Ko'rlik (blindness) va darkness tumani. */
    public boolean effectFog = true;
    /** Powder snow tumani. */
    public boolean powderSnowFog = true;
    /** Blok ichida qolgandagi qora ekran. */
    public boolean inWall = true;
    /** Zarba yeganda kamera silkinishi. */
    public boolean hurtCam = true;
    /** Totem animatsiyasi (ekranni yopadigan). */
    public boolean totemAnimation = true;
    /** Portal va nausea (bosh aylanishi) effekti. */
    public boolean portalNausea = true;
    /** Qovoq / powder snow ekran qatlami. */
    public boolean pumpkin = true;
    /** Ekran chetlari qorayishi (vignette). */
    public boolean vignette = false;
    /** Yomg'ir va qor. */
    public boolean weather = false;
    /** Portlash zarrachalari (crystal/anchor/TNT) - ekran tozaligi va FPS. */
    public boolean explosions = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NoRenderConfig instance = new NoRenderConfig();

    public static NoRenderConfig get() { return instance; }

    /** Modul yoqilgan va shu bandi ham yoqilgan. */
    public static boolean on(boolean option) {
        return instance.enabled && option;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("norender.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                NoRenderConfig c = GSON.fromJson(Files.readString(p), NoRenderConfig.class);
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

package uz.autoelytra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ElytraConfig {
    /** Firework bosilganda elytra'ni o'zi kiyadi. */
    public boolean enabled = true;
    /** Hotbar'da bo'lmasa inventardan ham qidiradi. */
    public boolean fromInventory = true;
    /** Yerda bo'lsangiz o'zi sakraydi. */
    public boolean autoJump = true;
    /** Havoda elytra'ni o'zi ochadi (ikki marta probel bosish shart emas). */
    public boolean autoDeploy = true;
    /** Elytra ochilgach firework'ni o'zi ishlatadi. */
    public boolean autoFirework = true;
    /** Qo'nganda avvalgi nagrudnikni qaytarib kiyadi. */
    public boolean restoreChest = true;
    /** Qo'ngandan keyin shuncha tick kutib nagrudnik kiyadi. */
    public int landDelayTicks = 10;
    /** Uchayotganda elytra sinay desa boshqa elytra'ga almashtiradi. */
    public boolean autoReplace = true;
    /** Durability shundan kam elytra'ni ishlatmaydi. */
    public int minDurability = 5;
    /** Durability shundan kam qolsa ogohlantiradi (0 = o'chiq). */
    public int warnDurability = 30;
    /** Amallar orasidagi kechikish (tick). */
    public int delayTicks = 0;
    /** Uchib dushman ustiga kelganda elytra'ni yechadi (nagrudnik kiyadi) - mace bilan tushish uchun. */
    public boolean unequipNearEnemy = true;
    /** Dushmangacha shu masofada (blok). */
    public int unequipDist = 8;
    /** Dushmandan kamida shuncha baland bo'lsangiz (blok). */
    public int unequipMinAbove = 2;
    /** Yechgach mace'ni qo'lga oladi. */
    public boolean unequipToMace = true;
    public boolean messages = true;
    /** Uchayotganda ekranda elytra durability'si. */
    public boolean hud = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ElytraConfig instance = new ElytraConfig();

    public static ElytraConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autoelytra.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ElytraConfig c = GSON.fromJson(Files.readString(p), ElytraConfig.class);
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

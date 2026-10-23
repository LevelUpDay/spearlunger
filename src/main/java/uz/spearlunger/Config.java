package uz.spearlunger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    /** Ikkala modga ham tegishli anti-cheat sozlamalari. */
    public static class AntiCheat {
        /** Kamerani bir zumda emas, asta-sekin burish. */
        public boolean smoothRotation = true;
        /** Bir tickda eng ko'pi bilan shuncha gradus burilish. */
        public int maxTurnPerTick = 40;
        /** Burilish tezligiga tasodifiy ± qo'shimcha (gradus). */
        public int turnJitter = 6;
        /** Burilishni sichqoncha sezgirligi qadamiga (GCD) moslash. */
        public boolean gcdFix = true;
        /** Nishonga "qarab turibdi" deb hisoblash chegarasi (gradus). */
        public int aimTolerance = 8;
        /** X: mace faqat haqiqatan nishonga qarab turganda uradi. */
        public boolean requireLookAt = true;
        /** X: mace'ga almashtirish va urish bir tickda (OFF bo'lsa mace zarbi juda kuchsiz chiqadi). */
        public boolean sameTickSwap = true;
        /** Har bir lunge oldidan 0..N tick tasodifiy kutish. */
        public int randomDelayMax = 0;
        /** TNT: har bir blok/minecart oldidan unga qarab burilish. */
        public boolean rotateToBlocks = true;
        /** TNT: tugagach kamerani avvalgi holatiga qaytarish. */
        public boolean restoreView = true;
    }

    /**
     * Cooldown to'lgandan keyin qo'shimcha kutish (tick, 20 tick = 1 soniya).
     * Nayza cooldown'i baribir majburiy (netherite ~23 tick, diamond ~21 tick), bu undan tashqari.
     */
    public int extraDelayTicks = 0;
    /** Ochlik shundan past bo'lsa lunge qilmaydi (0 = o'chiq). Lunge III har safar ~3 ochlik yeydi. */
    public int minFood = 6;
    /** R: ochlik kam bo'lsa hotbar'dagi ovqatni avtomatik yeydi. */
    public boolean autoEat = true;
    /** Avto-ovqat oltin olmalarni ham yesinmi. */
    public boolean eatGoldenApples = false;
    /** Nayza durability'si shundan kam qolsa ishlatmaydi (0 = o'chiq). */
    public int minDurability = 10;
    /** R: false = bosib turish, true = bir bosib yoqish/o'chirish. */
    public boolean holdToggle = false;
    /** Ekranda lunge holati (cooldown, ochlik). */
    public boolean hud = true;

    /** X uchun qidiruv: gorizontal masofa (blok). */
    public int rangeHorizontal = 50;
    /** X uchun qidiruv: vertikal masofa (blok). */
    public int rangeVertical = 100;
    /** X: 0 = eng yaqin, 1 = qarash yo'nalishiga eng yaqin. */
    public int targetSelect = 0;
    /** X: o'yinchilardan tashqari dushman moblarni ham nishonga olish. */
    public boolean targetMobs = false;
    /** X: uzoqda pitch = 0 qilib qarash (Lunge impulsi faqat gorizontal, pastga qarasa kuchsiz). */
    public boolean flattenPitch = true;
    /** X: faqat havoda ishga tushadi (mace uchun tushish masofasi kerak). */
    public boolean targetRequireAir = true;
    /** X: yerda bo'lsangiz wind charge bilan sakrab, keyin lunge qiladi. */
    public boolean windChargeJump = true;
    /** X: nishongacha shu masofada mace bilan uradi (blok). */
    public double maceReach = 3.0;
    /** X: eng ko'pi bilan shuncha tick ishlaydi. */
    public int targetTimeoutTicks = 200;

    public AntiCheat antiCheat = new AntiCheat();

    public AntiCheat ac() {
        if (antiCheat == null) antiCheat = new AntiCheat();
        return antiCheat;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spearlunger.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            }
            instance.ac();
            save();   // yangi maydonlarni faylga yozib qo'yamiz
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

package uz.spearlunger.gui;

import uz.anchorguard.AnchorGuardClient;
import uz.anchorguard.AnchorGuardConfig;
import uz.autoclutch.AutoClutchClient;
import uz.autoclutch.ClutchConfig;
import uz.autocobweb.AutoCobwebClient;
import uz.autocobweb.CobwebConfig;
import uz.autoelytra.AutoElytraClient;
import uz.automace.AutoMaceClient;
import uz.automace.MaceConfig;
import uz.autotravel.AutoTravelClient;
import uz.autotravel.TravelConfig;
import uz.autopotion.AutoPotionClient;
import uz.autopvp.AutoPvpClient;
import uz.autopvp.PvpConfig;
import uz.autopotion.PotionConfig;
import uz.autoshield.AutoShieldClient;
import uz.autoshield.ShieldConfig;
import uz.autoelytra.ElytraConfig;
import uz.autotnt.AutoTntClient;
import uz.autotnt.AutoTntConfig;
import uz.autototem.AutoTotemClient;
import uz.autototem.TotemConfig;
import uz.bowaim.BowAimClient;
import uz.bowaim.BowAimConfig;
import uz.crystaldefense.CrystalDefenseClient;
import uz.crystaldefense.CrystalDefenseConfig;
import uz.crystalhud.CrystalHudClient;
import uz.crystalhud.CrystalHudConfig;
import uz.elytrapvp.ElytraPvpClient;
import uz.elytrapvp.ElytraPvpConfig;
import uz.fallcombo.FallComboClient;
import uz.fallcombo.FallComboConfig;
import uz.fastplace.FastPlaceClient;
import uz.fastplace.FastPlaceConfig;
import uz.cheststealer.ChestStealerClient;
import uz.cheststealer.StealerConfig;
import uz.macecombo.MaceComboClient;
import uz.macecombo.MaceComboConfig;
import uz.nametags.NametagsClient;
import uz.nametags.NametagsConfig;
import uz.norender.NoRenderClient;
import uz.norender.NoRenderConfig;
import uz.pearlboost.PearlBoostClient;
import uz.pearlboost.PearlBoostConfig;
import uz.pearlclutch.PearlClutchClient;
import uz.pearltracker.PearlTrackerClient;
import uz.pearltracker.PearlTrackerConfig;
import uz.pearlclutch.PearlConfig;
import uz.spearlunger.Config;
import uz.spearlunger.SpearLungerClient;

import java.util.ArrayList;
import java.util.List;

import static uz.spearlunger.gui.Setting.action;
import static uz.spearlunger.gui.Setting.bool;
import static uz.spearlunger.gui.Setting.choice;
import static uz.spearlunger.gui.Setting.group;
import static uz.spearlunger.gui.Setting.items;
import static uz.spearlunger.gui.Setting.key;
import static uz.spearlunger.gui.Setting.num;

/** Barcha modullar va ularning sozlamalari. */
public final class Modules {
    private static List<Module> all;

    private Modules() {}

    public static List<Module> all() {
        if (all == null) all = build();
        return all;
    }

    public static Module byName(String name) {
        for (Module m : all()) if (m.name.equals(name)) return m;
        return null;
    }

    public static void saveAll() {
        Config.save();
        AutoTntConfig.save();
        ElytraConfig.save();
        TotemConfig.save();
        CobwebConfig.save();
        ShieldConfig.save();
        PotionConfig.save();
        BowAimConfig.save();
        FastPlaceConfig.save();
        AnchorGuardConfig.save();
        StealerConfig.save();
        NametagsConfig.save();
        FallComboConfig.save();
        PearlConfig.save();
        ClutchConfig.save();
        PvpConfig.save();
        MaceComboConfig.save();
        PearlBoostConfig.save();
        CrystalDefenseConfig.save();
        MaceConfig.save();
        TravelConfig.save();
        ElytraPvpConfig.save();
        CrystalHudConfig.save();
        NoRenderConfig.save();
        PearlTrackerConfig.save();
    }

    private static List<Module> build() {
        List<Module> l = new ArrayList<>();

        l.add(new Module("Spear Lunger", "Lunge nayza bilan avto jab (R) va nishonga uchib mace bilan urish (X)",
                Module.Category.COMBAT, () -> Config.get().enabled, v -> Config.get().enabled = v, () -> {
            Config c = Config.get();
            return List.of(
                    group("Tugmalar"),
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", SpearLungerClient.keyToggle),
                    key("Auto Lunge", "Bosib turing (yoki bir bosib yoqing) - avto jab", SpearLungerClient.keyHold),
                    key("Nishonga lunge", "Eng yaqin o'yinchiga uchib mace bilan uradi", SpearLungerClient.keyTarget),
                    choice("R rejimi", "Avto: bir bossangiz yoqiladi (yana bossangiz o'chadi), bosib tursangiz qo'yib yuborguncha",
                            new String[] {"Avto", "Bosib turish", "Bosib yoqish"},
                            () -> Math.floorMod(c.rMode, 3), v -> c.rMode = v),
                    group("Lunge"),
                    num("Qo'shimcha kechikish", "Cooldown to'lgach yana kutish", 0, 40,
                            () -> c.extraDelayTicks, v -> c.extraDelayTicks = v, v -> v + " tick"),
                    num("Min ochlik", "Ochlik shundan kam bo'lsa lunge qilmaydi", 0, 20,
                            () -> c.minFood, v -> c.minFood = v, v -> v == 0 ? "o'chiq" : String.valueOf(v)),
                    num("Min durability", "Nayza durability'si shundan kam bo'lsa ishlatmaydi", 0, 100,
                            () -> c.minDurability, v -> c.minDurability = v, v -> v == 0 ? "o'chiq" : String.valueOf(v)),
                    bool("Avto-ovqat", "Ochlik kam bo'lsa hotbar'dagi ovqatni yeydi", () -> c.autoEat, v -> c.autoEat = v),
                    bool("Oltin olma yeyish", "Avto-ovqat oltin olmalarni ham yesin", () -> c.eatGoldenApples,
                            v -> c.eatGoldenApples = v),
                    bool("HUD", "Ekranda lunge holati", () -> c.hud, v -> c.hud = v),
                    group("Nishon (X)"),
                    choice("Nishon tanlash", "Qaysi o'yinchini tanlash", new String[] {"Eng yaqin", "Qarash bo'yicha"},
                            () -> Math.floorMod(c.targetSelect, 2), v -> c.targetSelect = v),
                    bool("Moblar ham", "Dushman moblarni ham nishonga olish", () -> c.targetMobs, v -> c.targetMobs = v),
                    bool("Faqat havoda", "Mace uchun tushish masofasi kerak", () -> c.targetRequireAir,
                            v -> c.targetRequireAir = v),
                    bool("Wind charge", "Yerda bo'lsangiz wind charge bilan sakraydi", () -> c.windChargeJump,
                            v -> c.windChargeJump = v),
                    bool("Gorizontal qarash", "Lunge impulsi faqat gorizontal - pitch 0 qiladi", () -> c.flattenPitch,
                            v -> c.flattenPitch = v),
                    num("Gorizontal masofa", "Nishon qidirish (blok)", 5, 200,
                            () -> c.rangeHorizontal, v -> c.rangeHorizontal = v),
                    num("Vertikal masofa", "Nishon qidirish (blok)", 5, 400, () -> c.rangeVertical, v -> c.rangeVertical = v),
                    num("Mace masofasi", "Shu masofada mace bilan uradi", 20, 60, () -> (int) Math.round(c.maceReach * 10),
                            v -> c.maceReach = v / 10.0, v -> (v / 10.0) + " blok"),
                    num("Vaqt limiti", "X eng ko'pi bilan shuncha ishlaydi", 40, 600,
                            () -> c.targetTimeoutTicks, v -> c.targetTimeoutTicks = v, v -> v + " tick"));
        }));

        l.add(new Module("Auto PvP", "K bilan yoqing: eng yaqin dushman bilan o'zi jang qiladi (lunge, qilich, mace, wind charge, pearl)",
                Module.Category.COMBAT, () -> PvpConfig.get().enabled, v -> PvpConfig.get().enabled = v, () -> {
            PvpConfig c = PvpConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (standart K)",
                            AutoPvpClient.keyToggle),
                    num("Nishon masofasi", "Eng yaqin dushmanni shu masofada qidiradi", 8, 64, () -> c.range,
                            v -> c.range = v, v -> v + " blok"),
                    group("Yaqinlashish"),
                    bool("Lunge", "Uzoqda Lunge nayza bilan uchib boradi (cooldown to'lishi bilan)", () -> c.lunge,
                            v -> c.lunge = v),
                    bool("Gorizontal lunge", "Uzoqda pitch 0 - impuls to'liq oldinga", () -> c.flatten,
                            v -> c.flatten = v),
                    bool("Wind charge sakrash", "Yerda bo'lsangiz wind charge bilan sakraydi", () -> c.windJump,
                            v -> c.windJump = v),
                    num("Wind charge masofasi", "Shundan uzoqda sakraydi", 2, 20, () -> c.windMinDist,
                            v -> c.windMinDist = v, v -> v + " blok"),
                    bool("Ender pearl", "Juda uzoq bo'lsa nishon oldiga pearl (harakatni hisoblab)", () -> c.pearl,
                            v -> c.pearl = v),
                    num("Pearl masofasi", "Shundan uzoqda pearl", 8, 60, () -> c.pearlDist, v -> c.pearlDist = v,
                            v -> v + " blok"),
                    num("Min ochlik", "Lunge ochlik yeydi: shundan kam bo'lsa lunge qilmaydi", 0, 20, () -> c.minFood,
                            v -> c.minFood = v),
                    group("Urish"),
                    bool("Yaqinda urish", "Yaqin kelganda uradi", () -> c.melee, v -> c.melee = v),
                    bool("Nayza bilan urish", "Yaqinda ham nayza jab'i (OFF - qilich, bo'lmasa bolta)",
                            () -> c.spearMelee, v -> c.spearMelee = v),
                    num("Urish masofasi", "Qilich bilan urish masofasi", 20, 45, () -> c.meleeTenths,
                            v -> c.meleeTenths = v, v -> (v / 10.0) + " blok"),
                    bool("Qalqonni sindirish", "Nishon qalqon ushlasa bolta bilan o'chiradi", () -> c.axeShield,
                            v -> c.axeShield = v),
                    bool("Mace", "Tushayotganda mace: zirhli nishonga Breach, aks holda Density", () -> c.mace,
                            v -> c.mace = v),
                    num("Mace balandligi", "Kamida shuncha tushganda", 1, 20, () -> c.maceMinFall,
                            v -> c.maceMinFall = v, v -> v + " blok"),
                    bool("Mace combo", "Yaqinda yerda bo'lsangiz wind charge bilan sakrab, tushishda mace bilan uradi",
                            () -> c.maceCombo, v -> c.maceCombo = v),
                    num("Combo masofasi", "Shu masofada sakraydi", 2, 10, () -> c.maceComboDist,
                            v -> c.maceComboDist = v, v -> v + " blok"),
                    group("Nishon"),
                    bool("O'yinchilar", "O'yinchilarga", () -> c.players, v -> c.players = v),
                    bool("Moblar", "Dushman moblarga ham", () -> c.mobs, v -> c.mobs = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("HUD", "Ekranda nishon va holat", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Elytra PvP", "Elytra kiyib firework/wind charge bilan uchib boradi, yechib mace bilan uradi, takrorlaydi",
                Module.Category.COMBAT, () -> ElytraPvpConfig.get().enabled, v -> ElytraPvpConfig.get().enabled = v, () -> {
            ElytraPvpConfig c = ElytraPvpConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", ElytraPvpClient.keyToggle),
                    num("Nishon masofasi", "Shu masofadagi eng yaqin o'yinchi", 8, 128, () -> c.range, v -> c.range = v,
                            v -> v + " blok"),
                    group("Elytra hujum"),
                    bool("Elytra bilan", "Uzoqda elytra kiyib uchib boradi, ustidan mace bilan tushadi", () -> c.elytra,
                            v -> c.elytra = v),
                    num("Uchish masofasi", "Nishon shundan uzoq bo'lsa uchadi", 6, 64, () -> c.flyDist, v -> c.flyDist = v,
                            v -> v + " blok"),
                    num("Hujum balandligi", "Nishondan shuncha baland uchib, keyin sho'ng'iydi", 6, 50, () -> c.attackHeight,
                            v -> c.attackHeight = v, v -> v + " blok"),
                    num("Yechish vaqti", "Yetib borishga shuncha tick qolganda yechadi (tez bo'lsa oldinroq)", 1, 10,
                            () -> c.unequipLead, v -> c.unequipLead = v, v -> v + " tick"),
                    num("Firework oralig'i", "Firework'lar orasida eng kami", 5, 60, () -> c.fireworkDelay,
                            v -> c.fireworkDelay = v, v -> v + " tick"),
                    num("Min durability", "Elytra shundan kam bo'lsa ishlatilmaydi", 1, 100, () -> c.minDurability,
                            v -> c.minDurability = v, v -> v + ""),
                    bool("Wind charge bilan uchish", "Firework bo'lmasa oyoq ostiga wind charge - tepaga otilib uchadi",
                            () -> c.windTakeoff, v -> c.windTakeoff = v),
                    bool("Yerda nagrudnik", "Yerda elytra kiyib qolsa nagrudnikni kiyadi", () -> c.armorOnGround,
                            v -> c.armorOnGround = v),
                    group("Yaqin jang"),
                    bool("Wind sakrash", "Yaqinda oyoq ostiga wind charge - sakrab mace bilan", () -> c.windJump,
                            v -> c.windJump = v),
                    num("Wind masofasi", "Shu masofada sakraydi", 2, 8, () -> c.windJumpDist, v -> c.windJumpDist = v,
                            v -> v + " blok"),
                    bool("Pearl bilan tepaga", "Pearl + wind charge (Pearl Boost) - tepaga teleport, keyin mace",
                            () -> c.pearlUp, v -> c.pearlUp = v),
                    num("Pearl masofasi", "Shundan yaqin bo'lsa pearl bilan", 6, 30, () -> c.pearlUpDist,
                            v -> c.pearlUpDist = v, v -> v + " blok"),
                    bool("Stun slam", "Qalqon ushlasa bolta + mace bir tickda", () -> c.stunSlam, v -> c.stunSlam = v),
                    bool("O'zi yurish", "Siz yurmasangiz nishonga yuradi (siz yursangiz sizniki ustun)", () -> c.walk,
                            v -> c.walk = v),
                    num("Masofa", "Urish masofasi", 20, 45, () -> c.reachTenths, v -> c.reachTenths = v,
                            v -> (v / 10.0) + " blok"),
                    group("Omon qolish"),
                    num("Ovqat joni", "Jon shundan kam bo'lsa ovqat (avval oltin olma) yeydi", 2, 18, () -> c.eatHealth,
                            v -> c.eatHealth = v, v -> (v / 2.0) + " yurak"),
                    bool("Qochish pearl'i", "Ovqatdan oldin nishondan uzoqqa pearl otadi", () -> c.retreatPearl,
                            v -> c.retreatPearl = v),
                    num("Qochish masofasi", "Pearl shuncha uzoqqa", 6, 30, () -> c.retreatDist, v -> c.retreatDist = v,
                            v -> v + " blok"),
                    group("Boshqa"),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Nishon, holat va oxirgi zarba", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Mace", "Tushayotganda nishonga yetishi bilan mace'ga o'tib uradi (bir tickda, eng kuchli paytda)",
                Module.Category.COMBAT, () -> MaceConfig.get().enabled, v -> MaceConfig.get().enabled = v, () -> {
            MaceConfig c = MaceConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", AutoMaceClient.keyToggle),
                    group("Zarba"),
                    bool("Avto almashtirish", "Mace'ga o'zi o'tadi (OFF - faqat qo'lda mace bo'lsa)", () -> c.autoSwitch,
                            v -> c.autoSwitch = v),
                    choice("Qaysi mace", "Avto: zirhli nishonga Breach, qolganiga Density",
                            new String[] {"Avto", "Density", "Breach", "Wind Burst"}, () -> Math.floorMod(c.prefer, 4),
                            v -> c.prefer = v),
                    choice("Urgandan keyin", "Mace'da qoladimi yoki eski slotga qaytadimi",
                            new String[] {"Mace'da qolish", "Eskisiga qaytish"}, () -> Math.floorMod(c.after, 2),
                            v -> c.after = v),
                    num("Qaytish kechikishi", "Eski slotga qaytishdan oldin", 0, 20, () -> c.returnDelay,
                            v -> c.returnDelay = v, v -> v == 0 ? "darhol" : v + " tick"),
                    choice("Qachon", "Eng kuchli: yerga tegishdan oldingi oxirgi tickda (maksimal tushish). Darhol: yetishi bilan",
                            new String[] {"Eng kuchli", "Darhol"}, () -> Math.floorMod(c.timing, 2), v -> c.timing = v),
                    num("Min tushish", "Shundan baland tushganda uradi", 1, 30, () -> c.minFall, v -> c.minFall = v,
                            v -> v + " blok"),
                    num("Masofa", "Urish masofasi", 20, 45, () -> c.reachTenths, v -> c.reachTenths = v,
                            v -> (v / 10.0) + " blok"),
                    num("Kechikish", "Zarbalar orasida eng kami", 1, 20, () -> c.delay, v -> c.delay = v,
                            v -> v + " tick"),
                    bool("Qalqonni sindirish", "Nishon qalqon ushlasa avval bolta bilan o'chiradi", () -> c.axeShield,
                            v -> c.axeShield = v),
                    group("Nishon"),
                    bool("Faqat pritselda", "Faqat qarab turgan nishonga (OFF - eng yaqiniga o'zi qaraydi)",
                            () -> c.requireCrosshair, v -> c.requireCrosshair = v),
                    bool("O'yinchilar", "O'yinchilarga", () -> c.players, v -> c.players = v),
                    bool("Moblar", "Dushman moblarga ham", () -> c.mobs, v -> c.mobs = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    group("Boshqa"),
                    bool("Xabarlar", "Har zarbada action bar xabari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Tushish balandligi va oxirgi zarba", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Mace Combo", "J: pearl (uzoq bo'lsa) -> wind charge -> mace; harakatingiz va nishonniki hisoblanadi",
                Module.Category.COMBAT, () -> MaceComboConfig.get().enabled, v -> MaceComboConfig.get().enabled = v, () -> {
            MaceComboConfig c = MaceComboConfig.get();
            return List.of(
                    key("Kombinatsiya tugmasi", "Bir bosish - bitta kombinatsiya, yana bossangiz bekor (standart J)",
                            MaceComboClient.keyCombo),
                    bool("Ender pearl", "Nishon uzoq bo'lsa avval pearl bilan yaqinlashadi", () -> c.pearl,
                            v -> c.pearl = v),
                    num("Pearl masofasi", "Shundan uzoq bo'lsa pearl", 6, 40, () -> c.pearlDist, v -> c.pearlDist = v,
                            v -> v + " blok"),
                    num("Pearl oldinroq", "Pearl nishondan shuncha oldinga tushadi", 0, 50, () -> c.pearlShortTenths,
                            v -> c.pearlShortTenths = v, v -> (v / 10.0) + " blok"),
                    num("Wind charge masofasi", "Shu masofada tepaga sakraydi", 2, 12, () -> c.windDist,
                            v -> c.windDist = v, v -> v + " blok"),
                    num("Mace masofasi", "Shu masofada mace bilan uradi", 20, 45, () -> c.reachTenths,
                            v -> c.reachTenths = v, v -> (v / 10.0) + " blok"),
                    num("Nishon masofasi", "Eng yaqin dushmanni shu masofada qidiradi", 8, 96, () -> c.range,
                            v -> c.range = v, v -> v + " blok"),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Pearl Boost", "H: pearl + uni urib yuboradigan wind charge - havoga teleport, keyin mace; Pearl Chase",
                Module.Category.COMBAT, () -> PearlBoostConfig.get().enabled, v -> PearlBoostConfig.get().enabled = v, () -> {
            PearlBoostConfig c = PearlBoostConfig.get();
            return List.of(
                    key("Pearl Boost tugmasi", "Qaragan tomonga pearl, keyin unga tegadigan wind charge (standart H)",
                            PearlBoostClient.keyBoost),
                    choice("Wind charge qachon", "Pearl eng balandga chiqqanda (eng baland teleport) yoki iloji boricha tez",
                            new String[] {"Eng balandda", "Tez"}, () -> Math.floorMod(c.timing, 2), v -> c.timing = v),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan (keyin joyiga qaytaradi)",
                            () -> c.fromInventory, v -> c.fromInventory = v),
                    group("Pearl Chase"),
                    key("Pearl Chase tugmasi", "Pearl Chase'ni yoqib-o'chiradi", PearlBoostClient.keyChase),
                    bool("Pearl Chase", "Dushman pearl otsa o'zingiz ham o'sha joy yoniga pearl otasiz", () -> c.chase,
                            v -> c.chase = v),
                    num("Chase masofasi", "Dushman pearl'i shundan uzoqqa tushsa quvmaydi", 8, 80, () -> c.chaseRange,
                            v -> c.chaseRange = v, v -> v + " blok"),
                    num("Oldinroq", "Dushman tushadigan joydan shuncha oldinroqqa", 0, 50, () -> c.chaseShortTenths,
                            v -> c.chaseShortTenths = v, v -> (v / 10.0) + " blok"),
                    bool("Do'stlarnikini emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Crystal Defense", "Crystal PvP: chuqurdagi dushmandan pearl bilan qochish, surround, crystal joylarini yopish",
                Module.Category.COMBAT, () -> CrystalDefenseConfig.get().enabled, v -> CrystalDefenseConfig.get().enabled = v, () -> {
            CrystalDefenseConfig c = CrystalDefenseConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", CrystalDefenseClient.keyToggle),
                    num("Dushman radiusi", "Dushman shu masofada bo'lsa xavf", 3, 16, () -> c.range, v -> c.range = v,
                            v -> v + " blok"),
                    group("Qochish (pearl)"),
                    bool("Pearl bilan qochish", "Dushman pastda/chuqurda turib crystal qilsa yoki jon kam bo'lsa",
                            () -> c.escapePearl, v -> c.escapePearl = v),
                    num("Jon", "Shu jon yoki kamida dushman pastda bo'lmasa ham qochadi", 0, 20, () -> c.escapeHealth,
                            v -> c.escapeHealth = v, v -> (v / 2.0) + " yurak"),
                    choice("Qayerga", "Dushman ko'rmaydigan xavfsiz joyga (topilmasa oldinga) yoki doim qaragan tomonga",
                            new String[] {"Xavfsiz joyga", "Oldinga"}, () -> Math.floorMod(c.escapeMode, 2),
                            v -> c.escapeMode = v),
                    group("Bloklar"),
                    bool("Surround", "Oyog'ingiz atrofiga 4 tomondan obsidian", () -> c.surround, v -> c.surround = v),
                    bool("Crystal joylarini yopish", "Yoningizdagi obsidian/bedrock ustiga oddiy blok - crystal qo'yib bo'lmaydi",
                            () -> c.sealSpots, v -> c.sealSpots = v),
                    num("Blok/tick", "Bir tickda eng ko'pi bilan", 1, 6, () -> c.blocksPerTick, v -> c.blocksPerTick = v),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan (keyin joyiga qaytaradi)",
                            () -> c.fromInventory, v -> c.fromInventory = v),
                    group("Boshqa"),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar xavf emas", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Ekranda crystal xavfi ogohlantirishi", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Totem", "Chap qo'lda totem bo'lmasa inventardan qo'yadi",
                Module.Category.COMBAT, () -> TotemConfig.get().enabled, v -> TotemConfig.get().enabled = v, () -> {
            TotemConfig c = TotemConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoTotemClient.keyToggle),
                    choice("Turi", "Inventar: E + sichqoncha + F. Tezkor: inventar ochilmaydi. Hotbar: F",
                            new String[] {"Inventar (E + F)", "Tezkor", "Hotbar (F)"},
                            () -> Math.floorMod(c.mode, 3), v -> c.mode = v),
                    num("Jon", "Faqat jon shundan kam bo'lsa ishlaydi", 0, 20, () -> c.health, v -> c.health = v,
                            v -> v == 0 ? "har doim" : (v / 2.0) + " yurak"),
                    bool("Hotbar'dan ham", "Totemni hotbar'dan ham olish", () -> c.fromHotbar, v -> c.fromHotbar = v),
                    group("Vaqt"),
                    num("Kechikish min", "Totem kerak bo'lgach tasodifiy kutish", 0, 10,
                            () -> c.delayMin, v -> c.delayMin = v, v -> v + " tick"),
                    num("Kechikish max", "Totem kerak bo'lgach tasodifiy kutish", 0, 10,
                            () -> c.delayMax, v -> c.delayMax = v, v -> v + " tick"),
                    num("Sichqoncha", "Sichqonchani totemga olib borish vaqti", 0, 10,
                            () -> c.moveTicks, v -> c.moveTicks = v, v -> v == 0 ? "bir zumda" : v + " tick"),
                    num("Yopishdan oldin", "F bosilgach inventarni yopishdan oldin kutish", 0, 10,
                            () -> c.closeDelay, v -> c.closeDelay = v, v -> v + " tick"),
                    group("Boshqa"),
                    bool("Pop xabari", "Totem ishlatilganda nechta qolganini yozadi", () -> c.popMessage,
                            v -> c.popMessage = v),
                    bool("Xabarlar", "Chatdagi xabarlar", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Ekranda totemlar soni", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Cobweb", "Dushman oyog'iga, yo'liga va mace'ga qarshi cobweb; yiqilganda MLG",
                Module.Category.COMBAT, () -> CobwebConfig.get().enabled, v -> CobwebConfig.get().enabled = v, () -> {
            CobwebConfig c = CobwebConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoCobwebClient.keyToggle),
                    group("Dushmanga"),
                    bool("Turgan dushman", "Joyida turgan / devorga tiqilgan dushman oyog'iga", () -> c.trapStill,
                            v -> c.trapStill = v),
                    num("Turish vaqti", "Shuncha tick qimirlamasa \"turibdi\"", 1, 20, () -> c.stillTicks,
                            v -> c.stillTicks = v, v -> v + " tick"),
                    bool("Kelayotgan dushman", "Yugurib/sakrab kelayotgan dushman tushadigan joyiga", () -> c.trapApproach,
                            v -> c.trapApproach = v),
                    num("Kelish masofasi", "Shundan yaqin kelsa", 2, 8, () -> c.approachDist, v -> c.approachDist = v,
                            v -> v + " blok"),
                    num("Oldindan hisoblash", "Dushman necha tickdan keyin qayerda bo'lishi", 0, 10, () -> c.lookahead,
                            v -> c.lookahead = v, v -> v + " tick"),
                    bool("Ikki qavat", "Oyog'i bilan birga bosh balandligiga ham", () -> c.doubleWeb,
                            v -> c.doubleWeb = v),
                    bool("Moblar ham", "Dushman moblarga ham", () -> c.targetMobs, v -> c.targetMobs = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilarga qo'ymaydi", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    group("Himoya"),
                    bool("Anti-mace", "Ustingizdan tushayotgan dushman bo'lsa boshingiz ustiga", () -> c.antiMace,
                            v -> c.antiMace = v),
                    bool("Anti-web", "Ipga ilinsangiz: suv paqiri bilan ipni yo'q qiladi, suvni qaytarib oladi",
                            () -> c.antiWeb, v -> c.antiWeb = v),
                    bool("Anti-web: kesish", "Suv bo'lmasa (yoki Nether'da) qaychi/qilich bilan kesadi", () -> c.antiWebCut,
                            v -> c.antiWebCut = v),
                    group("Qo'yish"),
                    num("Masofa", "Qo'yish masofasi", 30, 60, () -> c.reachTenths, v -> c.reachTenths = v,
                            v -> (v / 10.0) + " blok"),
                    num("Kechikish", "Qo'yishlar orasida", 0, 20, () -> c.placeDelay, v -> c.placeDelay = v,
                            v -> v + " tick"),
                    bool("Qarab qo'yish", "Avval o'sha joyga qaraydi (Anti-cheat burilish sozlamalari)", () -> c.rotate,
                            v -> c.rotate = v),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan olib keladi", () -> c.fromInventory,
                            v -> c.fromInventory = v),
                    bool("Xabarlar", "Nima uchun qo'yganini action bar'da yozadi", () -> c.messages,
                            v -> c.messages = v),
                    bool("HUD", "Ekranda cobweb soni", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Shield", "Qo'lda qalqon bo'lsa, urmoqchi bo'lishsa o'zi ko'taradi",
                Module.Category.COMBAT, () -> ShieldConfig.get().enabled, v -> ShieldConfig.get().enabled = v, () -> {
            ShieldConfig c = ShieldConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoShieldClient.keyToggle),
                    group("O'yinchilar"),
                    bool("O'yinchilar", "Yaqinda sizga qarab turgan / yugurib kelayotgan o'yinchi", () -> c.players,
                            v -> c.players = v),
                    num("Masofa", "Shu masofada xavf deb hisoblanadi", 25, 80, () -> c.playerRange,
                            v -> c.playerRange = v, v -> (v / 10.0) + " blok"),
                    num("Qarash burchagi", "Sizga shuncha gradus ichida qarab tursa", 10, 90, () -> c.faceAngle,
                            v -> c.faceAngle = v, v -> v + "°"),
                    bool("Kamon/arbalet", "Sizni kamon, arbalet yoki trident bilan nishonga olsa", () -> c.playerRanged,
                            v -> c.playerRanged = v),
                    bool("Mace", "Ustingizdan tushayotgan o'yinchi", () -> c.maceFall, v -> c.maceFall = v),
                    group("Moblar"),
                    bool("Moblar", "Yaqindagi dushman mob urmoqchi bo'lsa", () -> c.mobs, v -> c.mobs = v),
                    num("Mob masofasi", "Shu masofada", 15, 60, () -> c.mobRangeTenths, v -> c.mobRangeTenths = v,
                            v -> (v / 10.0) + " blok"),
                    bool("Skelet/pillager", "Sizga qarab o'q tortayotgan moblar", () -> c.mobRanged,
                            v -> c.mobRanged = v),
                    bool("Creeper", "Portlay deb turgan creeper", () -> c.creepers, v -> c.creepers = v),
                    group("Boshqa"),
                    bool("O'qlar", "Sizga qarab uchayotgan o'q, trident, fireball", () -> c.projectiles,
                            v -> c.projectiles = v),
                    bool("Zarba yeganda", "Urishsa darhol ko'taradi", () -> c.onHurt, v -> c.onHurt = v),
                    num("Ushlab turish", "Xavf o'tgach yana shuncha tick", 0, 20, () -> c.holdAfterTicks,
                            v -> c.holdAfterTicks = v, v -> v + " tick"),
                    num("Eng uzoq", "Qalqon eng ko'pi bilan shuncha tick ko'tariladi, keyin tushadi", 5, 60,
                            () -> c.maxUpTicks, v -> c.maxUpTicks = v, v -> v + " tick"),
                    num("Qayta ko'tarish", "Tushgach shuncha tick kutadi (zarba/o'qda darhol)", 0, 40,
                            () -> c.rearmTicks, v -> c.rearmTicks = v, v -> v + " tick"),
                    bool("Hujumchiga burilish", "Orqangizdan kelsa unga buriladi (qalqon faqat oldni to'sadi)",
                            () -> c.autoFace, v -> c.autoFace = v),
                    bool("Urish uchun tushirish", "Chap tugmani bossangiz qalqon tushadi va urasiz", () -> c.lowerToAttack,
                            v -> c.lowerToAttack = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar xavf emas", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("HUD", "Ekranda qalqon holati", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Potion", "Jon kam qolsa Healing splash'ni oyog'ingizga otib, darhol qaytadi",
                Module.Category.COMBAT, () -> PotionConfig.get().enabled, v -> PotionConfig.get().enabled = v, () -> {
            PotionConfig c = PotionConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoPotionClient.keyToggle),
                    group("Davolash"),
                    num("Jon", "Shundan kam yoki teng bo'lsa otadi", 1, 19, () -> c.health, v -> c.health = v,
                            v -> (v / 2.0) + " yurak"),
                    bool("Absorption hisoblansin", "Sariq yuraklar ham jonga qo'shiladi", () -> c.countAbsorption,
                            v -> c.countAbsorption = v),
                    bool("Regeneration zaxira", "Healing bo'lmasa Regeneration damlamasini otadi", () -> c.useRegen,
                            v -> c.useRegen = v),
                    bool("Lingering ham", "Lingering damlamalarni ham ishlatadi", () -> c.useLingering,
                            v -> c.useLingering = v),
                    num("Kechikish", "Otishlar orasida", 1, 20, () -> c.cooldownTicks, v -> c.cooldownTicks = v,
                            v -> v + " tick"),
                    bool("Silent", "Kamera qimirlamaydi, pastga qarash faqat serverga ketadi (eng tez)", () -> c.silent,
                            v -> c.silent = v),
                    bool("To'xtatib otish", "Ovqat yeyayotgan / qalqon ushlab turgan bo'lsangiz ham", () -> c.interrupt,
                            v -> c.interrupt = v),
                    bool("Inventardan to'ldirish", "Hotbar'da tugasa inventardagini olib keladi", () -> c.refill,
                            v -> c.refill = v),
                    group("Buff damlamalar"),
                    bool("Strength", "Strength effekti yo'q bo'lsa otadi", () -> c.buffStrength,
                            v -> c.buffStrength = v),
                    bool("Speed", "Speed effekti yo'q bo'lsa otadi", () -> c.buffSpeed, v -> c.buffSpeed = v),
                    bool("Fire Resistance", "Yonayotgan / lavada bo'lsangiz", () -> c.buffFireRes,
                            v -> c.buffFireRes = v),
                    group("Boshqa"),
                    bool("Xabarlar", "Damlama tugasa ogohlantiradi", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Ekranda healing damlamalar soni", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Bow Aim", "Kamon/arbalet/trident bilan pritselga eng yaqin nishonni o'zi oladi",
                Module.Category.COMBAT, () -> BowAimConfig.get().enabled, v -> BowAimConfig.get().enabled = v, () -> {
            BowAimConfig c = BowAimConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", BowAimClient.keyToggle),
                    group("Nishon"),
                    num("FOV", "Pritseldan shuncha gradus ichidagi eng yaqini", 5, 180, () -> c.fov, v -> c.fov = v,
                            v -> v + "°"),
                    num("Masofa", "Eng uzoq masofa", 10, 150, () -> c.range, v -> c.range = v, v -> v + " blok"),
                    choice("Qayerga", "Nishonning qaysi joyiga", new String[] {"Gavda", "Bosh", "Oyoq"},
                            () -> Math.floorMod(c.aimPoint, 3), v -> c.aimPoint = v),
                    bool("O'yinchilar", "O'yinchilarni nishonga olish", () -> c.players, v -> c.players = v),
                    bool("Moblar", "Dushman moblarni ham", () -> c.mobs, v -> c.mobs = v),
                    bool("Faqat ko'rinadigan", "Devor orqasidagilarni olmaydi", () -> c.visibleOnly,
                            v -> c.visibleOnly = v),
                    bool("Nishonda qolish", "Tanlangan nishondan sakramaydi", () -> c.stickyTarget,
                            v -> c.stickyTarget = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    group("Hisoblash"),
                    bool("Harakatni oldindan", "Qayerga yurayotgani / sakrayotganini hisobga oladi", () -> c.predict,
                            v -> c.predict = v),
                    bool("O'z tezligim", "Yugurib otganda o'z tezligingizni ham hisobga oladi", () -> c.selfVelocity,
                            v -> c.selfVelocity = v),
                    bool("Silliq burilish", "Anti-cheat burilish sozlamalari bilan (OFF - bir zumda)", () -> c.smooth,
                            v -> c.smooth = v),
                    bool("Trident", "Trident otganda ham", () -> c.trident, v -> c.trident = v),
                    group("Otish"),
                    bool("O'zi otsin", "Kamon tortilib nishonga qaraganda o'zi qo'yib yuboradi", () -> c.autoShoot,
                            v -> c.autoShoot = v),
                    num("Tortish", "Kamonni necha % tortganda otadi", 10, 100, () -> c.shootCharge,
                            v -> c.shootCharge = v, v -> v + "%"),
                    bool("Arbalet ham", "O'qlangan arbaletni nishonga qaraganda otadi", () -> c.crossbowShoot,
                            v -> c.crossbowShoot = v),
                    bool("HUD", "Ekranda nishon va masofa", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Anchor Guard", "Anchor qo'yganingizda oldiga bitta blok qo'yadi - portlash zarari kamayadi",
                Module.Category.COMBAT, () -> AnchorGuardConfig.get().enabled, v -> AnchorGuardConfig.get().enabled = v, () -> {
            AnchorGuardConfig c = AnchorGuardConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AnchorGuardClient.keyToggle),
                    bool("Anchor qo'yganda", "Anchor qo'ysangiz o'sha zahoti oraga bitta blok (qarab qo'yadi va qaytadi)",
                            () -> c.onPlace, v -> c.onPlace = v),
                    bool("Ikki qavat", "Bosh balandligiga ham (odatda bitta yetadi)", () -> c.twoHigh,
                            v -> c.twoHigh = v),
                    bool("Dushman anchor'lari", "Yaqinda zaryadlangan anchor bo'lsa oldindan to'sadi", () -> c.proactiveScan,
                            v -> c.proactiveScan = v),
                    num("Radius", "Dushman anchor'larini qidirish", 2, 8, () -> c.radius, v -> c.radius = v,
                            v -> v + " blok"),
                    bool("Faqat obsidian", "OFF - istalgan to'liq blok (obsidian afzal)", () -> c.obsidianOnly,
                            v -> c.obsidianOnly = v),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan", () -> c.fromInventory,
                            v -> c.fromInventory = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Fall Combo", "Tepadan tushayotganda: spear zaryadi → qilich/bolta → mace'ga o'tish",
                Module.Category.COMBAT, () -> FallComboConfig.get().enabled, v -> FallComboConfig.get().enabled = v, () -> {
            FallComboConfig c = FallComboConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", FallComboClient.keyToggle),
                    group("Kombinatsiya"),
                    bool("Spear zaryadi", "Avval spear bilan o'ng tugma (tushish tezligi zararni oshiradi)",
                            () -> c.useSpear, v -> c.useSpear = v),
                    bool("Qilich/bolta zarbasi", "Spear tegishi bilan qilichga (bo'lmasa boltaga) o'tib uradi",
                            () -> c.followHit, v -> c.followHit = v),
                    bool("Qalqonni sindirish", "Nishon qalqon ushlasa avval bolta bilan o'chiradi", () -> c.breakShield,
                            v -> c.breakShield = v),
                    bool("Mace'ga o'tish", "Oxirida mace'ni qo'lga oladi (zarbani o'zingiz/X rejimi beradi)",
                            () -> c.endOnMace, v -> c.endOnMace = v),
                    bool("1 tick swap", "Almashtirish va zarba bir tickda (zarba kuchsizlanmaydi)", () -> c.sameTickSwap,
                            v -> c.sameTickSwap = v),
                    bool("Nishonga qarash", "Tushayotganda nishonga buriladi", () -> c.aim, v -> c.aim = v),
                    group("Masofalar"),
                    num("Min balandlik", "Shundan baland tushayotganda", 1, 30, () -> c.minFall, v -> c.minFall = v,
                            v -> v + " blok"),
                    num("Nishon radiusi", "Ostingizdagi nishon gorizontal masofasi", 10, 80, () -> c.rangeTenths,
                            v -> c.rangeTenths = v, v -> (v / 10.0) + " blok"),
                    num("Qilich masofasi", "Shu masofada qilich bilan uradi", 20, 45, () -> c.hitReachTenths,
                            v -> c.hitReachTenths = v, v -> (v / 10.0) + " blok"),
                    num("Spear tegmasa", "Shu masofaga kelganda baribir qilichga o'tadi", 10, 45, () -> c.fallbackTenths,
                            v -> c.fallbackTenths = v, v -> (v / 10.0) + " blok"),
                    group("Nishon"),
                    bool("O'yinchilar", "O'yinchilarga", () -> c.players, v -> c.players = v),
                    bool("Moblar", "Dushman moblarga ham", () -> c.mobs, v -> c.mobs = v),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Auto Elytra", "Firework bosilganda elytra'ni kiyadi, ochadi va firework'ga qaytadi",
                Module.Category.MOVEMENT, () -> ElytraConfig.get().enabled, v -> ElytraConfig.get().enabled = v, () -> {
            ElytraConfig c = ElytraConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoElytraClient.keyToggle),
                    group("Uchish"),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan qidiradi", () -> c.fromInventory,
                            v -> c.fromInventory = v),
                    bool("Yerda sakrash", "Yerda bo'lsangiz o'zi sakraydi", () -> c.autoJump, v -> c.autoJump = v),
                    bool("Elytra'ni ochish", "Havoda elytra'ni o'zi ochadi", () -> c.autoDeploy, v -> c.autoDeploy = v),
                    bool("Firework ishlatish", "Elytra ochilgach firework'ni o'zi ishlatadi", () -> c.autoFirework,
                            v -> c.autoFirework = v),
                    num("Kechikish", "Amallar orasida kutish", 0, 10, () -> c.delayTicks, v -> c.delayTicks = v,
                            v -> v + " tick"),
                    group("Mace (dushman ustida yechish)"),
                    bool("Dushman oldida yechish", "Uchib dushman ustiga kelganda elytra yechiladi, pastga tushasiz",
                            () -> c.unequipNearEnemy, v -> c.unequipNearEnemy = v),
                    num("Masofa", "Dushmangacha shu masofada", 3, 20, () -> c.unequipDist, v -> c.unequipDist = v,
                            v -> v + " blok"),
                    num("Balandlik", "Dushmandan kamida shuncha baland bo'lsangiz", 0, 20, () -> c.unequipMinAbove,
                            v -> c.unequipMinAbove = v, v -> v + " blok"),
                    num("Oldindan", "Dushmangacha shuncha tick qolganda yechadi (tez uchsangiz uzoqroqdan)", 2, 30,
                            () -> c.unequipLeadTicks, v -> c.unequipLeadTicks = v, v -> v + " tick"),
                    bool("Mace'ni olish", "Yechgach mace'ni qo'lga oladi", () -> c.unequipToMace,
                            v -> c.unequipToMace = v),
                    group("Qo'nish"),
                    bool("Nagrudnikni qaytarish", "Qo'nganda avvalgi nagrudnikni kiyadi", () -> c.restoreChest,
                            v -> c.restoreChest = v),
                    num("Qo'ngach kutish", "Nagrudnik kiyishdan oldin", 0, 40, () -> c.landDelayTicks,
                            v -> c.landDelayTicks = v, v -> v + " tick"),
                    group("Durability"),
                    bool("Almashtirish", "Uchayotganda elytra sinay desa boshqasiga almashtiradi", () -> c.autoReplace,
                            v -> c.autoReplace = v),
                    num("Min durability", "Bundan kam elytra'ni ishlatmaydi", 0, 100, () -> c.minDurability,
                            v -> c.minDurability = v),
                    num("Ogohlantirish", "Durability shundan kam qolsa ogohlantiradi", 0, 100, () -> c.warnDurability,
                            v -> c.warnDurability = v, v -> v == 0 ? "o'chiq" : String.valueOf(v)),
                    group("Boshqa"),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Uchayotganda elytra durability'si va firework soni", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Auto Clutch", "Yiqilayotganda tagingizga suv/powder snow/slime/cobweb... qo'yadi (MLG)",
                Module.Category.MOVEMENT, () -> ClutchConfig.get().enabled, v -> ClutchConfig.get().enabled = v, () -> {
            ClutchConfig c = ClutchConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)",
                            AutoClutchClient.keyToggle),
                    num("Min yiqilish", "Jami yiqilish shundan ko'p bo'lsa (3 blokdan ko'pi zarar)", 4, 40,
                            () -> c.minFall, v -> c.minFall = v, v -> v + " blok"),
                    group("Nimalar (ustuvorlik tartibida)"),
                    bool("Suv paqiri", "Nether'da ishlatilmaydi", () -> c.water, v -> c.water = v),
                    bool("Powder snow", "Powder snow paqiri", () -> c.powderSnow, v -> c.powderSnow = v),
                    bool("Slime", "Slime block", () -> c.slime, v -> c.slime = v),
                    bool("Cobweb", "Cobweb", () -> c.cobweb, v -> c.cobweb = v),
                    bool("Scaffolding", "Scaffolding", () -> c.scaffolding, v -> c.scaffolding = v),
                    bool("Pichan", "Hay bale (zararni 80% kamaytiradi)", () -> c.hay, v -> c.hay = v),
                    group("Boshqa"),
                    bool("Suvni qaytarib olish", "Qo'ngach suvni paqirga oladi", () -> c.pickupWater,
                            v -> c.pickupWater = v),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan (keyin joyiga qaytaradi)",
                            () -> c.fromInventory, v -> c.fromInventory = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Pearl Clutch", "Ender pearl: harakatingizni hisoblab aniq otadi, yiqilayotganda o'zi clutch qiladi",
                Module.Category.MOVEMENT, () -> PearlConfig.get().enabled, v -> PearlConfig.get().enabled = v, () -> {
            PearlConfig c = PearlConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)",
                            PearlClutchClient.keyToggle),
                    bool("Aim tuzatish", "O'zingiz otganda: yugurib/sakrab/uchib otsangiz ham qaragan joyingizga tushadi",
                            () -> c.aimAssist, v -> c.aimAssist = v),
                    bool("Avto clutch", "Baland joydan yiqilayotganda o'zi xavfsiz blokka otadi", () -> c.autoClutch,
                            v -> c.autoClutch = v),
                    bool("Void clutch", "Ostida blok yo'q bo'lsa (void) ham", () -> c.voidClutch, v -> c.voidClutch = v),
                    num("Min yiqilish", "Jami yiqilish shundan ko'p bo'lsa clutch", 4, 40, () -> c.minFall,
                            v -> c.minFall = v, v -> v + " blok"),
                    num("Qidiruv radiusi", "Qo'nadigan blokni shu radiusda qidiradi", 3, 16, () -> c.radius,
                            v -> c.radius = v, v -> v + " blok"),
                    bool("Inventardan olish", "Hotbar'da bo'lmasa inventardan (keyin joyiga qaytaradi)",
                            () -> c.fromInventory, v -> c.fromInventory = v),
                    bool("Xabarlar", "Action bar xabarlari", () -> c.messages, v -> c.messages = v));
        }));

        l.add(new Module("Auto TNT Cart", "Kamon/arbalet o'qi tushadigan joyga yoki qo'ygan railingizga TNT minecart",
                Module.Category.WORLD, () -> AutoTntConfig.get().enabled, v -> AutoTntConfig.get().enabled = v, () -> {
            AutoTntConfig c = AutoTntConfig.get();
            AutoTntConfig.Profile p = c.p();
            return List.of(
                    group("Tugmalar"),
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AutoTntClient.keyToggle),
                    key("Kamon rejimi", "Kamon auto-TNT yoqish/o'chirish", AutoTntClient.keyBow),
                    key("Arbalet", "Arbalet auto-TNT (bir marta)", AutoTntClient.keyCrossbow),
                    key("Rail rejimi", "Rail qo'yganda auto-TNT yoqish/o'chirish", AutoTntClient.keyRail),
                    key("Profil", "Profilni almashtirish", AutoTntClient.keyProfile),
                    key("Sozlamalar", "Shu oynani ochadi", AutoTntClient.keySettings),
                    group("Rejimlar"),
                    bool("Kamon auto-TNT", "Kamonni qo'yib yuborganda o'q tushadigan joyga TNT", () -> c.bowMode,
                            v -> c.bowMode = v),
                    bool("Rail qo'yganda TNT", "Rail qo'ysangiz: cart, olov va kamon bilan otish", () -> c.railTrigger,
                            v -> c.railTrigger = v),
                    bool("Rail: kamon bilan otish", "Rail rejimida oxirida cartga otadi", () -> c.railBow,
                            v -> c.railBow = v),
                    num("Kamon tortish", "Rail rejimida kamonni necha tick tortish", 4, 20, () -> c.bowPullTicks,
                            v -> c.bowPullTicks = v, v -> v + " tick"),
                    group("Profil " + (c.active + 1)),
                    choice("Profil", "Har bir profilning o'z sozlamalari", new String[] {"1", "2", "3"},
                            () -> Math.floorMod(c.active, c.profiles.length), v -> c.active = v),
                    choice("Olov", "TNT minecart'ni nima bilan yoqish",
                            new String[] {"Auto", "Flint (cart)", "Fire charge", "Activator"},
                            () -> Math.floorMod(p.ignition, 4), v -> p.ignition = v),
                    num("Delay", "Amallar orasidagi kechikish", 0, 20, () -> p.delayTicks, v -> p.delayTicks = v,
                            v -> v + " tick"),
                    num("Reach", "Blok qo'yish masofasi", 30, 60, () -> p.reachTenths, v -> p.reachTenths = v,
                            v -> (v / 10.0) + " blok"),
                    num("O'zimdan min", "Portlash joyi sizdan eng kami shuncha uzoqda", 0, 100,
                            () -> p.minSelfDistTenths, v -> p.minSelfDistTenths = v, v -> (v / 10.0) + " blok"),
                    num("Minecart soni", "Nechta TNT minecart (zanjir)", 1, 5, () -> p.cartCount, v -> p.cartCount = v),
                    num("Do'st radiusi", "Do'st shu radiusda bo'lsa ishlamaydi", 0, 20, () -> p.friendRadius,
                            v -> p.friendRadius = v, v -> v == 0 ? "o'chiq" : String.valueOf(v)),
                    bool("Nishonni oldindan", "Harakatlanayotgan o'yinchini oldindan hisoblash", () -> p.predictTarget,
                            v -> p.predictTarget = v),
                    num("Oldinga", "Necha tick oldinga hisoblash", 0, 40, () -> p.lookaheadTicks,
                            v -> p.lookaheadTicks = v, v -> v + " tick"),
                    group("Inventar"),
                    bool("Inventardan olish", "Hotbar'da yo'q narsani inventardan olib keladi", () -> c.autoRefill,
                            v -> c.autoRefill = v),
                    bool("Inventarga qaytarish", "Olib kelinganlarini joyiga qaytaradi", () -> c.restoreInventory,
                            v -> c.restoreInventory = v),
                    group("Boshqa"),
                    bool("Tovush", "Muvaffaqiyat/xato tovushlari", () -> c.sounds, v -> c.sounds = v),
                    bool("Xabarlar", "Chat va action bar xabarlari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Ekranda holat va o'q tushadigan joy", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Fast Place", "O'ng tugmani bosib turganda bloklar tez qo'yiladi (vanilla 4 tick)",
                Module.Category.WORLD, () -> FastPlaceConfig.get().enabled, v -> FastPlaceConfig.get().enabled = v, () -> {
            FastPlaceConfig c = FastPlaceConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", FastPlaceClient.keyToggle),
                    num("Kechikish", "Qo'yishlar orasida (vanilla 4, eng tezi 1)", 1, 4, () -> c.delay, v -> c.delay = v,
                            v -> v == 4 ? "4 tick (vanilla)" : v + " tick"),
                    num("Bloklar/tick", "Bir tickda nechta blok (tepaga ko'tarilish, ko'prik)", 1, 4, () -> c.perTick,
                            v -> c.perTick = v),
                    bool("Faqat bloklar", "Ovqat, kamon va boshqa narsalarga ta'sir qilmaydi", () -> c.onlyBlocks,
                            v -> c.onlyBlocks = v));
        }));

        l.add(new Module("Chest Stealer", "Sandiq ochilganda faqat tanlangan itemlarni o'zi oladi",
                Module.Category.WORLD, () -> StealerConfig.get().enabled, v -> StealerConfig.get().enabled = v, () -> {
            StealerConfig c = StealerConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", ChestStealerClient.keyToggle),
                    choice("Nima olinadi", "Tanlanganlar / hammasi / tanlanganlardan tashqari",
                            new String[] {"Tanlanganlar", "Hammasi", "Tanlanmaganlar"},
                            () -> Math.floorMod(c.mode, 3), v -> c.mode = v),
                    items("Itemlar", "Bosing va olinadigan itemlarni tanlang", c.items),
                    num("Kechikish", "Itemlar orasida (0 - hammasi birdaniga)", 0, 10, () -> c.delay,
                            v -> c.delay = v, v -> v + " tick"),
                    num("Ochilgach kutish", "Birinchi itemdan oldin", 0, 20, () -> c.openDelay, v -> c.openDelay = v,
                            v -> v + " tick"),
                    bool("Avto yopish", "Tugagach sandiqni yopadi", () -> c.autoClose, v -> c.autoClose = v),
                    bool("Faqat haqiqiy sandiq", "Server menyulari (sandiqqa o'xshash oynalar) dan olmaydi",
                            () -> c.realOnly, v -> c.realOnly = v),
                    bool("Shulker ham", "Shulker box'dan ham oladi", () -> c.shulkers, v -> c.shulkers = v),
                    bool("Shift - to'xtatish", "Shift bosib ochsangiz ishlamaydi", () -> c.shiftPause,
                            v -> c.shiftPause = v));
        }));

        l.add(new Module("Nametags+", "O'yinchi ustida ism, zirh, protection, qo'lidagilar va totem soni",
                Module.Category.RENDER, () -> NametagsConfig.get().enabled, v -> NametagsConfig.get().enabled = v, () -> {
            NametagsConfig c = NametagsConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", NametagsClient.keyToggle),
                    group("Matn"),
                    bool("Ism", "O'yinchi nicki (do'stlar yashil)", () -> c.name, v -> c.name = v),
                    bool("Jon", "Health indicator modingiz bo'lsa o'chiq qoldiring", () -> c.health,
                            v -> c.health = v),
                    bool("Masofa", "Necha blok uzoqda", () -> c.distance, v -> c.distance = v),
                    bool("Totem pop", "Nechta totem ishlatgani (o'lsa nolga tushadi)", () -> c.totemPops,
                            v -> c.totemPops = v),
                    bool("Pop xabari", "Chatga \"X totem ishlatdi\"", () -> c.popChat, v -> c.popChat = v),
                    group("Itemlar"),
                    bool("Zirh", "Kiygan zirhi", () -> c.armor, v -> c.armor = v),
                    bool("Qo'lidagilar", "Asosiy va chap qo'lidagi narsalar", () -> c.hands, v -> c.hands = v),
                    bool("Protection", "Zirhdagi himoya darajasi (P4, B4...) va qurol (S5, D5...)", () -> c.protection,
                            v -> c.protection = v),
                    bool("Durability", "Durability chizig'i va %", () -> c.durability, v -> c.durability = v),
                    group("Ko'rinish"),
                    num("Balandlik", "Bosh ustidan (health indicator'ga xalaqit bermasligi uchun)", 0, 30,
                            () -> c.heightTenths, v -> c.heightTenths = v, v -> (v / 10.0) + " blok"),
                    num("Hajmi", "Nametag kattaligi", 50, 200, () -> c.scale, v -> c.scale = v, v -> v + "%"),
                    num("Masofa limiti", "Shundan uzoqdagilar ko'rsatilmaydi", 8, 256, () -> c.range,
                            v -> c.range = v, v -> v + " blok"),
                    bool("Ko'rinmaslar ham", "Invisibility ichgan o'yinchilar", () -> c.invisible,
                            v -> c.invisible = v));
        }));

        l.add(new Module("NoRender", "Olovni kichik, suv/lava ichini tiniq, totem/portlash/silkinish va boshqalarni o'chiradi",
                Module.Category.RENDER, () -> NoRenderConfig.get().enabled, v -> NoRenderConfig.get().enabled = v, () -> {
            NoRenderConfig c = NoRenderConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", NoRenderClient.keyToggle),
                    group("Olov"),
                    choice("Olov", "Ekrandagi olov: past (crystal PvP uchun yaxshi) yoki umuman yo'q",
                            new String[] {"Vanilla", "Past", "Yo'q"}, () -> Math.floorMod(c.fire, 3), v -> c.fire = v),
                    num("Qancha past", "Past olov qancha tushiriladi", 10, 80, () -> c.fireLower, v -> c.fireLower = v,
                            v -> v + "%"),
                    group("Tuman va qatlamlar"),
                    bool("Suv tumani", "Suv ichida hamma narsa tiniq ko'rinadi", () -> c.waterFog, v -> c.waterFog = v),
                    bool("Suv ostidagi qatlam", "Ko'k ekran qatlami", () -> c.underwaterOverlay,
                            v -> c.underwaterOverlay = v),
                    bool("Lava tumani", "Lava ichida ko'rish", () -> c.lavaFog, v -> c.lavaFog = v),
                    bool("Blindness/Darkness", "Ko'rlik va darkness tumani", () -> c.effectFog, v -> c.effectFog = v),
                    bool("Powder snow", "Powder snow tumani", () -> c.powderSnowFog, v -> c.powderSnowFog = v),
                    bool("Blok ichida", "Blok ichida qolgandagi qora ekran", () -> c.inWall, v -> c.inWall = v),
                    bool("Qovoq qatlami", "Qovoq / powder snow ekran qatlami", () -> c.pumpkin, v -> c.pumpkin = v),
                    bool("Vignette", "Ekran chetlari qorayishi", () -> c.vignette, v -> c.vignette = v),
                    group("Effektlar"),
                    bool("Zarba silkinishi", "Zarba yeganda kamera silkinmaydi", () -> c.hurtCam, v -> c.hurtCam = v),
                    bool("Totem animatsiyasi", "Ekranni yopadigan totem animatsiyasi", () -> c.totemAnimation,
                            v -> c.totemAnimation = v),
                    bool("Portal/nausea", "Portal va bosh aylanishi effekti", () -> c.portalNausea,
                            v -> c.portalNausea = v),
                    bool("Portlash zarrachalari", "Crystal/anchor/TNT portlash zarrachalari (FPS va ko'rish)",
                            () -> c.explosions, v -> c.explosions = v),
                    bool("Ob-havo", "Yomg'ir va qor", () -> c.weather, v -> c.weather = v));
        }));

        l.add(new Module("Auto Travel", "/goto x z (yoki x y z): o'zi boradi - elytra, piyoda (blok sindirib/qo'yib), suvda kema",
                Module.Category.MOVEMENT, () -> TravelConfig.get().enabled, v -> TravelConfig.get().enabled = v, () -> {
            TravelConfig c = TravelConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (o'chsa yo'l to'xtaydi)",
                            AutoTravelClient.keyToggle),
                    action("To'xtatish", "Hozirgi yo'lni to'xtatadi (/goto stop)",
                            () -> AutoTravelClient.CONTROLLER.stop(net.minecraft.client.MinecraftClient.getInstance(),
                                    "§eAuto Travel: to'xtatildi")),
                    group("Elytra"),
                    bool("Elytra bilan", "Elytra (kiyilgan yoki inventarda) va firework bo'lsa uchib boradi", () -> c.elytra,
                            v -> c.elytra = v),
                    num("Elytra masofasi", "Shundan uzoq bo'lsa uchadi", 30, 1000, () -> c.elytraMinDist,
                            v -> c.elytraMinDist = v, v -> v + " blok"),
                    num("Balandlik", "Yer va oldindagi tog'/devordan shuncha baland uchadi", 8, 80, () -> c.cruiseHeight,
                            v -> c.cruiseHeight = v, v -> v + " blok"),
                    num("Min durability", "Elytra shundan kam qolsa qo'nadi, yechadi va piyoda boradi", 2, 200,
                            () -> c.minDurability, v -> c.minDurability = v, v -> v + ""),
                    num("Firework tezligi", "Tezlik shundan past bo'lsa firework", 5, 25, () -> c.fireworkSpeedTenths,
                            v -> c.fireworkSpeedTenths = v, v -> (v / 10.0) + " blok/tick"),
                    num("Firework oralig'i", "Firework'lar orasida eng kami", 5, 80, () -> c.fireworkDelay,
                            v -> c.fireworkDelay = v, v -> v + " tick"),
                    bool("Nagrudnikni kiyish", "Qo'ngach elytra o'rniga nagrudnik", () -> c.restoreChest,
                            v -> c.restoreChest = v),
                    group("Kema"),
                    bool("Kema bilan", "Katta suvda kemani qo'yib o'tiradi", () -> c.boat, v -> c.boat = v),
                    num("Suv kattaligi", "Oldinda shuncha blok suv bo'lsa kema", 4, 64, () -> c.boatMinWater,
                            v -> c.boatMinWater = v, v -> v + " blok"),
                    bool("Kemani olish", "Qirg'oqda kemani sindirib oladi", () -> c.pickupBoat, v -> c.pickupBoat = v),
                    group("Piyoda"),
                    bool("Blok sindirish", "Yo'lni to'sgan bloklarni eng yaxshi asbob bilan sindiradi", () -> c.breakBlocks,
                            v -> c.breakBlocks = v),
                    bool("Blok qo'yish", "Bo'shliqqa ko'prik, tepaga ustun (arzon bloklar)", () -> c.placeBlocks,
                            v -> c.placeBlocks = v),
                    num("Max tushish", "Shundan baland joydan sakramaydi", 1, 20, () -> c.maxFall, v -> c.maxFall = v,
                            v -> v + " blok"),
                    bool("Yugurish", "Sprint", () -> c.sprint, v -> c.sprint = v),
                    num("Qidiruv", "Bitta yo'l qidirishdagi nuqtalar (ko'p - aqlliroq, lekin og'irroq)", 2000, 40000,
                            () -> c.searchNodes, v -> c.searchNodes = v, v -> v + ""),
                    group("Boshqa"),
                    bool("Xabarlar", "Chatga holat xabarlari", () -> c.messages, v -> c.messages = v),
                    bool("HUD", "Ekranda holat va masofa", () -> c.hud, v -> c.hud = v));
        }));

        l.add(new Module("Crystal HUD", "Dushmanga eng ko'p zarar beradigan crystal joylarini ko'rsatadi (o'zingiz qo'yasiz)",
                Module.Category.COMBAT, () -> CrystalHudConfig.get().enabled, v -> CrystalHudConfig.get().enabled = v, () -> {
            CrystalHudConfig c = CrystalHudConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", CrystalHudClient.keyToggle),
                    bool("Faqat crystal qo'lda", "Qo'lda end crystal bo'lganda ko'rsatadi", () -> c.onlyHolding,
                            v -> c.onlyHolding = v),
                    num("Dushman masofasi", "Shu masofadagi eng yaqin dushman", 4, 24, () -> c.range, v -> c.range = v,
                            v -> v + " blok"),
                    num("Qo'yish masofasi", "Shu masofadagi obsidian/bedrock", 30, 60, () -> c.placeRangeTenths,
                            v -> c.placeRangeTenths = v, v -> (v / 10.0) + " blok"),
                    num("Nechta joy", "Eng yaxshi joylar soni (yashil - eng yaxshisi)", 1, 8, () -> c.count,
                            v -> c.count = v, v -> v + ""),
                    num("Min zarar", "Dushmanga kamida (1 = yarim yurak)", 1, 30, () -> c.minDamage, v -> c.minDamage = v,
                            v -> v + ""),
                    num("Max o'zimga", "O'zingizga eng ko'pi", 1, 36, () -> c.maxSelf, v -> c.maxSelf = v, v -> v + ""),
                    bool("Do'stlarga emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v));
        }));

        l.add(new Module("Pearl Tracker", "Dushman pearl'i qayerga tushishini qizil bilan ajratadi - borib kutib olasiz",
                Module.Category.RENDER, () -> PearlTrackerConfig.get().enabled, v -> PearlTrackerConfig.get().enabled = v, () -> {
            PearlTrackerConfig c = PearlTrackerConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi", PearlTrackerClient.keyToggle),
                    bool("Uchish yo'li", "Pearl uchadigan yo'lni nuqtalar bilan ko'rsatadi", () -> c.trajectory,
                            v -> c.trajectory = v),
                    num("Qizil radius", "Tushadigan joy atrofidagi qizil doira", 5, 40, () -> c.radiusTenths,
                            v -> c.radiusTenths = v, v -> (v / 10.0) + " blok"),
                    bool("O'zimniki ham", "O'zingiz otgan pearl'lar ham", () -> c.own, v -> c.own = v),
                    bool("Do'stlarniki emas", "/tntfriend ro'yxatidagilar", () -> c.ignoreFriends,
                            v -> c.ignoreFriends = v),
                    bool("Chat xabari", "\"X pearl otdi: koordinata\"", () -> c.chat, v -> c.chat = v));
        }));

        l.add(new Module("Arraylist", "Ekran chetida yoqilgan modullar ro'yxati (Meteor kabi)",
                Module.Category.RENDER, () -> Config.get().arraylist, v -> Config.get().arraylist = v, () -> {
            Config c = Config.get();
            return List.of(
                    choice("Joyi", "Ekranning qaysi burchagida", ArrayListHud.POSITIONS,
                            () -> Math.floorMod(c.arraylistPos, ArrayListHud.POSITIONS.length), v -> c.arraylistPos = v),
                    bool("Kamalak", "Rangi kamalak bo'lib o'zgarib turadi (OFF - asosiy rang)", () -> c.arraylistRainbow,
                            v -> c.arraylistRainbow = v));
        }));

        l.add(new Module("Anti-cheat", "Kamerani silliq burish va boshqa \"odamga o'xshash\" sozlamalar",
                Module.Category.CLIENT, null, null, () -> {
            Config.AntiCheat ac = Config.get().ac();
            return List.of(
                    group("Burilish"),
                    bool("Silliq burilish", "Kamera bir zumda emas, asta-sekin buriladi", () -> ac.smoothRotation,
                            v -> ac.smoothRotation = v),
                    num("Max burilish", "Bir tickda eng ko'pi bilan", 5, 180, () -> ac.maxTurnPerTick,
                            v -> ac.maxTurnPerTick = v, v -> v + "°/tick"),
                    num("Tasodifiy ±", "Burilish tezligiga tasodifiy qo'shimcha", 0, 30, () -> ac.turnJitter,
                            v -> ac.turnJitter = v, v -> v + "°"),
                    num("Nishon aniqligi", "Shuncha gradus ichida \"qarab turibdi\"", 1, 30, () -> ac.aimTolerance,
                            v -> ac.aimTolerance = v, v -> v + "°"),
                    bool("GCD fix", "Burilish sichqoncha sezgirligi qadamiga moslanadi", () -> ac.gcdFix,
                            v -> ac.gcdFix = v),
                    num("Tasodifiy kechikish", "Har lunge oldidan 0..N tick", 0, 10, () -> ac.randomDelayMax,
                            v -> ac.randomDelayMax = v, v -> "0-" + v + " tick"),
                    group("Mace"),
                    bool("Qarab urish", "Mace faqat nishonga qarab turganda uradi", () -> ac.requireLookAt,
                            v -> ac.requireLookAt = v),
                    bool("1 tick swap", "Almashtirish va urish bir tickda (OFF - zarba kuchsiz)", () -> ac.sameTickSwap,
                            v -> ac.sameTickSwap = v),
                    group("Inventar"),
                    bool("Inventarni ochib olish", "Inventardan hotbar'ga olishda E ochilib-yopiladi (serverlar uchun)",
                            () -> ac.openInventory, v -> ac.openInventory = v),
                    num("Inventar ochiq turishi", "O'zi ochgan inventar necha tick ko'rinib turadi (0 - darhol yopiladi)",
                            0, 20, () -> ac.invShowTicks, v -> ac.invShowTicks = v, v -> v + " tick"),
                    bool("Joyiga qaytarish", "Olingan narsa ishlatilgach eski joyiga qaytadi, hotbar'dagi narsa o'z joyida",
                            () -> ac.returnItems, v -> ac.returnItems = v),
                    group("TNT"),
                    bool("Blokka qarash", "Rail/minecart qo'yishdan oldin o'sha joyga qaraydi", () -> ac.rotateToBlocks,
                            v -> ac.rotateToBlocks = v),
                    bool("Kamerani qaytarish", "Tugagach kamerani avvalgi holatiga qaytaradi", () -> ac.restoreView,
                            v -> ac.restoreView = v));
        }));

        l.add(new Module("ClickGUI", "Shu menyu: tugmasi va rangi", Module.Category.CLIENT, null, null, () -> {
            Config c = Config.get();
            return List.of(
                    key("Menyu tugmasi", "ClickGUI'ni ochadigan tugma", SpearLungerClient.keySettings),
                    choice("Rang", "Asosiy rang", Theme.ACCENT_NAMES, () -> c.guiAccent, v -> c.guiAccent = v),
                    action("Panellarni joyiga qaytarish", "Sudralgan panellar boshlang'ich joyiga qaytadi",
                            () -> c.guiPanels.clear()));
        }));
        return l;
    }
}

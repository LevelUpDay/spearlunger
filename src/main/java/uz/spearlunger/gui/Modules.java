package uz.spearlunger.gui;

import uz.anchorguard.AnchorGuardClient;
import uz.anchorguard.AnchorGuardConfig;
import uz.autocobweb.AutoCobwebClient;
import uz.autocobweb.CobwebConfig;
import uz.autoelytra.AutoElytraClient;
import uz.autopotion.AutoPotionClient;
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
import uz.fallcombo.FallComboClient;
import uz.fallcombo.FallComboConfig;
import uz.fastplace.FastPlaceClient;
import uz.fastplace.FastPlaceConfig;
import uz.cheststealer.ChestStealerClient;
import uz.cheststealer.StealerConfig;
import uz.nametags.NametagsClient;
import uz.nametags.NametagsConfig;
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
                    bool("MLG", "Baland joydan yiqilayotganda tagingizga (fall damage yo'q)", () -> c.selfMlg,
                            v -> c.selfMlg = v),
                    num("MLG balandligi", "Shundan baland yiqilganda", 3, 30, () -> c.minFall, v -> c.minFall = v,
                            v -> v + " blok"),
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
                    num("Masofa", "Shu masofada xavf deb hisoblanadi", 25, 80, () -> c.playerRangeTenths,
                            v -> c.playerRangeTenths = v, v -> (v / 10.0) + " blok"),
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
                    num("Ushlab turish", "Xavf o'tgach yana shuncha tick", 0, 30, () -> c.holdTicks,
                            v -> c.holdTicks = v, v -> v + " tick"),
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

        l.add(new Module("Anchor Guard", "Anchor portlashidan oldin oldiga blok qo'yadi - o'zingizga zarar kamayadi",
                Module.Category.COMBAT, () -> AnchorGuardConfig.get().enabled, v -> AnchorGuardConfig.get().enabled = v, () -> {
            AnchorGuardConfig c = AnchorGuardConfig.get();
            return List.of(
                    key(Setting.TOGGLE_KEY, "Bosing va harf tanlang - modulni yoqib-o'chiradi (ClickGUI'da o'rta tugma ham)", AnchorGuardClient.keyToggle),
                    bool("Portlatganda", "Anchor'ni bossangiz: avval blok, keyin o'sha zahoti portlaydi",
                            () -> c.onDetonate, v -> c.onDetonate = v),
                    bool("Proaktiv", "Yaqinda zaryadlangan anchor bo'lsa (dushmanniki ham) oldindan to'sadi",
                            () -> c.proactive, v -> c.proactive = v),
                    num("Radius", "Proaktiv qidiruv", 2, 8, () -> c.radius, v -> c.radius = v, v -> v + " blok"),
                    bool("Ikki qavat", "Oyoq va bosh balandligida (zarar ancha kam)", () -> c.twoHigh,
                            v -> c.twoHigh = v),
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

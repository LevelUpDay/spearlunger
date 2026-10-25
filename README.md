# Spear Lunger + Auto TNT Cart + Auto Elytra + Auto Totem

Minecraft **1.21.11** uchun Fabric client mod. Bitta jar ichida to'rtta mod bor.
Kerakli modlar: Fabric Loader, Fabric API.

## Tugmalar

| Tugma | Vazifa |
|---|---|
| **R** | Auto Lunge: Lunge enchantli nayza bilan cooldown to'lishi bilan avtomatik jab (bosib turish yoki bir bosib yoqish) |
| **X** | Nishonga lunge qilib uchish, yaqinlashganda mace bilan urish (yerda bo'lsangiz wind charge bilan sakraydi). Yana bossangiz bekor bo'ladi |
| **O** | ClickGUI menyusi (Meteor uslubida) |
| **B** | Kamon auto-TNT yoqish/o'chirish |
| **Z** | Arbalet auto-TNT (bir marta) |
| **V** | TNT profilini almashtirish |
| **P** | Auto TNT sozlamalari |
| **G** | Rail qo'yganda auto-TNT yoqish/o'chirish |

## ClickGUI (O)

Kategoriyalar alohida panellarda: Combat, Movement, World, Client. Panel sarlavhasini sudrab joyini o'zgartirish mumkin.
- modul ustida **chap tugma** — yoqish/o'chirish;
- **o'ng tugma** — sozlamalar oynasi: modulni yoqish tugmasi (bind), boshqa tugmalar va barcha sozlamalar.
  Tugmani bosing va klaviaturadan harf tanlang (Esc — bekor, Backspace yoki o'ng tugma — tugmani o'chirish).
- Client → ClickGUI: menyu tugmasi va rangi.

`/tntfriend add|remove|list <ism>`: do'stlar ro'yxati. Ularni X nishonga olmaydi, TNT esa ular yonida portlamaydi.

## Auto TNT: rail rejimi (G)

Rail qo'ying (oddiy, powered/qizil, detector yoki activator — hammasi ishlaydi). Mod o'zi:
1. rail ustiga TNT minecart qo'yadi;
2. kamonda **Flame** bo'lmasa cart oldiga (siz tomonga) flint bilan olov qo'yadi — o'q olovdan o'tib yonadi;
3. kamonni cartga qaratib (o'q tushishini hisobga olgan holda) qisqa tortib otadi. Oxirigacha tortish shart emas
   ("Kamon tortish" sozlamasi, 4 tick ham yetadi).

Kamon yoki o'q bo'lmasa cartni flint bilan yoqadi. O'ng tugmani bosib turmang — bir marta bossangiz yetadi.

## Auto Elytra

Firework'ni bosing: mod hotbar'dan (yoki inventardan) eng yaxshi elytra'ni topib kiyadi, yerda bo'lsangiz sakraydi,
elytra'ni ochadi va firework'ni ishlatadi, keyin firework slotiga qaytadi. Qo'shimcha:
- qo'nganda avvalgi nagrudnikni qaytarib kiyadi;
- uchayotganda elytra sinay desa boshqa elytra'ga almashtiradi, durability kam qolsa ogohlantiradi;
- HUD: elytra durability'si va firework soni.

## Auto Cobweb

Hotbar (yoki inventar) dagi cobweb'ni o'zi qo'yadi:
- **Turgan dushman**: joyida turgan yoki devorga tiqilib qolgan dushman oyog'iga;
- **Kelayotgan dushman**: yugurib yoki sakrab oldingizga kelayotgan dushman tushadigan joyga (oldindan hisoblaydi);
- **Anti-mace**: ustingizdan tushayotgan dushman bo'lsa boshingiz ustiga — u ipga ilinib, mace zarbasi bo'lmaydi;
- **MLG**: baland joydan yiqilayotganda tagingizga — fall damage bo'lmaydi;
- ikki qavat (oyoq + bosh), do'stlarga qo'ymaslik, masofa, kechikish, avval qarab qo'yish.

## Auto Shield

Faqat qo'lda (o'ng yoki chap) qalqon bo'lsa ishlaydi. Qalqon ko'tarilgach ~5 tickdan keyin to'sa boshlaydi,
shuning uchun xavf zarbadan **oldin** aniqlanadi:
- yaqinda sizga qarab turgan yoki yugurib kelayotgan o'yinchi; ustingizdan mace bilan tushayotgan o'yinchi;
- sizni kamon/arbalet/trident bilan nishonga olgan o'yinchi yoki mob (skelet, pillager);
- yaqindagi dushman mob, portlay deb turgan creeper, sizga qarab uchayotgan o'q/fireball;
- zarba yesangiz darhol ko'taradi.

Hujumchi orqada bo'lsa unga buriladi, chap tugmani bossangiz urishingiz uchun qalqonni tushiradi. Bolta bilan
o'chirilgan qalqonni ko'tarmaydi. Har bir tur alohida yoqiladi/o'chiriladi.

## Auto Totem

Chap qo'lda totem bo'lmasa qo'yadi. Turlari:
- **Inventar (E + F)**: inventarni ochadi, sichqonchani totemga olib boradi, F bosadi va yopadi;
- **Tezkor**: inventar ochilmaydi, bir zumda;
- **Hotbar (F)**: hotbar'dagi totemni tanlab F bosadi (hotbar'da bo'lmasa inventar usuli).

Totem umuman bo'lmasa hech narsa qilmaydi (inventar ham ochilmaydi). Kechikish, sichqoncha tezligi, faqat jon kam
bo'lganda ishlash va "totem ishlatildi" xabari sozlanadi.

## Nayza qanday ishlaydi (1.21.11)

- Nayzada `minimum_attack_charge = 1.0` bor, ya'ni jab faqat **cooldown 100%** bo'lganda ishlaydi
  (netherite ≈ 23 tick, diamond ≈ 21 tick). Shuning uchun 1 soniyada ~1 lunge, bundan tez bo'lmaydi.
- Qo'ldagi item almashsa cooldown 0 dan boshlanadi, shuning uchun mod nayzani qo'lda ushlab turadi.
- Lunge faqat gorizontal itaradi: pastga qarasangiz kuchsiz uchasiz.
- Suvda, elytra'da va minib turganda Lunge ishlamaydi. Har lunge 1 durability va
  (Lunge III da) ~3 ochlik oladi. Ochlik kam bo'lsa mod hotbar'dagi ovqatni o'zi yeydi.

## Anti-cheat sozlamalari

O → Anti-cheat oynasida:
- **Silliq burilish** va **Max burilish (°/tick)**: kamera bir zumda emas, asta-sekin buriladi.
- **Tasodifiy ±**: burilish tezligi har tickda biroz o'zgarib turadi.
- **GCD fix**: burilish sichqoncha sezgirligi qadamlariga moslanadi.
- **Nishon aniqligi**: qancha gradus ichida "qarab turibdi" deb hisoblanadi.
- **Tasodifiy kechikish**: har lunge oldidan 0..N tick tasodifiy kutish.
- **Mace: qarab urish**: mace faqat nishonga haqiqatan qarab turganda uradi.
- **Mace: 1 tick swap**: almashtirish va urish bir tickda bo'ladi (OFF bo'lsa zarba juda kuchsiz).
- **TNT: blokka qarash / kamerani qaytarish**: TNT rail/minecart qo'yishdan oldin o'sha joyga qaraydi.

Diqqat: bu sozlamalar aniqlanish xavfini kamaytiradi, lekin yo'q qilmaydi. Serverda ishlatish o'sha
server qoidalariga bog'liq.

## Build

```
./gradlew build
```
Tayyor jar: `build/libs/`. GitHub Actions ham har push'da jar yasaydi (`spearlunger-jar` artifact).

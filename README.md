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

## Auto Potion

Jon belgilangan darajadan (standart 3 yurak) kam qolsa hotbar'dagi **Healing** splash damlamasini oyog'ingizga otadi
va darhol eski slotga qaytadi. **Silent** rejimda kamera umuman qimirlamaydi — pastga qarash faqat otish paketida
serverga ketadi, shuning uchun bir tickda bo'ladi. Qo'shimcha:
- Healing bo'lmasa Regeneration, lingering damlamalar ham;
- ovqat yeyayotgan yoki qalqon ushlab turgan bo'lsangiz ham to'xtatib otadi;
- hotbar'da tugasa inventardagini olib keladi, umuman qolmasa ogohlantiradi;
- buff damlamalar: Strength, Speed, yonganda Fire Resistance (effekt yo'q bo'lsa);
- HUD: healing damlamalar soni.

## Bow Aim

Kamonni tortganingizda (yoki o'qlangan arbalet / trident ushlaganingizda) pritselga eng yaqin nishonga o'zi qaraydi:
- o'q tushishi (gravitatsiya + havo qarshiligi) hisoblanadi, arbaletdagi firework to'g'ri chiziqda;
- nishon qayerga yurayotgani va sakrayotgani oldindan hisoblanadi, o'q yetib borish vaqti bilan aniqlashtiriladi;
- yugurib otsangiz o'z tezligingiz ham hisobga olinadi;
- FOV, masofa, gavda/bosh/oyoq, devor orqasidagilarni olmaslik, nishonda qolish, do'stlarga emas, moblar;
- **O'zi otsin** (standart o'chiq): kamon belgilangan % tortilib nishonga qaraganda o'zi qo'yib yuboradi;
  o'ng tugmani bosib tursangiz ketma-ket otadi. Arbaletni ham otishi mumkin.

## Fast Place (World)

O'ng tugmani bosib turganda vanilla bloklarni har 4 tickda qo'yadi. Fast Place bilan **Kechikish** 1 tick (har tickda)
gacha tushadi, **Bloklar/tick** bilan bir tickda 4 tagacha blok qo'yiladi — wind charge bilan tepaga ko'tarilishda
yoki bedwars'da ko'prik qurishda. "Faqat bloklar" yoqilgan bo'lsa ovqat, kamon va boshqalarga ta'sir qilmaydi.

## Anchor Guard (Combat)

Respawn anchor portlashi zarari "ko'rinish"ga bog'liq: siz bilan anchor orasidagi to'liq blok zararni keskin kamaytiradi.
- **Portlatganda**: zaryadlangan anchor'ni bossangiz mod o'sha zahoti oraga (oyoq + bosh balandligi) blok qo'yadi va
  anchor'ning ustki tomonini bosib portlatadi — portlash kechikmaydi, sizning bosishingiz ham bekor bo'lmaydi;
- **Proaktiv**: yaqinda zaryadlangan anchor paydo bo'lsa (dushmanniki ham) oldindan to'sadi;
- obsidian afzal (portlashda buzilmaydi), bo'lmasa istalgan to'liq blok; Nether'da ishlamaydi (u yerda portlamaydi).

## Chest Stealer (World)

Sandiq yoki shulker ochilganda faqat **tanlangan** itemlarni o'zi oladi (yoki hammasini / tanlanmaganlarni).
Sozlamalarda "Itemlar" ni bosing — qidiruvli item tanlash oynasi ochiladi. Kechikish, avto yopish, Shift bosib ochsangiz
to'xtash.

## Nametags+ (Render)

O'yinchi ustida: ism (do'stlar yashil), masofa, **totem pop soni** (nechta totem ishlatgani, o'lsa nolga tushadi,
chatga xabar), zirh va qo'lidagi narsalar, har biri ustida **protection darajasi** (P4, B4, PP4; qurolda S5, D5...)
va durability %. Health indicator modiga xalaqit bermasligi uchun balandligi sozlanadi (jon standartda o'chiq).

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

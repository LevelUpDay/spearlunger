# Spear Lunger + Auto TNT Cart

Minecraft **1.21.11** uchun Fabric client mod. Bitta jar ichida ikki mod bor.
Kerakli modlar: Fabric Loader, Fabric API.

## Tugmalar

| Tugma | Vazifa |
|---|---|
| **R** | Auto Lunge: Lunge enchantli nayza bilan cooldown to'lishi bilan avtomatik jab (bosib turish yoki bir bosib yoqish) |
| **X** | Nishonga lunge qilib uchish, yaqinlashganda mace bilan urish (yerda bo'lsangiz wind charge bilan sakraydi). Yana bossangiz bekor bo'ladi |
| **O** | Umumiy menyu: Spear Lunger, Auto TNT, Anti-cheat |
| **B** | Kamon auto-TNT yoqish/o'chirish |
| **Z** | Arbalet auto-TNT (bir marta) |
| **V** | TNT profilini almashtirish |
| **P** | Auto TNT sozlamalari |

`/tntfriend add|remove|list <ism>`: do'stlar ro'yxati. Ularni X nishonga olmaydi, TNT esa ular yonida portlamaydi.

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

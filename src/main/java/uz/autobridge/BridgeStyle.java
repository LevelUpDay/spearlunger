package uz.autobridge;

/** Ko'prik qurish uslubi (ClickGUI'dagi "Turi" sozlamasi). */
public enum BridgeStyle {
    GOD("God Bridge", "Sprint bilan oldinga, pastga-oldinga qarab, har ~6 blokda sakraydi (klassik tez ko'prik)"),
    BREEZY("Breezily", "Yugurmasdan, osoyishta oldinga yurib - eng ishonchli va legit ko'rinadigan"),
    DOUBLE_GOD("Double God", "God Bridge, lekin har 2 blokda sakraydi - ko'proq masofani tezroq bosadi"),
    ANDROMEDA("Andromeda", "Kamera umuman qimirlamaydi (silent) - eng tezi, sprint uzilmaydi"),
    CLASSIC("Klassik", "Shift bosib, orqaga qarab (kamera burilib) orqaga-pastga yurib quradi"),
    NINJA("Ninja", "Klassik kabi, lekin har blokdan keyin shift'ni bir zumga qo'yib yuboradi"),
    SPEED("Speed", "Har qadamda sakrab-yugurib eng tez boradi (eng xavfli, ko'proq diqqat kerak)"),
    WITCHLY("Witchly", "Breezy kabi, lekin tasodifiy mikro-pauzalar bilan - odam qo'li kabi ko'rinadi"),
    JUMP("Jump Bridging", "Doim sakrab yuradi, tagida blok bo'lmasa havoda ham qo'yadi - kengroq bo'shliqlardan o'tadi");

    public final String title;
    public final String desc;

    BridgeStyle(String title, String desc) {
        this.title = title;
        this.desc = desc;
    }

    public static BridgeStyle of(int ordinal) {
        BridgeStyle[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }

    public static String[] titles() {
        BridgeStyle[] v = values();
        String[] out = new String[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i].title;
        return out;
    }
}

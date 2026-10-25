package uz.spearlunger.gui;

import uz.spearlunger.Config;

/** ClickGUI ranglari (Meteor uslubida to'q fon + bitta asosiy rang). */
public final class Theme {
    public static final String[] ACCENT_NAMES = {"Binafsha", "Ko'k", "Yashil", "Qizil", "To'q sariq", "Pushti"};
    private static final int[] ACCENTS = {0xFF913DE2, 0xFF3D8BE2, 0xFF3DD47A, 0xFFE23D4F, 0xFFE2903D, 0xFFE23DB8};

    public static final int PANEL_BG = 0xE0141418;
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_OFF = 0xFFA8A8B0;
    public static final int TEXT_DIM = 0xFF7C7C86;
    public static final int WIDGET_BG = 0xFF2A2A32;

    private Theme() {}

    public static int accent() {
        return ACCENTS[Math.floorMod(Config.get().guiAccent, ACCENTS.length)];
    }

    public static int accent(int alpha) {
        return (accent() & 0xFFFFFF) | (alpha << 24);
    }
}

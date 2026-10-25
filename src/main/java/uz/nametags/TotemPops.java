package uz.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** O'yinchilar nechta totem ishlatganini sanaydi (entity status 35 = totem). O'lsa nolga tushadi. */
public final class TotemPops {
    private static final byte TOTEM = 35;
    private static final byte DEATH = 3;
    private static final Map<UUID, Integer> POPS = new HashMap<>();

    private TotemPops() {}

    public static int get(UUID id) {
        return POPS.getOrDefault(id, 0);
    }

    public static void clear() {
        POPS.clear();
    }

    /** ClientPlayNetworkHandler.onEntityStatus mixin'idan (asosiy thread'da). */
    public static void onStatus(EntityStatusS2CPacket packet) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        Entity e = packet.getEntity(c.world);
        if (!(e instanceof PlayerEntity pl)) return;
        if (packet.getStatus() == TOTEM) {
            int n = POPS.merge(pl.getUuid(), 1, Integer::sum);
            if (NametagsConfig.get().popChat && c.player != null && pl != c.player) {
                c.player.sendMessage(Text.literal("§6" + pl.getName().getString() + " §7totem ishlatdi §f(" + n + ")"), false);
            }
        } else if (packet.getStatus() == DEATH) {
            POPS.remove(pl.getUuid());
        }
    }
}

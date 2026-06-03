package com.secretgif.network;

import com.secretgif.common.ActiveGifData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Пакет: клиент → сервер → все клиенты.
 * Сообщает, что игрок выбрал GIF-ку.
 */
public record ShowGifPacket(UUID playerUUID, String gifUrl, int repeatCount)
        implements CustomPacketPayload {

    public static final Type<ShowGifPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("secretgif", "show_gif"));

    public static final StreamCodec<FriendlyByteBuf, ShowGifPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.playerUUID());
                        buf.writeUtf(pkt.gifUrl());
                        buf.writeInt(pkt.repeatCount());
                    },
                    buf -> new ShowGifPacket(buf.readUUID(), buf.readUtf(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Обработка на СЕРВЕРЕ: пересылаем всем игрокам
    public static void handleServer(ShowGifPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;

            // Рассылаем всем игрокам на сервере
            var server = player.getServer();
            if (server == null) return;

            var broadcastPkt = new GifBroadcastPacket(
                    pkt.playerUUID(), pkt.gifUrl(), pkt.repeatCount()
            );

            server.getPlayerList().getPlayers().forEach(p ->
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, broadcastPkt)
            );
        });
    }
}

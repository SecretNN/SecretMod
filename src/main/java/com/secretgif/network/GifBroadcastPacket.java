package com.secretgif.network;

import com.secretgif.common.ActiveGifData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Пакет: сервер → все клиенты.
 * Говорит всем: "над игроком X теперь показывать GIF Y"
 */
public record GifBroadcastPacket(UUID playerUUID, String gifUrl, int repeatCount)
        implements CustomPacketPayload {

    public static final Type<GifBroadcastPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("secretgif", "gif_broadcast"));

    public static final StreamCodec<FriendlyByteBuf, GifBroadcastPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.playerUUID());
                        buf.writeUtf(pkt.gifUrl());
                        buf.writeInt(pkt.repeatCount());
                    },
                    buf -> new GifBroadcastPacket(buf.readUUID(), buf.readUtf(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Обработка на КЛИЕНТЕ: записываем в карту активных GIF
    public static void handleClient(GifBroadcastPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ActiveGifData.ACTIVE_GIFS.put(
                    pkt.playerUUID(),
                    new ActiveGifData.ActiveGif(pkt.gifUrl(), System.currentTimeMillis(), pkt.repeatCount())
            );
        });
    }
}

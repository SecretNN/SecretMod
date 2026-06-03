package com.secretgif.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "secretgif", bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {

    public static void register() {
        // регистрация через событие происходит ниже
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Клиент → Сервер
        registrar.playToServer(
                ShowGifPacket.TYPE,
                ShowGifPacket.CODEC,
                ShowGifPacket::handleServer
        );

        // Сервер → Клиент
        registrar.playToClient(
                GifBroadcastPacket.TYPE,
                GifBroadcastPacket.CODEC,
                GifBroadcastPacket::handleClient
        );
    }
}
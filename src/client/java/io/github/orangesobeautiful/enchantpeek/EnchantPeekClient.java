package io.github.orangesobeautiful.enchantpeek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class EnchantPeekClient implements ClientModInitializer {
    public static final String MOD_ID = "enchantpeek";

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(ServerPreviewPayload.TYPE, ServerPreviewPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ServerPreviewPayload.TYPE, (payload, context) -> {
            ServerPreviewStore.update(payload);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerPreviewStore.clear();
        });
    }
}

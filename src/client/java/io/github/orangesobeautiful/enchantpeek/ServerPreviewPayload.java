package io.github.orangesobeautiful.enchantpeek;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record ServerPreviewPayload(int containerId, ItemStack inputItem, List<OptionPreview> options)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerPreviewPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(EnchantPeekClient.MOD_ID, "preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerPreviewPayload> CODEC = StreamCodec.ofMember(
            ServerPreviewPayload::write,
            ServerPreviewPayload::read);
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_OPTION_COUNT = 3;
    private static final int MAX_ENCHANTMENT_COUNT = 256;
    private static final int MAX_STRING_BYTES = 1024;

    @Override
    public CustomPacketPayload.Type<ServerPreviewPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(PROTOCOL_VERSION);
        buf.writeInt(containerId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, inputItem);
        buf.writeInt(options.size());

        for (OptionPreview option : options) {
            buf.writeInt(option.cost());
            buf.writeInt(option.enchantClue());
            buf.writeInt(option.levelClue());
            buf.writeInt(option.enchantments().size());

            for (EnchantmentPreview enchantment : option.enchantments()) {
                writeString(buf, enchantment.id());
                buf.writeInt(enchantment.level());
                writeString(buf, enchantment.fallbackName());
                buf.writeBoolean(enchantment.highestTableLevel());
            }
        }
    }

    private static ServerPreviewPayload read(RegistryFriendlyByteBuf buf) {
        try {
            int version = buf.readInt();

            if (version != PROTOCOL_VERSION) {
                throw new InvalidPayloadException();
            }

            int containerId = buf.readInt();
            ItemStack inputItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int optionCount = readBoundedCount(buf, MAX_OPTION_COUNT);
            List<OptionPreview> options = new ArrayList<>(optionCount);

            for (int option = 0; option < optionCount; option++) {
                int cost = buf.readInt();
                int enchantClue = buf.readInt();
                int levelClue = buf.readInt();
                int enchantmentCount = readBoundedCount(buf, MAX_ENCHANTMENT_COUNT);
                List<EnchantmentPreview> enchantments = new ArrayList<>(enchantmentCount);

                for (int index = 0; index < enchantmentCount; index++) {
                    String id = readString(buf);
                    int level = buf.readInt();
                    String fallbackName = readString(buf);
                    boolean highestTableLevel = buf.readBoolean();
                    enchantments.add(new EnchantmentPreview(id, level, fallbackName, highestTableLevel));
                }

                options.add(new OptionPreview(cost, enchantClue, levelClue, enchantments));
            }

            return new ServerPreviewPayload(containerId, inputItem, options);
        } catch (IndexOutOfBoundsException | InvalidPayloadException exception) {
            buf.skipBytes(buf.readableBytes());
            return new ServerPreviewPayload(-1, ItemStack.EMPTY, List.of());
        }
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readInt();

        if (count < 0 || count > max) {
            throw new InvalidPayloadException();
        }

        return count;
    }

    private static String readString(RegistryFriendlyByteBuf buf) {
        int length = buf.readInt();

        if (length < 0 || length > MAX_STRING_BYTES || length > buf.readableBytes()) {
            throw new InvalidPayloadException();
        }

        if (length == 0) {
            return "";
        }

        return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void writeString(RegistryFriendlyByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public record OptionPreview(
            int cost,
            int enchantClue,
            int levelClue,
            List<EnchantmentPreview> enchantments) {
    }

    public record EnchantmentPreview(String id, int level, String fallbackName, boolean highestTableLevel) {
    }

    private static final class InvalidPayloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}

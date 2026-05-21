package io.github.orangesobeautiful.enchantpeek;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ServerPreviewStore {
    private static ServerPreviewPayload preview;

    private ServerPreviewStore() {
    }

    public static void update(ServerPreviewPayload payload) {
        preview = payload;
    }

    public static void clear() {
        preview = null;
    }

    public static List<Component> getTooltip(EnchantmentMenu menu, int option) {
        if (preview == null || preview.containerId() != menu.containerId || !matchesInputItem(menu) || option < 0
                || option >= preview.options().size()) {
            return List.of();
        }

        ServerPreviewPayload.OptionPreview optionPreview = preview.options().get(option);

        if (optionPreview.cost() != menu.costs[option] || optionPreview.enchantClue() != menu.enchantClue[option]
                || optionPreview.levelClue() != menu.levelClue[option] || optionPreview.enchantments().isEmpty()) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("EnchantPeek Server"));

        RegistryAccess registryAccess = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess()
                : null;

        for (ServerPreviewPayload.EnchantmentPreview enchantment : optionPreview.enchantments()) {
            lines.add(addHighestTableLevelMark(getLocalizedName(registryAccess, enchantment), enchantment));
        }

        return lines;
    }

    private static boolean matchesInputItem(EnchantmentMenu menu) {
        ItemStack stack = menu.getSlot(0).getItem();

        return ItemStack.isSameItemSameComponents(preview.inputItem(), stack)
                && preview.inputItem().getCount() == stack.getCount();
    }

    private static Component getLocalizedName(RegistryAccess registryAccess,
            ServerPreviewPayload.EnchantmentPreview enchantment) {
        if (registryAccess != null && enchantment.level() > 0) {
            Identifier id = Identifier.tryParse(enchantment.id());

            if (id != null) {
                return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(id)
                        .map(holder -> Enchantment.getFullname(holder, enchantment.level()))
                        .orElseGet(() -> Component.literal(enchantment.fallbackName()));
            }
        }

        return Component.literal(enchantment.fallbackName());
    }

    private static Component addHighestTableLevelMark(
            Component name,
            ServerPreviewPayload.EnchantmentPreview enchantment) {
        if (!enchantment.highestTableLevel()) {
            return name;
        }

        return name.copy().append(Component.literal(" \u2605").withStyle(ChatFormatting.GOLD));
    }
}

package io.github.orangesobeautiful.enchantpeek.server;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public final class EnchantPeekServerPlugin extends JavaPlugin implements Listener {
    private static final String CHANNEL = "enchantpeek:preview";
    private static final int PROTOCOL_VERSION = 1;
    private static final int OPTION_COUNT = 3;

    private final Map<UUID, String> lastInsertedItems = new HashMap<>();
    private Field enchantSlotsField;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        try {
            enchantSlotsField = EnchantmentMenu.class.getDeclaredField("enchantSlots");
            enchantSlotsField.setAccessible(true);
        }
        catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not access enchantment internals. Refresh-on-insert will be disabled.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

        if (!(serverPlayer.containerMenu instanceof EnchantmentMenu menu)) {
            return;
        }

        if (getConfig().getBoolean("refresh-on-item-insert", false)) {
            boolean refreshScheduled = scheduleSeedRefreshOnNewItem(player, menu.containerId, menu.getSlot(0).getItem());

            if (refreshScheduled) {
                return;
            }
        }

        if (getConfig().getBoolean("send-preview", true)) {
            schedulePreview(player, menu.containerId);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            lastInsertedItems.remove(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isEnchantingView(event.getView().getTopInventory())) {
            updateInputSlotStateNextTick(player, event.getView().getTopInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isEnchantingView(event.getView().getTopInventory())) {
            updateInputSlotStateNextTick(player, event.getView().getTopInventory());
        }
    }

    private boolean scheduleSeedRefreshOnNewItem(
            Player player,
            int containerId,
            net.minecraft.world.item.ItemStack itemStack) {
        if (enchantSlotsField == null) {
            return false;
        }

        String itemKey = getItemKey(itemStack);
        String previousKey = lastInsertedItems.put(player.getUniqueId(), itemKey);

        if (itemKey.equals(previousKey)) {
            return false;
        }

        getServer().getScheduler().runTask(this, () -> refreshSeed(player, containerId, itemKey));

        return true;
    }

    private void refreshSeed(Player player, int containerId, String itemKey) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

        if (!(serverPlayer.containerMenu instanceof EnchantmentMenu menu) || menu.containerId != containerId
                || !itemKey.equals(getItemKey(menu.getSlot(0).getItem()))) {
            return;
        }

        try {
            int newSeed = serverPlayer.getRandom().nextInt();
            serverPlayer.enchantmentSeed = newSeed;
            menu.setEnchantmentSeed(newSeed);
            menu.slotsChanged((Container) enchantSlotsField.get(menu));
        }
        catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not refresh enchantment seed for " + player.getName() + ".");
        }
    }

    private static String getItemKey(net.minecraft.world.item.ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        return itemId + ":" + stack.getCount() + ":" + stack.getComponentsPatch();
    }

    private void updateInputSlotStateNextTick(Player player, Inventory inventory) {
        getServer().getScheduler().runTask(this, () -> {
            if (isEnchantingView(inventory) && isEmpty(inventory.getItem(0))) {
                lastInsertedItems.remove(player.getUniqueId());
            }
        });
    }

    private static boolean isEnchantingView(Inventory inventory) {
        return inventory.getType() == InventoryType.ENCHANTING;
    }

    private static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty();
    }

    private void schedulePreview(Player player, int containerId) {
        getServer().getScheduler().runTask(this, () -> {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

            if (serverPlayer.containerMenu instanceof EnchantmentMenu menu && menu.containerId == containerId) {
                sendPreview(player, serverPlayer, menu);
            }
        });
    }

    private void sendPreview(Player player, ServerPlayer serverPlayer, EnchantmentMenu menu) {
        try {
            byte[] message = encodePreview(serverPlayer, menu);
            player.sendPluginMessage(this, CHANNEL, message);
        }
        catch (RuntimeException exception) {
            getLogger().warning("Could not encode enchantment preview for " + player.getName() + ".");
        }
    }

    private byte[] encodePreview(ServerPlayer player, EnchantmentMenu menu) {
        RegistryAccess registryAccess = player.level().registryAccess();
        RegistryFriendlyByteBuf output = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        net.minecraft.world.item.ItemStack stack = menu.getSlot(0).getItem();

        output.writeInt(PROTOCOL_VERSION);
        output.writeInt(menu.containerId);
        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(output, stack);
        output.writeInt(OPTION_COUNT);

        for (int option = 0; option < OPTION_COUNT; option++) {
            output.writeInt(menu.costs[option]);
            output.writeInt(menu.enchantClue[option]);
            output.writeInt(menu.levelClue[option]);

            List<PreviewEnchantment> enchantments = menu.costs[option] > 0
                    ? getEnchantments(registryAccess, stack, option, menu.costs[option], menu.getEnchantmentSeed())
                    : List.of();

            output.writeInt(enchantments.size());

            for (PreviewEnchantment enchantment : enchantments) {
                writeString(output, enchantment.id());
                output.writeInt(enchantment.level());
                writeString(output, enchantment.fallbackName());
                output.writeBoolean(enchantment.highestTableLevel());
            }
        }

        byte[] message = new byte[output.readableBytes()];
        output.readBytes(message);

        return message;
    }

    private List<PreviewEnchantment> getEnchantments(
            RegistryAccess registryAccess,
            net.minecraft.world.item.ItemStack stack,
            int option,
            int cost,
            int seed) {
        RandomSource random = RandomSource.create();
        random.setSeed((long) seed + option);

        List<EnchantmentInstance> enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(tag -> {
                    List<EnchantmentInstance> selected = EnchantmentHelper.selectEnchantment(
                            random,
                            stack,
                            cost,
                            tag.stream());

                    if (stack.is(Items.BOOK) && selected.size() > 1) {
                        selected.remove(random.nextInt(selected.size()));
                    }

                    return selected;
                })
                .orElse(List.of());
        List<PreviewEnchantment> previews = new ArrayList<>(enchantments.size());

        for (EnchantmentInstance enchantment : enchantments) {
            Component component = Enchantment.getFullname(enchantment.enchantment(), enchantment.level());
            String id = enchantment.enchantment()
                    .unwrapKey()
                    .map(key -> key.identifier())
                    .map(Identifier::toString)
                    .orElse("");
            previews.add(new PreviewEnchantment(
                    id,
                    enchantment.level(),
                    component.getString(),
                    isHighestTableLevel(registryAccess, stack, enchantment, cost)));
        }

        return previews;
    }

    private static boolean isHighestTableLevel(
            RegistryAccess registryAccess,
            net.minecraft.world.item.ItemStack stack,
            EnchantmentInstance enchantment,
            int cost) {
        int minPower = getMinimumPossiblePower(stack, cost);
        int maxPower = getMaximumPossiblePower(stack, cost);

        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(enchantments -> {
                    for (int power = minPower; power <= maxPower; power++) {
                        boolean hasHigherLevel = EnchantmentHelper.getAvailableEnchantmentResults(
                                power,
                                stack,
                                enchantments.stream()).stream()
                                .anyMatch(candidate -> candidate.enchantment().is(enchantment.enchantment())
                                        && candidate.level() > enchantment.level());

                        if (hasHigherLevel) {
                            return false;
                        }
                    }

                    return true;
                })
                .orElse(true);
    }

    private static int getMinimumPossiblePower(net.minecraft.world.item.ItemStack stack, int cost) {
        return Math.max(1, Math.round(cost * 0.85F));
    }

    private static int getMaximumPossiblePower(net.minecraft.world.item.ItemStack stack, int cost) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        int enchantabilityBonus = enchantable != null ? enchantable.value() / 4 : 0;

        return Math.max(getMinimumPossiblePower(stack, cost), Math.round((cost + enchantabilityBonus * 2) * 1.15F));
    }

    private static void writeString(RegistryFriendlyByteBuf output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.writeBytes(bytes);
    }

    private record PreviewEnchantment(String id, int level, String fallbackName, boolean highestTableLevel) {
    }
}

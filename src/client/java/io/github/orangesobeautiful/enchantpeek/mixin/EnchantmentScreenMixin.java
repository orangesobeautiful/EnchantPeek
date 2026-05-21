package io.github.orangesobeautiful.enchantpeek.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import io.github.orangesobeautiful.enchantpeek.EnchantmentPreviewData.InferenceResult;
import io.github.orangesobeautiful.enchantpeek.EnchantmentPreviewData.Observation;
import io.github.orangesobeautiful.enchantpeek.EnchantmentPreviewData.Offer;
import io.github.orangesobeautiful.enchantpeek.EnchantmentPreviewData.Prediction;
import io.github.orangesobeautiful.enchantpeek.ServerPreviewStore;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    private static final int OPTION_COUNT = 3;
    private static final int OPTION_X = 60;
    private static final int OPTION_Y = 14;
    private static final int OPTION_WIDTH = 108;
    private static final int OPTION_HEIGHT = 19;

    private int enchantpeek$mouseX;
    private int enchantpeek$mouseY;
    private int enchantpeek$seedLow = Integer.MIN_VALUE;
    private final List<Observation> enchantpeek$observations = new ArrayList<>();
    private InferenceResult enchantpeek$inferenceResult = InferenceResult.empty();

    private EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void enchantpeek$captureMouse(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        enchantpeek$mouseX = mouseX;
        enchantpeek$mouseY = mouseY;
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean enchantpeek$replaceClueTooltip(List<Component> lines, Object originalLine) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> preview = getHoveredPreviewTooltip(minecraft);

        if (preview == null) {
            return lines.add((Component) originalLine);
        }

        return lines.addAll(preview);
    }

    @Nullable
    private List<Component> getHoveredPreviewTooltip(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }

        for (int option = 0; option < OPTION_COUNT; option++) {
            if (isHovering(OPTION_X, OPTION_Y + option * OPTION_HEIGHT, OPTION_WIDTH, 17, enchantpeek$mouseX,
                    enchantpeek$mouseY)) {
                return getPreviewTooltip(minecraft, option);
            }
        }

        return null;
    }

    @Nullable
    private List<Component> getPreviewTooltip(Minecraft minecraft, int option) {
        List<Component> serverTooltip = ServerPreviewStore.getTooltip(menu, option);

        if (!serverTooltip.isEmpty()) {
            return serverTooltip;
        }

        ItemStack stack = menu.getSlot(0).getItem();
        int cost = menu.costs[option];

        if (minecraft.level == null || stack.isEmpty() || cost <= 0 || menu.enchantClue[option] < 0
                || menu.levelClue[option] < 0) {
            return null;
        }

        RegistryAccess registryAccess = minecraft.level.registryAccess();
        Prediction prediction = getPrediction(minecraft, registryAccess, stack);

        if (!prediction.hasSeed()) {
            return getInferenceTooltip(prediction);
        }

        List<EnchantmentInstance> enchantments = getEnchantmentList(
                registryAccess,
                stack,
                option,
                cost,
                prediction.seed());

        if (enchantments.isEmpty()) {
            return null;
        }

        Component clue = getClue(registryAccess, option);
        boolean matchesClue = clueMatches(registryAccess, option, prediction.seed(), stack, cost);

        return getFullTooltip(registryAccess, stack, enchantments, cost, clue, matchesClue);
    }

    private Prediction getPrediction(Minecraft minecraft, RegistryAccess registryAccess, ItemStack stack) {
        if (minecraft.hasSingleplayerServer() && minecraft.player != null) {
            ServerPlayer serverPlayer = minecraft.getSingleplayerServer()
                    .getPlayerList()
                    .getPlayer(minecraft.player.getUUID());

            if (serverPlayer != null) {
                return Prediction.exact(serverPlayer.getEnchantmentSeed(), 1);
            }
        }

        updateInference(registryAccess, stack);

        if (enchantpeek$inferenceResult.candidateCount() == 1) {
            return Prediction.exact(enchantpeek$inferenceResult.seed(), 1);
        }

        return Prediction.inferred(enchantpeek$inferenceResult.candidateCount());
    }

    private void updateInference(RegistryAccess registryAccess, ItemStack stack) {
        int seedLow = menu.getEnchantmentSeed() & 0xFFFF;

        if (seedLow != enchantpeek$seedLow) {
            enchantpeek$seedLow = seedLow;
            enchantpeek$observations.clear();
            enchantpeek$inferenceResult = InferenceResult.empty();
        }

        Observation observation = Observation.from(menu, stack);

        if (observation == null || enchantpeek$observations.stream().anyMatch(observation::sameAs)) {
            return;
        }

        enchantpeek$observations.add(observation);
        enchantpeek$inferenceResult = inferSeed(registryAccess, seedLow);
    }

    private InferenceResult inferSeed(RegistryAccess registryAccess, int seedLow) {
        int count = 0;
        int onlySeed = 0;

        for (int high = 0; high <= 0xFFFF; high++) {
            int seed = (high << 16) | seedLow;

            if (matchesObservations(registryAccess, seed)) {
                count++;
                onlySeed = seed;
            }
        }

        return count == 1 ? InferenceResult.exact(onlySeed) : InferenceResult.multiple(count);
    }

    private boolean matchesObservations(RegistryAccess registryAccess, int seed) {
        for (Observation observation : enchantpeek$observations) {
            for (int option = 0; option < OPTION_COUNT; option++) {
                int cost = observation.costs()[option];

                if (cost <= 0) {
                    continue;
                }

                Offer offer = getOffer(registryAccess, observation.stack(), option, cost, seed);

                if (offer == null || offer.enchantmentId() != observation.enchantClues()[option]
                        || offer.level() != observation.levelClues()[option]) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<EnchantmentInstance> getEnchantmentList(
            RegistryAccess registryAccess,
            ItemStack stack,
            int option,
            int cost,
            int seed) {
        RandomSource random = RandomSource.create();
        random.setSeed((long) seed + option);

        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(enchantments -> {
                    List<EnchantmentInstance> selected = EnchantmentHelper.selectEnchantment(
                            random,
                            stack,
                            cost,
                            enchantments.stream());

                    if (stack.is(Items.BOOK) && selected.size() > 1) {
                        selected.remove(random.nextInt(selected.size()));
                    }

                    return selected;
                })
                .orElse(List.of());
    }

    @Nullable
    private Offer getOffer(RegistryAccess registryAccess, ItemStack stack, int option, int cost, int seed) {
        RandomSource random = RandomSource.create();
        random.setSeed((long) seed + option);

        List<EnchantmentInstance> enchantments = getEnchantmentList(registryAccess, stack, option, cost, random);

        if (enchantments.isEmpty()) {
            return null;
        }

        EnchantmentInstance clue = enchantments.get(random.nextInt(enchantments.size()));
        int clueId = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .asHolderIdMap()
                .getId(clue.enchantment());

        return new Offer(enchantments, clueId, clue.level());
    }

    private List<EnchantmentInstance> getEnchantmentList(
            RegistryAccess registryAccess,
            ItemStack stack,
            int option,
            int cost,
            RandomSource random) {
        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(enchantments -> {
                    List<EnchantmentInstance> selected = EnchantmentHelper.selectEnchantment(
                            random,
                            stack,
                            cost,
                            enchantments.stream());

                    if (stack.is(Items.BOOK) && selected.size() > 1) {
                        selected.remove(random.nextInt(selected.size()));
                    }

                    return selected;
                })
                .orElse(List.of());
    }

    private Component getClue(RegistryAccess registryAccess, int option) {
        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
                .get(menu.enchantClue[option])
                .map(enchantment -> Enchantment.getFullname(enchantment, menu.levelClue[option]))
                .orElse(Component.literal("?"));
    }

    private boolean clueMatches(RegistryAccess registryAccess, int option, int seed, ItemStack stack, int cost) {
        Offer offer = getOffer(registryAccess, stack, option, cost, seed);

        return offer != null
                && offer.enchantmentId() == menu.enchantClue[option]
                && offer.level() == menu.levelClue[option];
    }

    private List<Component> getFullTooltip(
            RegistryAccess registryAccess,
            ItemStack stack,
            List<EnchantmentInstance> enchantments,
            int cost,
            Component clue,
            boolean matchesClue) {
        List<Component> lines = enchantments.stream()
                .map(entry -> getEnchantmentName(registryAccess, stack, entry, cost))
                .collect(Collectors.toList());

        lines.add(0, Component.literal("EnchantPeek").withStyle(ChatFormatting.GRAY));

        if (!matchesClue) {
            lines.add(Component.literal(""));
            lines.add(Component.translatable("container.enchant.clue", clue).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.literal("Prediction does not match the synced clue.").withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    private static Component getEnchantmentName(
            RegistryAccess registryAccess,
            ItemStack stack,
            EnchantmentInstance enchantment,
            int cost) {
        Component name = Enchantment.getFullname(enchantment.enchantment(), enchantment.level())
                .copy()
                .withStyle(ChatFormatting.WHITE);

        if (isHighestTableLevel(registryAccess, stack, enchantment, cost)) {
            return name.copy().append(Component.literal(" \u2605").withStyle(ChatFormatting.GOLD));
        }

        return name;
    }

    private static boolean isHighestTableLevel(
            RegistryAccess registryAccess,
            ItemStack stack,
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

    private static int getMinimumPossiblePower(ItemStack stack, int cost) {
        return Math.max(1, Math.round(cost * 0.85F));
    }

    private static int getMaximumPossiblePower(ItemStack stack, int cost) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        int enchantabilityBonus = enchantable != null ? enchantable.value() / 4 : 0;

        return Math.max(getMinimumPossiblePower(stack, cost), Math.round((cost + enchantabilityBonus * 2) * 1.15F));
    }

    private List<Component> getInferenceTooltip(Prediction prediction) {
        return List.of(
                Component.literal("EnchantPeek").withStyle(ChatFormatting.GRAY),
                Component.literal("Learning server enchantment seed...").withStyle(ChatFormatting.YELLOW),
                Component.literal(prediction.candidateCount() + " candidates remain.").withStyle(ChatFormatting.GRAY),
                Component.literal("Try another enchantable item.").withStyle(ChatFormatting.GRAY));
    }
}

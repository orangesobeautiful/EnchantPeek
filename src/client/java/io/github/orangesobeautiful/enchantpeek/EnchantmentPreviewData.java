package io.github.orangesobeautiful.enchantpeek;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public final class EnchantmentPreviewData {
    private EnchantmentPreviewData() {
    }

    public record Offer(List<EnchantmentInstance> enchantments, int enchantmentId, int level) {
    }

    public record Prediction(boolean hasSeed, int seed, int candidateCount) {
        public static Prediction exact(int seed, int candidateCount) {
            return new Prediction(true, seed, candidateCount);
        }

        public static Prediction inferred(int candidateCount) {
            return new Prediction(false, 0, candidateCount);
        }
    }

    public record InferenceResult(int candidateCount, int seed) {
        public static InferenceResult empty() {
            return new InferenceResult(0, 0);
        }

        public static InferenceResult exact(int seed) {
            return new InferenceResult(1, seed);
        }

        public static InferenceResult multiple(int count) {
            return new InferenceResult(count, 0);
        }
    }

    public record Observation(ItemStack stack, int[] costs, int[] enchantClues, int[] levelClues) {
        private static final int OPTION_COUNT = 3;

        @Nullable
        public static Observation from(EnchantmentMenu menu, ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }

            int[] costs = menu.costs.clone();
            int[] enchantClues = menu.enchantClue.clone();
            int[] levelClues = menu.levelClue.clone();

            for (int option = 0; option < OPTION_COUNT; option++) {
                if (costs[option] > 0 && (enchantClues[option] < 0 || levelClues[option] < 0)) {
                    return null;
                }
            }

            return new Observation(stack.copy(), costs, enchantClues, levelClues);
        }

        public boolean sameAs(Observation other) {
            return ItemStack.matches(stack, other.stack())
                    && Arrays.equals(costs, other.costs)
                    && Arrays.equals(enchantClues, other.enchantClues)
                    && Arrays.equals(levelClues, other.levelClues);
        }
    }
}

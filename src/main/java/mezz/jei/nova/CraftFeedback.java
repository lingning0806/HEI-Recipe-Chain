package mezz.jei.nova;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.transfer.RecipeTransferErrorSlots;
import mezz.jei.transfer.RecipeTransferErrorTooltip;
import net.minecraft.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.Set;

/** Converts transfer errors into actionable messages; slot reports do not imply exact deficits. */
public final class CraftFeedback {
    private CraftFeedback() { }
    public static String describe(IRecipeTransferError error, IRecipeLayout layout) {
        CraftDiagnostics.log("transfer check failed: " + error.getType() + " / " + error.getClass().getName());
        String reason = error instanceof RecipeTransferErrorTooltip ? ((RecipeTransferErrorTooltip) error).getReason() : "";
        if (error instanceof RecipeTransferErrorSlots) {
            Set<String> names = new LinkedHashSet<>();
            for (Integer slot : ((RecipeTransferErrorSlots) error).getMissingSlots()) {
                IGuiIngredient<ItemStack> input = layout.getItemStacks().getGuiIngredients().get(slot);
                if (input == null || !input.isInput()) continue;
                ItemStack stack = input.getDisplayedIngredient();
                if (stack == null || stack.isEmpty()) continue;
                names.add(stack.getDisplayName() + (input.getAllIngredients().size() > 1 ? "（或配方允许的替代材料）" : ""));
            }
            String materials = names.stream().limit(3).collect(java.util.stream.Collectors.joining("、"));
            return (reason.isEmpty() ? "所需材料不足或不匹配" : reason) +
                    (materials.isEmpty() ? "。" : "。需检查：" + materials + (names.size() > 3 ? "等" : "") + "。") +
                    "请按 R 查看配方，将所需材料放入背包后重试。";
        }
        if (!reason.isEmpty()) return reason + "。请处理后重试。";
        if (error.getType() == IRecipeTransferError.Type.INTERNAL)
            return "此配方暂时无法快速合成，请使用配方界面手动合成。";
        return "当前配方无法填入，请检查材料、背包空间及合成格大小。";
    }
}

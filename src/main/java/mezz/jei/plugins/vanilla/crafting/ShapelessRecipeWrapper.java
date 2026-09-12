package mezz.jei.plugins.vanilla.crafting;

import javax.annotation.Nullable;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IStackHelper;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import mezz.jei.recipes.BrokenCraftingRecipeException;
import mezz.jei.util.ErrorUtil;

public class ShapelessRecipeWrapper<T extends IRecipe> implements ICraftingRecipeWrapper, mezz.jei.nova.RecipeInputUsageProvider {
	private final IJeiHelpers jeiHelpers;
	protected final T recipe;

	public ShapelessRecipeWrapper(IJeiHelpers jeiHelpers, T recipe) {
		this.jeiHelpers = jeiHelpers;
		this.recipe = recipe;
	}

	@Override
	public void getIngredients(IIngredients ingredients) {
		ItemStack recipeOutput = recipe.getRecipeOutput();
		IStackHelper stackHelper = jeiHelpers.getStackHelper();

		try {
			List<List<ItemStack>> inputLists = stackHelper.expandRecipeItemStackInputs(recipe.getIngredients());
			ingredients.setInputLists(VanillaTypes.ITEM, inputLists);
			ingredients.setOutput(VanillaTypes.ITEM, recipeOutput);
		} catch (RuntimeException e) {
			String info = ErrorUtil.getInfoFromBrokenCraftingRecipe(recipe, recipe.getIngredients(), recipeOutput);
			throw new BrokenCraftingRecipeException(info, e);
		}
	}

    @Override
    public mezz.jei.nova.InputUsage getInputUsage(List<?> alternatives) {
        // Only known standard recipes use Forge's item container convention unchanged.
        Class<?> type = recipe.getClass();
        if (type != net.minecraft.item.crafting.ShapedRecipes.class &&
            type != net.minecraft.item.crafting.ShapelessRecipes.class &&
            type != net.minecraftforge.oredict.ShapedOreRecipe.class &&
            type != net.minecraftforge.oredict.ShapelessOreRecipe.class) return mezz.jei.nova.InputUsage.CONSUMED;
        mezz.jei.nova.InputUsage result = null;
        for (Object alternative : alternatives) {
            if (!(alternative instanceof ItemStack)) return mezz.jei.nova.InputUsage.CONSUMED;
            ItemStack stack = ((ItemStack) alternative).copy(); stack.setCount(1);
            mezz.jei.nova.InputUsage usage = mezz.jei.nova.InputUsage.CONSUMED;
            if (stack.getItem().hasContainerItem(stack)) {
                ItemStack remaining = stack.getItem().getContainerItem(stack.copy());
                if (!remaining.isEmpty()) {
                    long uses = 0;
                    if (ItemStack.areItemsEqual(stack, remaining) && ItemStack.areItemStackTagsEqual(stack, remaining)) uses = Long.MAX_VALUE;
                    else if (stack.getItem() == remaining.getItem() && stack.isItemStackDamageable() &&
                        ItemStack.areItemStackTagsEqual(stack, remaining) && remaining.getItemDamage() > stack.getItemDamage()) {
                        // Count the final use that breaks the tool; keep separate simultaneous slots.
                        uses = 1L + (stack.getMaxDamage() - stack.getItemDamage()) /
                            (remaining.getItemDamage() - stack.getItemDamage());
                    }
                    usage = new mezz.jei.nova.InputUsage(uses, remaining.copy());
                }
            }
            if (result != null && (result.uses != usage.uses || !sameRemainder(result.remainder, usage.remainder)))
                return mezz.jei.nova.InputUsage.CONSUMED;
            result = usage;
        }
        return result == null ? mezz.jei.nova.InputUsage.CONSUMED : result;
    }

    private static boolean sameRemainder(Object a, Object b) {
        return a == b || (a instanceof ItemStack && b instanceof ItemStack && ItemStack.areItemStacksEqual((ItemStack)a, (ItemStack)b));
    }

	@Nullable
	@Override
	public ResourceLocation getRegistryName() {
		return recipe.getRegistryName();
	}
}

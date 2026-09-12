package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.CountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.util.ITooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("rawtypes")
public class BookmarkItemRender implements IIngredientRenderer<BookmarkItem> {
    @Override
    public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable BookmarkItem ingredient) {
        if (ingredient != null) {
            IngredientRegistry registry = Internal.getIngredientRegistry();
            IIngredientType<Object> ingredientType = registry.getIngredientType(ingredient.ingredient);
            registry.getIngredientRenderer(ingredientType).render(minecraft, xPosition, yPosition, ingredient.ingredient);

            if(ingredient.getGroup() instanceof mezz.jei.autocrafting.RecipeBookmarkGroup && ((mezz.jei.autocrafting.RecipeBookmarkGroup)ingredient.getGroup()).viewMode!=0) {
                int color=ingredient instanceof RecipeBookmarkItem ? 0xFFFFCD63 : (((mezz.jei.autocrafting.RecipeBookmarkGroup)ingredient.getGroup()).viewMode==1?0xFF86DCE8:0xFFFF8585);
                net.minecraft.client.gui.Gui.drawRect(xPosition,yPosition+16,xPosition+16,yPosition+17,color);
            }
            FontRenderer fontRenderer = getFontRenderer(minecraft, ingredient);
            if (ingredient instanceof RecipeBookmarkItem || ingredient.getDisplayAmount() > 1L) {
                CountUtil.renderCountString(fontRenderer, ingredient.getDisplayAmount(), xPosition, yPosition, true, true);
            }
        }
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, ITooltipFlag tooltipFlag) {
        if (ingredient instanceof InfusionBookmarkItem) return ((InfusionBookmarkItem)ingredient).description();
        List<String> tooltip = new java.util.ArrayList<>(getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient, tooltipFlag));
        if (ingredient instanceof RecipeBookmarkItem) {
            RecipeBookmarkItem<?> recipe = (RecipeBookmarkItem<?>) ingredient;
            if(ingredient.getGroup() instanceof mezz.jei.autocrafting.RecipeBookmarkGroup && ((mezz.jei.autocrafting.RecipeBookmarkGroup)ingredient.getGroup()).viewMode!=0)
                tooltip.add("目标（金色标线）：原目标 "+recipe.selfOutputAmount+"；本次剩余 "+((mezz.jei.autocrafting.RecipeBookmarkGroup)ingredient.getGroup()).remainingGoal(recipe));
            tooltip.add("需求：" + recipe.amount + "；合成次数：" + recipe.getMultiplier());
            tooltip.add("每次产出：" + recipe.outputAmount + "；合计产出：" + recipe.getDisplayAmount());
        }
        if (ingredient instanceof DummyBookmarkItem && ingredient.getGroup() instanceof mezz.jei.autocrafting.RecipeBookmarkGroup) {
            int mode=((mezz.jei.autocrafting.RecipeBookmarkGroup)ingredient.getGroup()).viewMode;
            tooltip.add((mode==1?"本组启动需备（含可复用启动材料）：":mode==2?"本次剩余目标缺口：":"本配方用量：") + ingredient.getDisplayAmount());
            if (ingredient instanceof RecipeInputBookmarkItem) {
                tooltip.addAll(((RecipeInputBookmarkItem) ingredient).getRequirementDescription());
            }
        }
        return tooltip;
    }

    @Override
    public FontRenderer getFontRenderer(Minecraft minecraft, BookmarkItem ingredient) {
        return getIngredientRenderer(ingredient.ingredient).getFontRenderer(minecraft, ingredient.ingredient);
    }

    private static <E> IIngredientRenderer<E> getIngredientRenderer(E ingredient) {
        return Internal.getIngredientRegistry().getIngredientRenderer(ingredient);
    }
}

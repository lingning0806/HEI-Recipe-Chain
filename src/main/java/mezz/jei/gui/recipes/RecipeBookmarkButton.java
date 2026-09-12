package mezz.jei.gui.recipes;

import mezz.jei.Internal;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;

public class RecipeBookmarkButton extends GuiIconButtonSmall {
    private final IRecipeCategory<?> category;
    private final IRecipeWrapper recipe;
    private RecipeLayout recipeLayout;

    public RecipeBookmarkButton(int id, int width, int height, IDrawable icon, IRecipeCategory<?> category, IRecipeWrapper recipe, RecipeLayout recipeLayout) {
        super(id, 0, 0, width, height, icon);
        this.category = category;
        this.recipe = recipe;
        this.recipeLayout = recipeLayout;
    }

    public void openPlanner(net.minecraft.client.gui.GuiScreen parent) {
        if (!recipeLayout.getRecipeFavoriteButton().enabled) return;
        mezz.jei.gui.ingredients.GuiIngredient<?> hovered = recipeLayout.getGuiIngredientUnderMouse(mezz.jei.input.MouseHelper.getX(), mezz.jei.input.MouseHelper.getY());
        Object target = hovered != null && !hovered.isInput() ? hovered.getDisplayedIngredient() : recipeLayout.getRecipeFavoriteButton().getDisplayedIngredient();
        if (target == null) return;
        if (Internal.getRuntime().getAutocraftingHandler().isActive()) {
            mezz.jei.nova.CraftDiagnostics.notice("请等待当前合成结束后再建立配方树");
            return;
        }
        try {
            BookmarkList bookmarks = Internal.getBookmarkList();
            RecipeBookmarkGroup group = mezz.jei.nova.RecipeTreeBuilder.build(bookmarks.nextId(), target, recipe, category);
            bookmarks.add(group);
            if (!Config.isBookmarkOverlayEnabled()) Config.toggleBookmarkEnabled();
            mezz.jei.nova.CraftDiagnostics.notice("已建立配方组 · Ctrl+Z 撤销；未确定的材料配方可指定收藏后重新建树");
        } catch (RuntimeException ex) {
            mezz.jei.util.Log.get().warn("Recipe tree creation rejected", ex);
            mezz.jei.nova.CraftDiagnostics.notice("无法建立配方树：请检查循环依赖或配方数据，原有书签已保留");
        }
    }

    public void bookmarkSingle() {
        if (!recipeLayout.getRecipeFavoriteButton().enabled) return;
        if (Internal.getRuntime().getAutocraftingHandler().isActive()) return;
        Object target = recipeLayout.getRecipeFavoriteButton().getDisplayedIngredient();
        if (target == null) return;
        try {
            BookmarkList bookmarks = Internal.getBookmarkList();
            RecipeBookmarkGroup group = new RecipeBookmarkGroup(bookmarks.nextId());
            RecipeBookmarkItem<?> item = new RecipeBookmarkItem<>(target);
            item.setGroup(group);
            item.populateWith(recipe, category);
            if (!item.isPopulated() || item.outputAmount <= 0) return;
            item.selfOutputAmount = item.outputAmount;
            group.addItemInternal(item);
            group.finishLoading();
            bookmarks.add(group);
            if (!Config.isBookmarkOverlayEnabled()) Config.toggleBookmarkEnabled();
            mezz.jei.nova.CraftDiagnostics.notice("已收藏当前配方 · Ctrl+Z 撤销");
        } catch (RuntimeException ex) {
            mezz.jei.util.Log.get().warn("Single recipe bookmark rejected", ex);
            mezz.jei.nova.CraftDiagnostics.notice("无法收藏此配方，原有书签已保留");
        }
    }

    public void init(RecipeLayout recipeLayout) {
        this.recipeLayout = recipeLayout;
        // Propagates the state of the favorite button if there are no outputs to the recipe.
        this.enabled = this.visible = recipeLayout.getRecipeFavoriteButton().enabled;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        visible = enabled && Config.areRecipeBookmarksEnabled();
        super.drawButton(mc, mouseX, mouseY, partialTicks);
    }

    public void drawToolTip(Minecraft mc, int mouseX, int mouseY) {
        if (hovered && visible) {
            String tooltipTransfer = Translator.translateToLocal("hei.tooltip.recipe_bookmark");
            TooltipRenderer.drawHoveringText(mc, tooltipTransfer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) {
            return false;
        }
        if (!Config.isBookmarkOverlayEnabled()) {
            Config.toggleBookmarkEnabled();
        }
        BookmarkList bookmarkList = Internal.getBookmarkList();
        RecipeBookmarkGroup group = new RecipeBookmarkGroup(bookmarkList.nextId());
        RecipeBookmarkItem<?> recipeBookmarkItem = new RecipeBookmarkItem<>(recipeLayout.getRecipeFavoriteButton().getDisplayedIngredient());
        recipeBookmarkItem.setGroup(group); // Do this early so that the dummy items are also added.
        recipeBookmarkItem.populateWith(recipe, category);
        try {
            group.addItem(recipeBookmarkItem);
            group.update();
            return bookmarkList.add(group);
        } catch (RuntimeException ex) {
            mezz.jei.util.Log.get().warn("Recipe bookmark creation rejected", ex);
            mezz.jei.nova.CraftDiagnostics.notice("无法建立配方链：" + ex.getMessage());
            return true;
        }
    }
}

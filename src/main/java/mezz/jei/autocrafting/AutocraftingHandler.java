package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeCraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.recipes.RecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.Stack;

public class AutocraftingHandler implements IAutocraftingHandler {
    @Nullable
    private RecipeChain currentChain;
    @Nullable
    private RecipeBookmarkItem<?> currentRequester;
    private Stack<RecipeBookmarkItem<?>> recipesToAutocraft;

    private IRecipeLayout cachedLayout;
    private Object cachedRecipe;
    private Object cachedCategory;
    private int totalSteps;
    private int completedSteps;

    private String stepLabel() {
        String name = currentRequester != null && currentRequester.ingredient instanceof net.minecraft.item.ItemStack
                ? ((net.minecraft.item.ItemStack) currentRequester.ingredient).getDisplayName() : "当前配方";
        return "合成已停止（" + (completedSteps + 1) + "/" + totalSteps + "，" + name + "）：";
    }

    public void start(RecipeChain chain) {
        this.currentChain = chain;
        chain.beginExecution();

        recipesToAutocraft = new Stack<>();
        try {
            chain.calculateMissingIngredients(recipesToAutocraft, null);
        } catch (RuntimeException ex) {
            mezz.jei.util.Log.get().warn("Resource flow planning failed", ex);
            mezz.jei.nova.CraftDiagnostics.notice("无法规划返还物：请减少数量或检查循环配方");
            stop(); return;
        }
        totalSteps = recipesToAutocraft.size();
        completedSteps = 0;
        mezz.jei.nova.CraftDiagnostics.log("planned recipes="+totalSteps);
        if (recipesToAutocraft.isEmpty()) {
            mezz.jei.nova.CraftDiagnostics.notice("当前配方组没有可合成的步骤，请先收藏需要制作的配方。");
            stop();
            return;
        }
        chain.calculateCrafting(); // Reset the displayed amounts.
        autocraftLoop();
    }

    // Returns false if the autocrafting cannot continue (either if it failed or if we're waiting on a recipe to complete).
    // Returns true if the autocrafting can continue (if a recipe isn't craftable, we just continue to something else).
    private boolean autocraft() {
        if (this.currentRequester == null) {
            stop();
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            stop();
            return false;
        }
        Container openContainer = player.openContainer;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        if (openContainer == null) {
            stop();
            return false;
        }
        IRecipeCategory recipeCategory = currentRequester.category;
        if(cachedRecipe != currentRequester.recipe || cachedCategory != currentRequester.category) {
            cachedLayout = currentRequester.createLayout();
            cachedRecipe = currentRequester.recipe; cachedCategory = currentRequester.category;
        }
        IRecipeLayout recipeLayout = cachedLayout;
        if (recipeLayout == null) {
            mezz.jei.nova.CraftDiagnostics.notice(stepLabel() + "无法读取配方。请重新查询并收藏该配方后重试。");
            stop();
            return false;
        }
        IRecipeTransferHandler recipeTransferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
        mezz.jei.nova.CraftDiagnostics.log("container="+openContainer.getClass().getName()+" category="+recipeCategory.getUid()+" handler="+(recipeTransferHandler==null?"null":recipeTransferHandler.getClass().getName())+" batches="+currentRequester.getMultiplier());
        if (recipeTransferHandler == null || !(recipeTransferHandler instanceof IRecipeCraftingHandler)) {
            mezz.jei.nova.CraftDiagnostics.notice(stepLabel() + "当前界面不支持快速合成此配方，请打开对应的工作台；机器加工配方需在机器中完成。");
            stop();
            return false;
        }
        IRecipeCraftingHandler craftingHandler = (IRecipeCraftingHandler) recipeTransferHandler;
        if(craftingHandler instanceof mezz.jei.nova.ae2.Ae2CraftingHandler)
            ((mezz.jei.nova.ae2.Ae2CraftingHandler)craftingHandler).setExecutionInputs(currentRequester.executionInputs);
        mezz.jei.api.recipe.transfer.IRecipeTransferError check = craftingHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), false);
        if (check != null) mezz.jei.nova.CraftDiagnostics.notice(stepLabel() + mezz.jei.nova.CraftFeedback.describe(check, recipeLayout));
        if (check == null) {
            mezz.jei.nova.CraftDiagnostics.log("preflight passed; submitting craft");
            mezz.jei.api.recipe.transfer.IRecipeTransferError submitted = craftingHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), true);
            if(submitted != null) {
                mezz.jei.nova.CraftDiagnostics.notice(stepLabel() + mezz.jei.nova.CraftFeedback.describe(submitted, recipeLayout));
                stop();
            }
            return false; // This "false" return is different from the others; it just means we're waiting for the recipe to complete
        }
        stop();
        return false;
    }

    private void autocraftLoop() {
        if (recipesToAutocraft.isEmpty()) {
            if (completedSteps > 0) mezz.jei.nova.CraftDiagnostics.notice("合成完成：已完成 " + completedSteps + " 个配方步骤。");
            stop();
            return;
        }
        while (recipesToAutocraft != null && !recipesToAutocraft.isEmpty()) {
            this.currentRequester = recipesToAutocraft.pop();
            // Coalesce only adjacent copies of the same planned recipe. Never reorder returns.
            Container batchingContainer=Minecraft.getMinecraft().player==null?null:Minecraft.getMinecraft().player.openContainer;
            boolean aeBatch=mezz.jei.nova.ae2.Ae2Terminal.supports(batchingContainer);
            boolean vanillaBatch=batchingContainer instanceof net.minecraft.inventory.ContainerWorkbench || batchingContainer instanceof net.minecraft.inventory.ContainerPlayer;
            if (currentRequester.executionInputs!=null && (aeBatch || vanillaBatch)) {
                while (!recipesToAutocraft.isEmpty()) {
                    RecipeBookmarkItem<?> next = recipesToAutocraft.peek();
                    if ((!aeBatch && (currentRequester.amount+next.amount)/currentRequester.outputAmount>8) || next.recipe != currentRequester.recipe || next.category != currentRequester.category ||
                        !java.util.Objects.equals(next.executionInputs,currentRequester.executionInputs) ||
                        !Internal.getIngredientRegistry().getUniqueId(next.ingredient).equals(Internal.getIngredientRegistry().getUniqueId(currentRequester.ingredient)) ||
                        !mezz.jei.nova.ae2.Ae2Batch.canAppend(currentRequester.amount, next.amount, currentRequester.outputAmount)) break;
                    currentRequester.amount += recipesToAutocraft.pop().amount;
                    totalSteps--;
                }
            }
            if (!autocraft()) return; // Keep the final request alive until its server reply.
        }
        stop();
    }

    @Override
    public void stepFinished(boolean success, int amount) {
        mezz.jei.nova.CraftDiagnostics.log("craft reply: success="+success+", amount="+amount);
        if (this.recipesToAutocraft == null) {
            return;
        }
        if (!success || amount <= 0) {
            mezz.jei.nova.CraftDiagnostics.notice(stepLabel() + "未能完成本步骤，请检查材料和背包空间后重试；已完成步骤的产物会保留。");
            stop();
            return;
        }
        if (currentChain != null) {
            currentChain.recordCompletion(currentRequester, Math.min((long)amount, currentRequester.amount));
            if(currentRequester.selfOutputAmount>0) mezz.jei.nova.CraftDiagnostics.log("goal reply item="+Internal.getIngredientRegistry().getUniqueId(currentRequester.ingredient)+" delivered="+amount+" requested="+currentRequester.amount+" original="+currentRequester.selfOutputAmount+" remaining="+currentChain.remainingGoal(currentRequester));
        }
        if (amount < this.currentRequester.amount) {
            this.currentRequester.amount -= amount;
            this.recipesToAutocraft.push(this.currentRequester);
        } else {
            completedSteps++;
        }
        autocraftLoop();
    }

    @Override
    public void stop() {
        this.currentChain = null;
        cachedLayout=null; cachedRecipe=null; cachedCategory=null;
        this.currentRequester = null;
        this.recipesToAutocraft = null;
    }

    @Override
    public boolean isActive() {
        return this.currentChain != null;
    }

}

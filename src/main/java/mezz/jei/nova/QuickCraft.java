package mezz.jei.nova;

import mezz.jei.Internal;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeCraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.config.ServerInfo;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.util.text.TextComponentString;

/** Uses the existing HEI protocol. No new inventory mutation or server packet type. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class QuickCraft {
    private QuickCraft() {}
    public static boolean run(RecipesGui gui, RecipeLayout layout, boolean batch) {
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null)return false;
        Container container=mc.player.openContainer;
        if(container==null || (container.getClass()!=ContainerPlayer.class && container.getClass()!=ContainerWorkbench.class)) {
            message("快速合成当前支持背包与原版工作台，请先打开对应界面。");return true;
        }
        if(!VanillaRecipeCategoryUid.CRAFTING.equals(layout.getRecipeCategory().getUid())) {
            message("此配方需要机器加工，不能在工作台快速合成。");return true;
        }
        if(!ServerInfo.isJeiOnServer()) {message("快速合成需要服务端支持 HEI。");return true;}
        if(Internal.getRuntime().getAutocraftingHandler().isActive()) {message("请等待当前合成结束。");return true;}
        if(!mc.player.inventory.getItemStack().isEmpty()) {message("请先放下鼠标拿着的物品。");return true;}
        Object handler=Internal.getRuntime().getRecipeRegistry().getRecipeTransferHandler(container,layout.getRecipeCategory());
        if(!(handler instanceof IRecipeCraftingHandler)) {message("当前容器没有快速合成处理器。");return true;}
        IRecipeCraftingHandler crafter=(IRecipeCraftingHandler)handler;
        int amount=batch?64:1;
        IRecipeTransferError error=crafter.craft(container,layout,mc.player,amount,false);
        if(error!=null) {message("无法合成：" + CraftFeedback.describe(error, layout));return true;}
        error=crafter.craft(container,layout,mc.player,amount,true);
        if(error==null)gui.close();else message("未提交合成，请检查当前容器。");
        return true;
    }
    private static void message(String text) {
        Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentString(text),true);
    }
}

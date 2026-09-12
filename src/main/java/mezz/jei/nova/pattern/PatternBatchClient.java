package mezz.jei.nova.pattern;

import mezz.jei.JustEnoughItems;
import mezz.jei.api.gui.*;
import mezz.jei.autocrafting.*;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.nova.CraftDiagnostics;
import mezz.jei.network.packets.PacketPatternBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import java.util.*;

public final class PatternBatchClient {
    private static RecipeBookmarkGroup lastGroup;
    private static Object lastContainer;
    private static NBTTagCompound cached;
    private static long at;
    private PatternBatchClient() {}
    private static NBTTagCompound identity(ItemStack stack) {
        ItemStack copy=stack.copy();copy.setCount(1);return copy.writeToNBT(new NBTTagCompound());
    }
    public static void start(RecipeBookmarkGroup group) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player!=null) CraftDiagnostics.log("pattern terminal="+mc.player.openContainer.getClass().getName());
        if(mc.player==null || !PatternBatchServer.supports(mc.player.openContainer)) {
            CraftDiagnostics.notice("当前界面未适配批量样板，请使用 AE 或 AE2FC 的有线样板终端；具体类型已记录到日志。");return;
        }
        long now=System.currentTimeMillis();
        NBTTagCompound request;
        if(lastGroup==group && lastContainer==mc.player.openContainer && cached!=null && now-at<30000) {
            request=cached.copy(); // Keep the exact preview through failed preflights and retries.
        } else {
            NBTTagList entries=new NBTTagList();
            if(group.getItemsInternal().size()>64) {CraftDiagnostics.notice("本版每组最多预检 64 个配方，请拆分配方组。");return;}
            for(BookmarkItem<?> item:group.getItemsInternal()) {
                RecipeBookmarkItem<?> recipe=(RecipeBookmarkItem<?>)item;
                NBTTagCompound entry=new NBTTagCompound();
                entry.setString("label",item.ingredient instanceof ItemStack ? ((ItemStack)item.ingredient).getDisplayName() : "非物品配方");
                if(!"minecraft.crafting".equals(recipe.category.getUid())) entry.setString("skip","暂不支持机器或流体配方");
                else {
                    try {
                        IRecipeLayout layout=recipe.createLayout();
                        if(layout==null) throw new IllegalArgumentException();
                        PatternMaterialGuides<NBTTagCompound> guides=new PatternMaterialGuides<>();
                        for(RecipeBookmarkItem<?> input:recipe.inputs) {
                            if(!(input.ingredient instanceof ItemStack)) continue;
                            List<NBTTagCompound> family=new ArrayList<>();
                            for(Object alias:input.aliases) if(alias instanceof ItemStack && !((ItemStack)alias).isEmpty()) family.add(identity((ItemStack)alias));
                            guides.add(family,identity((ItemStack)input.ingredient),input.amount);
                        }
                        NBTTagList inputs=new NBTTagList();ItemStack output=ItemStack.EMPTY;
                        for(IGuiIngredient<ItemStack> slot:new TreeMap<>(layout.getItemStacks().getGuiIngredients()).values()) {
                            ItemStack stack=slot.getDisplayedIngredient();
                            if(slot.isInput()) {
                                List<NBTTagCompound> family=new ArrayList<>();
                                for(ItemStack candidate:slot.getAllIngredients()) if(candidate!=null && !candidate.isEmpty()) family.add(identity(candidate));
                                inputs.appendTag(family.isEmpty() ? new NBTTagCompound() : guides.take(family).copy());
                            }
                            else if(output.isEmpty() && stack!=null) output=stack.copy();
                        }
                        if(inputs.tagCount()>9 || output.isEmpty()) throw new IllegalArgumentException();
                        while(inputs.tagCount()<9) inputs.appendTag(new NBTTagCompound());
                        entry.setTag("in",inputs);entry.setTag("expected",output.writeToNBT(new NBTTagCompound()));
                    } catch(RuntimeException ex) {entry.setString("skip","无法读取九宫格配方");}
                }
                entries.appendTag(entry);
            }
            request=new NBTTagCompound();request.setTag("entries",entries);
            cached=request.copy();lastGroup=group;lastContainer=mc.player.openContainer;at=now;
        }
        JustEnoughItems.getProxy().sendPacketToServer(new PacketPatternBatch(mc.player.openContainer.windowId,request));
    }
}

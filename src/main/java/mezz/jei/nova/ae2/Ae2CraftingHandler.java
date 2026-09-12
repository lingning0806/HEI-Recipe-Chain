package mezz.jei.nova.ae2;

import mezz.jei.JustEnoughItems;
import mezz.jei.api.gui.*;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.network.packets.PacketAe2Craft;
import mezz.jei.transfer.RecipeTransferErrorTooltip;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import java.util.*;

public final class Ae2CraftingHandler implements IRecipeCraftingHandler<Container> {
    private final IRecipeTransferHandler<Container> delegate;
    private Map<String,Long> executionInputs;
    public void setExecutionInputs(Map<String,Long> inputs) { executionInputs=inputs; }
    public Ae2CraftingHandler(IRecipeTransferHandler<Container> delegate) { this.delegate=delegate; }
    public Class<Container> getContainerClass() { return delegate.getContainerClass(); }
    public IRecipeTransferError transferRecipe(Container c,IRecipeLayout l,EntityPlayer p,boolean max,boolean run) {
        return delegate.transferRecipe(c,l,p,max,run);
    }
    public IRecipeTransferError craft(Container c,IRecipeLayout l,EntityPlayer p,int amount,boolean run) {
        if (!p.inventory.getItemStack().isEmpty()) return new RecipeTransferErrorTooltip("请先放下鼠标上的物品，再进行 AE 快速合成。");
        if (!Ae2Terminal.supports(c) || !"minecraft.crafting".equals(l.getRecipeCategory().getUid()))
            return new RecipeTransferErrorTooltip("当前终端仅支持工作台配方快速合成。");
        IRecipeTransferError error=null;
        if(!run) try {
            // NEE returns an informational USER_FACING object even when ingredients are present.
            // Use AE's actual missing-item check for quick crafting, while retaining NEE for +.
            Class<?> type=Class.forName("appeng.integration.modules.jei.JEIMissingItem");
            java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(Container.class,IRecipeLayout.class);
            ctor.setAccessible(true);
            Object check=ctor.newInstance(c,l);
            error=Boolean.TRUE.equals(Ae2Terminal.call(check,"errored")) ? (IRecipeTransferError)check : null;
        } catch(ReflectiveOperationException | RuntimeException ex) {
            mezz.jei.util.Log.get().warn("AE2 preflight unavailable",ex);
            return new RecipeTransferErrorTooltip("无法检查 AE 终端材料，请查看日志后重试。");
        }
        if(error!=null) return error;
        if(run) {
            NBTTagCompound recipe=new NBTTagCompound();
            ItemStack expected=ItemStack.EMPTY;
            int index=0;
            for(IGuiIngredient<ItemStack> ingredient : new TreeMap<>(l.getItemStacks().getGuiIngredients()).values()) {
                if(!ingredient.isInput()) { if(expected.isEmpty() && ingredient.getDisplayedIngredient()!=null) expected=ingredient.getDisplayedIngredient().copy(); continue; }
                NBTTagList alternatives=new NBTTagList();
                ItemStack displayed=ingredient.getDisplayedIngredient();
                if(displayed!=null && !displayed.isEmpty()) alternatives.appendTag(displayed.writeToNBT(new NBTTagCompound()));
                for(ItemStack stack:ingredient.getAllIngredients()) if(!stack.isEmpty() && !Ae2Terminal.same(stack,displayed==null?ItemStack.EMPTY:displayed)) alternatives.appendTag(stack.writeToNBT(new NBTTagCompound()));
                recipe.setTag("#"+index++,alternatives);
            }
            if(index>9 || expected.isEmpty()) return new RecipeTransferErrorTooltip("无法读取此配方的九宫格或产物。");
            NBTTagList outputs=new NBTTagList(); outputs.appendTag(expected.writeToNBT(new NBTTagCompound())); recipe.setTag("outputs",outputs);
            if(executionInputs!=null && !bindRecipe(recipe,index)) return new RecipeTransferErrorTooltip("无法将已选材料对应到九宫格，请重新规划此配方；本次未提交合成。");
            JustEnoughItems.getProxy().sendPacketToServer(new PacketAe2Craft(c.windowId,Math.max(1,Math.min(64,amount)),recipe,expected));
        }
        return null;
    }
    private boolean bindRecipe(NBTTagCompound recipe,int slots) {
        List<Integer> indices=new ArrayList<>();
        List<Map<String,ItemStack>> alternatives=new ArrayList<>();
        List<List<String>> keys=new ArrayList<>();List<Long> amounts=new ArrayList<>();
        for(int i=0;i<slots;i++) {
            NBTTagList tags=recipe.getTagList("#"+i,10);
            if(tags.tagCount()==0) continue;
            Map<String,ItemStack> choices=new LinkedHashMap<>();
            long count=-1;
            for(int n=0;n<tags.tagCount();n++) {
                ItemStack stack=new ItemStack(tags.getCompoundTagAt(n));
                if(stack.isEmpty()) continue;
                if(count<0) count=stack.getCount();
                if(count!=stack.getCount()) return false;
                String key=mezz.jei.Internal.getIngredientRegistry().getUniqueId(stack);
                if(executionInputs.getOrDefault(key,0L)>=count) choices.put(key,stack);
            }
            if(choices.isEmpty()) return false;
            indices.add(i);alternatives.add(choices);keys.add(new ArrayList<>(choices.keySet()));amounts.add(count);
        }
        List<String> selected=mezz.jei.nova.MaterialBinding.bind(keys,amounts,executionInputs);
        if(selected==null) return false;
        for(int i=0;i<selected.size();i++) {
            NBTTagList tags=new NBTTagList();
            tags.appendTag(alternatives.get(i).get(selected.get(i)).writeToNBT(new NBTTagCompound()));
            recipe.setTag("#"+indices.get(i),tags);
        }
        return true;
    }

}

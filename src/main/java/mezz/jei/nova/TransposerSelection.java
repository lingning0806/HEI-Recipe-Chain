package mezz.jei.nova;

import java.util.*;
import java.lang.reflect.Field;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** TE's container/multi wrappers store correlated alternatives, not independent OR slots. */
public final class TransposerSelection {
    private TransposerSelection() { }
    @SuppressWarnings("unchecked")
    public static void apply(IRecipeWrapper wrapper,Object target,String category,Ingredients ingredients) {
        String name=wrapper.getClass().getName();
        if (!category.equals("thermalexpansion.transposer_fill") ||
            !(name.equals("cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapperMulti") ||
              name.equals("cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapperContainer"))) return;
        try {
            List<ItemStack> outputs=((List<List<ItemStack>>)read(wrapper,"outputs")).get(0);
            List<ItemStack> inputs=((List<List<ItemStack>>)read(wrapper,"inputs")).get(0);
            List<FluidStack> fluids=((List<List<FluidStack>>)read(wrapper,"inputFluids")).get(0);
            List<String> keys=new ArrayList<>();
            for(ItemStack output:outputs)keys.add(mezz.jei.Internal.getIngredientRegistry().getUniqueId(output));
            int selected=selectIndex(keys,mezz.jei.Internal.getIngredientRegistry().getUniqueId(target),inputs.size(),fluids.size());
            ingredients.setInput(VanillaTypes.ITEM,inputs.get(selected).copy());
            ingredients.setInput(VanillaTypes.FLUID,fluids.get(selected).copy());
            ingredients.setOutput(VanillaTypes.ITEM,outputs.get(selected).copy());
        } catch(ReflectiveOperationException ex) {throw new IllegalArgumentException("无法读取装填配方对应关系",ex);}
    }
    public static int selectIndex(List<String> outputs,String target,int inputs,int fluids) {
        if(inputs!=outputs.size() || fluids!=outputs.size())throw new IllegalArgumentException("装填配方对应关系无效");
        int index=-1;
        for(int i=0;i<outputs.size();i++)if(outputs.get(i).equals(target)) {
            if(index>=0)throw new IllegalArgumentException("装填配方有多个同产物候选，需进一步指定");
            index=i;
        }
        if(index<0)throw new IllegalArgumentException("装填配方不包含所选产物");
        return index;
    }
    private static Object read(Object wrapper,String name)throws ReflectiveOperationException {
        Class<?> type=wrapper.getClass();
        while(type!=null) {
            try {Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(wrapper);}
            catch(NoSuchFieldException ex){type=type.getSuperclass();}
        }
        throw new NoSuchFieldException(name);
    }
}

package mezz.jei.nova;

import java.util.*;
import java.lang.reflect.*;
import net.minecraft.item.ItemStack;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.api.recipe.IRecipeWrapper;

/** Mek9 infusion is a quantity resource; feed items are conversion choices, not OR ingredients. */
public final class InfusionDemand {
    public final String key, name;
    public final Map<ItemStack,Integer> sources;
    private InfusionDemand(String key,String name,Map<ItemStack,Integer> sources) {this.key=key;this.name=name;this.sources=sources;}
    public static long feedCount(long units,long perItem) {return ChainMath.batches(units,perItem);}
    public static void adapt(IRecipeWrapper wrapper,List<RecipeBookmarkItem<?>> inputs) {
        if(!wrapper.getClass().getName().equals("mekanism.client.jei.machine.other.MetallurgicInfuserRecipeWrapper"))return;
        try {
            Object recipe=field(wrapper,"recipe");
            Object input=recipe.getClass().getMethod("getInput").invoke(recipe);
            Object storage=field(input,"infuse");
            Object type=storage.getClass().getMethod("getType").invoke(storage);
            int units=((Number)storage.getClass().getMethod("getAmount").invoke(storage)).intValue();
            if(inputs.size()!=2 || units<=0)throw new IllegalArgumentException("无法识别灌注输入结构");
            String name=(String)type.getClass().getMethod("getLocalizedName").invoke(type);
            String key="nova:infusion:"+field(type,"name");
            Class<?> registry=Class.forName("mekanism.api.infuse.InfuseRegistry");
            Method get=registry.getMethod("getObject",ItemStack.class);
            Map<ItemStack,Integer> sources=new LinkedHashMap<>();
            RecipeBookmarkItem<?> supply=inputs.get(1);
            for(Object alias:supply.aliases) {
                if(!(alias instanceof ItemStack))continue;
                Object info=get.invoke(null,alias);
                if(info!=null && field(info,"type")==type) {
                    int stored=((Number)field(info,"stored")).intValue();
                    if(stored>0)sources.put(((ItemStack)alias).copy(),stored);
                }
            }
            supply.infusion=new InfusionDemand(key,name,sources);
            supply.amount=units;
        }catch(ReflectiveOperationException ex){throw new IllegalArgumentException("无法读取Mek灌注单位",ex);}
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ex){ }
        }
        throw new NoSuchFieldException(name);
    }
}

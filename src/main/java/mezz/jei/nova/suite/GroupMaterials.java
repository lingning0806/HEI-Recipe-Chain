package mezz.jei.nova.suite;

import mezz.jei.Internal;
import mezz.jei.autocrafting.*;
import mezz.jei.bookmarks.*;
import mezz.jei.nova.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import java.util.*;

/** One ledger snapshot supplies views, detail and the AE pull plan. Never edits bookmark goals. */
public final class GroupMaterials {
    private final Map<String,Object> infusionIcons=new HashMap<>();
    public final Map<String,Object> items=new LinkedHashMap<>();
    public final Map<String,Long> player=new LinkedHashMap<>(),network=new LinkedHashMap<>(),pull=new LinkedHashMap<>();
    public final Map<String,Long> originalGoals=new LinkedHashMap<>(),remainingGoals=new LinkedHashMap<>();
    private final Set<String> recipeKeys=new LinkedHashSet<>();
    public final RemainderFlow.Result total,current;
    public GroupMaterials(RecipeBookmarkGroup group) {
        Map<String,RemainderFlow.Recipe> recipes=new LinkedHashMap<>();Map<String,Long> goals=new LinkedHashMap<>();
        for(BookmarkItem<?> item:group.getItemsInternal()) {
            RecipeBookmarkItem<?> recipe=(RecipeBookmarkItem<?>)item;String key=remember(recipe.ingredient);
            long goal=group.remainingGoal(recipe);if(recipe.selfOutputAmount>0){originalGoals.put(key,recipe.selfOutputAmount);remainingGoals.put(key,goal);}if(goal>0) goals.put(key,goal);
            List<RemainderFlow.Input> inputs=new ArrayList<>();
            for(RecipeBookmarkItem<?> input:recipe.inputs) {
                if(input.infusion!=null) {items.put(input.infusion.key,input.infusion);infusionIcons.put(input.infusion.key,input.ingredient);inputs.add(new RemainderFlow.Input(Collections.singletonList(input.infusion.key),input.amount,0,null,0));continue;}
                List<String> aliases=new ArrayList<>();for(Object alias:input.aliases) aliases.add(remember(alias));
                String chosen=remember(input.ingredient);if(aliases.remove(chosen))aliases.add(0,chosen);
                inputs.add(new RemainderFlow.Input(aliases,input.amount,input.usage.uses,input.usage.remainder==null?null:remember(input.usage.remainder),input.usage.remainder==null?0:IngredientUtil.getCount(input.usage.remainder)));
            }
            recipes.put(key,new RemainderFlow.Recipe(key,recipe.outputAmount,inputs));
        }
        recipeKeys.addAll(items.keySet());
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player!=null) for(ItemStack stack:mc.player.inventory.mainInventory) if(!stack.isEmpty()) player.merge(remember(stack),(long)stack.getCount(),Math::addExact);
        mezz.jei.nova.ae2.Ae2Inventory.addTo(network,false);
        if(mc.player!=null) for(net.minecraft.inventory.Slot slot:mc.player.openContainer.inventorySlots) {
            if(slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix") && !slot.getStack().isEmpty())player.merge(remember(slot.getStack()),(long)slot.getStack().getCount(),Math::addExact);
        }
        Map<String,Long> all=new LinkedHashMap<>(player);network.forEach((k,v)->all.merge(k,v,Math::addExact));
        total=new RemainderFlow(recipes,Collections.emptyMap()).plan(goals);
        current=new RemainderFlow(recipes,all).plan(goals);
        current.withdrawn.forEach((key,amount)->{long needed=Math.max(0,amount-player.getOrDefault(key,0L));if(needed>0)pull.put(key,Math.min(needed,network.getOrDefault(key,0L)));});
    }
    private String remember(Object value) {String key=Internal.getIngredientRegistry().getUniqueId(value);items.putIfAbsent(key,value);return key;}
    public List<BookmarkItem<?>> rows(Map<String,Long> amounts,BookmarkGroup group) {
        List<BookmarkItem<?>> result=new ArrayList<>();amounts.forEach((key,amount)->{Object item=items.get(key);if(amount>0 && item!=null) {
            if(item instanceof InfusionDemand)result.add(new InfusionBookmarkItem(infusionIcons.get(key),group,()->amount,(InfusionDemand)item));
            else result.add(new DummyBookmarkItem<>(item,group,()->amount));
        }});
        if(!result.isEmpty())((DummyBookmarkItem<?>)result.get(0)).setSectionStart(true);
        return result;
    }
    public Object icon(String key) {return infusionIcons.getOrDefault(key,items.get(key));}
    public List<MaterialPanelModel.Row> detailRows() {
        Set<String> keys=new LinkedHashSet<>(total.consumed.keySet());keys.addAll(total.required.keySet());keys.addAll(current.required.keySet());keys.addAll(current.withdrawn.keySet());keys.addAll(originalGoals.keySet());
        for(String key:recipeKeys)if(current.remaining.getOrDefault(key,0L)>0 || current.toolsRemaining.getOrDefault(key,0L)>0)keys.add(key);
        List<MaterialPanelModel.Row> rows=new ArrayList<>();
        for(String key:keys)rows.add(new MaterialPanelModel.Row(key,total.consumed.getOrDefault(key,0L),total.required.getOrDefault(key,0L),player.getOrDefault(key,0L),network.getOrDefault(key,0L),current.required.getOrDefault(key,0L),Math.addExact(current.remaining.getOrDefault(key,0L),current.toolsRemaining.getOrDefault(key,0L)),originalGoals.getOrDefault(key,0L),remainingGoals.getOrDefault(key,0L)));
        return rows;
    }
    public String unit(String key) {Object item=items.get(key);return item instanceof InfusionDemand?"灌注单位":item instanceof net.minecraftforge.fluids.FluidStack?"mB":"个";}
    public String name(String key) {
        Object item=items.get(key);if(item instanceof InfusionDemand)return ((InfusionDemand)item).name+"（灌注单位）";
        return item==null?key:Internal.getIngredientRegistry().getIngredientHelper(item).getDisplayName(item);
    }
}

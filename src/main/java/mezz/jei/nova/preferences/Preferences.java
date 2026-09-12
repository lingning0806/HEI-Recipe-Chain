package mezz.jei.nova.preferences;

import mezz.jei.Internal;
import mezz.jei.api.recipe.*;
import mezz.jei.autocrafting.*;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.nio.file.*;
import java.util.*;

/** One cached rule source for tree expansion and bookmarked material snapshots. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class Preferences {
    private static PreferenceRules rules=PreferenceRules.EMPTY;
    private static Path loaded;private static long modified=Long.MIN_VALUE,checked;
    private Preferences() {}
    public static PreferenceRules rules() {
        long now=System.currentTimeMillis();if(now-checked<1000) return rules;checked=now;
        try {
            net.minecraftforge.common.config.Configuration config=mezz.jei.config.Config.getConfig();
            if(config==null) return rules;
            Path path=config.getConfigFile().toPath().resolveSibling("recipe-preferences.txt");
            if(!Files.exists(path)) {
                Files.write(path,Arrays.asList("$ HEI 1.12.2 preference rules; higher priority appears first.",
                    "$ output starts a rule; input and recipe are optional (at least one required).",
                    "$ Expressions: ! & | ( ) ;  (semicolon separates priorities). Wildcard: *",
                    "$ Items: minecraft:planks or item:minecraft:planks@0; mod:minecraft; ore:plankWood / #plankWood.",
                    "$ Fluids: fluid:water. Modern tags are NOT automatically mapped to ore dictionary names.",
                    "$ recipe: category UID, category UID/HEI numeric recipe ID, or wrapper:fully.qualified.Class.",
                    "$ Rules reload within one second on use. Existing bookmarked choices remain saved.",
                    "$ Uncomment an example by removing its surrounding $$ lines.","$$",
                    "output = minecraft:crafting_table","input = item:minecraft:planks@0; mod:minecraft", "$$",
                    "$$","output = *","input = mod:thermalfoundation; mod:minecraft","$$"),java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            long stamp=Files.getLastModifiedTime(path).toMillis();
            if(!path.equals(loaded) || stamp!=modified) {
                try {PreferenceRules parsed=PreferenceRules.parse(Files.readAllLines(path,java.nio.charset.StandardCharsets.UTF_8));rules=parsed;}
                catch(RuntimeException ex) {mezz.jei.util.Log.get().warn("Invalid HEI recipe-preferences.txt; keeping last valid rules",ex);}
                loaded=path;modified=stamp;
            }
        } catch(Exception ex) {mezz.jei.util.Log.get().warn("Could not load HEI recipe preferences",ex);}
        return rules;
    }
    public static List<String> describe(Object object) {
        List<String> result=new ArrayList<>();
        if(object instanceof ItemStack) {
            ItemStack item=(ItemStack)object;if(item.isEmpty()) return result;
            String id=String.valueOf(item.getItem().getRegistryName());
            result.add(id);result.add("item:"+id);result.add(id+"@"+item.getMetadata());result.add("item:"+id+"@"+item.getMetadata());
            result.add("mod:"+id.split(":",2)[0]);
            for(int ore:OreDictionary.getOreIDs(item)) {String name=OreDictionary.getOreName(ore);result.add("ore:"+name);result.add("#"+name);}
        } else if(object instanceof FluidStack) {
            result.add("fluid:"+((FluidStack)object).getFluid().getName());
        }
        return result;
    }
    public static <T> PreferenceRules.Candidate<T> candidate(T value,Object output,IRecipeWrapper wrapper,IRecipeCategory category) {
        Ingredients data=new Ingredients();wrapper.getIngredients(data);List<String> inputs=new ArrayList<>();
        for(Object raw:data.getInputIngredients().values()) for(Object slot:(List)raw) for(Object alias:(List)slot) inputs.addAll(describe(alias));
        return new PreferenceRules.Candidate<>(value,describe(output),inputs,recipeIds(wrapper,category));
    }
    public static List<String> recipeIds(IRecipeWrapper wrapper,IRecipeCategory category) {
        String uid=category.getUid();long id=Internal.getRuntime().getRecipeRegistry().getRecipeId(wrapper);
        return Arrays.asList(uid,uid+"/"+id,"wrapper:"+wrapper.getClass().getName());
    }
    public static void apply(RecipeBookmarkItem<?> owner, NBTTagList saved) {
        PreferenceRules preferences=rules();
        for(int i=0;i<owner.inputs.size();i++) {
            RecipeBookmarkItem input=owner.inputs.get(i);Object chosen=null;
            if(i<saved.tagCount() && family(input).equals(saved.getCompoundTagAt(i).getString("family"))) {
                String key=saved.getCompoundTagAt(i).getString("selected");
                for(Object alias:input.aliases) if(identity(alias).equals(key)) {chosen=alias;break;}
            }
            if(chosen==null && !preferences.isEmpty()) {
                List<PreferenceRules.Candidate<Object>> candidates=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(Object alias:input.aliases) if(seen.add(identity(alias))) candidates.add(new PreferenceRules.Candidate<>(alias,Collections.emptyList(),describe(alias),Collections.emptyList()));
                chosen=preferences.material(describe(owner.ingredient),candidates,recipeIds(owner.recipe,owner.category));
            }
            if(chosen!=null) {
                input.ingredient=chosen;
                input.aliases=new ArrayList(input.aliases);input.aliases.remove(chosen);input.aliases.add(0,chosen);
            }
        }
    }
    public static String identity(Object value) {
        if(value instanceof ItemStack) {ItemStack copy=((ItemStack)value).copy();copy.setCount(1);return copy.writeToNBT(new NBTTagCompound()).toString();}
        if(value instanceof FluidStack) {FluidStack copy=((FluidStack)value).copy();copy.amount=1;return copy.writeToNBT(new NBTTagCompound()).toString();}
        return Internal.getIngredientRegistry().getUniqueId(value);
    }
    private static String family(RecipeBookmarkItem<?> input) {
        Set<String> keys=new TreeSet<>();for(Object alias:input.aliases) keys.add(identity(alias));return keys.toString();
    }
    public static NBTTagList snapshot(RecipeBookmarkItem<?> owner) {
        NBTTagList saved=new NBTTagList();if(owner.inputs==null) return saved;
        for(RecipeBookmarkItem<?> input:owner.inputs) {NBTTagCompound tag=new NBTTagCompound();tag.setString("selected",identity(input.ingredient));tag.setString("family",family(input));saved.appendTag(tag);}return saved;
    }
}

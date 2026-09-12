package mezz.jei.nova;

import mezz.jei.Internal;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.gui.Focus;
import java.util.*;

/** Adapts HEI 4.x plugins without changing any published bookmark or search types. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class PlannerSession {
    public static final class Route {
        public final IRecipeWrapper recipe;
        public final IRecipeCategory category;
        Route(IRecipeWrapper recipe, IRecipeCategory category) { this.recipe=recipe; this.category=category; }
    }
    public final String root;
    public long quantity = 1;
    public final Map<String,Object> ingredients = new LinkedHashMap<>();
    public final Map<String,Route> selected = new HashMap<>();
    public final Set<String> provided = new HashSet<>();
    public final Set<String> catalysts = new HashSet<>();
    public final Map<String,Integer> variants = new HashMap<>();
    public final Map<String,List<List<Object>>> slots = new HashMap<>();
    public final List<String> warnings = new ArrayList<>();
    private final Map<String,List<Route>> routes = new HashMap<>();
    public ChainPlanner.Plan plan;
    public PlannerSession(Object target, IRecipeWrapper recipe, IRecipeCategory category) {
        root = remember(target); load(); selected.put(root,new Route(recipe,category)); rebuild();
    }
    private java.nio.file.Path storage() {
        java.io.File bookmark = mezz.jei.config.Config.getBookmarkFile();
        return bookmark == null ? null : bookmark.toPath().resolveSibling("novahei-plan.json");
    }
    private void load() {
        java.nio.file.Path file=storage();
        if(file==null||!java.nio.file.Files.exists(file))return;
        try(java.io.Reader reader=java.nio.file.Files.newBufferedReader(file,java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject data=new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            if(!root.equals(data.get("root").getAsString()))return;
            quantity=Math.max(1,Math.min(1000000000L,data.get("quantity").getAsLong()));
            for(com.google.gson.JsonElement e:data.getAsJsonArray("provided"))provided.add(e.getAsString());
            for(com.google.gson.JsonElement e:data.getAsJsonArray("catalysts"))catalysts.add(e.getAsString());
            for(Map.Entry<String,com.google.gson.JsonElement> e:data.getAsJsonObject("variants").entrySet())variants.put(e.getKey(),Math.max(0,e.getValue().getAsInt()));
            RecipeRegistry registry=Internal.getRuntime().getRecipeRegistry();
            for(Map.Entry<String,com.google.gson.JsonElement> e:data.getAsJsonObject("routes").entrySet()) {
                com.google.gson.JsonObject route=e.getValue().getAsJsonObject();
                IRecipeCategory cat=registry.getRecipeCategory(route.get("category").getAsString());
                IRecipeWrapper wrapper=cat==null?null:registry.getRecipeById(route.get("id").getAsLong(),cat);
                if(wrapper!=null)selected.put(e.getKey(),new Route(wrapper,cat));
            }
        } catch(Exception ex) {mezz.jei.util.Log.get().warn("Could not load Nova HEI plan",ex);}
    }
    public void save() {
        java.nio.file.Path file=storage();if(file==null)return;
        com.google.gson.JsonObject data=new com.google.gson.JsonObject();
        data.addProperty("root",root);data.addProperty("quantity",quantity);
        com.google.gson.Gson gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        data.add("provided",gson.toJsonTree(provided));data.add("catalysts",gson.toJsonTree(catalysts));data.add("variants",gson.toJsonTree(variants));
        com.google.gson.JsonObject routes=new com.google.gson.JsonObject();
        selected.forEach((key,route)->{
            com.google.gson.JsonObject value=new com.google.gson.JsonObject();
            value.addProperty("category",route.category.getUid());value.addProperty("id",Internal.getRuntime().getRecipeRegistry().getRecipeId(route.recipe));routes.add(key,value);
        });data.add("routes",routes);
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Path temp=file.resolveSibling(file.getFileName()+".tmp");
            try(java.io.Writer writer=java.nio.file.Files.newBufferedWriter(temp,java.nio.charset.StandardCharsets.UTF_8)){gson.toJson(data,writer);}
            java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch(java.io.IOException ex){mezz.jei.util.Log.get().warn("Could not save Nova HEI plan",ex);}
    }
    public String remember(Object ingredient) {
        String key;
        // Exact item/fluid identity: HEI's recipe UID may intentionally omit meaningful NBT.
        if (ingredient instanceof net.minecraft.item.ItemStack) {
            net.minecraft.item.ItemStack item=((net.minecraft.item.ItemStack)ingredient).copy();item.setCount(1);
            key="item:"+item.serializeNBT().toString();
        } else if (ingredient instanceof net.minecraftforge.fluids.FluidStack) {
            net.minecraftforge.fluids.FluidStack fluid=((net.minecraftforge.fluids.FluidStack)ingredient).copy();fluid.amount=1;
            key="fluid:"+fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).toString();
        } else key=Internal.getIngredientRegistry().getUniqueId(ingredient);
        ingredients.putIfAbsent(key,ingredient);return key;
    }
    public String name(String key) {
        Object item=ingredients.get(key);
        return item==null?key:Internal.getIngredientRegistry().getIngredientHelper(item).getDisplayName(item);
    }
    public List<Route> routes(String key) {
        return routes.computeIfAbsent(key,k->{
            List<Route> result=new ArrayList<>();
            RecipeRegistry registry=Internal.getRuntime().getRecipeRegistry();
            IFocus<Object> focus=new Focus<>(IFocus.Mode.OUTPUT,ingredients.get(k));
            for (IRecipeCategory category:registry.getRecipeCategories(focus)) {
                for (Object wrapper:registry.getRecipeWrappers(category,focus)) {
                    if (result.size()>=128) { warnings.add("配方候选超过128条，列表已截断");return result; }
                    result.add(new Route((IRecipeWrapper)wrapper,category));
                }
            }
            return result;
        });
    }
    public void cycleRoute(String key) {
        List<Route> options=routes(key);if(options.isEmpty())return;
        Route current=selected.get(key);int index=-1;
        for(int i=0;i<options.size();i++)if(current!=null&&options.get(i).recipe==current.recipe)index=i;
        selected.put(key,options.get((index+1)%options.size()));provided.remove(key);rebuild();
    }
    public String slotId(String parent,int index) {
        Route route=selected.get(parent);
        return parent+"/"+(route==null?"none":route.category.getUid()+":"+Internal.getRuntime().getRecipeRegistry().getRecipeId(route.recipe))+"/"+index;
    }
    public void toggleCatalyst(String parent,int index) {
        String id=slotId(parent,index);if(!catalysts.remove(id))catalysts.add(id);rebuild();
    }
    public void cycleVariant(String parent,int index) {
        List<Object> choices=slots.get(parent).get(index);String id=slotId(parent,index);
        variants.put(id,(variants.getOrDefault(id,0)+1)%choices.size());rebuild();
    }
    public void rebuild() {
        warnings.clear();slots.clear();
        try {plan=ChainPlanner.build(root,quantity,this::resolve);}
        catch(RuntimeException ex) {plan=new ChainPlanner.Plan();plan.problems.add("配方插件无法解析："+ex.getClass().getSimpleName());}
    }
    private ChainPlanner.Recipe resolve(String key) {
        if(provided.contains(key))return null;
        Route route=selected.get(key);
        if(route==null) {
            Object ingredient=ingredients.get(key);IRecipeWrapper favorite=FavoriteRecipes.getFavorite(ingredient);
            if(favorite!=null)route=new Route(favorite,FavoriteRecipes.getFavoriteCategory(ingredient));
            else {
                List<Route> options=routes(key);
                List<mezz.jei.nova.preferences.PreferenceRules.Candidate<Route>> candidates=new ArrayList<>();
                for(Route option:options) candidates.add(mezz.jei.nova.preferences.Preferences.candidate(option,ingredient,option.recipe,option.category));
                route=mezz.jei.nova.preferences.Preferences.rules().choose(candidates);
                if(route==null) {if(options.size()>1)warnings.add(name(key)+"：偏好未能唯一确定配方，暂作为外部材料");return null;}
            }
            selected.put(key,route);
        }
        if(route.category==null)return null;
        Ingredients data=new Ingredients();route.recipe.getIngredients(data);
        long output=0;
        for(Object type:data.getOutputIngredients().keySet()) {
            for(Object value:data.getOutputIngredients().get((IIngredientType)type)) {
                if(value!=null&&remember(value).equals(key))output=Math.addExact(output,IngredientUtil.getCount(value));
            }
        }
        if(output<=0) {warnings.add(name(key)+"：没有可计算的确定数量");return null;}
        List<ChainPlanner.Input> inputs=new ArrayList<>();List<List<Object>> choices=new ArrayList<>();
        for(Object type:data.getInputIngredients().keySet()) {
            for(Object raw:data.getInputs((IIngredientType)type)) {
                List<Object> aliases=new ArrayList<>();for(Object a:(List)raw)if(a!=null)aliases.add(a);
                if(aliases.isEmpty())continue;
                int index=choices.size();choices.add(aliases);String id=slotId(key,index);
                if(!variants.containsKey(id)) {
                    List<mezz.jei.nova.preferences.PreferenceRules.Candidate<Object>> candidates=new ArrayList<>();
                    for(Object alias:aliases) candidates.add(new mezz.jei.nova.preferences.PreferenceRules.Candidate<>(alias,Collections.emptyList(),mezz.jei.nova.preferences.Preferences.describe(alias),Collections.emptyList()));
                    Object preferred=mezz.jei.nova.preferences.Preferences.rules().material(mezz.jei.nova.preferences.Preferences.describe(ingredients.get(key)),candidates,mezz.jei.nova.preferences.Preferences.recipeIds(route.recipe,route.category));
                    if(preferred!=null) variants.put(id,aliases.indexOf(preferred));
                }
                Object value=aliases.get(variants.getOrDefault(id,0)%aliases.size());long count=IngredientUtil.getCount(value);
                if(count<=0)throw new IllegalArgumentException("Unsupported quantity");
                inputs.add(new ChainPlanner.Input(remember(value),count,catalysts.contains(id)));
            }
        }
        slots.put(key,choices);return new ChainPlanner.Recipe(output,inputs);
    }
}

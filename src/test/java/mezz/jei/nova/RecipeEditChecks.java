package mezz.jei.nova;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.autocrafting.*;
import mezz.jei.bookmarks.*;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientRegistry;
import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;

/** Tests production group edits; only Minecraft's registry/rendering are substituted. */
public final class RecipeEditChecks {
    static Unsafe unsafe;
    static final IIngredientHelper helper = (IIngredientHelper) Proxy.newProxyInstance(RecipeEditChecks.class.getClassLoader(), new Class[]{IIngredientHelper.class}, (p,m,a) -> {
        if (m.getName().equals("copyIngredient") || m.getName().equals("getUniqueId")) return a[0];
        return null;
    });
    static final class Registry extends IngredientRegistry {
        private Registry() { super(null,null,null,null,null,null); }
        @Override public String getUniqueId(Object item) { return (String) item; }
        @Override public <V> IIngredientHelper<V> getIngredientHelper(V item) { return helper; }
    }
    static final class Group extends RecipeBookmarkGroup {
        Group(int id) { super(id); }
        @Override protected <T> IIngredientListElement<T> getIngredientListElement(T item) { return null; }
        @Override public List<BookmarkItem<?>> getItems() {
            List<BookmarkItem<?>> rows = new ArrayList<>();
            for (BookmarkItem<?> entry : getItemsInternal()) {
                rows.add(entry); rows.addAll(((RecipeBookmarkItem<?>) entry).getInputs());
            }
            return rows;
        }
        @Override protected RecipeBookmarkGroup newGroup(int id) { return new Group(id); }
    }
    static final class Recipe extends RecipeBookmarkItem<String> {
        Map<String, Integer> specs;
        List<DummyBookmarkItem<?>> rows;
        Recipe() { super("unused"); }
        @Override public void populateWith(IRecipeWrapper wrapper, IRecipeCategory<?> category) {
            inputs = new ArrayList<>(); rows = new ArrayList<>();
            for (Map.Entry<String,Integer> e : specs.entrySet()) {
                inputs.add(new RecipeBookmarkItem<>(new ArrayList<>(Arrays.asList(e.getKey().split("\\|"))), e.getValue()));
                rows.add(new DummyBookmarkItem<>(e.getKey(), getGroup(), () -> (long)e.getValue()*getMultiplier()));
            }
        }
        @Override public List<DummyBookmarkItem<?>> getInputs() { return rows; }
        @Override public Recipe copy() {
            try {
                Recipe r = make(ingredient, outputAmount, selfOutputAmount, specs);
                r.recipe = recipe; r.amount = amount; return r;
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    static Recipe make(String name, long output, long goal, Map<String,Integer> inputs) throws Exception {
        Recipe r = (Recipe) unsafe.allocateInstance(Recipe.class);
        r.usage=InputUsage.CONSUMED; r.ingredient=name; r.aliases=new ArrayList<>(Arrays.asList(name));
        r.outputAmount=output; r.selfOutputAmount=goal; r.specs=inputs;
        r.recipe = new IRecipeWrapper() {
            @Override public void getIngredients(mezz.jei.api.ingredients.IIngredients ignored) { }
        };
        return r;
    }
    static Map<String,Integer> inputs(Object... args) {
        Map<String,Integer> map=new LinkedHashMap<>();
        for(int i=0;i<args.length;i+=2)map.put((String)args[i],(Integer)args[i+1]);
        return map;
    }
    static RecipeBookmarkGroup group(int id, Recipe... recipes) {
        Group g=new Group(id); for(Recipe r:recipes)g.addItemInternal(r); g.finishLoading(); return g;
    }
    static RecipeBookmarkItem<?> get(RecipeBookmarkGroup g,String id) {
        for(BookmarkItem<?> b:g.getItemsInternal())if(b.ingredient.equals(id))return (RecipeBookmarkItem<?>)b;
        throw new AssertionError("missing "+id);
    }
    static void check(boolean b) { if(!b)throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
        Internal.setIngredientRegistry((IngredientRegistry)unsafe.allocateInstance(Registry.class));
        Recipe root=make("table",1,1,inputs("component",4,"core",1));
        Recipe core=make("core",1,1,inputs("component",4,"iron",1));
        Recipe part=make("component",2,2,inputs("iron",5,"coal",4));
        RecipeBookmarkGroup a=group(1,root), b=group(2,core), c=group(3,part);
        RecipeBookmarkGroup forward=a.mergedWith(b).mergedWith(c);
        RecipeBookmarkGroup reverse=c.mergedWith(b).mergedWith(a);
        check(get(forward,"component").amount==8 && get(reverse,"component").amount==8);
        check(part.selfOutputAmount==2 && core.selfOutputAmount==1); // Original snapshots untouched.
        check(get(forward,"component").getInputs().get(0).getDisplayAmount()==20);
        Field chainField=RecipeBookmarkGroup.class.getDeclaredField("chain"); chainField.setAccessible(true);
        RecipeChain displayChain=(RecipeChain)chainField.get(reverse);
        List<RecipeBookmarkItem<?>> displayOrder=displayChain.getDisplayOutputs();
        check(displayOrder.indexOf(get(reverse,"table")) < displayOrder.indexOf(get(reverse,"core")));
        check(displayOrder.indexOf(get(reverse,"core")) < displayOrder.indexOf(get(reverse,"component")));
        check(reverse.getItemsInternal().get(0)==get(reverse,"component"));
        check(Collections.frequency(displayOrder,get(reverse,"component"))==1);
        RecipeBookmarkGroup peers=group(9,make("left",1,1,inputs("shared",1)),make("right",1,1,inputs("shared",1)),make("shared",1,0,inputs()));
        List<RecipeBookmarkItem<?>> peerOrder=((RecipeChain)chainField.get(peers)).getDisplayOutputs();
        check(peerOrder.indexOf(get(peers,"left")) < peerOrder.indexOf(get(peers,"right")));
        check(peerOrder.indexOf(get(peers,"right")) < peerOrder.indexOf(get(peers,"shared")));
        check(peerOrder.equals(((RecipeChain)chainField.get(peers)).getDisplayOutputs()));
        check(InputUsage.CONSUMED.required(2,12)==24);
        check(new InputUsage(Long.MAX_VALUE,null).required(2,12)==2);
        check(new InputUsage(10,null).required(2,21)==6);
        check(new InputUsage(10,null).required(2,0)==0);
        RecipeBookmarkGroup tooling=group(10,make("first",1,20,inputs("tool",2)),make("second",1,30,inputs("tool",1)));
        get(tooling,"first").inputs.get(0).usage=new InputUsage(Long.MAX_VALUE,null);
        get(tooling,"second").inputs.get(0).usage=new InputUsage(Long.MAX_VALUE,null);
        tooling.update();
        RecipeChain toolChain=(RecipeChain)chainField.get(tooling);
        for(RecipeBookmarkItem<?> node:toolChain.graphStorage.nodes()) if(node.ingredient.equals("tool")) check(node.amount==2);
        get(tooling,"first").inputs.get(0).usage=new InputUsage(10,null);
        tooling.update();
        for(RecipeBookmarkItem<?> node:toolChain.graphStorage.nodes()) if(node.ingredient.equals("tool")) check(node.amount==5);
        RecipeBookmarkGroup progress=group(15,make("cake",1,2,inputs("milk",3)));
        RecipeChain progressChain=(RecipeChain)chainField.get(progress);
        progressChain.beginExecution();
        progressChain.recordCompletion(get(progress,"cake"),1);
        check(progressChain.hasExecutionProgress() && get(progress,"cake").selfOutputAmount==2);
        Method remaining=RecipeChain.class.getDeclaredMethod("remainingGoal",RecipeBookmarkItem.class); remaining.setAccessible(true);
        check((Long)remaining.invoke(progressChain,get(progress,"cake"))==1);
        progressChain.beginExecution();
        check((Long)remaining.invoke(progressChain,get(progress,"cake"))==1);
        RecipeBookmarkItem<?> compactTarget=get(progress,"cake");
        check(compactTarget.startsNewRow());progress.viewMode=1;
        check(!compactTarget.startsNewRow());progress.viewMode=0;
        mezz.jei.bookmarks.DummyBookmarkItem<String> section=new mezz.jei.bookmarks.DummyBookmarkItem<>("iron",progress,()->3L);
        check(!section.startsNewRow());section.setSectionStart(true);check(section.startsNewRow());
        // AE reports partial coalesced deliveries using a copied requester; retain progress across retry.
        RecipeBookmarkGroup aeProgress=group(16,make("aeTarget",1,16,inputs("iron",1)));
        RecipeChain aeChain=(RecipeChain)chainField.get(aeProgress);
        aeChain.beginExecution();
        aeChain.recordCompletion(get(aeProgress,"aeTarget").copy(),6);
        aeChain.beginExecution();
        check(aeChain.remainingGoal(get(aeProgress,"aeTarget"))==10);
        aeChain.recordCompletion(get(aeProgress,"aeTarget").copy(),10);
        check(aeChain.remainingGoal(get(aeProgress,"aeTarget"))==0);
        check(get(aeProgress,"aeTarget").selfOutputAmount==16);
        check(TransposerSelection.selectIndex(Arrays.asList("water","milk","lava"),"milk",3,3)==1);
        boolean invalidPair=false;
        try {TransposerSelection.selectIndex(Arrays.asList("water","milk"),"milk",2,1);} catch(IllegalArgumentException ex){invalidPair=true;}
        check(invalidPair);
        check(InfusionDemand.feedCount(80,10)==8);
        check(InfusionDemand.feedCount(80,80)==1);
        check(InfusionDemand.feedCount(90,80)==2);
        check(InfusionDemand.feedCount(0,80)==0);
        RecipeBookmarkGroup copy=forward.copyForEdit();
        copy.finishLoading();copy.finishLoading();
        check(copy.getItemsInternal().size()==3 && get(copy,"component").amount==8);
        // A on an input row removes its whole owning recipe, leaving required raw inputs.
        BookmarkItem<?> row=get(copy,"component").getInputs().get(0);
        RecipeBookmarkGroup removed=copy.without(row);
        check(removed.getItemsInternal().size()==2);
        check(get(removed,"core").amount==1);
        removed=removed.without(get(removed,"core"));
        check(removed.getItemsInternal().size()==1);
        removed=removed.without(get(removed,"table"));
        check(removed.getItemsInternal().isEmpty() && removed.getItems().isEmpty());
        check(forward.getItemsInternal().size()==3); // Removal was transactional.
        List<RecipeBookmarkGroup> split=forward.split(new java.util.concurrent.atomic.AtomicInteger(10)::getAndIncrement);
        check(split.size()==3);for(RecipeBookmarkGroup g:split)check(g.getItemsInternal().size()==1);
        RecipeBookmarkGroup roundtrip=split.get(0).mergedWith(split.get(1)).mergedWith(split.get(2));
        check(get(roundtrip,"component").amount==8);
        Recipe bad=make("component",2,2,inputs("table",1));
        try { a.mergedWith(group(4,bad)); throw new AssertionError("cycle accepted"); }
        catch(mezz.jei.autocrafting.toposort.CyclePresentException expected) { }
        check(a.getItemsInternal().size()==1);
        Recipe mixed=make("mixed",1,1,inputs("iron|copper",2,"iron",3));
        Recipe iron=make("iron",1,0,inputs("ore",1));
        RecipeBookmarkGroup overlapping=group(7,mixed,iron);
        check(get(overlapping,"iron").amount==5); // Parallel requirements must add, not overwrite.
        BookmarkList edits=new BookmarkList(Internal.getIngredientRegistry()) {
            @Override public void saveBookmarks() { }
            @Override public void notifyListenersOfChange() { }
        };
        edits.add(a); edits.add(b);
        edits.mergeRecipeGroups(a,b);
        check(edits.getBookmarkGroup(1)==null);
        check(edits.undoRecipeEdit());
        check(edits.getBookmarkGroup(1)!=null && edits.getBookmarkGroup(2)!=null);
        RecipeBookmarkGroup restored=(RecipeBookmarkGroup)edits.getBookmarkGroup(1);
        check(edits.remove(get(restored,"table")));
        check(edits.getBookmarkGroup(1)==null);
        check(edits.undoRecipeEdit());
        check(edits.getBookmarkGroup(1)!=null);
        edits.mergeRecipeGroups(Arrays.asList(2,1));
        check(edits.getBookmarkGroup(1)!=null && edits.getBookmarkGroup(2)==null);
        check(edits.getBookmarkIndex(1)==0); // Range merge stays anchored at first selected position.
        check(edits.undoRecipeEdit());
        check(edits.getBookmarkGroup(1)!=null && edits.getBookmarkGroup(2)!=null);
        Recipe displayOwner=make("display",1,12,inputs());
        displayOwner.amount=12;
        RecipeBookmarkItem<String> oreInput=new RecipeBookmarkItem<>(new ArrayList<>(Arrays.asList("iron","otherIron")),3);
        RecipeBookmarkItem<String> fixedInput=new RecipeBookmarkItem<>(new ArrayList<>(Arrays.asList("iron")),2);
        RecipeInputBookmarkItem display=new RecipeInputBookmarkItem(displayOwner,Arrays.asList(oreInput,fixedInput));
        check(display.getDisplayAmount()==60);
        check(display.getRequirementDescription().contains("指定物品：24"));
        check(display.getRequirementDescription().contains("可用配方允许的替代材料：36"));
        check(oreInput.amount==3 && fixedInput.amount==2); // View never modifies recipe requirements.
        System.out.println("PASS: bidirectional merge, shared output quantity, repeated rebuild, input-owner deletion, repeated A, split/remerge, immutable originals, cycle rejection");
    }
}

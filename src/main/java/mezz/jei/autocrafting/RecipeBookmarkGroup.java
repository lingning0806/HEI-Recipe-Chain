package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RecipeBookmarkGroup extends BookmarkGroup {
    public int viewMode;
    private mezz.jei.nova.suite.GroupMaterials materialCache;
    private long materialAt;
    public mezz.jei.nova.suite.GroupMaterials materials() {
        long now=System.currentTimeMillis();
        if(materialCache==null || now-materialAt>500) {materialCache=new mezz.jei.nova.suite.GroupMaterials(this);materialAt=now;}
        return materialCache;
    }
    public long remainingGoal(RecipeBookmarkItem<?> recipe) {return chain.remainingGoal(recipe);}
    public void cycleView() {viewMode=(viewMode+1)%3;for(BookmarkItem<?> item:items)((RecipeBookmarkItem<?>)item).savedViewMode=viewMode;materialCache=null;Internal.getBookmarkList().saveBookmarks();Internal.getBookmarkList().notifyListenersOfChange();}
    private final RecipeChain chain = new RecipeChain(this);

    public RecipeBookmarkGroup(int id) {
        super(id);
    }

    public void addItemInternal(BookmarkItem<?> item) {
        super.addItemInternal(item);
    }

    public boolean addItem(BookmarkItem<?> item) {
        if (canAddItem(item)) {
            addItemInternal(item);
            chain.addOutput((RecipeBookmarkItem<?>) item); // From canAddItem
            return true;
        }
        return false;
    }

    public boolean canAddItem(BookmarkItem<?> item) {
        return item instanceof RecipeBookmarkItem;
    }

    @Override
    public List<BookmarkItem<?>> getItems() {
        List<BookmarkItem<?>> list = new ArrayList<>();
        if (!Config.areRecipeBookmarksEnabled()) {
            return list;
        }
        if(viewMode!=0) {
            for(RecipeBookmarkItem<?> item:chain.getDisplayOutputs()) if(item.selfOutputAmount>0)list.add(item);
            mezz.jei.nova.suite.GroupMaterials report=materials();
            list.addAll(report.rows(viewMode==1?report.total.required:report.current.required,this));
            return list;
        }
        for (RecipeBookmarkItem<?> item : chain.getDisplayOutputs()) {
            if (item.isPopulated() && item.inputs != null) {
                list.add(item);
                if (chain.secondaryOutputs.containsKey(item)) {
                    list.addAll(chain.secondaryOutputs.get(item));
                }
                list.addAll(item.getInputs());
            }
        }
        return list;
    }

    public void finishLoading() {
        materialCache=null;
        if(!items.isEmpty())viewMode=((RecipeBookmarkItem<?>)items.get(0)).savedViewMode;
        chain.rebuildGraph();
    }

    public List<IIngredientListElement<?>> getIngredientListElements() {
        return getItems().stream().map(this::getIngredientListElement).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public boolean acceptsChanges() {
        return false;
    }

    public void update() {
        chain.calculateCrafting();
    }

    public RecipeBookmarkItem<?> ownerOf(BookmarkItem<?> entry) {
        for (BookmarkItem<?> stored : items) {
            RecipeBookmarkItem<?> recipe = (RecipeBookmarkItem<?>) stored;
            if (recipe == entry || (recipe.getInputs() != null && recipe.getInputs().contains(entry))) return recipe;
        }
        return null;
    }

    protected RecipeBookmarkGroup newGroup(int groupId) { return new RecipeBookmarkGroup(groupId); }

    public RecipeBookmarkGroup copyForEdit() {
        RecipeBookmarkGroup copy = newGroup(id);
        for (BookmarkItem<?> entry : items) {
            RecipeBookmarkItem<?> recipe = (RecipeBookmarkItem<?>) entry;
            if (!recipe.isPopulated()) continue;
            RecipeBookmarkItem<?> cloned = recipe.copy();
            cloned.aliases = new ArrayList(cloned.aliases);
            cloned.secondaryTo = null;
            copy.addItemInternal(cloned);
        }
        copy.finishLoading();
        return copy;
    }

    public RecipeBookmarkGroup without(BookmarkItem<?> entry) {
        RecipeBookmarkItem<?> owner = ownerOf(entry);
        if (owner == null) return this;
        int index = items.indexOf(owner);
        RecipeBookmarkGroup copy = copyForEdit();
        copy.items.remove(index);
        copy.finishLoading();
        copy.chain.removeDanglingNodes();
        return copy;
    }

    public List<RecipeBookmarkGroup> split(java.util.function.IntSupplier ids) {
        List<RecipeBookmarkGroup> result = new ArrayList<>();
        for (BookmarkItem<?> entry : items) {
            RecipeBookmarkItem<?> recipe = (RecipeBookmarkItem<?>) entry;
            RecipeBookmarkGroup single = newGroup(ids.getAsInt());
            RecipeBookmarkItem<?> copy = recipe.copy();
            copy.aliases = new ArrayList(copy.aliases);
            copy.selfOutputAmount = Math.max(recipe.amount, recipe.outputAmount);
            single.addItemInternal(copy);
            single.finishLoading();
            result.add(single);
        }
        return result;
    }

    @Override
    public void removeItem(BookmarkItem<?> entry) {
        RecipeBookmarkItem<?> owner = ownerOf(entry);
        if (owner != null) chain.removeNode(owner);
    }

    /** Build separately so invalid/cyclic merges leave both original groups intact. */
    public RecipeBookmarkGroup mergedWith(RecipeBookmarkGroup other) {
        RecipeBookmarkGroup merged = newGroup(id);
        java.util.Map<String, RecipeBookmarkItem<?>> selected = new java.util.LinkedHashMap<>();
        for (RecipeBookmarkGroup source : new RecipeBookmarkGroup[]{this, other}) {
            for (BookmarkItem<?> entry : source.getItemsInternal()) {
                RecipeBookmarkItem<?> item = (RecipeBookmarkItem<?>) entry;
                if (!item.isPopulated()) continue;
                String key = Internal.getIngredientRegistry().getUniqueId(item.ingredient);
                RecipeBookmarkItem<?> existing = selected.get(key);
                if (existing != null) {
                    if (existing.recipe != item.recipe && !existing.recipe.equals(item.recipe)) {
                        throw new IllegalArgumentException("同一产物选择了不同配方，请先统一配方");
                    }
                    existing.selfOutputAmount = Math.addExact(existing.selfOutputAmount, item.selfOutputAmount);
                } else {
                    RecipeBookmarkItem<?> copy = item.copy();
                    copy.aliases = new ArrayList(copy.aliases);
                    copy.secondaryTo = null;
                    selected.put(key, copy);
                    merged.addItemInternal(copy);
                }
            }
        }
        merged.finishLoading();
        // A consumed output becomes an intermediate; only terminal products remain goals.
        for (RecipeBookmarkItem<?> node : merged.chain.graphStorage.nodes()) {
            if (!merged.chain.graphStorage.predecessors(node).isEmpty()) node.selfOutputAmount = 0;
        }
        merged.update(); // Also rejects dependency cycles before committing the merge.
        return merged;
    }

    public boolean hasExecutionProgress() { return chain.hasExecutionProgress(); }

    public void autocraft() {
        IAutocraftingHandler handler = Internal.getRuntime().getAutocraftingHandler();
        if (!handler.isActive()) {
            ((AutocraftingHandler) Internal.getRuntime().getAutocraftingHandler()).start(chain);
        }
    }

    public int getColor() {
        return Config.getRecipeBookmarkGroupColor();
    }

    public List<IIngredientListElement> getMissingIngredients() {
        List<BookmarkItem<?>> missing = new ObjectArrayList<>();
        chain.calculateMissingIngredients(null, missing);
        return missing.stream().map(this::getIngredientListElement).filter(Objects::nonNull).collect(Collectors.toList());
    }
}

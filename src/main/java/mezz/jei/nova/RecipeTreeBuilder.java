package mezz.jei.nova;

import mezz.jei.Internal;
import mezz.jei.api.recipe.*;
import mezz.jei.autocrafting.*;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.gui.Focus;
import mezz.jei.recipes.RecipeRegistry;
import java.util.*;

/** Creates canonical recipes only; quantities, display and edits stay in RecipeBookmarkGroup. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RecipeTreeBuilder {
    private RecipeTreeBuilder() { }

    public static RecipeBookmarkGroup build(int id, Object target, IRecipeWrapper recipe, IRecipeCategory category) {
        RecipeBookmarkGroup group = new RecipeBookmarkGroup(id);
        RecipeBookmarkItem root = new RecipeBookmarkItem(target);
        root.setGroup(group);
        root.populateWith(recipe, category);
        if (!root.isPopulated() || root.outputAmount <= 0) throw new IllegalArgumentException("Invalid root output");
        root.selfOutputAmount = root.outputAmount;
        group.addItemInternal(root);
        List<RecipeBookmarkItem<?>> pending = new ArrayList<>();
        pending.add(root);
        Set<String> outputs = new HashSet<>();
        outputs.add(Internal.getIngredientRegistry().getUniqueId(target));
        for (int i = 0; i < pending.size(); i++) {
            for (RecipeBookmarkItem<?> input : pending.get(i).inputs) {
                if (input.infusion != null) continue;
                if (input.aliases.stream().anyMatch(a -> outputs.contains(Internal.getIngredientRegistry().getUniqueId(a)))) continue;
                if (pending.size() >= 256) throw new IllegalArgumentException("Recipe tree exceeds 256 recipes");
                RecipeBookmarkItem selected = select(input);
                if (selected == null) continue;
                selected.setGroup(group);
                selected.selfOutputAmount = 0;
                outputs.add(Internal.getIngredientRegistry().getUniqueId(selected.ingredient));
                group.addItemInternal(selected);
                pending.add(selected);
            }
        }
        group.finishLoading(); // Validates cycles and calculates all shared requirements before commit.
        return group;
    }

    private static RecipeBookmarkItem select(RecipeBookmarkItem<?> input) {
        Object chosen = null;
        IRecipeWrapper recipe = null;
        IRecipeCategory category = null;
        // Multiple explicit alternatives are ambiguous too: never pick the first silently.
        for (Object alias : input.aliases) {
            IRecipeWrapper favorite = FavoriteRecipes.getFavorite(alias);
            if (favorite == null) continue;
            if (recipe != null && (recipe != favorite || !same(chosen, alias))) return null;
            chosen = alias; recipe = favorite; category = FavoriteRecipes.getFavoriteCategory(alias);
        }
        if (recipe == null) {
            RecipeRegistry registry = Internal.getRuntime().getRecipeRegistry();
            mezz.jei.nova.preferences.PreferenceRules preferences=mezz.jei.nova.preferences.Preferences.rules();
            List<mezz.jei.nova.preferences.PreferenceRules.Candidate<RecipeBookmarkItem>> candidates=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(Object alias:input.aliases) {
                IFocus<Object> focus=new Focus<>(IFocus.Mode.OUTPUT,alias);
                for(IRecipeCategory candidateCategory:registry.getRecipeCategories(focus)) {
                    for(Object raw:registry.getRecipeWrappers(candidateCategory,focus)) {
                        IRecipeWrapper wrapper=(IRecipeWrapper)raw;
                        String key=candidateCategory.getUid()+":"+registry.getRecipeId(wrapper)+":"+mezz.jei.nova.preferences.Preferences.identity(alias);
                        if(!seen.add(key)) continue;
                        if(candidates.size()>=512 || preferences.isEmpty() && !candidates.isEmpty()) return null;
                        RecipeBookmarkItem candidate=new RecipeBookmarkItem(alias);
                        candidate.recipe=wrapper;candidate.category=candidateCategory;
                        try {candidates.add(preferences.isEmpty()?new mezz.jei.nova.preferences.PreferenceRules.Candidate<>(candidate,Collections.emptyList(),Collections.emptyList(),Collections.emptyList()):mezz.jei.nova.preferences.Preferences.candidate(candidate,alias,wrapper,candidateCategory));}
                        catch(RuntimeException ex) {return null;}
                    }
                }
            }
            RecipeBookmarkItem preferred=preferences.choose(candidates);
            if(preferred==null) return null;
            chosen=preferred.ingredient;recipe=preferred.recipe;category=preferred.category;
        }
        if (recipe == null || category == null) return null;
        RecipeBookmarkItem selected = new RecipeBookmarkItem(chosen);
        selected.populateWith(recipe, category);
        return selected.isPopulated() ? selected : null;
    }

    private static boolean same(Object a, Object b) {
        return Internal.getIngredientRegistry().getUniqueId(a).equals(Internal.getIngredientRegistry().getUniqueId(b));
    }
}

package mezz.jei.autocrafting;

import com.google.common.base.Preconditions;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mezz.jei.Internal;
import mezz.jei.autocrafting.toposort.TopologicalSort;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class RecipeChain {
    // noinspection
    public final MutableValueGraph<RecipeBookmarkItem<?>, Long> graphStorage = ValueGraphBuilder.directed()
            .allowsSelfLoops(false)
            .nodeOrder(ElementOrder.insertion())
            .expectedNodeCount(128)
            .build();

    public final Map<RecipeBookmarkItem<?>, List<RecipeBookmarkItem<?>>> secondaryOutputs = new Object2ObjectOpenHashMap<>();

    private final RecipeBookmarkGroup group;
    private final Map<String,Long> completedGoals = new HashMap<>();
    private boolean executionSession;

    public void beginExecution() {
        boolean remaining = group.getItemsInternal().stream().map(e -> (RecipeBookmarkItem<?>)e)
            .anyMatch(e -> remainingGoal(e) > 0);
        mezz.jei.util.Log.get().info("[HEI-DIAG] execution begin mode="+(remaining?"remaining-target":"new-round")+" completed="+completedGoals);
        if (!remaining) completedGoals.clear();
        executionSession = true;
    }
    public long remainingGoal(RecipeBookmarkItem<?> item) {
        return Math.max(0, item.selfOutputAmount - completedGoals.getOrDefault(Internal.getIngredientRegistry().getUniqueId(item.ingredient),0L));
    }
    public void recordCompletion(RecipeBookmarkItem<?> item,long amount) {
        if (item.selfOutputAmount <= 0 || amount <= 0) return;
        String key=Internal.getIngredientRegistry().getUniqueId(item.ingredient);
        completedGoals.put(key, Math.min(item.selfOutputAmount, Math.addExact(completedGoals.getOrDefault(key,0L),amount)));
    }
    public boolean hasExecutionProgress() { return !completedGoals.isEmpty(); }

    private final Map<RecipeBookmarkItem<?>, Map<RecipeBookmarkItem<?>, List<RecipeBookmarkItem<?>>>> edgeInputs = new IdentityHashMap<>();

    public RecipeChain(RecipeBookmarkGroup group) {
        this.group = group;
    }

    public boolean addOutput(RecipeBookmarkItem<?> output) {
        if (output.selfOutputAmount == 0) output.selfOutputAmount = output.outputAmount;
        // Favorite expansion is an explicit creation operation, never part of deletion/reload.
        List<RecipeBookmarkItem<?>> pending = new ArrayList<>();
        pending.add(output);
        for (int i = 0; i < pending.size(); i++) {
            if (pending.size() > 256) throw new IllegalArgumentException("配方链超过256个节点");
            RecipeBookmarkItem<?> item = pending.get(i);
            if (item.inputs == null) continue;
            for (RecipeBookmarkItem<?> input : item.inputs) {
                if (input.infusion != null || findOutputUsingAnAlias(input) != null) continue;
                RecipeBookmarkItem<?> selected = new RecipeBookmarkItem<>(new ArrayList<>(input.aliases));
                selected.setGroup(group);
                selected.populateWithFavorite();
                if (selected.isPopulated()) {
                    group.addItemInternal(selected);
                    pending.add(selected);
                }
            }
        }
        rebuildGraph();
        return false;
    }

    public RecipeBookmarkItem<?> findOutputUsingAnAlias(RecipeBookmarkItem<?> input) {
        if (input.infusion != null) return null;
        IngredientRegistry registry = Internal.getIngredientRegistry();
        Set<String> allowed = input.aliases.stream().map(registry::getUniqueId).collect(Collectors.toSet());
        // Match the actual selected output, not its wider historical OreDictionary aliases.
        for (BookmarkItem<?> entry : group.getItemsInternal()) {
            RecipeBookmarkItem<?> candidate = (RecipeBookmarkItem<?>) entry;
            if (candidate.isPopulated() && allowed.contains(registry.getUniqueId(candidate.ingredient))) return candidate;
        }
        return null;
    }

    public RecipeBookmarkItem<?> findOutputWithSameRecipe(RecipeBookmarkItem<?> output) {
        // Co-products need explicit per-output accounting; do not create implicit secondary links.
        return null;
    }

    public void calculateCrafting() {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            node.amount = node.selfOutputAmount;
        }
        TopologicalSort.topologicalSort(graphStorage, null).forEach(this::calculateCrafting);
    }

    public void calculateCrafting(RecipeBookmarkItem<?> needed) {
        if (graphStorage.predecessors(needed).isEmpty())
            return;
        long reusable = 0;
        for (RecipeBookmarkItem<?> requester : graphStorage.predecessors(needed)) {
            if (requester.outputAmount == 0) continue;
            long batches = mezz.jei.nova.ChainMath.batches(requester.amount, requester.outputAmount);
            List<RecipeBookmarkItem<?>> requirements = edgeInputs.getOrDefault(requester, Collections.emptyMap()).get(needed);
            if (requirements == null) {
                needed.amount = Math.addExact(needed.amount, Math.multiplyExact(batches, edgeValue(requester, needed)));
                continue;
            }
            long simultaneous = 0;
            for (RecipeBookmarkItem<?> input : requirements) {
                long count = input.usage.required(input.amount, batches);
                if (input.usage.uses == Long.MAX_VALUE) simultaneous = Math.addExact(simultaneous, count);
                else needed.amount = Math.addExact(needed.amount, count);
            }
            reusable = Math.max(reusable, simultaneous);
        }
        needed.amount = Math.addExact(needed.amount, reusable);
        if (needed.secondaryTo != null) {
            needed.secondaryTo.amount = Math.max(needed.secondaryTo.amount, needed.amount);
        }
    }

    private Long edgeValue(RecipeBookmarkItem<?> nodeU, RecipeBookmarkItem<?> nodeV) {
        Long value = graphStorage.edgeValueOrDefault(nodeU, nodeV, null);
        if (value == null) {
            Preconditions.checkArgument(graphStorage.nodes().contains(nodeU), "Node %s is not an element of this graph.", nodeU);
            Preconditions.checkArgument(graphStorage.nodes().contains(nodeV), "Node %s is not an element of this graph.", nodeV);
            throw new IllegalArgumentException(String.format("Edge connecting %s to %s is not present in this graph.", nodeU, nodeV));
        }
        return value;
    }

    public List<RecipeBookmarkItem<?>> getDisplayOutputs() {
        // Display only: canonical insertion order breaks ties between ready recipes.
        Map<RecipeBookmarkItem<?>, Integer> order = new IdentityHashMap<>();
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) order.put(node, order.size());
        return TopologicalSort.topologicalSort(graphStorage, Comparator.comparingInt(order::get));
    }

    public void removeNode(RecipeBookmarkItem<?> node) {
        group.getItemsInternal().remove(node);
        rebuildGraph();
        removeDanglingNodes();
    }

    public void removeDanglingNodes() {
        Set<RecipeBookmarkItem<?>> live = new HashSet<>();
        Deque<RecipeBookmarkItem<?>> pending = new ArrayDeque<>();
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) if (node.selfOutputAmount > 0) pending.add(node);
        while (!pending.isEmpty()) {
            RecipeBookmarkItem<?> node = pending.removeFirst();
            if (live.add(node)) pending.addAll(graphStorage.successors(node));
        }
        group.getItemsInternal().removeIf(node -> !live.contains(node));
        rebuildGraph();
    }

    private boolean calculateRemainderFlow(Stack<RecipeBookmarkItem<?>> recipeList, List<BookmarkItem<?>> missing) {
        boolean hasReturns = group.getItemsInternal().stream().map(e -> (RecipeBookmarkItem<?>) e)
                .flatMap(e -> e.inputs.stream()).anyMatch(i -> i.infusion != null || i.usage.remainder != null || i.usage.uses > 0);
        if (!hasReturns && !executionSession) return false;
        Map<String, mezz.jei.nova.RemainderFlow.Recipe> recipes = new LinkedHashMap<>();
        Map<String, RecipeBookmarkItem<?>> selected = new LinkedHashMap<>();
        Map<String, Object> representatives = new HashMap<>();
        Map<String,mezz.jei.nova.InfusionDemand> infusions = new HashMap<>();
        Map<String, Long> goals = new LinkedHashMap<>();
        for (BookmarkItem<?> entry : group.getItemsInternal()) {
            RecipeBookmarkItem<?> recipe = (RecipeBookmarkItem<?>) entry;
            String output = Internal.getIngredientRegistry().getUniqueId(recipe.ingredient);
            selected.put(output, recipe);
            representatives.put(output, recipe.ingredient);
            if (remainingGoal(recipe) > 0) goals.put(output, remainingGoal(recipe));
            List<mezz.jei.nova.RemainderFlow.Input> inputs = new ArrayList<>();
            for (RecipeBookmarkItem<?> input : recipe.inputs) {
                List<String> aliases = new ArrayList<>();
                if (input.infusion != null) {
                    String key=input.infusion.key;
                    infusions.put(key,input.infusion); representatives.put(key,input.ingredient);
                    inputs.add(new mezz.jei.nova.RemainderFlow.Input(Collections.singletonList(key),input.amount,0,null,0));
                    continue;
                }
                for (Object alias : input.aliases) {
                    String key = Internal.getIngredientRegistry().getUniqueId(alias);
                    aliases.add(key); representatives.put(key, alias);
                }
                // The bookmark's selected material is the stable first preference; inventory alternatives remain allowed.
                String preferred=Internal.getIngredientRegistry().getUniqueId(input.ingredient);
                if(aliases.remove(preferred)) aliases.add(0,preferred);
                String returned = input.usage.remainder == null ? null : Internal.getIngredientRegistry().getUniqueId(input.usage.remainder);
                long returnedCount = input.usage.remainder == null ? 0 : IngredientUtil.getCount(input.usage.remainder);
                if (returned != null) representatives.put(returned, input.usage.remainder);
                inputs.add(new mezz.jei.nova.RemainderFlow.Input(aliases, input.amount, input.usage.uses, returned, returnedCount));
            }
            recipes.put(output, new mezz.jei.nova.RemainderFlow.Recipe(output, recipe.outputAmount, inputs));
        }
        if(recipeList!=null) mezz.jei.util.Log.get().info("[HEI-DIAG] execution goals="+goals+" completed="+completedGoals);
        Map<String, Long> inventory = new HashMap<>();
        for (ItemStack stack : Minecraft.getMinecraft().player.inventory.mainInventory) {
            if (!stack.isEmpty()) inventory.merge(Internal.getIngredientRegistry().getUniqueId(stack), (long) stack.getCount(), Math::addExact);
        }
        mezz.jei.nova.ae2.Ae2Inventory.addTo(inventory);
        if(recipeList!=null) mezz.jei.util.Log.get().info("[HEI-DIAG] execution inventory="+inventory);
        mezz.jei.nova.RemainderFlow.Result result = new mezz.jei.nova.RemainderFlow(recipes, inventory).plan(goals, recipeList != null);
        if (missing != null) result.required.forEach((key, count) -> {
            Object ingredient = representatives.get(key);
            if (ingredient != null && count > 0) missing.add(infusions.containsKey(key) ? new mezz.jei.bookmarks.InfusionBookmarkItem(ingredient,null,()->count,infusions.get(key)) : new DummyBookmarkItem<>(ingredient, null, () -> count));
        });
        if (recipeList != null) {
            // Execute the ledger-validated schedule; UI order and bookmark goals stay unchanged.
            for (int i = result.steps.size() - 1; i >= 0; i--) {
                RecipeBookmarkItem<?> step = selected.get(result.steps.get(i)).copy();
                step.amount = step.outputAmount;
                mezz.jei.nova.ExecutionSchedule.Operation operation=result.execution.get(i);
                if(!operation.barrier) step.executionInputs=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(operation.consumed));
                recipeList.push(step);
            }
        }
        return true;
    }

    public void calculateMissingIngredients(Stack<RecipeBookmarkItem<?>> recipeList, List<BookmarkItem<?>> missing) {
        if (calculateRemainderFlow(recipeList, missing)) return;
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            node.amount = node.selfOutputAmount;
        }

        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        InventoryPlayer inv = Minecraft.getMinecraft().player.inventory;
        Map<String, Long> invCounts = new Object2LongOpenHashMap<>(inv.getSizeInventory() * 2);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            String uniqueId = ingredientRegistry.getUniqueId(stack);
            invCounts.compute(uniqueId, (k, v) -> v == null ? stack.getCount() : v + stack.getCount());
        }

        mezz.jei.nova.ae2.Ae2Inventory.addTo(invCounts);
        final Map<String, BookmarkItem<?>> lookup = missing == null ? null : new HashMap<>();
        TopologicalSort.topologicalSort(graphStorage, null).forEach(ingredient -> calculateMissingIngredients(ingredient, invCounts, recipeList, lookup));
        if (missing != null) {
            for (Map.Entry<String, BookmarkItem<?>> entry : lookup.entrySet()) {
                missing.add(entry.getValue());
            }
        }
        calculateCrafting(); // Reset the displayed amounts.
    }

    public void calculateMissingIngredients(RecipeBookmarkItem<?> needed, Map<String, Long> invCounts,
                                            Stack<RecipeBookmarkItem<?>> recipeList, Map<String, BookmarkItem<?>> lookup) {
        calculateCrafting(needed);
        if (needed.amount <= 0) {
            return;
        }
        String uniqueId = null;
        if (needed.selfOutputAmount == 0) {
            uniqueId = Internal.getIngredientRegistry().getUniqueId(needed.ingredient);
            invCounts.computeIfPresent(uniqueId, (k, v) -> {
                long used = Math.min(v, needed.amount);
                needed.amount -= used;
                return v - used;
            });
        }
        if (recipeList != null && needed.amount > 0 && needed.category != null) {
            // If we're preparing for autocrafting and this can be crafted, add it.
            recipeList.add(needed.copy());
        } else if (lookup != null && needed.amount > 0 && graphStorage.successors(needed).isEmpty()) {
            if (uniqueId == null) {
                uniqueId = Internal.getIngredientRegistry().getUniqueId(needed.ingredient);
            }
            // If we're preparing just to show the missing items, we can add it.
            lookup.compute(uniqueId, (k, v) -> {
                if (v == null) {
                    final long staticAmount = (int) needed.amount;
                    return new DummyBookmarkItem<>(needed, null, () -> staticAmount);
                } else {
                    v.amount += needed.amount;
                }
                return v;
            });
        }
    }

    public void rebuildGraph() {
        for (RecipeBookmarkItem<?> node : new ArrayList<>(graphStorage.nodes())) graphStorage.removeNode(node);
        secondaryOutputs.clear();
        edgeInputs.clear();
        // Only selected recipes are authoritative. Raw inputs and UI rows are derived.
        group.getItemsInternal().removeIf(entry -> !((RecipeBookmarkItem<?>) entry).isPopulated());
        List<BookmarkItem<?>> stored = new ArrayList<>(group.getItemsInternal());
        Map<Set<String>, RecipeBookmarkItem<?>> rawInputs = new LinkedHashMap<>();
        for (BookmarkItem<?> entry : stored) {
            RecipeBookmarkItem<?> requester = (RecipeBookmarkItem<?>) entry;
            requester.secondaryTo = null;
            requester.populateWith(requester.recipe, requester.category);
            if (requester.outputAmount <= 0) throw new IllegalArgumentException("配方产出数量无效");
            graphStorage.addNode(requester);
        }
        for (BookmarkItem<?> entry : stored) {
            RecipeBookmarkItem<?> requester = (RecipeBookmarkItem<?>) entry;
            if (requester.inputs == null) continue;
            for (RecipeBookmarkItem<?> input : requester.inputs) {
                RecipeBookmarkItem<?> other = findOutputUsingAnAlias(input);
                if (other == requester) throw new IllegalArgumentException("配方依赖自身，无法建立合成链");
                if (other == null) {
                    Set<String> key = input.infusion != null ? Collections.singleton(input.infusion.key) : input.aliases.stream().map(Internal.getIngredientRegistry()::getUniqueId).collect(Collectors.toSet());
                    other = rawInputs.get(key);
                    if (other == null) {
                        other = new RecipeBookmarkItem<>(new ArrayList<>(input.aliases));
                        other.setGroup(group);
                        rawInputs.put(key, other);
                    }
                }
                edgeInputs.computeIfAbsent(requester, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(other, ignored -> new ArrayList<>()).add(input);
                long previous = graphStorage.edgeValueOrDefault(requester, other, 0L);
                graphStorage.putEdgeValue(requester, other, Math.addExact(previous, input.amount));
            }
        }
        calculateCrafting();
    }

}

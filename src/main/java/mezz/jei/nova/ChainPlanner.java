package mezz.jei.nova;

import java.util.*;

/** Read-only demand graph. No Minecraft, inventory or network dependencies. */
public final class ChainPlanner {
    public interface Resolver { Recipe resolve(String key); }
    public static final class Input {
        public final String key;
        public final long amount;
        public final boolean catalyst;
        public Input(String key, long amount, boolean catalyst) {
            if (amount <= 0) throw new IllegalArgumentException("Input amount must be positive");
            this.key = key; this.amount = amount; this.catalyst = catalyst;
        }
    }
    public static final class Recipe {
        public final long output;
        public final List<Input> inputs;
        public Recipe(long output, List<Input> inputs) {
            if (output <= 0) throw new IllegalArgumentException("Output amount must be positive");
            this.output = output; this.inputs = inputs;
        }
    }
    public static final class Plan {
        public final Map<String, Recipe> recipes = new LinkedHashMap<>();
        public final Map<String, Long> demands = new LinkedHashMap<>();
        public final Map<String, Long> materials = new LinkedHashMap<>();
        public final Map<String, Long> catalysts = new LinkedHashMap<>();
        public final List<String> problems = new ArrayList<>();
        public boolean complete() { return problems.isEmpty(); }
    }
    public static Plan build(String target, long count, Resolver resolver) {
        Plan p = new Plan();
        if (count <= 0) throw new IllegalArgumentException("Target must be positive");
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        List<String> postorder = new ArrayList<>();
        collect(target, resolver, p, visiting, visited, postorder, 0);
        if (!p.complete()) return p; // Never publish a plausible but incomplete bill.
        Collections.reverse(postorder);
        p.demands.put(target, count);
        try {
            for (String key : postorder) {
                long demand = p.demands.getOrDefault(key, 0L);
                Recipe recipe = p.recipes.get(key);
                if (recipe == null) { p.materials.put(key, demand); continue; }
                long batches = demand / recipe.output + (demand % recipe.output == 0 ? 0 : 1);
                Map<String, Long> setup = new LinkedHashMap<>();
                for (Input input : recipe.inputs) {
                    if (input.catalyst) {
                        setup.merge(input.key, input.amount, Math::addExact);
                    } else {
                        p.demands.merge(input.key, Math.multiplyExact(batches, input.amount), Math::addExact);
                    }
                }
                // Reuse between sequential operations, but require all same-recipe slots at once.
                setup.forEach((k, v) -> p.catalysts.merge(k, v, Math::max));
            }
        } catch (ArithmeticException overflow) {
            p.problems.add("Quantity overflow"); p.materials.clear(); p.catalysts.clear();
        }
        return p;
    }
    private static void collect(String key, Resolver resolver, Plan p, Set<String> visiting,
                                Set<String> visited, List<String> order, int depth) {
        if (visiting.contains(key)) { p.problems.add("Cycle: " + key); return; }
        if (visited.contains(key)) return;
        if (depth > 16 || visited.size() + visiting.size() >= 256) {
            p.problems.add("Planning limit: " + key); return;
        }
        visiting.add(key);
        Recipe recipe = resolver.resolve(key);
        if (recipe != null) {
            p.recipes.put(key, recipe);
            for (Input input : recipe.inputs) {
                if (!input.catalyst) collect(input.key, resolver, p, visiting, visited, order, depth + 1);
            }
        }
        visiting.remove(key); visited.add(key); order.add(key);
    }
}

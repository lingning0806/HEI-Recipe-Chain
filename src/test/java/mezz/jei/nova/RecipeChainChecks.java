package mezz.jei.nova;

import mezz.jei.Internal;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.autocrafting.RecipeChain;
import mezz.jei.ingredients.IngredientRegistry;
import java.util.*;
import sun.misc.Unsafe;

/** Exercises the actual HEI graph calculation without starting a Minecraft client. */
public final class RecipeChainChecks {
    private static Unsafe unsafe;
    private static final class IdRegistry extends IngredientRegistry {
        private IdRegistry() { super(null, null, null, null, null, null); }
        @Override public String getUniqueId(Object ingredient) { return (String) ingredient; }
    }
    private static RecipeBookmarkItem<String> node(String id, long output, long goal) throws Exception {
        RecipeBookmarkItem<String> n = (RecipeBookmarkItem<String>) unsafe.allocateInstance(RecipeBookmarkItem.class);
        n.ingredient = id; n.outputAmount = output; n.selfOutputAmount = goal;
        return n;
    }
    private static void eq(long actual, long expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true); unsafe = (Unsafe) f.get(null);
        Internal.setIngredientRegistry((IngredientRegistry) unsafe.allocateInstance(IdRegistry.class));
        RecipeChain chain = new RecipeChain(null);
        RecipeBookmarkItem<String> tool = node("tool", 1, 2);
        RecipeBookmarkItem<String> stick = node("stick", 4, 0);
        RecipeBookmarkItem<String> plank = node("plank", 4, 0);
        RecipeBookmarkItem<String> log = node("log", 1, 0);
        chain.graphStorage.putEdgeValue(tool, stick, 2L);
        chain.graphStorage.putEdgeValue(tool, plank, 3L);
        chain.graphStorage.putEdgeValue(stick, plank, 2L);
        chain.graphStorage.putEdgeValue(plank, log, 1L);
        chain.calculateCrafting();
        eq(stick.amount, 4); eq(plank.amount, 8); eq(log.amount, 2);
        // Existing sticks remove their upstream demand, but direct plank demand remains.
        tool.amount = 2; stick.amount = plank.amount = log.amount = 0;
        Map<String, Long> inventory = new HashMap<>(); inventory.put("stick", 6L);
        chain.calculateMissingIngredients(stick, inventory, null, null);
        eq(stick.amount, 0); eq(inventory.get("stick"), 2);
        chain.calculateMissingIngredients(plank, inventory, null, null);
        chain.calculateMissingIngredients(log, inventory, null, null);
        eq(plank.amount, 6); eq(log.amount, 2);
        // Partial supply: exactly one stick remains to be crafted.
        stick.amount = 0; inventory.put("stick", 3L);
        chain.calculateMissingIngredients(stick, inventory, null, null);
        eq(stick.amount, 1); eq(inventory.get("stick"), 0);
        chain.graphStorage.putEdgeValue(log, tool, 1L);
        try { chain.calculateCrafting(); throw new AssertionError("cycle accepted"); }
        catch (mezz.jei.autocrafting.toposort.CyclePresentException expected) { }
        System.out.println("PASS: HEI shared intermediate counts, inventory deduction, partial supply, cycle rejection");
    }
}

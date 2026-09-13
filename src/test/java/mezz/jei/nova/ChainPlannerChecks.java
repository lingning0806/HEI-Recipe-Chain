package mezz.jei.nova;
import java.util.*;
import static mezz.jei.nova.ChainPlanner.*;
public final class ChainPlannerChecks {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static Recipe recipe(long output, Input... inputs) { return new Recipe(output, Arrays.asList(inputs)); }
    static Input use(String key, long amount) { return new Input(key, amount, false); }
    public static void main(String[] args) {
        Map<String, Recipe> recipes = new HashMap<>();
        recipes.put("a", recipe(1, use("b",1), use("c",1)));
        recipes.put("b", recipe(1, use("plate",1)));
        recipes.put("c", recipe(1, use("plate",1)));
        recipes.put("plate", recipe(3, use("ingot",1), new Input("mold",1,true)));
        Plan p = build("a",1, recipes::get);
        check(p.complete()); check(p.materials.get("ingot")==1); check(p.catalysts.get("mold")==1);
        p=build("a",64, recipes::get); check(p.materials.get("ingot")==43); check(p.catalysts.get("mold")==1);
        recipes.put("plate",recipe(1,use("a",1)));check(!build("a",1,recipes::get).complete());
        recipes.put("plate",recipe(1,use("ore",Long.MAX_VALUE)));check(!build("a",2,recipes::get).complete());
        recipes.clear(); recipes.put("a",recipe(1,new Input("mold",1,true),new Input("mold",1,true)));
        check(build("a",64,recipes::get).catalysts.get("mold")==2);
        // Four brewing stages must retain three bottles, not expand to 3^4.
        recipes.clear();
        for (int stage=1; stage<=4; stage++)
            recipes.put("potion"+stage, recipe(3, use("potion"+(stage-1),3), use("reagent"+stage,1)));
        for (long target : new long[]{1,3,4,16}) {
            Plan brewed=build("potion4",target,recipes::get);
            long batches=(target+2)/3;
            check(brewed.complete());
            check(brewed.materials.get("potion0")==batches*3);
            for(int stage=1;stage<=4;stage++)check(brewed.materials.get("reagent"+stage)==batches);
        }
        System.out.println("PASS: shared batches, catalyst reuse/slot count, cycle, overflow");
    }
}

/* SPDX-License-Identifier: LGPL-3.0-or-later
 * Local modifications: 绫宁, 2026-09-13. */
package mezz.jei.nova;
import java.util.*;
public final class RemainderFlowChecks {
    static RemainderFlow.Input input(String key,long n,String returned) { return new RemainderFlow.Input(Arrays.asList(key),n,0,returned,1); }
    static void check(boolean value) { if(!value)throw new AssertionError(); }
    public static void main(String[] args) {
        Map<String,RemainderFlow.Recipe> recipes=new LinkedHashMap<>();
        recipes.put("milk",new RemainderFlow.Recipe("milk",1,Arrays.asList(input("bucket",1,null),input("fluid",1000,null))));
        recipes.put("cake",new RemainderFlow.Recipe("cake",1,Arrays.asList(input("milk",3,"bucket"),input("sugar",2,null))));
        RemainderFlow.Result result=new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("cake",2L));
        check(result.required.get("bucket")==3 && result.required.get("fluid")==6000 && result.required.get("sugar")==4);
        check(result.steps.equals(Arrays.asList("milk","milk","milk","cake","milk","milk","milk","cake")));
        check(result.remaining.get("bucket")==3);
        result=new RemainderFlow(recipes,Collections.singletonMap("bucket",2L)).plan(Collections.singletonMap("cake",2L));
        check(result.required.get("bucket")==1);
        // Existing intermediates return their containers too.
        result=new RemainderFlow(recipes,Collections.singletonMap("milk",3L)).plan(Collections.singletonMap("cake",2L));
        check(!result.required.containsKey("bucket") && result.required.get("fluid")==3000);
        recipes.clear();
        recipes.put("part",new RemainderFlow.Recipe("part",4,Arrays.asList(input("iron",1,null))));
        recipes.put("root",new RemainderFlow.Recipe("root",1,Arrays.asList(input("part",1,null))));
        result=new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("root",3L));
        check(result.required.get("iron")==1 && result.remaining.get("part")==1);
        recipes.clear();
        recipes.put("toolwork",new RemainderFlow.Recipe("toolwork",1,Arrays.asList(new RemainderFlow.Input(Arrays.asList("tool"),2,3,null,0))));
        result=new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("toolwork",4L));
        check(result.required.get("tool")==4);
        recipes.put("loop",new RemainderFlow.Recipe("loop",1,Arrays.asList(input("loop",1,null))));
        boolean rejected=false;
        try {new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("loop",1L));}catch(IllegalArgumentException e){rejected=true;}
        check(rejected);
        recipes.clear();
        recipes.put("A",new RemainderFlow.Recipe("A",1,Arrays.asList(input("iron",1,null))));
        recipes.put("B",new RemainderFlow.Recipe("B",1,Arrays.asList(input("A",1,null))));
        recipes.put("C",new RemainderFlow.Recipe("C",1,Arrays.asList(input("B",1,null))));
        RemainderFlow.Result baseline=new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("C",3L));
        result=new RemainderFlow(recipes,Collections.emptyMap()).plan(Collections.singletonMap("C",3L),true);
        check(result.steps.equals(Arrays.asList("A","A","A","B","B","B","C","C","C")));
        check(result.required.equals(baseline.required) && result.remaining.equals(baseline.remaining));
        // Existing B lets C appear first in the trace; pending B/A must still batch first.
        Map<String,Long> partial=new HashMap<>();partial.put("B",1L);partial.put("A",1L);
        result=new RemainderFlow(recipes,partial).plan(Collections.singletonMap("C",4L),true);
        check(result.steps.equals(Arrays.asList("A","A","B","B","B","C","C","C","C")));
        recipes.clear();
        recipes.put("milk",new RemainderFlow.Recipe("milk",1,Arrays.asList(input("bucket",1,null),input("fluid",1000,null))));
        recipes.put("cake",new RemainderFlow.Recipe("cake",1,Arrays.asList(input("milk",3,"bucket"),input("sugar",2,null))));
        result=new RemainderFlow(recipes,Collections.singletonMap("bucket",3L)).plan(Collections.singletonMap("cake",2L),true);
        check(result.steps.equals(Arrays.asList("milk","milk","milk","cake","milk","milk","milk","cake")));
        check(!result.required.containsKey("bucket"));
        for(int n=1;n<=80;n++) {
            recipes.clear();
            recipes.put("A",new RemainderFlow.Recipe("A",4,Arrays.asList(new RemainderFlow.Input(Arrays.asList("iron","copper"),1,0,null,0))));
            recipes.put("B",new RemainderFlow.Recipe("B",1,Arrays.asList(input("A",2,null))));
            recipes.put("C",new RemainderFlow.Recipe("C",1,Arrays.asList(input("B",1,null),new RemainderFlow.Input(Arrays.asList("tool"),1,3,null,0))));
            Map<String,Long> goals=new LinkedHashMap<>();goals.put("C",(long)n);goals.put("B",2L);
            Map<String,Long> initial=new HashMap<>();initial.put("copper",20L);initial.put("tool",2L);
            baseline=new RemainderFlow(recipes,initial).plan(goals);
            result=new RemainderFlow(recipes,initial).plan(goals,true);
            check(result.required.equals(baseline.required) && result.remaining.equals(baseline.remaining));
            Map<String,Integer> oldCounts=new HashMap<>(),newCounts=new HashMap<>();
            for(String step:baseline.steps) oldCounts.merge(step,1,Integer::sum);
            for(String step:result.steps) newCounts.merge(step,1,Integer::sum);
            check(oldCounts.equals(newCounts));
        }
        // Ore alternatives must not turn every ordinary recipe into a scheduling barrier.
        recipes.clear();
        recipes.put("A",new RemainderFlow.Recipe("A",1,Arrays.asList(new RemainderFlow.Input(Arrays.asList("iron","copper"),1,0,null,0))));
        recipes.put("B",new RemainderFlow.Recipe("B",1,Arrays.asList(input("A",1,null))));
        result=new RemainderFlow(recipes,Collections.singletonMap("copper",16L)).plan(Collections.singletonMap("B",16L),true);
        check(result.steps.subList(0,16).equals(Collections.nCopies(16,"A")));
        check(result.steps.subList(16,32).equals(Collections.nCopies(16,"B")));
        for(int i=0;i<16;i++) check(result.execution.get(i).consumed.equals(Collections.singletonMap("copper",1L)));
        Map<String,Long> available=new HashMap<>();available.put("iron",1L);available.put("copper",1L);
        List<String> binding=MaterialBinding.bind(Arrays.asList(Arrays.asList("iron","copper"),Arrays.asList("iron")),Arrays.asList(1L,1L),available);
        check(binding.equals(Arrays.asList("copper","iron")));
        check(MaterialBinding.bind(Arrays.asList(Arrays.asList("iron"),Arrays.asList("iron")),Arrays.asList(1L,1L),available)==null);
        check(available.get("iron")==1 && available.get("copper")==1);
        System.out.println("Remainder flow checks passed");
    }
}

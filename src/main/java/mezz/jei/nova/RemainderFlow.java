/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Local adaptation and modifications: 绫宁, 2026-09-13.
 * See CONTRIBUTION_LICENSE.md and THIRD_PARTY_NOTICES.md.
 */
package mezz.jei.nova;

import java.util.*;

/** Sequential resource ledger: returns become available only AFTER all inputs are reserved.
 * Adaptation of JEI-unofficial RecipeChainMath's containerItems / reusableContainerItems model.
 * Keeps startup requirements separate from gross recipe throughput.
 */
public final class RemainderFlow {
    public static final class Input {
        public final List<String> alternatives;
        public final long count, uses, returnedCount;
        public final String returned;
        public Input(List<String> alternatives,long count,long uses,String returned,long returnedCount) {
            this.alternatives=new ArrayList<>(alternatives);this.count=count;this.uses=uses;
            this.returned=returned;this.returnedCount=returnedCount;
        }
    }
    public static final class Recipe {
        public final String output;
        public final long count;
        public final List<Input> inputs;
        public Recipe(String output,long count,List<Input> inputs) {this.output=output;this.count=count;this.inputs=inputs;}
    }
    public static final class Result {
        public final Map<String,Long> toolsRemaining=new LinkedHashMap<>();
        public final Map<String,Long> consumed=new LinkedHashMap<>();
        public final Map<String,Long> withdrawn=new LinkedHashMap<>();
        public final Map<String,Long> required=new LinkedHashMap<>();
        public final Map<String,Long> remaining=new LinkedHashMap<>();
        public final List<String> steps=new ArrayList<>();
        public final List<ExecutionSchedule.Operation> execution=new ArrayList<>();
    }
    private final Map<String,Recipe> recipes;
    private final Map<String,Long> initial;
    private final List<ExecutionSchedule.Operation> operationsTrace=new ArrayList<>();
    private final Map<String,Long> initialRemaining=new HashMap<>();
    private final Map<String,Long> stock=new LinkedHashMap<>();
    private final Map<String,List<Long>> toolLives=new HashMap<>();
    private final Set<String> active=new HashSet<>();
    private final Result result=new Result();
    private int operations;
    private boolean traceEnabled;
    public RemainderFlow(Map<String,Recipe> recipes,Map<String,Long> initial) {
        this.recipes=recipes;this.initial=new HashMap<>(initial);stock.putAll(initial);initialRemaining.putAll(initial);
    }
    public Result plan(Map<String,Long> goals) { return plan(goals,false); }
    public Result plan(Map<String,Long> goals,boolean schedule) {
        traceEnabled=schedule;
        for(Map.Entry<String,Long> goal:goals.entrySet()) {
            Recipe recipe=recipes.get(goal.getKey());
            if(recipe==null || recipe.count<=0)throw new IllegalArgumentException("Invalid goal recipe");
            long batches=ChainMath.batches(goal.getValue(),recipe.count);
            for(long i=0;i<batches;i++)craft(recipe);
            // Requested products are reserved, not reused to satisfy another final goal.
            take(goal.getKey(),goal.getValue(),false);
            if(traceEnabled) {
                ExecutionSchedule.Operation reserve=new ExecutionSchedule.Operation(null,true);
                reserve.consumed.put(goal.getKey(),goal.getValue()); operationsTrace.add(reserve);
            }
        }
        result.remaining.putAll(stock);
        toolLives.forEach((key,lives)->{if(!lives.isEmpty())result.toolsRemaining.put(key,(long)lives.size());});
        if(schedule) {
            List<ExecutionSchedule.Operation> ordered=ExecutionSchedule.order(operationsTrace,initial,result.required,result.remaining);
            result.steps.clear();
            for(ExecutionSchedule.Operation op:ordered) if(op.recipe!=null) {result.steps.add(op.recipe);result.execution.add(op);}
        }
        return result;
    }
    private void add(Map<String,Long> target,String key,long amount) {
        if(amount>0)target.put(key,Math.addExact(target.getOrDefault(key,0L),amount));
    }
    private void take(String key,long amount) {take(key,amount,true);}
    private void take(String key,long amount,boolean material) {
        long available=stock.getOrDefault(key,0L);
        if(available<amount)throw new IllegalStateException("Unreserved resource");
        long fromInitial=material?Math.min(amount,initialRemaining.getOrDefault(key,0L)):0;
        if(fromInitial>0) {initialRemaining.put(key,initialRemaining.get(key)-fromInitial);add(result.withdrawn,key,fromInitial);}
        stock.put(key,available-amount);
    }
    private void acquire(String key,long amount) {
        long missing=Math.max(0,amount-stock.getOrDefault(key,0L));
        if(missing==0)return;
        Recipe source=recipes.get(key);
        if(source==null) {add(result.required,key,missing);add(stock,key,missing);return;}
        long batches=ChainMath.batches(missing,source.count);
        for(long i=0;i<batches;i++)craft(source);
    }
    private String choose(Input input) {
        for(String key:input.alternatives)if(stock.getOrDefault(key,0L)>0 || !toolLives.getOrDefault(key,Collections.emptyList()).isEmpty())return key;
        for(String key:input.alternatives)if(recipes.containsKey(key))return key;
        if(input.alternatives.isEmpty())throw new IllegalArgumentException("No input alternatives");
        return input.alternatives.get(0);
    }
    private void craft(Recipe recipe) {
        if(++operations>65536)throw new IllegalArgumentException("返还物流转超过65536步，请减少目标数量");
        if(!active.add(recipe.output))throw new IllegalArgumentException("循环配方无法确定启动资源");
        ExecutionSchedule.Operation operation=traceEnabled ? new ExecutionSchedule.Operation(recipe.output,
                recipe.inputs.stream().anyMatch(input->input.uses>0 || input.returned!=null)) : null;
        List<Runnable> returns=new ArrayList<>();
        try {
            for(Input input:recipe.inputs) {
                if(input.count<0)throw new IllegalArgumentException("Negative input");
                String key=choose(input);add(result.consumed,key,input.count);
                if(input.uses>0) {
                    if(input.count>65536)throw new IllegalArgumentException("工具槽位数量过大");
                    List<Long> lives=toolLives.computeIfAbsent(key,k->new ArrayList<>());
                    for(long n=0;n<input.count;n++) {
                        long lifetime;
                        if(!lives.isEmpty())lifetime=lives.remove(lives.size()-1);
                        else {acquire(key,1);take(key,1);if(operation!=null)add(operation.consumed,key,1);lifetime=input.uses;}
                        long left=lifetime==Long.MAX_VALUE?lifetime:lifetime-1;
                        if(left>0)returns.add(()->lives.add(left));
                    }
                } else {
                    acquire(key,input.count);take(key,input.count);if(operation!=null)add(operation.consumed,key,input.count);
                    if(input.returned!=null) {
                        long amount=Math.multiplyExact(input.count,input.returnedCount);
                        returns.add(()->add(stock,input.returned,amount));
                        if(operation!=null)add(operation.produced,input.returned,amount);
                    }
                }
            }
            for(Runnable returned:returns)returned.run();
            add(stock,recipe.output,recipe.count);
            result.steps.add(recipe.output);
            if(operation!=null) {add(operation.produced,recipe.output,recipe.count);operationsTrace.add(operation);}
        } finally {active.remove(recipe.output);}
    }
}

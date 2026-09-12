/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Local adaptation and modifications: 绫宁, 2026-09-13.
 * See CONTRIBUTION_LICENSE.md and THIRD_PARTY_NOTICES.md.
 */
package mezz.jei.nova;

import java.util.*;

/** Reorders only ordinary operations, using the choices already made by the resource ledger. */
public final class ExecutionSchedule {
    public static final class Operation {
        public final String recipe;
        public final boolean barrier;
        public final Map<String, Long> consumed = new LinkedHashMap<>();
        public final Map<String, Long> produced = new LinkedHashMap<>();
        public Operation(String recipe, boolean barrier) { this.recipe=recipe; this.barrier=barrier; }
    }
    private ExecutionSchedule() {}
    public static List<Operation> order(List<Operation> operations, Map<String,Long> initial,
                                     Map<String,Long> required, Map<String,Long> expected) {
        Map<String,Long> stock=new HashMap<>(initial);
        required.forEach((key,value)->stock.merge(key,value,Math::addExact));
        List<Operation> ordered=new ArrayList<>();
        int position=0;
        while(position<operations.size()) {
            Operation first=operations.get(position);
            if(first.barrier) {
                if(!apply(first,stock,ordered)) return original(operations);
                position++;
                continue;
            }
            Map<String,Deque<Operation>> pending=new LinkedHashMap<>();
            while(position<operations.size() && !operations.get(position).barrier) {
                Operation op=operations.get(position++);
                pending.computeIfAbsent(op.recipe,key->new ArrayDeque<>()).add(op);
            }
            while(!pending.isEmpty()) {
                boolean advanced=false;
                for(Iterator<Deque<Operation>> iterator=pending.values().iterator();iterator.hasNext();) {
                    Deque<Operation> queue=iterator.next();
                    if(hasPendingProducer(queue.peek(),pending)) continue;
                    if(apply(queue.peek(),stock,ordered)) {
                        queue.remove();
                        if(queue.isEmpty()) iterator.remove();
                        advanced=true;
                        break; // Stable recipe priority; finish ready intermediates before their users.
                    }
                }
                if(!advanced) {
                    // Cyclic resource routes retain the original feasible order.
                    for(Iterator<Deque<Operation>> iterator=pending.values().iterator();iterator.hasNext();) {
                        Deque<Operation> queue=iterator.next();
                        if(apply(queue.peek(),stock,ordered)) {
                            queue.remove();if(queue.isEmpty())iterator.remove();advanced=true;break;
                        }
                    }
                    if(!advanced)return original(operations);
                }
            }
        }
        Set<String> keys=new HashSet<>(stock.keySet()); keys.addAll(expected.keySet());
        for(String key:keys) if(!stock.getOrDefault(key,0L).equals(expected.getOrDefault(key,0L)))
            return original(operations);
        return ordered;
    }
    private static boolean hasPendingProducer(Operation consumer,Map<String,Deque<Operation>> pending) {
        for(Deque<Operation> queue:pending.values()) {
            if(queue.peek().recipe.equals(consumer.recipe))continue;
            for(String input:consumer.consumed.keySet())
                if(queue.peek().produced.getOrDefault(input,0L)>0)return true;
        }
        return false;
    }
    private static boolean apply(Operation op,Map<String,Long> stock,List<Operation> ordered) {
        for(Map.Entry<String,Long> input:op.consumed.entrySet())
            if(stock.getOrDefault(input.getKey(),0L)<input.getValue()) return false;
        op.consumed.forEach((key,value)->stock.put(key,stock.getOrDefault(key,0L)-value));
        op.produced.forEach((key,value)->stock.merge(key,value,Math::addExact));
        ordered.add(op);
        return true;
    }
    private static List<Operation> original(List<Operation> operations) { return new ArrayList<>(operations); }
}
